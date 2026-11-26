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
        val folderId: String? = null,
    )

    private lateinit var dir: File
    lateinit var folderStore: FolderStore
    private lateinit var app: Context
    private val lock = Any()
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries

    private val indexFile get() = File(dir, "index.json")

    fun init(context: Context) {
        app = context.applicationContext
        dir = File(context.filesDir, "private").apply { mkdirs() }
        folderStore = FolderStore(File(dir, "folders.json"))
        val loaded = load()
        val existing = loaded.filter { File(dir, it.fileName).exists() }
        _entries.value = existing
        if (existing.size != loaded.size) persist(existing)
    }

    fun file(entry: Entry): File = File(dir, entry.fileName)

    /** True if a private file with this name (without extension) and extension already exists. */
    fun exists(base: String, ext: String): Boolean = _entries.value.any {
        it.title.equals(base, ignoreCase = true) && File(it.fileName).extension.equals(ext, ignoreCase = true)
    }

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

    // ------------------------------------------------------------------ export

    /** Hidden folder (leading dot plus a .nomedia file) so gallery and video players ignore it. */
    private const val EXPORT_DIR = "Download/.LinkGrabExport"

    private fun folderPath(folderId: String?): String {
        val names = ArrayList<String>()
        var id = folderId
        while (id != null) {
            val f = folderStore.folders.value.firstOrNull { it.id == id } ?: break
            names.add(0, DownloadRepository.safeName(f.name))
            id = f.parentId
        }
        return names.joinToString("/")
    }

    private fun ensureNoMedia() {
        runCatching {
            val resolver = app.contentResolver
            val collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val exists = resolver.query(
                collection, arrayOf(android.provider.MediaStore.Downloads._ID),
                "${android.provider.MediaStore.Downloads.DISPLAY_NAME} = ? AND ${android.provider.MediaStore.Downloads.RELATIVE_PATH} = ?",
                arrayOf(".nomedia", "$EXPORT_DIR/"), null,
            )?.use { it.count > 0 } ?: false
            if (!exists) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, ".nomedia")
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, EXPORT_DIR)
                }
                resolver.insert(collection, values)
            }
        }
    }

    private fun exportEntry(entry: Entry): Boolean {
        val src = file(entry)
        if (!src.exists()) return false
        val ext = src.extension.ifBlank { "bin" }
        val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
            ?: if (entry.audio) "audio/mpeg" else "video/mp4"
        val sub = folderPath(entry.folderId)
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "${DownloadRepository.safeName(entry.title)}.$ext")
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, if (sub.isEmpty()) EXPORT_DIR else "$EXPORT_DIR/$sub")
            put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = app.contentResolver
        val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            val out = resolver.openOutputStream(uri) ?: throw java.io.IOException("Can't write")
            out.use { o -> src.inputStream().use { it.copyTo(o) } }
            val done = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(uri, done, null, null)
            true
        } catch (e: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            false
        }
    }

    /**
     * Copies the chosen files (and everything inside the chosen folders) to the hidden export folder,
     * keeping the folder layout, then removes the copies from the app. Returns (exported, total).
     */
    fun exportOut(itemIds: Set<String>, folderIds: Set<String>): Pair<Int, Int> {
        val allFolders = folderIds.flatMap { folderStore.descendants(it) + it }.toSet()
        val targets = _entries.value.filter { it.id in itemIds || it.folderId in allFolders }
        if (targets.isNotEmpty()) ensureNoMedia()
        var ok = 0
        targets.forEach {
            if (exportEntry(it)) {
                delete(it.id)
                ok++
            }
        }
        if (ok == targets.size && allFolders.isNotEmpty()) folderStore.remove(allFolders)
        return ok to targets.size
    }

    fun rename(id: String, newTitle: String) {
        val title = DownloadRepository.safeName(newTitle)
        synchronized(lock) {
            _entries.update { list -> list.map { if (it.id == id) it.copy(title = title) else it } }
            persist(_entries.value)
        }
    }

    fun moveTo(ids: Set<String>, folderId: String?) {
        synchronized(lock) {
            _entries.update { list -> list.map { if (it.id in ids) it.copy(folderId = folderId) else it } }
            persist(_entries.value)
        }
    }

    /** Deletes a folder, everything inside it and all its sub-folders. */
    fun deleteFolder(id: String) {
        val folders = folderStore.descendants(id) + id
        _entries.value.filter { it.folderId in folders }.forEach { delete(it.id) }
        folderStore.remove(folders)
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
                    .put("folderId", it.folderId ?: JSONObject.NULL)
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
                    folderId = if (o.isNull("folderId")) null else o.getString("folderId"),
                )
            }
        }.getOrDefault(emptyList())
    }
}
