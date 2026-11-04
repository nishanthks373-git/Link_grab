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
        val folderId: String? = null,
    )

    private lateinit var app: Context
    private lateinit var dir: File
    lateinit var folderStore: FolderStore
    private val lock = Any()
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries

    private val indexFile get() = File(dir, "index.json")

    fun init(context: Context) {
        app = context.applicationContext
        dir = File(app.filesDir, "downloaded").apply { mkdirs() }
        folderStore = FolderStore(File(dir, "folders.json"))
        _entries.value = load()
        verify()
        Thread { runCatching { importExisting() } }.start()
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

    /** Lists files saved by earlier versions of the app (before this list existed). */
    private fun importExisting() {
        val known = _entries.value.map { it.uri }.toSet()
        val found = ArrayList<Entry>()
        val collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val cols = arrayOf(
            android.provider.MediaStore.Downloads._ID,
            android.provider.MediaStore.Downloads.DISPLAY_NAME,
            android.provider.MediaStore.Downloads.MIME_TYPE,
            android.provider.MediaStore.Downloads.SIZE,
            android.provider.MediaStore.Downloads.DATE_ADDED,
        )
        app.contentResolver.query(
            collection, cols,
            "${android.provider.MediaStore.Downloads.RELATIVE_PATH} LIKE ?", arrayOf("Download/LinkGrab%"),
            "${android.provider.MediaStore.Downloads.DATE_ADDED} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val uri = android.content.ContentUris.withAppendedId(collection, c.getLong(0)).toString()
                if (uri in known) continue
                val name = c.getString(1) ?: continue
                found += Entry(
                    id = UUID.randomUUID().toString(),
                    uri = uri,
                    title = name.substringBeforeLast('.'),
                    audio = c.getString(2)?.startsWith("audio") == true,
                    thumbFileName = null,
                    sizeBytes = c.getLong(3),
                    savedAt = c.getLong(4) * 1000,
                )
            }
        }
        if (found.isEmpty()) return
        synchronized(lock) {
            _entries.update { it + found }
            persist(_entries.value)
        }
    }

    /** Renames the file in Downloads as well as in the list. */
    fun rename(id: String, newTitle: String) {
        val title = DownloadRepository.safeName(newTitle)
        synchronized(lock) {
            val entry = _entries.value.firstOrNull { it.id == id } ?: return
            runCatching {
                val uri = Uri.parse(entry.uri)
                val old = app.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { if (it.moveToFirst()) it.getString(0) else null }
                val ext = old?.substringAfterLast('.', "").orEmpty()
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, if (ext.isBlank()) title else "$title.$ext")
                }
                app.contentResolver.update(uri, values, null, null)
            }
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
                    uri = o.getString("uri"),
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
