package com.example.linkgrab

import android.content.ContentValues
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
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
    private const val MAX_PARALLEL = 5

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
        val alreadyActive = _items.value.filter { it.isActive }
            .map { listOf(it.url, it.mode, it.height, it.isPrivate, it.baseName) }.toSet()
        val fresh = requests.filter { listOf(it.url, it.mode, it.height, it.isPrivate, planName(it).first) !in alreadyActive }
        if (fresh.isEmpty()) return 0

        val newItems = fresh.map {
            val (base, ext) = planName(it)
            DownloadItem(
                id = nextId.getAndIncrement(),
                url = it.url,
                mode = it.mode,
                height = it.height,
                title = it.title,
                thumbnail = it.thumbnail,
                isPrivate = it.isPrivate,
                baseName = base,
                ext = ext,
            )
        }
        _items.update { it + newItems }
        DownloadService.start(app)
        newItems.forEach { launchJob(it.id) }
        return newItems.size
    }

    // ------------------------------------------------------- file names and duplicates

    /** Removes characters that can't go in a file name. */
    fun safeName(raw: String): String {
        val cleaned = raw.replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), "_")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trim('.')
        return cleaned.take(150).trim().ifBlank { "download" }
    }

    /** The (name without extension, extension) a request will be saved as. */
    fun planName(r: QueueRequest): Pair<String, String> {
        val (base, ext) = if (LinkParser.useDirect(r.url, r.mode)) {
            val seg = safeName(android.net.Uri.parse(r.url).lastPathSegment ?: "download")
            val f = File(seg)
            f.nameWithoutExtension.ifBlank { "download" } to f.extension.ifBlank { "bin" }
        } else {
            safeName(r.title ?: android.net.Uri.parse(r.url).host ?: "download") to
                (if (r.mode == Mode.AUDIO) "mp3" else "mp4")
        }
        return (r.baseName ?: base) to ext
    }

    /** True if a file with this name is already saved, or is queued to be saved. */
    private fun nameTaken(base: String, ext: String, isPrivate: Boolean): Boolean {
        val queued = _items.value.any {
            it.isActive && it.isPrivate == isPrivate &&
                it.baseName.equals(base, ignoreCase = true) && it.ext.equals(ext, ignoreCase = true)
        }
        if (queued) return true
        return if (isPrivate) PrivateStore.exists(base, ext) else existsInDownloads("$base.$ext")
    }

    private fun existsInDownloads(displayName: String): Boolean = runCatching {
        app.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH} LIKE ?",
            arrayOf(displayName, "Download/LinkGrab%"),
            null,
        )?.use { it.count > 0 } ?: false
    }.getOrDefault(false)

    /** Indexes of requests whose file name already exists, is queued, or repeats earlier in the batch. */
    fun duplicateIndexes(requests: List<QueueRequest>): Set<Int> {
        val seen = HashSet<String>()
        val dups = HashSet<Int>()
        requests.forEachIndexed { i, r ->
            val (base, ext) = planName(r)
            val key = "${r.isPrivate}|${base.lowercase()}.${ext.lowercase()}"
            if (!seen.add(key) || nameTaken(base, ext, r.isPrivate)) dups += i
        }
        return dups
    }

    /** A copy of [r] named "<name>_1", "<name>_2"... whichever is free. [taken] tracks names used in this batch. */
    fun renamedCopy(r: QueueRequest, taken: MutableSet<String>): QueueRequest {
        val (base, ext) = planName(r)
        var n = 1
        while (true) {
            val candidate = "${base}_$n"
            val key = "$candidate.$ext".lowercase()
            if (key !in taken && !nameTaken(candidate, ext, r.isPrivate)) {
                taken += key
                return r.copy(baseName = candidate)
            }
            n++
        }
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
        patch(id) { it.copy(status = Status.RUNNING, progress = -1f, message = null, speedBps = 0, etaSec = null) }

        val tmp = File(app.cacheDir, "dl/$id").apply { deleteRecursively(); mkdirs() }
        try {
            val file = if (LinkParser.useDirect(item.url, item.mode)) {
                downloadDirect(item, tmp)
            } else {
                downloadWithYtDlp(item, tmp)
            }
            if (item.isPrivate) {
                savePrivately(item, file)
                // Private downloads never stay in the Downloads list; they live in the Private tab.
                _items.update { list -> list.filterNot { it.id == id } }
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(app, "Private download finished", Toast.LENGTH_SHORT).show()
                }
            } else {
                val savedName = saveToDownloads(file, "${item.baseName.ifBlank { file.nameWithoutExtension }}.${file.extension.ifBlank { item.ext }}")
                patch(id) {
                    it.copy(
                        status = Status.DONE,
                        progress = 1f,
                        title = savedName,
                        message = "Saved to Downloads/LinkGrab",
                    )
                }
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

            val name = "${item.baseName.ifBlank { "download" }}.${item.ext.ifBlank { "bin" }}"
            val out = File(dir, name)
            patchIfRunning(item.id) { it.copy(title = name) }

            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var lastTime = System.nanoTime()
                    var lastBytes = 0L
                    var smooth = 0.0
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        val now = System.nanoTime()
                        if (now - lastTime >= 500_000_000L) {
                            val instant = (done - lastBytes) * 1e9 / (now - lastTime)
                            smooth = if (smooth == 0.0) instant else smooth * 0.6 + instant * 0.4
                            val eta = if (total > 0 && smooth > 0) ((total - done) / smooth).toLong() else null
                            patchIfRunning(item.id) {
                                it.copy(
                                    progress = if (total > 0) done.toFloat() / total else it.progress,
                                    speedBps = smooth.toLong(),
                                    etaSec = eta,
                                )
                            }
                            lastTime = now
                            lastBytes = done
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

    private val speedRegex = Regex("""at\s+([\d.]+)\s*([KMGT]?)i?B/s""")

    /** Reads the speed out of a yt-dlp line like "[download] 45.3% of 50MiB at 2.35MiB/s ETA 00:12". */
    private fun parseSpeed(line: String): Long? {
        val m = speedRegex.findAll(line).lastOrNull() ?: return null
        val value = m.groupValues[1].toDoubleOrNull() ?: return null
        val unit = when (m.groupValues[2]) {
            "K" -> 1024.0
            "M" -> 1_048_576.0
            "G" -> 1_073_741_824.0
            "T" -> 1_099_511_627_776.0
            else -> 1.0
        }
        return (value * unit).toLong()
    }

    private fun downloadWithYtDlp(item: DownloadItem, dir: File): File {
        prepareEngine()
        val request = YoutubeDLRequest(item.url).apply {
            addOption("--no-playlist")
            addOption("--no-mtime")
            addOption("-o", "${dir.absolutePath}/${item.baseName.ifBlank { "download" }.replace("%", "%%")}.%(ext)s")
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

        YoutubeDL.getInstance().execute(request, processId(item.id)) { progress, eta, line ->
            val speed = parseSpeed(line)
            patchIfRunning(item.id) {
                it.copy(
                    progress = if (progress >= 0f) progress / 100f else it.progress,
                    message = line.lineSequence().lastOrNull { l -> l.isNotBlank() }?.take(120),
                    speedBps = speed ?: it.speedBps,
                    etaSec = if (eta >= 0) eta else it.etaSec,
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
        val title = item.baseName.ifBlank { file.nameWithoutExtension }
        PrivateStore.save(file, title, item.mode == Mode.AUDIO, thumbBytes)
        return title
    }

    // --------------------------------------------------------- save to Downloads

    /** Copies the finished file into Downloads/LinkGrab via MediaStore (no storage permission needed). */
    private fun saveToDownloads(file: File, displayName: String = file.name): String {
        val ext = file.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        val resolver = app.contentResolver

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
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
        return displayName
    }
}
