package com.knicventures.mediakit.transcribe

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A finished transcript, ready to render. */
data class TranscriptDocument(
    val title: String,
    val segments: List<TranscriptSegment>,
    val language: String?,
    val engineName: String,
    val durationSeconds: Double,
) {
    val wordCount: Int
        get() = segments.sumOf { segment ->
            segment.text.split(Regex("\\s+")).count { it.isNotBlank() }
        }
}

/** How much structure to put in the rendered markdown. */
enum class TranscriptStyle {
    /** Timestamped paragraphs — the default, good for reviewing and citing. */
    TIMESTAMPED,

    /** Continuous prose with no timestamps — good for feeding into other tools. */
    PROSE,

    /** One `- [mm:ss] …` bullet per segment. */
    BULLETS,
}

object MarkdownRenderer {

    fun render(
        document: TranscriptDocument,
        style: TranscriptStyle = TranscriptStyle.TIMESTAMPED,
        includeFrontMatter: Boolean = true,
        sourceUrl: String? = null,
    ): String = buildString {
        val generatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())

        if (includeFrontMatter) {
            appendLine("---")
            appendLine("title: \"${document.title.replace("\"", "\\\"")}\"")
            sourceUrl?.takeIf { it.isNotBlank() }?.let { appendLine("source: \"$it\"") }
            appendLine("duration: \"${formatTimestamp(document.durationSeconds)}\"")
            document.language?.let { appendLine("language: \"$it\"") }
            appendLine("transcribed_with: \"${document.engineName}\"")
            appendLine("transcribed_at: \"$generatedAt\"")
            appendLine("word_count: ${document.wordCount}")
            appendLine("---")
            appendLine()
        }

        appendLine("# ${document.title}")
        appendLine()

        if (document.segments.isEmpty()) {
            appendLine("_No speech was detected in this file._")
            return@buildString
        }

        when (style) {
            TranscriptStyle.PROSE -> {
                document.segments.forEach { segment ->
                    appendLine(segment.text.trim())
                    appendLine()
                }
            }

            TranscriptStyle.BULLETS -> {
                document.segments.forEach { segment ->
                    val speaker = segment.speaker?.let { "**$it:** " }.orEmpty()
                    appendLine("- `[${formatTimestamp(segment.startSeconds)}]` $speaker${segment.text.trim()}")
                }
            }

            TranscriptStyle.TIMESTAMPED -> {
                document.segments.forEach { segment ->
                    val speaker = segment.speaker?.let { " · **$it**" }.orEmpty()
                    appendLine("**[${formatTimestamp(segment.startSeconds)}]**$speaker")
                    appendLine()
                    appendLine(segment.text.trim())
                    appendLine()
                }
            }
        }
    }.trimEnd() + "\n"

    /** `h:mm:ss` past an hour, `mm:ss` below it. */
    fun formatTimestamp(seconds: Double): String {
        val total = seconds.toLong().coerceAtLeast(0)
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val secs = total % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, secs)
        }
    }
}
