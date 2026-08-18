package com.knicventures.mediakit.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.knicventures.mediakit.data.AppSettings
import com.knicventures.mediakit.data.EngineChoice

/** Pick any video or audio file on the device and turn it into a markdown transcript. */
@Composable
fun TranscribeScreen(
    state: UiState,
    settings: AppSettings,
    onFilePicked: (Uri) -> Unit,
    onTranscribe: () -> Unit,
) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onFilePicked) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Transcribe a file", style = MaterialTheme.typography.titleMedium)
        Text(
            "Any video or audio file this device can play. The transcript is written " +
                "as markdown into Documents/MediaKit.",
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedButton(
            onClick = { picker.launch(arrayOf("video/*", "audio/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Choose file")
        }

        state.pickedFile?.let { picked ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(picked.displayName, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        picked.uri.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Engine", style = MaterialTheme.typography.labelLarge)
                Text(
                    when (settings.engine) {
                        EngineChoice.WHISPER_API ->
                            "Whisper API · ${settings.whisperModel} · ${settings.whisperBaseUrl}"
                        EngineChoice.ON_DEVICE ->
                            "Android on-device speech · fully offline, lower accuracy"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Change this under Settings.",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        Button(
            onClick = onTranscribe,
            enabled = state.pickedFile != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Transcribe to markdown")
        }
    }
}
