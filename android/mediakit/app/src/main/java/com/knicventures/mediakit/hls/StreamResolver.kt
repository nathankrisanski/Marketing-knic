package com.knicventures.mediakit.hls

import com.knicventures.mediakit.util.Http
import java.net.URI

/** A stream URL discovered on a page, with the referer needed to fetch it. */
data class DiscoveredStream(
    val url: String,
    val referer: String,
    val source: String,
    val isMaster: Boolean = false,
) {
    val host: String
        get() = runCatching { URI(url).host }.getOrNull().orEmpty()
}

/**
 * Turns "the page the video is on" into an actual playlist URL.
 *
 * Static resolution covers the common case where the playlist URL is present in
 * the served HTML, in an inline script, in a JSON blob, or one iframe deep.
 * Pages that build the URL in JavaScript at runtime need
 * [com.knicventures.mediakit.hls.WebViewSniffer] instead — the UI falls back to
 * it automatically when this finds nothing.
 */
class StreamResolver(
    private val userAgent: String = Http.DEFAULT_USER_AGENT,
    private val cookie: String? = null,
) {

    /** Absolute playlist/media URLs sitting in text, after unescaping. */
    private val absoluteRegex = Regex(
        """https?://[^\s"'<>()\\\[\]{}]+?\.(?:m3u8|mpd|mp4)(?:\?[^\s"'<>()\\\[\]{}]*)?""",
        RegexOption.IGNORE_CASE,
    )

    /** Quoted values that look like a playlist path, absolute or relative. */
    private val quotedRegex = Regex(
        """["']([^"'\s]{3,}?\.(?:m3u8|mpd|mp4)(?:\?[^"'\s]*)?)["']""",
        RegexOption.IGNORE_CASE,
    )

    private val iframeRegex = Regex(
        """<iframe[^>]+src\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )

    private val sourceRegex = Regex(
        """<(?:source|video)[^>]+src\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Resolves [input]. A direct playlist link is returned as-is; anything else
     * is fetched and scraped.
     *
     * @param followIframes how many iframe levels to descend (0 disables it).
     */
    fun resolve(input: String, followIframes: Int = 1): List<DiscoveredStream> {
        val url = normalizeInput(input)
        if (looksLikeDirectMedia(url)) {
            return listOf(DiscoveredStream(url, refererFor(url), "direct link"))
        }

        val html = runCatching { Http.getString(url, referer = url, userAgent = userAgent, cookie = cookie) }
            .getOrElse { return emptyList() }

        // The URL may have been a playlist served without a telling extension.
        if (M3u8Parser.looksLikePlaylist(html)) {
            return listOf(DiscoveredStream(url, refererFor(url), "playlist body"))
        }

        val found = LinkedHashMap<String, DiscoveredStream>()
        scrape(html, url, "page HTML").forEach { found.putIfAbsent(it.url, it) }

        if (followIframes > 0) {
            for (iframeSrc in iframeRegex.findAll(html).map { it.groupValues[1] }.take(6)) {
                val iframeUrl = M3u8Parser.resolve(url, unescape(iframeSrc))
                if (!iframeUrl.startsWith("http", ignoreCase = true)) continue
                val iframeHtml = runCatching {
                    Http.getString(iframeUrl, referer = url, userAgent = userAgent, cookie = cookie)
                }.getOrNull() ?: continue

                if (M3u8Parser.looksLikePlaylist(iframeHtml)) {
                    found.putIfAbsent(
                        iframeUrl,
                        DiscoveredStream(iframeUrl, url, "iframe playlist"),
                    )
                    continue
                }
                scrape(iframeHtml, iframeUrl, "iframe HTML").forEach { found.putIfAbsent(it.url, it) }
            }
        }

        return rank(found.values.toList())
    }

    private fun scrape(html: String, pageUrl: String, source: String): List<DiscoveredStream> {
        val text = unescape(html)
        val hits = LinkedHashSet<String>()

        absoluteRegex.findAll(text).forEach { hits += it.value }
        quotedRegex.findAll(text).forEach { hits += M3u8Parser.resolve(pageUrl, unescape(it.groupValues[1])) }
        sourceRegex.findAll(html).forEach { match ->
            val candidate = M3u8Parser.resolve(pageUrl, unescape(match.groupValues[1]))
            if (looksLikeDirectMedia(candidate)) hits += candidate
        }

        return hits
            .map { it.trimEnd(',', ';', ')', '\\') }
            .filter { it.startsWith("http", ignoreCase = true) }
            .map { DiscoveredStream(it, refererFor(pageUrl), source) }
    }

    /**
     * Fetches each candidate and marks which are master playlists, dropping any
     * that turn out not to be playlists at all. MP4 links are kept untouched.
     */
    fun verify(candidates: List<DiscoveredStream>): List<DiscoveredStream> =
        candidates.mapNotNull { candidate ->
            if (!candidate.url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) {
                return@mapNotNull candidate
            }
            val body = runCatching {
                Http.getString(candidate.url, candidate.referer, userAgent, cookie)
            }.getOrNull() ?: return@mapNotNull null
            if (!M3u8Parser.looksLikePlaylist(body)) return@mapNotNull null
            candidate.copy(isMaster = M3u8Parser.isMasterPlaylist(body))
        }

    /** Master playlists and obvious "master/index/playlist" names float to the top. */
    private fun rank(streams: List<DiscoveredStream>): List<DiscoveredStream> =
        streams.sortedWith(
            compareByDescending<DiscoveredStream> { it.url.contains(".m3u8", ignoreCase = true) }
                .thenByDescending { it.isMaster }
                .thenByDescending {
                    val lower = it.url.lowercase()
                    lower.contains("master") || lower.contains("playlist") || lower.contains("index")
                }
                .thenBy { it.url.length },
        )

    private fun normalizeInput(input: String): String {
        val trimmed = input.trim()
        return if (trimmed.startsWith("http", ignoreCase = true)) trimmed else "https://$trimmed"
    }

    private fun looksLikeDirectMedia(url: String): Boolean {
        val path = url.substringBefore('?').lowercase()
        return path.endsWith(".m3u8") || path.endsWith(".mp4") || path.endsWith(".mpd")
    }

    /** Segment CDNs check Referer against the page origin, so send the page URL. */
    private fun refererFor(url: String): String = url

    /** Undoes the escaping playlist URLs pick up inside JSON and HTML attributes. */
    private fun unescape(text: String): String = text
        .replace("\\/", "/")
        .replace("\\u002F", "/", ignoreCase = true)
        .replace("\\u0026", "&", ignoreCase = true)
        .replace("&amp;", "&")
        .replace("&#38;", "&")
        .replace("&quot;", "\"")
}
