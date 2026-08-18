package com.knicventures.mediakit.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.knicventures.mediakit.data.AppSettings
import com.knicventures.mediakit.data.EngineChoice
import com.knicventures.mediakit.transcribe.TranscriptStyle

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    // Android will not start any recogniser without RECORD_AUDIO, even though the
    // audio comes from a file, so ask for it the moment on-device mode is picked.
    val requestMicrophone = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* the engine reports the outcome when a job runs */ }

    val onEngineSelected: (EngineChoice) -> Unit = { choice ->
        onChange { it.copy(engine = choice) }
        if (choice == EngineChoice.ON_DEVICE) {
            requestMicrophone.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Transcription engine", style = MaterialTheme.typography.titleMedium)

        EngineChoice.entries.forEach { choice ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = settings.engine == choice,
                        onClick = { onEngineSelected(choice) },
                    )
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = settings.engine == choice,
                    onClick = { onEngineSelected(choice) },
                )
                Column {
                    Text(
                        when (choice) {
                            EngineChoice.WHISPER_API -> "Whisper-compatible server"
                            EngineChoice.ON_DEVICE -> "Android on-device speech"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        when (choice) {
                            EngineChoice.WHISPER_API ->
                                "OpenAI, Groq, or your own whisper.cpp / faster-whisper server."
                            EngineChoice.ON_DEVICE ->
                                "Offline, no key needed. Android 13+ with a speech model installed; " +
                                    "lower accuracy and approximate timings."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        if (settings.engine == EngineChoice.WHISPER_API) {
            OutlinedTextField(
                value = settings.whisperBaseUrl,
                onValueChange = { value -> onChange { it.copy(whisperBaseUrl = value) } },
                label = { Text("Server base URL") },
                supportingText = { Text("e.g. https://api.openai.com/v1 or http://192.168.1.20:8080/v1") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = settings.whisperApiKey,
                onValueChange = { value -> onChange { it.copy(whisperApiKey = value) } },
                label = { Text("API key") },
                supportingText = { Text("Leave blank for a local server that needs no key.") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = settings.whisperModel,
                onValueChange = { value -> onChange { it.copy(whisperModel = value) } },
                label = { Text("Model") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        OutlinedTextField(
            value = settings.languageHint,
            onValueChange = { value -> onChange { it.copy(languageHint = value) } },
            label = { Text("Language hint (optional)") },
            supportingText = { Text("ISO code such as en, es, fr. Blank means auto-detect.") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider()
        Text("Transcript format", style = MaterialTheme.typography.titleMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TranscriptStyle.entries.forEach { style ->
                FilterChip(
                    selected = settings.transcriptStyle == style,
                    onClick = { onChange { it.copy(transcriptStyle = style) } },
                    label = {
                        Text(
                            when (style) {
                                TranscriptStyle.TIMESTAMPED -> "Timestamps"
                                TranscriptStyle.PROSE -> "Prose"
                                TranscriptStyle.BULLETS -> "Bullets"
                            },
                        )
                    },
                )
            }
        }

        ToggleRow(
            title = "YAML front matter",
            subtitle = "Adds title, source, duration and word count at the top of the file.",
            checked = settings.includeFrontMatter,
            onCheckedChange = { value -> onChange { it.copy(includeFrontMatter = value) } },
        )

        Text("Audio chunk length: ${settings.chunkMinutes} min", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Long files are split before being sent for recognition. Smaller chunks use " +
                "less memory and stay under upload limits.",
            style = MaterialTheme.typography.bodySmall,
        )
        Slider(
            value = settings.chunkMinutes.toFloat(),
            onValueChange = { value -> onChange { it.copy(chunkMinutes = value.toInt()) } },
            valueRange = 1f..30f,
            steps = 28,
        )

        HorizontalDivider()
        Text("Downloading", style = MaterialTheme.typography.titleMedium)

        ToggleRow(
            title = "Prefer highest quality",
            subtitle = "Picks the top rendition in a master playlist instead of a mid one.",
            checked = settings.preferHighestQuality,
            onCheckedChange = { value -> onChange { it.copy(preferHighestQuality = value) } },
        )

        ToggleRow(
            title = "Keep raw stream if MP4 conversion fails",
            subtitle = "Saves the downloaded .ts rather than discarding the download.",
            checked = settings.keepRawOnRemuxFailure,
            onCheckedChange = { value -> onChange { it.copy(keepRawOnRemuxFailure = value) } },
        )

        Text(
            "Parallel segment downloads: ${settings.segmentConcurrency}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = settings.segmentConcurrency.toFloat(),
            onValueChange = { value -> onChange { it.copy(segmentConcurrency = value.toInt()) } },
            valueRange = 1f..8f,
            steps = 6,
        )

        HorizontalDivider()
        Text("Request headers", style = MaterialTheme.typography.titleMedium)
        Text(
            "Some CDNs only serve segments to a browser-looking client, or to a signed-in " +
                "session. Paste a cookie here if a stream 403s.",
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedTextField(
            value = settings.userAgent,
            onValueChange = { value -> onChange { it.copy(userAgent = value) } },
            label = { Text("User-Agent") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = settings.cookie,
            onValueChange = { value -> onChange { it.copy(cookie = value) } },
            label = { Text("Cookie header (optional)") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
