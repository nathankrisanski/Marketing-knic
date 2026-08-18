package com.knicventures.mediakit.hls

import java.net.URI

/** A selectable rendition advertised by a master playlist. */
data class Variant(
    val url: String,
    val bandwidth: Long,
    val resolution: String?,
    val codecs: String?,
    val name: String?,
) {
    /** Height in pixels, used for "best quality" sorting when bandwidth ties. */
    val height: Int
        get() = resolution?.substringAfter('x', "")?.toIntOrNull() ?: 0

    val label: String
        get() = buildString {
            append(resolution ?: name ?: "stream")
            if (bandwidth > 0) append(" · ${bandwidth / 1000} kbps")
        }
}

/** An alternate audio/subtitle rendition (`#EXT-X-MEDIA`). */
data class MediaRendition(
    val type: String,
    val groupId: String?,
    val name: String?,
    val language: String?,
    val url: String?,
    val isDefault: Boolean,
)

data class EncryptionKey(
    val method: String,
    val uri: String?,
    val iv: String?,
)

data class Segment(
    val url: String,
    val durationSeconds: Double,
    val key: EncryptionKey?,
    /** Media sequence number, used to derive the IV when `#EXT-X-KEY` omits one. */
    val sequence: Long,
    val byteRangeLength: Long? = null,
    val byteRangeOffset: Long? = null,
)

data class MasterPlaylist(
    val variants: List<Variant>,
    val renditions: List<MediaRendition>,
)

data class MediaPlaylist(
    val segments: List<Segment>,
    /** `#EXT-X-MAP` initialisation segment for fMP4 streams, if present. */
    val initSegmentUrl: String?,
    val initSegmentKey: EncryptionKey?,
    val targetDuration: Double,
    val isLive: Boolean,
) {
    val totalDurationSeconds: Double
        get() = segments.sumOf { it.durationSeconds }
}

/**
 * Minimal but practical HLS playlist parser.
 *
 * It covers what real-world VOD streams actually use: master playlists with
 * multiple variants, AES-128 (and SAMPLE-AES detection), byte-range segments,
 * and fMP4 initialisation segments.
 */
object M3u8Parser {

    fun isMasterPlaylist(text: String): Boolean =
        text.lineSequence().any { it.startsWith("#EXT-X-STREAM-INF") }

    fun looksLikePlaylist(text: String): Boolean =
        text.trimStart().startsWith("#EXTM3U")

