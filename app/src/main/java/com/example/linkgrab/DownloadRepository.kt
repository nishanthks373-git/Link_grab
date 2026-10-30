package com.example.linkgrab

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Holds the download queue and does the actual work. A process-wide singleton so the
 * UI and the foreground service see the same state.
 */
object DownloadRepository {
    private const val MAX_PARALLEL = 2

    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items: StateFlow<List<DownloadItem>> = _items

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(MAX_PARALLEL)
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val nextId = AtomicLong(1)
    private val http = OkHttpClient()

    private lateinit var app: Context
    private val engineLock = Any()
    private var engineReady = false

    fun init(context: Context) {
        app = context.applicationContext
    }

    // ---------------------------------------------------------------- queue API

    /** Adds chosen downloads to the queue. Returns how many were new (identical active ones are skipped). */
    fun enqueue(requests: List<QueueRequest>): Int {
        val alreadyActive = _items.value.filter { it.isActive }.map { listOf(it.url, it.mode, it.height, it.isPrivate) }.toSet()
        val fresh = requests.filter { listOf(it.url, it.mode, it.height, it.isPrivate) !in alreadyActive }
        if (fresh.isEmpty()) return 0

        val newItems = fresh.map {
            DownloadItem(
                id = nextId.getAndIncrement(),
                url = it.url,
                mode = it.mode,
                height = it.height,
                title = it.title,
                thumbnail = it.thumbnail,
                isPrivate = it.isPrivate,
            )
        }
        _items.update { it + newItems }
        DownloadService.start(app)
        newItems.forEach { launchJob(it.id) }
        return newItems.size
    }

