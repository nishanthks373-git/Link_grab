package com.example.linkgrab

import java.util.Locale

enum class Mode { VIDEO, AUDIO }

enum class Status { QUEUED, RUNNING, DONE, FAILED, CANCELLED }

/** What the preview screen hands to the download queue. */
data class QueueRequest(
    val url: String,
    val mode: Mode,
    /** Max video height (e.g. 720), or null for best/original. Ignored for audio. */
    val height: Int?,
    val title: String?,
    val thumbnail: String?,
    /** Save inside the app only (not visible to other apps). */
    val isPrivate: Boolean = false,
    /** Overrides the file name (without extension); used for "_1" copies. */
    val baseName: String? = null,
)

data class DownloadItem(
    val id: Long,
    val url: String,
    val mode: Mode,
    val height: Int? = null,
    val status: Status = Status.QUEUED,
    /** 0f..1f, or a negative value when unknown (shows an indeterminate bar). */
    val progress: Float = -1f,
    val title: String? = null,
    val thumbnail: String? = null,
    val message: String? = null,
    val isPrivate: Boolean = false,
    /** Current speed in bytes per second (only meaningful while running). */
    val speedBps: Long = 0,
    /** Estimated seconds left, if known. */
    val etaSec: Long? = null,
    /** File name (without extension) and extension this download will be saved as. */
    val baseName: String = "",
    val ext: String = "",
) {
    val isActive get() = status == Status.QUEUED || status == Status.RUNNING
}

fun formatSpeed(bytesPerSec: Long): String = when {
    bytesPerSec >= 1_048_576 -> String.format(Locale.US, "%.1f MB/s", bytesPerSec / 1_048_576.0)
    bytesPerSec >= 1024 -> "${bytesPerSec / 1024} KB/s"
    bytesPerSec > 0 -> "$bytesPerSec B/s"
    else -> "0 KB/s"
}

fun formatEta(sec: Long?): String? = when {
    sec == null || sec <= 0 -> null
    sec < 60 -> "${sec}s left"
    sec < 3600 -> "${sec / 60}m ${sec % 60}s left"
    else -> "${sec / 3600}h ${(sec % 3600) / 60}m left"
}
