package com.knicventures.mediakit.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** Tags used to filter the job list in the UI. */
object JobTags {
    const val DOWNLOAD = "mediakit_download"
    const val TRANSCRIBE = "mediakit_transcribe"
    const val ANY = "mediakit_job"
}

object Jobs {

    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * Enqueues a download, optionally chaining a transcription onto its output
     * so "download and transcribe" is a single tap.
     */
    fun enqueueDownload(
        context: Context,
        url: String,
        referer: String?,
        title: String,
        variantUrl: String?,
        alsoTranscribe: Boolean,
    ): UUID {
        val download = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(DownloadWorker.inputData(url, referer, title, variantUrl))
            .setConstraints(networkConstraints)
            .addTag(JobTags.DOWNLOAD)
            .addTag(JobTags.ANY)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        val manager = WorkManager.getInstance(context)
        if (!alsoTranscribe) {
            manager.enqueueUniqueWork(uniqueName(title), ExistingWorkPolicy.APPEND_OR_REPLACE, download)
            return download.id
        }

        // The download's output carries source_uri and title straight into the
        // transcription step, so no extra input wiring is needed here.
        val transcribe = OneTimeWorkRequestBuilder<TranscribeWorker>()
            .setInputData(
                androidx.work.workDataOf(TranscribeWorker.KEY_SOURCE_URL to url),
            )
            .setConstraints(networkConstraints)
            .addTag(JobTags.TRANSCRIBE)
            .addTag(JobTags.ANY)
            .build()

        manager.beginUniqueWork(uniqueName(title), ExistingWorkPolicy.APPEND_OR_REPLACE, download)
            .then(transcribe)
            .enqueue()
        return download.id
    }

    fun enqueueTranscription(
        context: Context,
        sourceUri: String,
        title: String,
        sourceUrl: String? = null,
    ): UUID {
        val request = OneTimeWorkRequestBuilder<TranscribeWorker>()
            .setInputData(TranscribeWorker.inputData(sourceUri, title, sourceUrl))
            .addTag(JobTags.TRANSCRIBE)
            .addTag(JobTags.ANY)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(uniqueName(title), ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        return request.id
    }

    fun observeJobs(context: Context): Flow<List<WorkInfo>> =
        WorkManager.getInstance(context).getWorkInfosByTagFlow(JobTags.ANY)

    fun cancel(context: Context, id: UUID) {
        WorkManager.getInstance(context).cancelWorkById(id)
    }

    fun pruneFinished(context: Context) {
        WorkManager.getInstance(context).pruneWork()
    }

    /** Keeps repeat taps on the same source from queueing duplicate work. */
    private fun uniqueName(title: String): String = "mediakit:${title.take(60)}"
}
