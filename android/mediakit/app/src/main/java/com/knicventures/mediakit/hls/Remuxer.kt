package com.knicventures.mediakit.hls

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Repackages a downloaded elementary stream into a real MP4 container.
 *
 * This is a container-level copy, not a transcode: the H.264/H.265 and AAC
 * samples are written through untouched, so it is fast and lossless. It relies
 * on the platform's own MPEG-TS demuxer and MP4 muxer, which is why the app
 * needs no bundled FFmpeg.
 */
object Remuxer {

    private const val DEFAULT_BUFFER_BYTES = 1 shl 20

    data class Result(val file: File, val durationUs: Long, val trackCount: Int)

    suspend fun remuxToMp4(
        source: File,
        target: File,
        onProgress: (Float) -> Unit = {},
    ): Result = withContext(Dispatchers.IO) {
        if (!source.exists() || source.length() == 0L) {
            throw IOException("Nothing to remux — the downloaded stream is empty.")
        }
        target.parentFile?.mkdirs()
        if (target.exists()) target.delete()

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(source.absolutePath)
            if (extractor.trackCount == 0) {
                throw IOException("No media tracks found. The stream may use a codec Android cannot demux.")
            }

            muxer = MediaMuxer(target.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val trackMap = HashMap<Int, Int>()
            var maxBuffer = DEFAULT_BUFFER_BYTES
            var durationUs = 0L

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (!mime.startsWith("video/") && !mime.startsWith("audio/")) continue

                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    maxBuffer = maxOf(maxBuffer, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                }
                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    durationUs = maxOf(durationUs, format.getLong(MediaFormat.KEY_DURATION))
                }

                extractor.selectTrack(i)
                trackMap[i] = muxer.addTrack(format)
            }

            if (trackMap.isEmpty()) {
                throw IOException("Stream has no audio or video tracks this device can mux.")
            }

            muxer.start()

            val buffer = ByteBuffer.allocate(maxBuffer)
            val info = MediaCodec.BufferInfo()
            // Timestamps in a mid-stream TS capture rarely start at zero.
            var firstPtsUs = -1L
            var lastPtsUs = 0L

            while (true) {
                currentCoroutineContext().ensureActive()
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                val trackIndex = trackMap[extractor.sampleTrackIndex]
                if (trackIndex != null) {
                    val ptsUs = extractor.sampleTime
                    if (firstPtsUs < 0) firstPtsUs = ptsUs
                    val normalizedPts = (ptsUs - firstPtsUs).coerceAtLeast(0L)

                    info.offset = 0
                    info.size = sampleSize
                    info.presentationTimeUs = normalizedPts
                    info.flags = extractor.sampleFlags.coerceAtLeast(0)
                    muxer.writeSampleData(trackIndex, buffer, info)

                    lastPtsUs = normalizedPts
                    if (durationUs > 0) onProgress((normalizedPts.toFloat() / durationUs).coerceIn(0f, 1f))
                }
                extractor.advance()
            }

            Result(target, if (durationUs > 0) durationUs else lastPtsUs, trackMap.size)
        } finally {
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }
}
