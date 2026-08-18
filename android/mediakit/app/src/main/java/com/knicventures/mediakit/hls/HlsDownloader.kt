package com.knicventures.mediakit.hls

import com.knicventures.mediakit.util.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Coarse-grained progress for the download half of a job. */
data class DownloadProgress(
    val completedSegments: Int,
    val totalSegments: Int,
    val bytesDownloaded: Long,
) {
    val fraction: Float
        get() = if (totalSegments <= 0) 0f else completedSegments.toFloat() / totalSegments
}

data class DownloadedStream(
    val file: File,
    /** True when segments were fMP4 (`#EXT-X-MAP` present) rather than MPEG-TS. */
    val isFragmentedMp4: Boolean,
    val durationSeconds: Double,
    val bytes: Long,
)

/**
 * Downloads every segment of a media playlist and concatenates them into one
 * contiguous file, decrypting AES-128 along the way.
 *
 * The result is a raw MPEG-TS (or fMP4) stream — playable, but not yet an MP4
 * container. [Remuxer] does that second step.
 */
class HlsDownloader(
    private val userAgent: String = Http.DEFAULT_USER_AGENT,
    private val cookie: String? = null,
    private val concurrency: Int = 4,
    private val maxRetries: Int = 3,
) {

    /** Fetches a playlist and, if it is a master, the chosen variant's media playlist. */
    suspend fun loadPlaylist(url: String, referer: String?): PlaylistLoad =
        withContext(Dispatchers.IO) {
            val body = Http.getString(url, referer, userAgent, cookie)
            if (!M3u8Parser.looksLikePlaylist(body)) {
                throw IOException("Not an HLS playlist: $url")
            }
            if (M3u8Parser.isMasterPlaylist(body)) {
                PlaylistLoad.Master(M3u8Parser.parseMaster(body, url), url)
            } else {
                PlaylistLoad.Media(M3u8Parser.parseMedia(body, url), url)
            }
        }

    /** Loads a media playlist, following one level of master indirection. */
    suspend fun loadMediaPlaylist(
        url: String,
        referer: String?,
        selectVariant: (List<Variant>) -> Variant? = { it.firstOrNull() },
    ): Pair<MediaPlaylist, String> = withContext(Dispatchers.IO) {
        when (val load = loadPlaylist(url, referer)) {
            is PlaylistLoad.Media -> load.playlist to load.url
            is PlaylistLoad.Master -> {
                val variant = selectVariant(load.playlist.variants)
                    ?: throw IOException("Master playlist has no usable variants: $url")
                when (val inner = loadPlaylist(variant.url, referer)) {
                    is PlaylistLoad.Media -> inner.playlist to inner.url
                    is PlaylistLoad.Master ->
                        throw IOException("Nested master playlists are not supported")
                }
            }
        }
    }

    /**
     * Downloads [playlist] into [target].
     *
     * Segments are fetched [concurrency]-at-a-time into scratch files, then
     * appended strictly in playlist order so the output stays decodable.
     */
    suspend fun download(
        playlist: MediaPlaylist,
        referer: String?,
        target: File,
        scratchDir: File,
        onProgress: (DownloadProgress) -> Unit = {},
    ): DownloadedStream = withContext(Dispatchers.IO) {
        if (playlist.segments.isEmpty()) throw IOException("Playlist contains no segments")
        scratchDir.mkdirs()
        target.parentFile?.mkdirs()
        if (target.exists()) target.delete()

        // Fetched concurrently by the segment workers below.
        val keyCache = ConcurrentHashMap<String, ByteArray>()
        var bytes = 0L
        var completed = 0

        target.outputStream().buffered(1 shl 16).use { out ->
            playlist.initSegmentUrl?.let { initUrl ->
                val data = fetchSegment(
                    url = initUrl,
                    referer = referer,
                    key = playlist.initSegmentKey,
                    sequence = 0L,
                    byteRangeLength = null,
                    byteRangeOffset = null,
                    keyCache = keyCache,
                )
                out.write(data)
                bytes += data.size
            }

            playlist.segments.chunked(concurrency.coerceAtLeast(1)).forEach { batch ->
                currentCoroutineContext().ensureActive()
                val fetched = coroutineScopeAwait(batch) { segment ->
                    val data = fetchSegment(
                        url = segment.url,
                        referer = referer,
                        key = segment.key,
                        sequence = segment.sequence,
                        byteRangeLength = segment.byteRangeLength,
                        byteRangeOffset = segment.byteRangeOffset,
                        keyCache = keyCache,
                    )
                    val scratch = File(scratchDir, "seg_${segment.sequence}.part")
                    scratch.writeBytes(data)
                    scratch
                }

                for (part in fetched) {
                    part.inputStream().use { it.copyTo(out) }
                    bytes += part.length()
                    part.delete()
                    completed++
                    onProgress(DownloadProgress(completed, playlist.segments.size, bytes))
                }
            }
        }

        DownloadedStream(
            file = target,
            isFragmentedMp4 = playlist.initSegmentUrl != null,
            durationSeconds = playlist.totalDurationSeconds,
            bytes = bytes,
        )
    }

    /** Downloads a plain progressive file (an .mp4 link rather than a playlist). */
    suspend fun downloadDirect(
        url: String,
        referer: String?,
        target: File,
        onProgress: (DownloadProgress) -> Unit = {},
    ): DownloadedStream = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        Http.execute(Http.request(url, referer, userAgent, cookie)).use { response ->
            val total = response.body?.contentLength() ?: -1L
            var read = 0L
            response.body?.byteStream()?.use { input ->
                target.outputStream().buffered(1 shl 16).use { out ->
                    val buffer = ByteArray(1 shl 16)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n <= 0) break
                        out.write(buffer, 0, n)
                        read += n
                        val pct = if (total > 0) ((read * 100) / total).toInt() else 0
                        onProgress(DownloadProgress(pct, if (total > 0) 100 else 0, read))
                    }
                }
            } ?: throw IOException("Empty response body for $url")
            DownloadedStream(target, isFragmentedMp4 = true, durationSeconds = 0.0, bytes = read)
        }
    }

    private suspend fun <T, R> coroutineScopeAwait(items: List<T>, block: suspend (T) -> R): List<R> =
        kotlinx.coroutines.coroutineScope {
            items.map { item -> async(Dispatchers.IO) { block(item) } }.awaitAll()
        }

    private suspend fun fetchSegment(
        url: String,
        referer: String?,
        key: EncryptionKey?,
        sequence: Long,
        byteRangeLength: Long?,
        byteRangeOffset: Long?,
        keyCache: MutableMap<String, ByteArray>,
    ): ByteArray {
        val range = if (byteRangeLength != null) {
            val start = byteRangeOffset ?: 0L
            "bytes=$start-${start + byteRangeLength - 1}"
        } else {
            null
        }

        val raw = withRetries { Http.getBytes(url, referer, userAgent, cookie, range) }
        if (key == null) return raw

        return when {
            key.method.equals("AES-128", ignoreCase = true) -> {
                val keyUri = key.uri ?: throw IOException("AES-128 segment without a key URI")
                val keyBytes = keyCache.getOrPut(keyUri) {
                    withRetries { Http.getBytes(keyUri, referer, userAgent, cookie) }
                }
                if (keyBytes.size != 16) throw IOException("Unexpected AES key length: ${keyBytes.size}")
                decryptAes128(raw, keyBytes, ivFor(key.iv, sequence))
            }

            key.method.equals("NONE", ignoreCase = true) -> raw

            else -> throw IOException(
                "Stream uses ${key.method} encryption, which this app cannot decrypt.",
            )
        }
    }

    private fun decryptAes128(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(data)
    }

    /** Explicit `IV=0x…`, else the media sequence number as a 128-bit big-endian value. */
    private fun ivFor(iv: String?, sequence: Long): ByteArray {
        if (iv != null) {
            val hex = iv.removePrefix("0x").removePrefix("0X")
            val bytes = ByteArray(16)
            val padded = hex.padStart(32, '0').takeLast(32)
            for (i in 0 until 16) {
                bytes[i] = ((padded[i * 2].digitToInt(16) shl 4) or padded[i * 2 + 1].digitToInt(16)).toByte()
            }
            return bytes
        }
        val bytes = ByteArray(16)
        for (i in 0 until 8) {
            bytes[15 - i] = ((sequence shr (8 * i)) and 0xFF).toByte()
        }
        return bytes
    }

    private suspend fun <T> withRetries(block: () -> T): T {
        var lastError: Exception? = null
        repeat(maxRetries) { attempt ->
            currentCoroutineContext().ensureActive()
            try {
                return block()
            } catch (e: IOException) {
                lastError = e
                delay(500L * (attempt + 1))
            }
        }
        throw lastError ?: IOException("Request failed")
    }
}

sealed interface PlaylistLoad {
    val url: String

    data class Master(val playlist: MasterPlaylist, override val url: String) : PlaylistLoad
    data class Media(val playlist: MediaPlaylist, override val url: String) : PlaylistLoad
}