    fun cancel(id: Long) {
        patch(id) { it.copy(status = Status.CANCELLED, message = "Cancelled") }
        jobs.remove(id)?.cancel()
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId(id)) }
    }

    fun retry(id: Long) {
        patch(id) { it.copy(status = Status.QUEUED, progress = -1f, message = null) }
        DownloadService.start(app)
        launchJob(id)
    }

    fun remove(id: Long) {
        jobs.remove(id)?.cancel()
        _items.update { list -> list.filterNot { it.id == id } }
    }

    fun clearFinished() {
        _items.update { list -> list.filter { it.isActive } }
    }

    /** yt-dlp needs updating regularly because sites change. Returns a short status message. */
    fun updateYtDlp(): String = try {
        prepareEngine()
        YoutubeDL.getInstance().updateYoutubeDL(app)
        "yt-dlp is up to date"
    } catch (e: Throwable) {
        "Update failed: ${e.message}"
    }

    // ----------------------------------------------------------------- internals

    private fun processId(id: Long) = "lg-$id"

    private fun patch(id: Long, change: (DownloadItem) -> DownloadItem) {
        _items.update { list -> list.map { if (it.id == id) change(it) else it } }
    }

    private fun patchIfRunning(id: Long, change: (DownloadItem) -> DownloadItem) {
        _items.update { list ->
            list.map { if (it.id == id && it.status == Status.RUNNING) change(it) else it }
        }
    }

    private fun current(id: Long) = _items.value.firstOrNull { it.id == id }

    private fun launchJob(id: Long) {
        jobs[id] = scope.launch { gate.withPermit { runItem(id) } }
    }

    private suspend fun runItem(id: Long) {
        val item = current(id) ?: return
        if (item.status == Status.CANCELLED) return
        patch(id) { it.copy(status = Status.RUNNING, progress = -1f, message = null) }

        val tmp = File(app.cacheDir, "dl/$id").apply { deleteRecursively(); mkdirs() }
        try {
            val file = if (LinkParser.useDirect(item.url, item.mode)) {
                downloadDirect(item, tmp)
            } else {
                downloadWithYtDlp(item, tmp)
            }
            val savedName = if (item.isPrivate) savePrivately(item, file) else saveToDownloads(file)
            patch(id) {
                it.copy(
                    status = Status.DONE,
                    progress = 1f,
                    title = savedName,
                    message = if (item.isPrivate) "Saved privately. Open the Private tab to play it"
                    else "Saved to Downloads/LinkGrab",
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // A user cancel kills the process and lands here too; keep the Cancelled state.
            if (current(id)?.status != Status.CANCELLED) {
                patch(id) {
                    it.copy(
                        status = Status.FAILED,
                        message = e.message?.take(300) ?: e.javaClass.simpleName,
                    )
                }
            }
        } finally {
            tmp.deleteRecursively()
            jobs.remove(id)
        }
    }

    // --------------------------------------------------------- direct file links

    private suspend fun downloadDirect(item: DownloadItem, dir: File): File {
        val request = Request.Builder().url(item.url).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body ?: error("Empty response")
            val total = body.contentLength()

            val name = fileNameFor(item, response.header("Content-Disposition"))
            val out = File(dir, name)
            patchIfRunning(item.id) { it.copy(title = name) }

            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total > 0) {
                            val p = done.toFloat() / total
                            patchIfRunning(item.id) { it.copy(progress = p) }
                        }
                    }
                }
            }
            return out
        }
    }

    private fun fileNameFor(item: DownloadItem, contentDisposition: String?): String {
        val fromHeader = contentDisposition
            ?.let { Regex("""filename\*?=(?:UTF-8'')?"?([^";]+)"?""", RegexOption.IGNORE_CASE).find(it) }
            ?.groupValues?.get(1)
        val fromUrl = android.net.Uri.parse(item.url).lastPathSegment
        val raw = (fromHeader ?: fromUrl ?: "download-${item.id}")
        return raw.replace(Regex("""[\\/:*?"<>|]"""), "_").take(150)
    }

    // ------------------------------------------------------------------- yt-dlp

    /** Unpacks yt-dlp and ffmpeg on first use. Safe to call repeatedly from any thread. */
    fun prepareEngine() {
        synchronized(engineLock) {
            if (engineReady) return
            YoutubeDL.getInstance().init(app)
            FFmpeg.getInstance().init(app)
            engineReady = true
        }
    }

    private fun downloadWithYtDlp(item: DownloadItem, dir: File): File {
        prepareEngine()
        val request = YoutubeDLRequest(item.url).apply {
            addOption("--no-playlist")
            addOption("--no-mtime")
            addOption("-o", "${dir.absolutePath}/%(title).150s.%(ext)s")
            if (item.mode == Mode.AUDIO) {
                addOption("-x")
                addOption("--audio-format", "mp3")
                addOption("--audio-quality", "192K")
            } else {
                val h = item.height
                val selector = if (h != null) {
                    "bv*[height<=$h][ext=mp4]+ba[ext=m4a]/b[height<=$h][ext=mp4]/bv*[height<=$h]+ba/b[height<=$h]/b"
                } else {
                    "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/bv*+ba/b"
                }
                addOption("-f", selector)
                addOption("--merge-output-format", "mp4")
            }
        }

        YoutubeDL.getInstance().execute(request, processId(item.id)) { progress, _, line ->
            patchIfRunning(item.id) {
                it.copy(
                    progress = if (progress >= 0f) progress / 100f else it.progress,
                    message = line.lineSequence().lastOrNull { l -> l.isNotBlank() }?.take(120),
                )
            }
        }

        val result = dir.listFiles().orEmpty()
            .filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
            .maxByOrNull { it.length() }
            ?: error("yt-dlp finished but produced no file")
        patchIfRunning(item.id) { it.copy(title = result.name) }
        return result
    }

    // ------------------------------------------------------------ save privately

    /** Moves the file into the app's private storage; it never touches Downloads or MediaStore. */
    private fun savePrivately(item: DownloadItem, file: File): String {
        val thumbBytes = item.thumbnail?.let { url ->
            runCatching {
                http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    if (r.isSuccessful) r.body?.bytes() else null
                }
            }.getOrNull()
        }
        val title = file.nameWithoutExtension
        PrivateStore.save(file, title, item.mode == Mode.AUDIO, thumbBytes)
        return title
    }

    // --------------------------------------------------------- save to Downloads

    /** Copies the finished file into Downloads/LinkGrab via MediaStore (no storage permission needed). */
    private fun saveToDownloads(file: File): String {
        val ext = file.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        val resolver = app.contentResolver

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/LinkGrab")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create file in Downloads")

        try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: error("Could not open output file")
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null,
                null,
            )
        } catch (e: Throwable) {
            resolver.delete(uri, null, null)
            throw e
        }
        return file.name
    }
}
