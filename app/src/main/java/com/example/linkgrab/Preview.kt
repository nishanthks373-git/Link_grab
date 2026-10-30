package com.example.linkgrab

import android.net.Uri
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/** One entry in the quality dropdown. */
data class QualityOption(
    val label: String,
    /** Max video height, or null for "original/best" and audio. */
    val height: Int?,
    val audioOnly: Boolean,
    val sizeBytes: Long?,
) {
    val display: String get() = "$label · ${formatSize(sizeBytes)}"
}

enum class PreviewState { LOADING, READY, ERROR }

data class PreviewItem(
    val id: Long,
    val url: String,
    val state: PreviewState = PreviewState.LOADING,
    val title: String? = null,
    val thumbnail: String? = null,
    val durationSec: Long? = null,
    val options: List<QualityOption> = emptyList(),
    val selected: Int = 0,
    val checked: Boolean = true,
    val error: String? = null,
) {
    val chosen: QualityOption? get() = options.getOrNull(selected)
}

fun formatSize(bytes: Long?): String {
    if (bytes == null || bytes <= 0) return "size unknown"
    val mb = bytes / 1_048_576.0
    return when {
        mb >= 1024 -> String.format(Locale.US, "~%.1f GB", mb / 1024)
        mb >= 10 -> String.format(Locale.US, "~%.0f MB", mb)
        else -> String.format(Locale.US, "~%.1f MB", mb)
    }
}

