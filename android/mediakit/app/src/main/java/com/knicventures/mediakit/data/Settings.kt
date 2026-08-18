package com.knicventures.mediakit.data

import android.content.Context
import androidx.core.content.edit
import com.knicventures.mediakit.transcribe.TranscriptStyle
import com.knicventures.mediakit.util.Http

/** Which speech backend to run. */
enum class EngineChoice { WHISPER_API, ON_DEVICE }

data class AppSettings(
    val whisperBaseUrl: String = "https://api.openai.com/v1",
    val whisperApiKey: String = "",
    val whisperModel: String = "whisper-1",
    val engine: EngineChoice = EngineChoice.WHISPER_API,
    val languageHint: String = "",
    val transcriptStyle: TranscriptStyle = TranscriptStyle.TIMESTAMPED,
    val includeFrontMatter: Boolean = true,
    val chunkMinutes: Int = 10,
    val userAgent: String = Http.DEFAULT_USER_AGENT,
    val cookie: String = "",
    val preferHighestQuality: Boolean = true,
    val segmentConcurrency: Int = 4,
    /** Keep the demuxed .ts alongside the MP4 when remuxing fails. */
    val keepRawOnRemuxFailure: Boolean = true,
)

/**
 * Plain SharedPreferences store. The API key lives here because the app has no
 * account system — it is private to the app sandbox, and can be left empty when
 * pointing at a local whisper.cpp server.
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("mediakit_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        whisperBaseUrl = prefs.getString(KEY_BASE_URL, null) ?: "https://api.openai.com/v1",
        whisperApiKey = prefs.getString(KEY_API_KEY, null).orEmpty(),
        whisperModel = prefs.getString(KEY_MODEL, null) ?: "whisper-1",
        engine = runCatching {
            EngineChoice.valueOf(prefs.getString(KEY_ENGINE, null) ?: EngineChoice.WHISPER_API.name)
        }.getOrDefault(EngineChoice.WHISPER_API),
        languageHint = prefs.getString(KEY_LANGUAGE, null).orEmpty(),
        transcriptStyle = runCatching {
            TranscriptStyle.valueOf(prefs.getString(KEY_STYLE, null) ?: TranscriptStyle.TIMESTAMPED.name)
        }.getOrDefault(TranscriptStyle.TIMESTAMPED),
        includeFrontMatter = prefs.getBoolean(KEY_FRONT_MATTER, true),
        chunkMinutes = prefs.getInt(KEY_CHUNK_MINUTES, 10).coerceIn(1, 30),
        userAgent = prefs.getString(KEY_USER_AGENT, null) ?: Http.DEFAULT_USER_AGENT,
        cookie = prefs.getString(KEY_COOKIE, null).orEmpty(),
        preferHighestQuality = prefs.getBoolean(KEY_BEST_QUALITY, true),
        segmentConcurrency = prefs.getInt(KEY_CONCURRENCY, 4).coerceIn(1, 8),
        keepRawOnRemuxFailure = prefs.getBoolean(KEY_KEEP_RAW, true),
    )

    fun save(settings: AppSettings) = prefs.edit {
        putString(KEY_BASE_URL, settings.whisperBaseUrl)
        putString(KEY_API_KEY, settings.whisperApiKey)
        putString(KEY_MODEL, settings.whisperModel)
        putString(KEY_ENGINE, settings.engine.name)
        putString(KEY_LANGUAGE, settings.languageHint)
        putString(KEY_STYLE, settings.transcriptStyle.name)
        putBoolean(KEY_FRONT_MATTER, settings.includeFrontMatter)
        putInt(KEY_CHUNK_MINUTES, settings.chunkMinutes)
        putString(KEY_USER_AGENT, settings.userAgent)
        putString(KEY_COOKIE, settings.cookie)
        putBoolean(KEY_BEST_QUALITY, settings.preferHighestQuality)
        putInt(KEY_CONCURRENCY, settings.segmentConcurrency)
        putBoolean(KEY_KEEP_RAW, settings.keepRawOnRemuxFailure)
    }

    private companion object {
        const val KEY_BASE_URL = "whisper_base_url"
        const val KEY_API_KEY = "whisper_api_key"
        const val KEY_MODEL = "whisper_model"
        const val KEY_ENGINE = "engine"
        const val KEY_LANGUAGE = "language_hint"
        const val KEY_STYLE = "transcript_style"
        const val KEY_FRONT_MATTER = "front_matter"
        const val KEY_CHUNK_MINUTES = "chunk_minutes"
        const val KEY_USER_AGENT = "user_agent"
        const val KEY_COOKIE = "cookie"
        const val KEY_BEST_QUALITY = "best_quality"
        const val KEY_CONCURRENCY = "segment_concurrency"
        const val KEY_KEEP_RAW = "keep_raw"
    }
}
