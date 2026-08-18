package com.knicventures.mediakit.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.knicventures.mediakit.data.SettingsStore
import com.knicventures.mediakit.hls.HlsDownloader
import com.knicventures.mediakit.hls.Remuxer
import com.knicventures.mediakit.hls.Variant
import com.knicventures.mediakit.util.Notifications
import com.knicventures.mediakit.util.OutputStore
import java.io.File

/**
 * Downloads an HLS stream (or a direct MP4) and writes a finished MP4 into
 * shared storage.
 *
 * Runs as expedited foreground work so a long download survives the app being
 * backgrounded, and reports progress the UI can observe.
 */
class DownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_URL = "url"
        const val KEY_REFERER = "referer"
        const val KEY_TITLE = "title"
        const val KEY_VARIANT_URL = "variant_url"

        const val KEY_PROGRESS = "progress"
        const val KEY_STAGE = "stage"
        const val KEY_OUTPUT_URI = "output_uri"
        const val KEY_OUTPUT_NAME = "output_name"
        const val KEY_OUTPUT_LOCATION = "output_location"
        const val KEY_ERROR = "error"
        /** Feeds a chained TranscribeWorker. */
        const val KEY_SOURCE_URI = "source_uri"

        private const val NOTIFICATION_ID = 4101

        fun inputData(url: String, referer: String?, title: String, variantUrl: String?): Data =
            workDataOf(
                KEY_URL to url,
                KEY_REFERER to referer,
                KEY_TITLE to title,
                KEY_VARIANT_URL to variantUrl,
            )
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        foregroundInfo("Preparing download", null)

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL)
            ?: return Result.failure(workDataOf(KEY_ERROR to "No stream URL was supplied."))
        val referer = inputData.getString(KEY_REFERER)
        val rawTitle = inputData.getString(KEY_TITLE).orEmpty()
        val variantUrl = inputData.getString(KEY_VARIANT_URL)

        val settings = SettingsStore(applicationContext).load()
        val downloader = HlsDownloader(
            userAgent = settings.userAgent,
            cookie = settings.cookie.takeIf { it.isNotBlank() },
            concurrency = settings.segmentConcurrency,
        )

        val baseName = OutputStore.sanitizeFileName(
            raw = rawTitle.ifBlank { defaultNameFrom(url) },
            fallback = "stream",
        )
        val workDir = File(applicationContext.cacheDir, "download/${id}").apply { mkdirs() }
        val rawFile = File(workDir, "$baseName.raw")
        val mp4File = File(workDir, "$baseName.mp4")

        return try {
            setProgressSafely(0f, "Reading playlist")

            val target = variantUrl ?: url
            val isPlaylist = target.substringBefore('?').endsWith(".m3u8", ignoreCase = true) ||
                variantUrl != null

            val downloaded = if (isPlaylist) {
                val (playlist, playlistUrl) = downloader.loadMediaPlaylist(target, referer) { variants ->
                    selectVariant(variants, settings.preferHighestQuality)
                }
                if (playlist.isLive) {
                    setProgressSafely(0f, "Live stream — capturing the current window")
                }
                downloader.download(
                    playlist = playlist,
                    referer = referer ?: playlistUrl,
                    target = rawFile,
                    scratchDir = File(workDir, "segments"),
                ) { progress ->
                    setProgressSafely(
                        progress.fraction * 0.85f,
                        "Downloading segment ${progress.completedSegments}/${progress.totalSegments}",
                    )
                }
            } else {
                downloader.downloadDirect(target, referer, rawFile) { progress ->
                    val fraction = if (progress.totalSegments > 0) {
                        progress.completedSegments / 100f
                    } else {
                        0f
                    }
                    setProgressSafely(fraction * 0.85f, "Downloading file")
                }
            }

            setProgressSafely(0.87f, "Building MP4 container")

            val saved = try {
                Remuxer.remuxToMp4(downloaded.file, mp4File) { fraction ->
                    setProgressSafely(0.87f + fraction * 0.1f, "Building MP4 container")
                }
                setProgressSafely(0.98f, "Saving to your library")
                OutputStore.saveVideo(applicationContext, mp4File, "$baseName.mp4")
            } catch (e: Exception) {
                if (!settings.keepRawOnRemuxFailure) throw e
                // The stream downloaded fine but the platform muxer refused it —
                // hand over the raw stream rather than losing the download.
                setProgressSafely(0.98f, "Saving raw stream (MP4 conversion failed)")
                OutputStore.saveVideo(applicationContext, downloaded.file, "$baseName.ts")
            }

            Notifications.finished(
                applicationContext,
                NOTIFICATION_ID + 1,
                "Download complete",
                "${saved.displayName} saved to ${saved.locationLabel}",
            )

            Result.success(
                workDataOf(
                    KEY_OUTPUT_URI to saved.uri.toString(),
                    KEY_OUTPUT_NAME to saved.displayName,
                    KEY_OUTPUT_LOCATION to saved.locationLabel,
                    KEY_SOURCE_URI to saved.uri.toString(),
                    KEY_TITLE to baseName,
                ),
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Notifications.finished(
                applicationContext,
                NOTIFICATION_ID + 2,
                "Download failed",
                e.message ?: e.javaClass.simpleName,
            )
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Download failed")))
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun selectVariant(variants: List<Variant>, preferBest: Boolean): Variant? {
        if (variants.isEmpty()) return null
        return if (preferBest) {
            variants.maxByOrNull { it.bandwidth * 1000 + it.height }
        } else {
            // Middle rendition: a sane balance of size and quality.
            variants.sortedBy { it.bandwidth }[variants.size / 2]
        }
    }

    private fun defaultNameFrom(url: String): String {
        val path = url.substringBefore('?').substringAfterLast('/')
        val stem = path.substringBeforeLast('.', path)
        return stem.ifBlank { "stream" }
    }

    private suspend fun setProgressSafely(fraction: Float, stage: String) {
        setProgress(
            workDataOf(
                KEY_PROGRESS to fraction.coerceIn(0f, 1f),
                KEY_STAGE to stage,
            ),
        )
        runCatching {
            setForeground(foregroundInfo(stage, (fraction * 100).toInt()))
        }
    }

    private fun foregroundInfo(stage: String, percent: Int?): ForegroundInfo {
        val notification = Notifications.progress(
            applicationContext,
            title = "Downloading video",
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
