package com.knicventures.mediakit.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.knicventures.mediakit.data.EngineChoice
import com.knicventures.mediakit.data.SettingsStore
import com.knicventures.mediakit.transcribe.MarkdownRenderer
import com.knicventures.mediakit.transcribe.OnDeviceSpeechEngine
import com.knicventures.mediakit.transcribe.TranscribePhase
import com.knicventures.mediakit.transcribe.Transcriber
import com.knicventures.mediakit.transcribe.TranscriptionEngine
import com.knicventures.mediakit.transcribe.WhisperApiEngine
import com.knicventures.mediakit.util.Notifications
import com.knicventures.mediakit.util.OutputStore
import java.io.File

/**
 * Transcribes any media file the device can decode and writes the transcript to
 * a markdown file in shared storage.
 *
 * Accepts either a `content://` URI from the picker/share sheet or the output of
 * a preceding [DownloadWorker] in the same chain.
 */
class TranscribeWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_SOURCE_URI = "source_uri"
        const val KEY_TITLE = "title"
        const val KEY_SOURCE_URL = "source_url"

        const val KEY_PROGRESS = "progress"
        const val KEY_STAGE = "stage"
        const val KEY_OUTPUT_URI = "output_uri"
        const val KEY_OUTPUT_NAME = "output_name"
        const val KEY_OUTPUT_LOCATION = "output_location"
        const val KEY_PREVIEW = "preview"
        const val KEY_ERROR = "error"

        private const val NOTIFICATION_ID = 4201
        private const val PREVIEW_CHARS = 1200

        fun inputData(sourceUri: String, title: String, sourceUrl: String? = null): Data =
            workDataOf(
                KEY_SOURCE_URI to sourceUri,
                KEY_TITLE to title,
                KEY_SOURCE_URL to sourceUrl,
            )
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        foregroundInfo("Preparing transcription", null)

    override suspend fun doWork(): Result {
        val sourceUri = inputData.getString(KEY_SOURCE_URI)
            ?: return Result.failure(workDataOf(KEY_ERROR to "No file was supplied to transcribe."))
        val title = inputData.getString(KEY_TITLE).orEmpty().ifBlank { "Transcript" }
        val sourceUrl = inputData.getString(KEY_SOURCE_URL)

        val settings = SettingsStore(applicationContext).load()
        val engine: TranscriptionEngine = when {
            settings.engine == EngineChoice.ON_DEVICE &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                OnDeviceSpeechEngine(applicationContext)

            settings.engine == EngineChoice.ON_DEVICE ->
                return Result.failure(
                    workDataOf(
                        KEY_ERROR to "On-device transcription needs Android 13 or newer. " +
                            "Switch to a Whisper server in Settings.",
                    ),
                )

            else -> WhisperApiEngine(
                baseUrl = settings.whisperBaseUrl,
                apiKey = settings.whisperApiKey,
                model = settings.whisperModel,
            )
        }

        val workDir = File(applicationContext.cacheDir, "transcribe/${id}").apply { mkdirs() }

        return try {
            val document = Transcriber(applicationContext, engine).transcribe(
                source = Uri.parse(sourceUri),
                sourceLabel = title,
                workDir = workDir,
                languageHint = settings.languageHint.takeIf { it.isNotBlank() },
                chunkSeconds = settings.chunkMinutes * 60,
            ) { progress ->
                val fraction = when (progress.phase) {
                    // Audio extraction is roughly a quarter of the wall time.
                    TranscribePhase.EXTRACTING_AUDIO -> progress.fraction * 0.25f
                    TranscribePhase.RECOGNISING -> 0.25f + progress.fraction * 0.7f
                    TranscribePhase.WRITING -> 0.96f
                }
                setProgressSafely(fraction, progress.detail)
            }

            val markdown = MarkdownRenderer.render(
                document = document,
                style = settings.transcriptStyle,
                includeFrontMatter = settings.includeFrontMatter,
                sourceUrl = sourceUrl,
            )

            setProgressSafely(0.98f, "Saving markdown")
            val fileName = "${OutputStore.sanitizeFileName(title, "transcript")}.md"
            val saved = OutputStore.saveMarkdown(applicationContext, markdown, fileName)

            Notifications.finished(
                applicationContext,
                NOTIFICATION_ID + 1,
                "Transcript ready",
                "${saved.displayName} saved to ${saved.locationLabel}",
            )

            Result.success(
                workDataOf(
                    KEY_OUTPUT_URI to saved.uri.toString(),
                    KEY_OUTPUT_NAME to saved.displayName,
                    KEY_OUTPUT_LOCATION to saved.locationLabel,
                    KEY_PREVIEW to markdown.take(PREVIEW_CHARS),
                ),
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Notifications.finished(
                applicationContext,
                NOTIFICATION_ID + 2,
                "Transcription failed",
                e.message ?: e.javaClass.simpleName,
            )
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Transcription failed")))
        } finally {
            workDir.deleteRecursively()
        }
    }

    private suspend fun setProgressSafely(fraction: Float, stage: String) {
        setProgress(
            workDataOf(
                KEY_PROGRESS to fraction.coerceIn(0f, 1f),
                KEY_STAGE to stage,
            ),
        )
        runCatching { setForeground(foregroundInfo(stage, (fraction * 100).toInt())) }
    }

    private fun foregroundInfo(stage: String, percent: Int?): ForegroundInfo {
        val notification = Notifications.progress(
            applicationContext,
            title = "Transcribing",
            text = stage,
            percent = percent,
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
}
