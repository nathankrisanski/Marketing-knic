package com.knicventures.mediakit.transcribe

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import kotlin.concurrent.thread

/**
 * Offline transcription through Android's own on-device recogniser.
 *
 * No server, no API key, no data leaving the phone — but it depends on the
 * device shipping an on-device recognition model (Android 13+, and in practice
 * a Google-services device with the speech model downloaded). Quality and
 * punctuation are well below Whisper's, and timings are per recognition
 * segment rather than per phrase, so [WhisperApiEngine] stays the default.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class OnDeviceSpeechEngine(private val context: Context) : TranscriptionEngine {

    override val name: String = "Android on-device speech"

    override suspend fun unavailableReason(): String? = when {
        !SpeechRecognizer.isOnDeviceRecognitionAvailable(context) ->
            "This device has no on-device speech model installed."

        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED ->
            "Android requires the microphone permission before any speech recogniser will run, " +
                "even when the audio comes from a file."

        else -> null
    }

    override suspend fun transcribe(chunk: AudioChunk, languageHint: String?): TranscriptionResult {
        unavailableReason()?.let { throw IOException(it) }
        val phrases = recognize(chunk, languageHint)

        // The recogniser reports no timings, so phrases are spread evenly across
        // the chunk. Good enough to navigate by; not frame-accurate.
        val perPhrase = if (phrases.isEmpty()) 0.0 else chunk.durationSeconds / phrases.size
        val segments = phrases.mapIndexed { index, text ->
            TranscriptSegment(
                startSeconds = chunk.startSeconds + index * perPhrase,
                endSeconds = chunk.startSeconds + (index + 1) * perPhrase,
                text = text,
            )
        }
        return TranscriptionResult(segments, languageHint, name)
    }

    private suspend fun recognize(chunk: AudioChunk, languageHint: String?): List<String> =
        withContext(Dispatchers.Main) {
            val done = CompletableDeferred<List<String>>()
            val collected = mutableListOf<String>()

            val pipe = ParcelFileDescriptor.createPipe()
            val readSide = pipe[0]
            val writeSide = pipe[1]

            // Feed raw PCM (header stripped) into the recogniser off the main thread.
            thread(name = "pcm-feed-${chunk.index}") {
                runCatching {
                    ParcelFileDescriptor.AutoCloseOutputStream(writeSide).use { out ->
                        chunk.file.inputStream().use { input ->
                            input.skip(WavWriter.HEADER_BYTES.toLong())
                            input.copyTo(out, 1 shl 15)
                        }
                    }
                }
            }

            val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                languageHint?.takeIf { it.isNotBlank() }
                    ?.let { putExtra(RecognizerIntent.EXTRA_LANGUAGE, it) }
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, readSide)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                putExtra(
                    RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
                putExtra(
                    RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE,
                    AudioExtractor.TARGET_SAMPLE_RATE,
                )
                // Keeps the session alive across pauses instead of stopping at the
                // first silence, so a whole chunk is recognised in one pass.
                putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit

                override fun onError(error: Int) {
                    if (!done.isCompleted) done.complete(collected.toList())
                }

                override fun onResults(results: Bundle?) {
                    results?.take()?.let { collected += it }
                }

                override fun onSegmentResults(segmentResults: Bundle) {
                    segmentResults.take()?.let { collected += it }
                }

                override fun onEndOfSegmentedSession() {
                    if (!done.isCompleted) done.complete(collected.toList())
                }

                private fun Bundle.take(): String? =
                    getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.takeIf { it.isNotBlank() }
            })

            recognizer.startListening(intent)

            // Recognition runs near real time, so allow generous headroom.
            val timeoutMs = ((chunk.durationSeconds * 2 + 60) * 1000).toLong()
            val result = withTimeoutOrNull(timeoutMs) { done.await() } ?: collected.toList()

            runCatching { recognizer.stopListening() }
            runCatching { recognizer.destroy() }
            runCatching { readSide.close() }
            result
        }
}
