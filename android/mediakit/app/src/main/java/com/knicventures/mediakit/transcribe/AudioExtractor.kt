package com.knicventures.mediakit.transcribe

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** One chunk of extracted audio, plus where it sits in the source timeline. */
data class AudioChunk(
    val file: File,
    val startSeconds: Double,
    val durationSeconds: Double,
    val index: Int,
)

/**
 * Decodes the audio track of any file the device can play into 16 kHz mono
 * 16-bit WAV — the format every speech recogniser expects — and splits it into
 * chunks small enough to send one at a time.
 *
 * Works on local files and on content:// URIs handed over by the share sheet or
 * the document picker, so no storage permission is required.
 */
class AudioExtractor(private val context: Context) {

    companion object {
        const val TARGET_SAMPLE_RATE = 16_000
        private const val BYTES_PER_SAMPLE = 2
        private const val DEQUEUE_TIMEOUT_US = 10_000L
    }

    /**
     * @param chunkSeconds maximum length of each output chunk. Keep this under
     *   whatever the transcription backend accepts; 10 minutes of 16 kHz mono
     *   PCM is roughly 19 MB.
     */
    suspend fun extractChunks(
        source: Uri,
        outputDir: File,
        chunkSeconds: Int = 600,
        onProgress: (Float) -> Unit = {},
    ): List<AudioChunk> = withContext(Dispatchers.IO) {
        outputDir.mkdirs()
        val extractor = MediaExtractor()
        try {
            openDataSource(extractor, source)

            val audioTrack = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i)
                    .getString(MediaFormat.KEY_MIME)
                    .orEmpty()
                    .startsWith("audio/")
            } ?: throw IOException("No audio track found in this file.")

            val format = extractor.getTrackFormat(audioTrack)
            extractor.selectTrack(audioTrack)
            decodeToChunks(extractor, format, outputDir, chunkSeconds, onProgress)
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun openDataSource(extractor: MediaExtractor, source: Uri) {
        if (source.scheme == "file" || source.scheme == null) {
            extractor.setDataSource(source.path ?: throw IOException("Unusable file URI: $source"))
        } else {
            val descriptor = context.contentResolver.openFileDescriptor(source, "r")
                ?: throw IOException("Cannot open $source")
            descriptor.use { extractor.setDataSource(it.fileDescriptor) }
        }
    }

    private suspend fun decodeToChunks(
        extractor: MediaExtractor,
        format: MediaFormat,
        outputDir: File,
        chunkSeconds: Int,
        onProgress: (Float) -> Unit,
    ): List<AudioChunk> {
        val mime = format.getString(MediaFormat.KEY_MIME)
            ?: throw IOException("Audio track has no MIME type")
        val sourceSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val sourceChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val totalDurationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
            format.getLong(MediaFormat.KEY_DURATION)
        } else {
            0L
        }

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val chunks = mutableListOf<AudioChunk>()
        val samplesPerChunk = chunkSeconds.toLong() * TARGET_SAMPLE_RATE
        var writer: WavWriter? = null
        var chunkIndex = 0
        var samplesInChunk = 0L
        var totalSamples = 0L
        // Fractional read position into the source stream, for linear resampling.
        var resamplePosition = 0.0
        var carry: FloatArray = FloatArray(0)

        fun startChunk() {
            val file = File(outputDir, "chunk_%03d.wav".format(chunkIndex))
            writer = WavWriter(file, TARGET_SAMPLE_RATE)
            chunks += AudioChunk(
                file = file,
                startSeconds = totalSamples.toDouble() / TARGET_SAMPLE_RATE,
                durationSeconds = 0.0,
                index = chunkIndex,
            )
        }

        fun finishChunk() {
            val current = writer ?: return
            current.close()
            val last = chunks.lastIndex
            if (last >= 0) {
                chunks[last] = chunks[last].copy(
                    durationSeconds = samplesInChunk.toDouble() / TARGET_SAMPLE_RATE,
                )
            }
            writer = null
            samplesInChunk = 0
            chunkIndex++
        }

