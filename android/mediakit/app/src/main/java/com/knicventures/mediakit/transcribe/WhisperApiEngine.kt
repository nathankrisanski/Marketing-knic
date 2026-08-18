package com.knicventures.mediakit.transcribe

import com.knicventures.mediakit.util.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.IOException

/**
 * Speech-to-text over any OpenAI-compatible `/audio/transcriptions` endpoint.
 *
 * That covers OpenAI itself, Groq, and — the offline-friendly option — a
 * `whisper.cpp` server or a self-hosted `faster-whisper` running on your own
 * machine, which needs only a base URL and no key.
 */
class WhisperApiEngine(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String = "whisper-1",
) : TranscriptionEngine {

    override val name: String = "Whisper API ($model)"

    override suspend fun unavailableReason(): String? = when {
        baseUrl.isBlank() -> "No transcription server URL is set. Add one in Settings."
        !baseUrl.startsWith("http", ignoreCase = true) ->
            "The transcription server URL must start with http:// or https://"
        else -> null
    }

    override suspend fun transcribe(chunk: AudioChunk, languageHint: String?): TranscriptionResult =
        withContext(Dispatchers.IO) {
            val endpoint = buildEndpoint(baseUrl)

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    chunk.file.name,
                    chunk.file.asRequestBody("audio/wav".toMediaType()),
                )
                .addFormDataPart("model", model)
                // verbose_json is what carries per-segment timestamps.
                .addFormDataPart("response_format", "verbose_json")
                .apply {
                    languageHint?.takeIf { it.isNotBlank() }?.let { addFormDataPart("language", it) }
                }
                .build()

            val builder = Request.Builder()
                .url(endpoint)
                .post(body)
            if (apiKey.isNotBlank()) builder.header("Authorization", "Bearer $apiKey")

            val json = Http.execute(builder.build()).use { response ->
                response.body?.string().orEmpty()
            }
            parse(json, chunk)
        }

    /** Accepts a bare host, a `/v1` root, or the full endpoint path. */
    private fun buildEndpoint(base: String): String {
        val trimmed = base.trimEnd('/')
        return when {
            trimmed.endsWith("/audio/transcriptions") -> trimmed
            trimmed.endsWith("/v1") -> "$trimmed/audio/transcriptions"
            else -> "$trimmed/v1/audio/transcriptions"
        }
    }

    private fun parse(json: String, chunk: AudioChunk): TranscriptionResult {
        if (json.isBlank()) throw IOException("Transcription server returned an empty response.")
        val root = runCatching { JSONObject(json) }.getOrElse {
            throw IOException("Transcription server returned a response this app could not read.")
        }

        root.optJSONObject("error")?.let { error ->
            throw IOException(error.optString("message", "Transcription server reported an error."))
        }

        val language = root.optString("language").takeIf { it.isNotBlank() }
        val segmentsJson = root.optJSONArray("segments")

        // Some servers only return the flat `text` field.
        if (segmentsJson == null || segmentsJson.length() == 0) {
            val text = root.optString("text").trim()
            if (text.isEmpty()) return TranscriptionResult(emptyList(), language, name)
            return TranscriptionResult(
                segments = listOf(
                    TranscriptSegment(
                        startSeconds = chunk.startSeconds,
                        endSeconds = chunk.startSeconds + chunk.durationSeconds,
                        text = text,
                    ),
                ),
                language = language,
                engineName = name,
            )
        }

        val segments = ArrayList<TranscriptSegment>(segmentsJson.length())
        for (i in 0 until segmentsJson.length()) {
            val item = segmentsJson.optJSONObject(i) ?: continue
            val text = item.optString("text").trim()
            if (text.isEmpty()) continue
            segments += TranscriptSegment(
                // Chunk-relative timings are shifted onto the source timeline.
                startSeconds = chunk.startSeconds + item.optDouble("start", 0.0),
                endSeconds = chunk.startSeconds + item.optDouble("end", 0.0),
                text = text,
            )
        }
        return TranscriptionResult(segments, language, name)
    }
}
