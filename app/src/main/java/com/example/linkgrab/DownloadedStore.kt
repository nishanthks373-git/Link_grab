package com.example.linkgrab

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Record of the normal (non-hidden) downloads saved to Downloads/LinkGrab, so the Downloaded
 * tab can list them with thumbnails. Files deleted outside the app drop off the list.
 */
object DownloadedStore {
    data class Entry(
        val id: String,
        val uri: String,
        val title: String,
        val audio: Boolean,
        val thumbFileName: String?,
        val sizeBytes: Long,
        val savedAt: Long,
    )

    private lateinit var app: Context
    private lateinit var dir: File
    private val lock = Any()
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries

    private val indexFile get() = File(dir, "index.json")

    fun init(context: Context) {
        app = context.applicationContext
        dir = File(app.filesDir, "downloaded").apply { mkdirs() }
        _entries.value = load()
        verify()
    }

    fun thumb(entry: Entry): File? = entry.thumbFileName?.let { File(dir, it) }?.takeIf { it.exists() }

    fun add(uri: Uri, title: String, audio: Boolean, thumbBytes: ByteArray?, sizeBytes: Long) {
        synchronized(lock) {
            val id = UUID.randomUUID().toString()
            var thumbName: String? = null
            if (thumbBytes != null && thumbBytes.isNotEmpty()) {
                thumbName = "$id.thumb"
                runCatching { File(dir, thumbName).writeBytes(thumbBytes) }.onFailure { thumbName = null }
            }
            _entries.update { it + Entry(id, uri.toString(), title, audio, thumbName, sizeBytes, System.currentTimeMillis()) }
            persist(_entries.value)
        }
    }

    /** Deletes the file from Downloads and removes it from the list. */
    fun delete(id: String) {
        synchronized(lock) {
            val entry = _entries.value.firstOrNull { it.id == id } ?: return
            runCatching { app.contentResolver.delete(Uri.parse(entry.uri), null, null) }
            entry.thumbFileName?.let { File(dir, it).delete() }
            _entries.update { list -> list.filterNot { it.id == id } }
            persist(_entries.value)
        }
    }

    /**
     * Moves a normal download into the app-only storage and removes it from Downloads, so no other
     * app can see it any more. Returns false if the copy failed (nothing is removed then).
     */
    fun moveToHidden(entry: Entry): Boolean {
        val uri = Uri.parse(entry.uri)
        val name = runCatching {
            app.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull()
        val ext = name?.substringAfterLast('.', "").orEmpty().ifBlank { "bin" }
        val tmp = File(app.cacheDir, "move_${entry.id}.$ext")
        return try {
            app.contentResolver.openInputStream(uri)?.use { i -> tmp.outputStream().use { i.copyTo(it) } }
                ?: return false
            PrivateStore.save(tmp, entry.title, entry.audio, thumb(entry)?.readBytes())
            delete(entry.id)
            true
        } catch (e: Throwable) {
            false
        } finally {
            tmp.delete()
        }
    }

    /** Drops entries whose file no longer exists (deleted with a file manager, for example). */
    fun verify() {
        synchronized(lock) {
            val kept = _entries.value.filter { exists(it) }
            if (kept.size != _entries.value.size) {
                _entries.value.filterNot { it in kept }.forEach { e -> e.thumbFileName?.let { File(dir, it).delete() } }
                _entries.value = kept
                persist(kept)
            }
        }
    }

    private fun exists(entry: Entry): Boolean = runCatching {
        app.contentResolver.query(Uri.parse(entry.uri), arrayOf("_id"), null, null, null)?.use { it.count > 0 } ?: false
    }.getOrDefault(true)

    private fun persist(list: List<Entry>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("uri", it.uri)
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
                    uri = o.getString("uri"),
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
