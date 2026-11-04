package com.example.linkgrab

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** A folder inside the Downloaded list or the app-only list. */
data class LibFolder(val id: String, val name: String, val parentId: String?)

/** One file as the shared library screen sees it. */
class LibItem(
    val id: String,
    val title: String,
    val audio: Boolean,
    val sizeBytes: Long,
    val thumb: Any?,
    val folderId: String?,
)

/** Extra action on selected files (for example moving them to the app-only list). */
class ExtraAction(val label: String, val confirmTitle: String, val confirmText: String, val run: (List<String>) -> Unit)

class LibraryActions(
    val createFolder: (name: String, parentId: String?) -> String?,
    val renameFolder: (id: String, name: String) -> String?,
    val renameItem: (id: String, name: String) -> Unit,
    val moveItems: (ids: List<String>, folderId: String?) -> Unit,
    val moveFolders: (ids: List<String>, parentId: String?) -> Unit,
    val deleteItems: (ids: List<String>) -> Unit,
    val deleteFolders: (ids: List<String>) -> Unit,
    val descendants: (id: String) -> Set<String>,
    val play: (id: String) -> Unit,
    val extra: ExtraAction? = null,
)

/** Keeps the folder tree in a small JSON file. Files themselves record which folder they are in. */
class FolderStore(private val file: File) {
    private val _folders = MutableStateFlow<List<LibFolder>>(emptyList())
    val folders: StateFlow<List<LibFolder>> = _folders

    init {
        _folders.value = runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                LibFolder(o.getString("id"), o.getString("name"), if (o.isNull("parentId")) null else o.getString("parentId"))
            }
        }.getOrDefault(emptyList())
    }

    private fun clash(name: String, parentId: String?, exceptId: String?) =
        _folders.value.any { it.parentId == parentId && it.id != exceptId && it.name.equals(name, ignoreCase = true) }

    /** Returns an error message, or null when the folder was created. */
    @Synchronized
    fun create(rawName: String, parentId: String?): String? {
        val name = DownloadRepository.safeName(rawName.trim().ifBlank { return "Enter a name" })
        if (clash(name, parentId, null)) return "A folder with that name already exists here"
        _folders.value = _folders.value + LibFolder(UUID.randomUUID().toString(), name, parentId)
        persist()
        return null
    }

    @Synchronized
    fun rename(id: String, rawName: String): String? {
        val folder = _folders.value.firstOrNull { it.id == id } ?: return "Folder not found"
        val name = DownloadRepository.safeName(rawName.trim().ifBlank { return "Enter a name" })
        if (clash(name, folder.parentId, id)) return "A folder with that name already exists here"
        _folders.value = _folders.value.map { if (it.id == id) it.copy(name = name) else it }
        persist()
        return null
    }

    @Synchronized
    fun move(id: String, parentId: String?) {
        if (parentId == id || parentId in descendants(id)) return
        _folders.value = _folders.value.map { if (it.id == id) it.copy(parentId = parentId) else it }
        persist()
    }

    /** All folders inside [id], at any depth (not including [id] itself). */
    fun descendants(id: String): Set<String> {
        val result = HashSet<String>()
        var frontier = listOf(id)
        while (frontier.isNotEmpty()) {
            val next = _folders.value.filter { it.parentId in frontier && result.add(it.id) }.map { it.id }
            frontier = next
        }
        return result
    }

    @Synchronized
    fun remove(ids: Set<String>) {
        _folders.value = _folders.value.filterNot { it.id in ids }
        persist()
    }

    private fun persist() {
        val arr = JSONArray()
        _folders.value.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("name", it.name).put("parentId", it.parentId ?: JSONObject.NULL)
            )
        }
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(arr.toString())
            tmp.renameTo(file)
        }
    }
}
