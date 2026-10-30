package com.example.linkgrab

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Private downloads live in the app's internal storage (filesDir). Other apps, file managers
 * and the gallery can't read that folder, the files never go through MediaStore, and Android
 * deletes the whole folder when the app is uninstalled.
 */
object PrivateStore {
    data class Entry(
        val id: String,
        val fileName: String,
        val title: String,
        val audio: Boolean,
        val thumbFileName: String?,
        val sizeBytes: Long,
        val savedAt: Long,
    )

    private lateinit var dir: File
    private val lock = Any()
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries

    private val indexFile get() = File(dir, "index.json")

    fun init(context: Context) {
        dir = File(context.filesDir, "private").apply { mkdirs() }
        val loaded = load()
        val existing = loaded.filter { File(dir, it.fileName).exists() }
        _entries.value = existing
        if (existing.size != loaded.size) persist(existing)
    }

    fun file(entry: Entry): File = File(dir, entry.fileName)

    fun thumb(entry: Entry): File? = entry.thumbFileName?.let { File(dir, it) }?.takeIf { it.exists() }

    /** Copies [src] into private storage and records it. The caller deletes [src] afterwards. */
    fun save(src: File, title: String, audio: Boolean, thumbBytes: ByteArray?): Entry =
        synchronized(lock) {
            val id = UUID.randomUUID().toString()
            val fileName = "$id.${src.extension.ifBlank { "bin" }}"
            val dest = File(dir, fileName)
            src.copyTo(dest, overwrite = false)

            var thumbName: String? = null
            if (thumbBytes != null && thumbBytes.isNotEmpty()) {
                thumbName = "$id.thumb"
                runCatching { File(dir, thumbName).writeBytes(thumbBytes) }.onFailure { thumbName = null }
            }

            val entry = Entry(id, fileName, title, audio, thumbName, dest.length(), System.currentTimeMillis())
            _entries.update { it + entry }
            persist(_entries.value)
            entry
        }

    fun delete(id: String) {
        synchronized(lock) {
            val entry = _entries.value.firstOrNull { it.id == id } ?: return
            File(dir, entry.fileName).delete()
            entry.thumbFileName?.let { File(dir, it).delete() }
            _entries.update { list -> list.filterNot { it.id == id } }
            persist(_entries.value)
        }
    }

    private fun persist(list: List<Entry>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("fileName", it.fileName)
                    .put("title", it.title)
                    .put("audio", it.audio)
                    .put("thumbFileName", it.thumbFileName ?: JSONObject.NULL)
                    .put("sizeBytes", it.sizeBytes)
                    .put("savedAt", it.savedAt)
            )
        }
        val tmp = File(dir, "index.json.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(indexFile)
    }

    private fun load(): List<Entry> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(indexFile.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Entry(
                    id = o.getString("id"),
                    fileName = o.getString("fileName"),
                    title = o.getString("title"),
                    audio = o.optBoolean("audio"),
                    thumbFileName = if (o.isNull("thumbFileName")) null else o.getString("thumbFileName"),
                    sizeBytes = o.optLong("sizeBytes"),
                    savedAt = o.optLong("savedAt"),
                )
            }
        }.getOrDefault(emptyList())
    }
}
