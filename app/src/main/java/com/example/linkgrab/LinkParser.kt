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
            .map { tidy(it) }
            .filter { Uri.parse(it).host?.contains('.') == true }
            .distinct()
            .toList()

    fun isInstagram(url: String): Boolean =
        Uri.parse(url).host?.lowercase()?.let { it == "instagr.am" || it.endsWith("instagram.com") } == true

    /** Drops tracking parameters (?igsh=...) from Instagram links and fixes /reels/ links. */
    private fun tidy(url: String): String {
        if (!isInstagram(url)) return url
        return url.substringBefore('?').substringBefore('#').replace("/reels/", "/reel/")
    }

    /** Turns Instagram's usual refusals into something understandable. */
    fun friendlyError(url: String, message: String?): String? {
        if (!isInstagram(url)) return null
        val m = message.orEmpty()
        return if (Regex("login|rate-limit|empty media|private|checkpoint|not available|cookies", RegexOption.IGNORE_CASE).containsMatchIn(m))
            "Instagram wouldn't share this video. Only public posts work, and Instagram sometimes blocks requests. Try again later, or tap Update engine."
        else null
    }

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
