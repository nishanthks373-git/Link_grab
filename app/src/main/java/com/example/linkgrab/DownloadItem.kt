package com.example.linkgrab

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
) {
    val isActive get() = status == Status.QUEUED || status == Status.RUNNING
}