        try {
            startChunk()
            val info = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false

            while (!sawOutputEos) {
                currentCoroutineContext().ensureActive()

                if (!sawInputEos) {
                    val inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val buffer = codec.getInputBuffer(inputIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inputIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                    else -> {
                        if (outputIndex >= 0) {
                            if (info.size > 0) {
                                val outBuffer = codec.getOutputBuffer(outputIndex)!!
                                outBuffer.position(info.offset)
                                outBuffer.limit(info.offset + info.size)

                                // Some decoders throw if outputFormat is read before
                                // they have reported a format change; fall back to the
                                // track's own format when that happens.
                                val outFormat = runCatching { codec.outputFormat }.getOrNull()
                                val mono = toMonoFloats(
                                    buffer = outBuffer,
                                    channels = outFormat.intOr(
                                        MediaFormat.KEY_CHANNEL_COUNT,
                                        sourceChannels,
                                    ),
                                    isFloat = isFloatOutput(outFormat),
                                )

                                val actualRate = outFormat.intOr(
                                    MediaFormat.KEY_SAMPLE_RATE,
                                    sourceSampleRate,
                                )

                                val combined = if (carry.isEmpty()) {
                                    mono
                                } else {
                                    carry + mono
                                }
                                val resample = resampleLinear(combined, actualRate, resamplePosition)
                                resamplePosition = resample.nextPosition
                                carry = resample.tail

                                var offset = 0
                                while (offset < resample.samples.size) {
                                    val room = (samplesPerChunk - samplesInChunk).toInt()
                                    val take = minOf(room, resample.samples.size - offset)
                                    writer!!.writeSamples(resample.samples, offset, take)
                                    offset += take
                                    samplesInChunk += take
                                    totalSamples += take
                                    if (samplesInChunk >= samplesPerChunk) {
                                        finishChunk()
                                        startChunk()
                                    }
                                }

                                if (totalDurationUs > 0) {
                                    onProgress(
                                        (info.presentationTimeUs.toFloat() / totalDurationUs)
                                            .coerceIn(0f, 1f),
                                    )
                                }
                            }
                            codec.releaseOutputBuffer(outputIndex, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                sawOutputEos = true
                            }
                        }
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            finishChunk()
        }

        onProgress(1f)
        // A trailing empty chunk appears when the audio length is an exact multiple.
        return chunks.filter { it.file.length() > WavWriter.HEADER_BYTES }
    }

    /** ENCODING_PCM_FLOAT is 4; declaring the constant avoids an AudioFormat import. */
    private fun isFloatOutput(format: MediaFormat?): Boolean =
        format != null &&
            format.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
            format.getInteger(MediaFormat.KEY_PCM_ENCODING) == 4

    private fun MediaFormat?.intOr(key: String, fallback: Int): Int =
        if (this != null && containsKey(key)) {
            runCatching { getInteger(key) }.getOrDefault(fallback)
        } else {
            fallback
        }

    /** Downmixes interleaved PCM to a mono float signal in [-1, 1]. */
    private fun toMonoFloats(buffer: ByteBuffer, channels: Int, isFloat: Boolean): FloatArray {
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val safeChannels = channels.coerceAtLeast(1)
        return if (isFloat) {
            val floats = buffer.asFloatBuffer()
            val frames = floats.remaining() / safeChannels
            FloatArray(frames) { frame ->
                var sum = 0f
                for (c in 0 until safeChannels) sum += floats.get(frame * safeChannels + c)
                sum / safeChannels
            }
        } else {
            val shorts = buffer.asShortBuffer()
            val frames = shorts.remaining() / safeChannels
            FloatArray(frames) { frame ->
                var sum = 0f
                for (c in 0 until safeChannels) sum += shorts.get(frame * safeChannels + c) / 32768f
                sum / safeChannels
            }
        }
    }

    private data class Resampled(
        val samples: ShortArray,
        val nextPosition: Double,
        val tail: FloatArray,
    )

    /**
     * Linear-interpolation resample to [TARGET_SAMPLE_RATE].
     *
     * [position] carries the fractional read offset between calls, and the last
     * source sample is returned as [Resampled.tail] so the next buffer can
     * interpolate across the boundary without a click.
     */
    private fun resampleLinear(input: FloatArray, sourceRate: Int, position: Double): Resampled {
        if (input.isEmpty()) return Resampled(ShortArray(0), position, FloatArray(0))
        if (sourceRate == TARGET_SAMPLE_RATE) {
            val out = ShortArray(input.size) { toPcm16(input[it]) }
            return Resampled(out, 0.0, FloatArray(0))
        }

        val step = sourceRate.toDouble() / TARGET_SAMPLE_RATE
        val out = ArrayList<Short>(((input.size / step) + 2).toInt())
        var pos = position
        while (pos < input.size - 1) {
            val index = pos.toInt()
            val frac = (pos - index).toFloat()
            val sample = input[index] * (1 - frac) + input[index + 1] * frac
            out += toPcm16(sample)
            pos += step
        }

        // Keep the last source sample so the next call can interpolate into it.
        val tailStart = (input.size - 1).coerceAtLeast(0)
        return Resampled(
            samples = out.toShortArray(),
            nextPosition = (pos - tailStart).coerceAtLeast(0.0),
            tail = floatArrayOf(input[tailStart]),
        )
    }

    private fun toPcm16(value: Float): Short =
        (value.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
}

/** Streams 16-bit mono PCM into a WAV file, back-patching the header on close. */
class WavWriter(private val file: File, private val sampleRate: Int) {

    companion object {
        const val HEADER_BYTES = 44
    }

    private val stream: OutputStream = file.outputStream().buffered(1 shl 16)
    private var dataBytes = 0L

    init {
        stream.write(ByteArray(HEADER_BYTES)) // Placeholder, rewritten in close().
    }

    fun writeSamples(samples: ShortArray, offset: Int, count: Int) {
        if (count <= 0) return
        val bytes = ByteArray(count * 2)
        for (i in 0 until count) {
            val value = samples[offset + i].toInt()
            bytes[i * 2] = (value and 0xFF).toByte()
            bytes[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
        }
        stream.write(bytes)
        dataBytes += bytes.size
    }

    fun close() {
        stream.flush()
        stream.close()
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.write(header(dataBytes))
        }
    }

    private fun header(dataSize: Long): ByteArray {
        val buffer = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        val byteRate = sampleRate * 1 * 16 / 8
        buffer.put("RIFF".toByteArray())
        buffer.putInt((36 + dataSize).toInt())
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)          // Sub-chunk size for PCM.
        buffer.putShort(1)         // Format: PCM.
        buffer.putShort(1)         // Channels: mono.
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(2)         // Block align.
        buffer.putShort(16)        // Bits per sample.
        buffer.put("data".toByteArray())
        buffer.putInt(dataSize.toInt())
        return buffer.array()
    }
}
