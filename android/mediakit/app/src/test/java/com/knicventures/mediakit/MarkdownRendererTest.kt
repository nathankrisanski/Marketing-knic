package com.knicventures.mediakit

import com.knicventures.mediakit.transcribe.MarkdownRenderer
import com.knicventures.mediakit.transcribe.TranscriptDocument
import com.knicventures.mediakit.transcribe.TranscriptSegment
import com.knicventures.mediakit.transcribe.TranscriptStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownRendererTest {

    private val document = TranscriptDocument(
        title = "Quarterly update",
        segments = listOf(
            TranscriptSegment(0.0, 4.5, "Welcome to the update."),
            TranscriptSegment(4.5, 62.0, "Revenue grew twelve percent."),
        ),
        language = "en",
        engineName = "Whisper API (whisper-1)",
        durationSeconds = 3725.0,
    )

    @Test
    fun `formats timestamps with hours only when needed`() {
        assertEquals("00:00", MarkdownRenderer.formatTimestamp(0.0))
        assertEquals("01:05", MarkdownRenderer.formatTimestamp(65.4))
        assertEquals("1:02:05", MarkdownRenderer.formatTimestamp(3725.0))
    }

    @Test
    fun `writes front matter and timestamped body`() {
        val markdown = MarkdownRenderer.render(
            document = document,
            style = TranscriptStyle.TIMESTAMPED,
            sourceUrl = "https://example.com/video",
        )

        assertTrue(markdown.startsWith("---\n"))
        assertTrue(markdown.contains("title: \"Quarterly update\""))
        assertTrue(markdown.contains("source: \"https://example.com/video\""))
        assertTrue(markdown.contains("duration: \"1:02:05\""))
        assertTrue(markdown.contains("word_count: 8"))
        assertTrue(markdown.contains("# Quarterly update"))
        assertTrue(markdown.contains("**[00:04]**"))
        assertTrue(markdown.endsWith("\n"))
    }

    @Test
    fun `prose style omits timestamps and front matter can be disabled`() {
        val markdown = MarkdownRenderer.render(
            document = document,
            style = TranscriptStyle.PROSE,
            includeFrontMatter = false,
        )

        assertFalse(markdown.contains("---"))
        assertFalse(markdown.contains("[00:00]"))
        assertTrue(markdown.contains("Welcome to the update."))
    }

    @Test
    fun `bullet style emits one line per segment`() {
        val markdown = MarkdownRenderer.render(
            document = document,
            style = TranscriptStyle.BULLETS,
            includeFrontMatter = false,
        )

        assertEquals(2, markdown.lines().count { it.startsWith("- `[") })
    }

    @Test
    fun `empty transcripts say so instead of rendering nothing`() {
        val markdown = MarkdownRenderer.render(document.copy(segments = emptyList()))
        assertTrue(markdown.contains("No speech was detected"))
    }
}