    fun parseMaster(text: String, baseUrl: String): MasterPlaylist {
        val variants = mutableListOf<Variant>()
        val renditions = mutableListOf<MediaRendition>()
        val lines = text.lines()

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            when {
                line.startsWith("#EXT-X-STREAM-INF") -> {
                    val attrs = parseAttributes(line.substringAfter(':', ""))
                    val uri = nextUri(lines, i + 1)
                    if (uri != null) {
                        variants += Variant(
                            url = resolve(baseUrl, uri),
                            bandwidth = attrs["AVERAGE-BANDWIDTH"]?.toLongOrNull()
                                ?: attrs["BANDWIDTH"]?.toLongOrNull() ?: 0L,
                            resolution = attrs["RESOLUTION"],
                            codecs = attrs["CODECS"],
                            name = attrs["NAME"],
                        )
                    }
                }

                line.startsWith("#EXT-X-MEDIA") -> {
                    val attrs = parseAttributes(line.substringAfter(':', ""))
                    renditions += MediaRendition(
                        type = attrs["TYPE"] ?: "UNKNOWN",
                        groupId = attrs["GROUP-ID"],
                        name = attrs["NAME"],
                        language = attrs["LANGUAGE"],
                        url = attrs["URI"]?.let { resolve(baseUrl, it) },
                        isDefault = attrs["DEFAULT"].equals("YES", ignoreCase = true),
                    )
                }
            }
            i++
        }
        return MasterPlaylist(variants.sortedByDescending { it.bandwidth }, renditions)
    }

    fun parseMedia(text: String, baseUrl: String): MediaPlaylist {
        val segments = mutableListOf<Segment>()
        var currentKey: EncryptionKey? = null
        var initUrl: String? = null
        var initKey: EncryptionKey? = null
        var pendingDuration = 0.0
        var pendingByteRangeLength: Long? = null
        var pendingByteRangeOffset: Long? = null
        var previousEnd = 0L
        var targetDuration = 0.0
        var sequence = 0L
        var sawEndList = false

        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue

            when {
                line.startsWith("#EXT-X-MEDIA-SEQUENCE") ->
                    sequence = line.substringAfter(':', "0").trim().toLongOrNull() ?: 0L

                line.startsWith("#EXT-X-TARGETDURATION") ->
                    targetDuration = line.substringAfter(':', "0").trim().toDoubleOrNull() ?: 0.0

                line.startsWith("#EXT-X-KEY") -> {
                    val attrs = parseAttributes(line.substringAfter(':', ""))
                    val method = attrs["METHOD"] ?: "NONE"
                    currentKey = if (method.equals("NONE", ignoreCase = true)) {
                        null
                    } else {
                        EncryptionKey(
                            method = method,
                            uri = attrs["URI"]?.let { resolve(baseUrl, it) },
                            iv = attrs["IV"],
                        )
                    }
                }

                line.startsWith("#EXT-X-MAP") -> {
                    val attrs = parseAttributes(line.substringAfter(':', ""))
                    initUrl = attrs["URI"]?.let { resolve(baseUrl, it) }
                    initKey = currentKey
                }

                line.startsWith("#EXTINF") ->
                    pendingDuration = line.substringAfter(':', "0")
                        .substringBefore(',')
                        .trim()
                        .toDoubleOrNull() ?: 0.0

                line.startsWith("#EXT-X-BYTERANGE") -> {
                    val spec = line.substringAfter(':', "")
                    val length = spec.substringBefore('@').trim().toLongOrNull()
                    val offset = spec.substringAfter('@', "").trim().toLongOrNull()
                    pendingByteRangeLength = length
                    pendingByteRangeOffset = offset ?: previousEnd
                }

                line.startsWith("#EXT-X-ENDLIST") -> sawEndList = true

                line.startsWith("#") -> Unit // Tag we do not need.

                else -> {
                    segments += Segment(
                        url = resolve(baseUrl, line),
                        durationSeconds = pendingDuration,
                        key = currentKey,
                        sequence = sequence,
                        byteRangeLength = pendingByteRangeLength,
                        byteRangeOffset = pendingByteRangeOffset,
                    )
                    if (pendingByteRangeLength != null) {
                        previousEnd = (pendingByteRangeOffset ?: 0L) + pendingByteRangeLength
                    }
                    sequence++
                    pendingDuration = 0.0
                    pendingByteRangeLength = null
                    pendingByteRangeOffset = null
                }
            }
        }

        return MediaPlaylist(
            segments = segments,
            initSegmentUrl = initUrl,
            initSegmentKey = initKey,
            targetDuration = targetDuration,
            isLive = !sawEndList,
        )
    }

    /** First non-comment, non-blank line at or after [from]. */
    private fun nextUri(lines: List<String>, from: Int): String? {
        for (i in from until lines.size) {
            val candidate = lines[i].trim()
            if (candidate.isNotEmpty() && !candidate.startsWith("#")) return candidate
        }
        return null
    }

    /**
     * Splits an HLS attribute list, honouring quoted values that contain commas
     * (`CODECS="avc1.640028,mp4a.40.2"`).
     */
    fun parseAttributes(spec: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        val current = StringBuilder()
        var inQuotes = false
        val parts = mutableListOf<String>()

        for (c in spec) {
            when {
                c == '"' -> {
                    inQuotes = !inQuotes
                    current.append(c)
                }
                c == ',' && !inQuotes -> {
                    parts += current.toString()
                    current.setLength(0)
                }
                else -> current.append(c)
            }
        }
        if (current.isNotEmpty()) parts += current.toString()

        for (part in parts) {
            val eq = part.indexOf('=')
            if (eq <= 0) continue
            val key = part.substring(0, eq).trim()
            val value = part.substring(eq + 1).trim().removeSurrounding("\"")
            result[key] = value
        }
        return result
    }

    /** Resolves a playlist-relative URI against the playlist's own URL. */
    fun resolve(baseUrl: String, reference: String): String {
        val ref = reference.trim()
        if (ref.startsWith("http://", ignoreCase = true) ||
            ref.startsWith("https://", ignoreCase = true)
        ) {
            return ref
        }
        return runCatching { URI(baseUrl).resolve(ref).toString() }.getOrElse { ref }
    }
}
