package com.example.linkgrab

import android.net.Uri

object LinkParser {
    private val urlRegex = Regex("""(?i)\b(?:https?://|www\.)[^\s<>"'“”]+""")

    private val videoExt = setOf("mp4", "mkv", "webm", "mov", "avi", "flv", "m4v", "3gp", "ts")
    private val audioExt = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "wma")

    /**
     * Pulls every URL out of free text. Works for a single link, a pasted list
     * (one per line, or mixed with other text) and the contents of a .txt file.
     */
    fun extract(text: String): List<String> =
        urlRegex.findAll(text)
            .map { it.value.trimEnd('.', ',', ';', ')', ']', '}', '!', '?') }
            .map { if (it.startsWith("www.", ignoreCase = true)) "https://$it" else it }
            .filter { Uri.parse(it).host?.contains('.') == true }
            .distinct()
            .toList()

    private fun extensionOf(url: String): String =
        Uri.parse(url).lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()

    /**
     * A plain file link can be fetched directly. Anything else (a page URL such as
     * a video site) goes through yt-dlp. Audio mode on a video file also needs
     * yt-dlp/ffmpeg to extract the audio track.
     */
    fun useDirect(url: String, mode: Mode): Boolean {
        val ext = extensionOf(url)
        return when {
            ext in audioExt -> true
            ext in videoExt -> mode == Mode.VIDEO
            else -> false
        }
    }
}