fun formatDuration(sec: Long?): String? {
    if (sec == null || sec <= 0) return null
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

private class FetchedInfo(
    val title: String?,
    val thumbnail: String?,
    val durationSec: Long?,
    val options: List<QualityOption>,
)

/**
 * Looks up title, thumbnail, available resolutions and approximate sizes for each link
 * before anything is downloaded, and holds the user's selection.
 */
object PreviewRepository {
    private val _items = MutableStateFlow<List<PreviewItem>>(emptyList())
    val items: StateFlow<List<PreviewItem>> = _items

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(3)
    private val nextId = AtomicLong(1)
    private val http = OkHttpClient()

    // ------------------------------------------------------------------ actions

    /** Starts fetching info for new links. Returns how many were new. */
    fun load(urls: List<String>): Int {
        val existing = _items.value.map { it.url }.toSet()
        val fresh = urls.distinct().filter { it !in existing }
        if (fresh.isEmpty()) return 0
        val newItems = fresh.map { PreviewItem(id = nextId.getAndIncrement(), url = it) }
        _items.update { it + newItems }
        newItems.forEach { fetch(it.id) }
        return newItems.size
    }

    fun retry(id: Long) {
        patch(id) { it.copy(state = PreviewState.LOADING, error = null) }
        fetch(id)
    }

    fun remove(id: Long) {
        runCatching { YoutubeDL.getInstance().destroyProcessById("info-$id") }
        _items.update { list -> list.filterNot { it.id == id } }
    }

    fun clear() {
        _items.value.forEach { runCatching { YoutubeDL.getInstance().destroyProcessById("info-${it.id}") } }
        _items.value = emptyList()
    }

    fun toggle(id: Long) = patch(id) { it.copy(checked = !it.checked) }

    fun setAllChecked(checked: Boolean) {
        _items.update { list -> list.map { if (it.state == PreviewState.READY) it.copy(checked = checked) else it } }
    }

    fun selectOption(id: Long, index: Int) = patch(id) { it.copy(selected = index) }

    /**
     * Applies one choice to every item: the best resolution at or below [maxHeight]
     * (null = best available), or audio-only. Items without a matching option are left alone.
     */
    fun applyToAll(maxHeight: Int?, audio: Boolean) {
        _items.update { list ->
            list.map { item ->
                if (item.state != PreviewState.READY) return@map item
                val index = if (audio) {
                    item.options.indexOfFirst { it.audioOnly }.takeIf { it >= 0 }
                } else {
                    val videos = item.options.withIndex().filter { !it.value.audioOnly }
                    val limit = maxHeight ?: Int.MAX_VALUE
                    val rank = { o: QualityOption -> o.height ?: Int.MAX_VALUE }
                    (videos.filter { rank(it.value) <= limit }.maxByOrNull { rank(it.value) }
                        ?: videos.minByOrNull { rank(it.value) })?.index
                }
                if (index != null) item.copy(selected = index) else item
            }
        }
    }

    /** Queues every ticked item with its chosen quality and removes it from the preview list. */
    fun downloadChecked(): Int {
        val chosen = _items.value.filter { it.checked && it.state == PreviewState.READY && it.chosen != null }
        if (chosen.isEmpty()) return 0
        val requests = chosen.map {
            val o = it.chosen!!
            QueueRequest(
                url = it.url,
                mode = if (o.audioOnly) Mode.AUDIO else Mode.VIDEO,
                height = o.height,
                title = it.title,
                thumbnail = it.thumbnail,
            )
        }
        val added = DownloadRepository.enqueue(requests)
        val ids = chosen.map { it.id }.toSet()
        _items.update { list -> list.filterNot { it.id in ids } }
        return added
    }

    // ---------------------------------------------------------------- internals

    private fun patch(id: Long, change: (PreviewItem) -> PreviewItem) {
        _items.update { list -> list.map { if (it.id == id) change(it) else it } }
    }

    private fun fetch(id: Long) {
        scope.launch {
            val url = _items.value.firstOrNull { it.id == id }?.url ?: return@launch
            try {
                gate.withPermit {
                    val info = if (isDirectFile(url)) fetchDirect(url) else fetchWithYtDlp(id, url)
                    patch(id) {
                        it.copy(
                            state = PreviewState.READY,
                            title = info.title,
                            thumbnail = info.thumbnail,
                            durationSec = info.durationSec,
                            options = info.options,
                            selected = 0,
                            error = null,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val msg = e.message.orEmpty().lineSequence()
                    .lastOrNull { it.isNotBlank() }?.removePrefix("ERROR: ")?.take(200)
                patch(id) {
                    it.copy(state = PreviewState.ERROR, error = msg ?: "Couldn't read this link")
                }
            }
        }
    }

    private fun isDirectFile(url: String) =
        LinkParser.useDirect(url, Mode.VIDEO) || LinkParser.useDirect(url, Mode.AUDIO)

    // ------------------------------------------------------------ direct links

    private fun fetchDirect(url: String): FetchedInfo {
        val size = runCatching {
            http.newCall(Request.Builder().url(url).head().build()).execute().use {
                it.header("Content-Length")?.toLongOrNull()
            }
        }.getOrNull()

        val name = Uri.parse(url).lastPathSegment
        val isAudio = LinkParser.useDirect(url, Mode.AUDIO)
        val options = if (isAudio) {
            listOf(QualityOption("Original audio", null, true, size))
        } else {
            listOf(
                QualityOption("Original video", null, false, size),
                QualityOption("Audio only (MP3)", null, true, null),
            )
        }
        return FetchedInfo(name, null, null, options)
    }

    // ------------------------------------------------------------------ yt-dlp

    private class Fmt(val height: Int, val vcodec: String, val acodec: String, val size: Long?)

    private fun fetchWithYtDlp(id: Long, url: String): FetchedInfo {
        DownloadRepository.prepareEngine()
        val request = YoutubeDLRequest(url).apply {
            addOption("-J")
            addOption("--no-playlist")
            addOption("--no-warnings")
        }
        val out = YoutubeDL.getInstance().execute(request, "info-$id") { _, _, _ -> }.out
        val json = JSONObject(out.substring(out.indexOf('{').coerceAtLeast(0)))

        if (json.optString("_type") == "playlist") {
            error("This is a playlist link. Paste the individual video links instead.")
        }

        val duration = json.optDouble("duration", 0.0).takeIf { it > 0 }

        fun sizeOf(f: JSONObject): Long? {
            val exact = f.optLong("filesize", 0)
            if (exact > 0) return exact
            val approx = f.optLong("filesize_approx", 0)
            if (approx > 0) return approx
            val tbr = f.optDouble("tbr", 0.0) // kbit/s
            return if (tbr > 0 && duration != null) (tbr * 125 * duration).toLong() else null
        }

        val formats = mutableListOf<Fmt>()
        json.optJSONArray("formats")?.let { arr ->
            for (i in 0 until arr.length()) {
                val f = arr.optJSONObject(i) ?: continue
                formats += Fmt(
                    height = f.optInt("height", 0),
                    vcodec = f.optString("vcodec", "unknown"),
                    acodec = f.optString("acodec", "unknown"),
                    size = sizeOf(f),
                )
            }
        }

        val audioFormats = formats.filter { it.vcodec == "none" && it.acodec != "none" }
        val bestAudioSize = audioFormats.mapNotNull { it.size }.maxOrNull()

        val videoByHeight = formats
            .filter { it.height > 0 && it.vcodec != "none" }
            .groupBy { it.height }

        val options = mutableListOf<QualityOption>()
        for (h in videoByHeight.keys.sortedDescending()) {
            val best = videoByHeight.getValue(h).maxByOrNull { it.size ?: 0L } ?: continue
            var total = best.size
            if (best.acodec == "none" && total != null && bestAudioSize != null) total += bestAudioSize
            options += QualityOption("${h}p", h, false, total)
        }

        if (options.isEmpty() && audioFormats.isEmpty()) {
            val top = json.optLong("filesize", 0).takeIf { it > 0 }
                ?: json.optLong("filesize_approx", 0).takeIf { it > 0 }
            options += QualityOption("Best available", null, false, top)
        }

        // MP3 is re-encoded at 192 kbit/s (see DownloadRepository), so the size is predictable.
        val mp3Size = duration?.let { (it * 192 * 125).toLong() } ?: bestAudioSize
        options += QualityOption("Audio only (MP3)", null, true, mp3Size)

        return FetchedInfo(
            title = json.optString("title").takeIf { it.isNotBlank() && it != "null" },
            thumbnail = json.optString("thumbnail").takeIf { it.startsWith("http") },
            durationSec = duration?.toLong(),
            options = options,
        )
    }
}
