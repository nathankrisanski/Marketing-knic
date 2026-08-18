package com.knicventures.mediakit.transcribe

/** One timed span of recognised speech. */
data class TranscriptSegment(
    val startSeconds: Double,
    val endSeconds: Double,
    val text: String,
    val speaker: String? = null,
)

data class TranscriptionResult(
    val segments: List<TranscriptSegment>,
    val language: String?,
    val engineName: String,
) {
    val plainText: String
        get() = segments.joinToString(" ") { it.text.trim() }.trim()
}

/**
 * A speech-to-text backend.
 *
 * Implementations receive one [AudioChunk] at a time (16 kHz mono WAV) and are
 * responsible only for that chunk; [Transcriber] stitches the chunks back into
 * a single timeline.
 */
interface TranscriptionEngine {
    val name: String

    /** Human-readable reason this engine cannot run right now, or null if it can. */
    suspend fun unavailableReason(): String?

    suspend fun transcribe(chunk: AudioChunk, languageHint: String?): TranscriptionResult
}
