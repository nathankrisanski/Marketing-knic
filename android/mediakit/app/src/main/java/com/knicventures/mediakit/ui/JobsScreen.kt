package com.knicventures.mediakit.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.work.WorkInfo
import com.knicventures.mediakit.work.DownloadWorker
import com.knicventures.mediakit.work.JobTags
import com.knicventures.mediakit.work.TranscribeWorker

/** Live view of every queued, running, and finished job. */
@Composable
fun JobsScreen(
    jobs: List<WorkInfo>,
    onCancel: (WorkInfo) -> Unit,
    onClearFinished: () -> Unit,
) {
    val ordered = jobs.sortedBy { it.state.isFinished }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Jobs", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onClearFinished) { Text("Clear finished") }
        }

        if (ordered.isEmpty()) {
            Text(
                "Nothing queued yet.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(ordered, key = { it.id.toString() }) { info -> JobCard(info, onCancel) }
            }
        }
    }
}

@Composable
private fun JobCard(info: WorkInfo, onCancel: (WorkInfo) -> Unit) {
    val context = LocalContext.current
    val isDownload = info.tags.contains(JobTags.DOWNLOAD)
    val kind = if (isDownload) "Download" else "Transcription"

    val stage = info.progress.getString(DownloadWorker.KEY_STAGE)
        ?: info.progress.getString(TranscribeWorker.KEY_STAGE)
    val fraction = info.progress.getFloat(DownloadWorker.KEY_PROGRESS, -1f)

    val outputName = info.outputData.getString(DownloadWorker.KEY_OUTPUT_NAME)
    val outputLocation = info.outputData.getString(DownloadWorker.KEY_OUTPUT_LOCATION)
    val outputUri = info.outputData.getString(DownloadWorker.KEY_OUTPUT_URI)
    val error = info.outputData.getString(DownloadWorker.KEY_ERROR)
    val preview = info.outputData.getString(TranscribeWorker.KEY_PREVIEW)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$kind · ${info.state.name.lowercase()}", style = MaterialTheme.typography.bodyLarge)
                if (!info.state.isFinished) {
                    TextButton(onClick = { onCancel(info) }) { Text("Cancel") }
                }
            }

            stage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

            if (info.state == WorkInfo.State.RUNNING) {
                if (fraction >= 0f) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
            }

            if (info.state == WorkInfo.State.SUCCEEDED && outputName != null) {
                Text(
                    "Saved $outputName${outputLocation?.let { " to $it" }.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                )
                outputUri?.let { uri ->
                    TextButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(
                                uri.toUri(),
                                if (outputName.endsWith(".md")) "text/plain" else "video/*",
                            )
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching { context.startActivity(intent) }
                    }) { Text("Open") }
                }
            }

            preview?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it.lineSequence().take(8).joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (info.state == WorkInfo.State.FAILED) {
                Text(
                    error ?: "Job failed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
