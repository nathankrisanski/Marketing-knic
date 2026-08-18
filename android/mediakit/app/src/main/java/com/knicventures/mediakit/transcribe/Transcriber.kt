package com.knicventures.mediakit.transcribe

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Where the pipeline currently is, for progress reporting. */
enum class TranscribePhase { EXTRACTING_AUDIO, RECOGNISING, WRITING }

data class TranscribeProgress(
    val phase: TranscribePhase,
    val fraction: Float,
    val detail: String,
)

/**
 * Runs the whole file-to-markdown path: decode audio, recognise each chunk,
 * merge the chunk timelines, and render markdown.
 */
class Transcriber(
    private val context: Context,
    private val engine: TranscriptionEngine,
) {

    suspend fun transcribe(
        source: Uri,
        sourceLabel: String,
        workDir: File,
        languageHint: String? = null,
        chunkSeconds: Int = 600,
        onProgress: (TranscribeProgress) -> Unit = {},
    ): TranscriptDocument = withContext(Dispatchers.IO) {
        engine.unavailableReason()?.let { throw IllegalStateException(it) }

        val audioDir = File(workDir, "audio").apply { mkdirs() }
        val chunks = try {
            AudioExtractor(context).extractChunks(source, audioDir, chunkSeconds) { fraction ->
                onProgress(
                    TranscribeProgress(
                        TranscribePhase.EXTRACTING_AUDIO,
                        fraction,
                        "Extracting audio",
                    ),
                )
            }
        } catch (e: Exception) {
            audioDir.deleteRecursively()
            throw e
        }

        if (chunks.isEmpty()) {
            audioDir.deleteRecursively()
            throw IllegalStateException("No audio could be extracted from this file.")
        }

        val segments = mutableListOf<TranscriptSegment>()
        var language: String? = languageHint
        try {
            chunks.forEachIndexed { index, chunk ->
                onProgress(
                    TranscribeProgress(
                        phase = TranscribePhase.RECOGNISING,
                        fraction = index.toFloat() / chunks.size,
                        detail = "Transcribing part ${index + 1} of ${chunks.size}",
                    ),
                )
                val result = engine.transcribe(chunk, language)
                segments += result.segments
                if (language == null) language = result.language
            }
        } finally {
            audioDir.deleteRecursively()
        }

        onProgress(TranscribeProgress(TranscribePhase.WRITING, 1f, "Writing markdown"))

        val totalDuration = chunks.sumOf { it.durationSeconds }
        TranscriptDocument(
            title = sourceLabel,
            segments = mergeShortSegments(segments.sortedBy { it.startSeconds }),
            language = language,
            engineName = engine.name,
            durationSeconds = totalDuration,
        )
    }

    /**
     * Whisper emits one segment per breath group. Gluing consecutive fragments
     * into ~30-second paragraphs makes the markdown readable instead of a wall
     * of one-line timestamps.
     */
    private fun mergeShortSegments(
        segments: List<TranscriptSegment>,
        maxParagraphSeconds: Double = 30.0,
    ): List<TranscriptSegment> {
        if (segments.isEmpty()) return segments
        val merged = mutableListOf<TranscriptSegment>()
        var current = segments.first()

        for (next in segments.drop(1)) {
            val wouldSpan = next.endSeconds - current.startSeconds
            val sameSpeaker = current.speaker == next.speaker
            val endsSentence = current.text.trimEnd().lastOrNull() in setOf('.', '!', '?', '。', '？', '！')

            current = if (sameSpeaker && wouldSpan <= maxParagraphSeconds && !endsSentence) {
                current.copy(
                    endSeconds = next.endSeconds,
                    text = "${current.text.trim()} ${next.text.trim()}",
                )
            } else {
                merged += current
                next
            }
        }
        merged += current
        return merged
    }
}
