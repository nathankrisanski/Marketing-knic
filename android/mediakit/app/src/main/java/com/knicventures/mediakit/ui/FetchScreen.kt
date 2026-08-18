package com.knicventures.mediakit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.knicventures.mediakit.hls.DiscoveredStream
import com.knicventures.mediakit.hls.Variant

/**
 * "Give me the video behind this link" — accepts a page URL or a direct
 * playlist link, shows what was found, and queues the download.
 */
@Composable
fun FetchScreen(
    state: UiState,
    onUrlChanged: (String) -> Unit,
    onTitleChanged: (String) -> Unit,
    onResolve: () -> Unit,
    onSelectStream: (DiscoveredStream) -> Unit,
    onSelectVariant: (Variant) -> Unit,
    onAlsoTranscribeChanged: (Boolean) -> Unit,
    onDownload: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Video source", style = MaterialTheme.typography.titleMedium)
        Text(
            "Paste the page the video plays on, or a direct .m3u8 / .mp4 link.",
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedTextField(
            value = state.inputUrl,
            onValueChange = onUrlChanged,
            label = { Text("Page URL or stream link") },
            singleLine = false,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onResolve, enabled = !state.isResolving) {
                Text(if (state.isResolving) "Searching…" else "Find streams")
            }
            if (state.isResolving) {
                CircularProgressIndicator(modifier = Modifier.padding(4.dp), strokeWidth = 2.dp)
            }
        }

        state.resolveStatus?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }

        if (state.discovered.isNotEmpty()) {
            HorizontalDivider()
            Text("Streams found", style = MaterialTheme.typography.titleMedium)

            state.discovered.forEach { stream ->
                StreamRow(
                    stream = stream,
                    selected = stream == state.selectedStream,
                    onSelect = { onSelectStream(stream) },
                )
            }
        }

        if (state.isLoadingVariants) {
            Text("Reading playlist…", style = MaterialTheme.typography.bodySmall)
        }

        if (state.variants.isNotEmpty()) {
            HorizontalDivider()
            Text("Quality", style = MaterialTheme.typography.titleMedium)
            state.variants.forEach { variant ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = variant == state.selectedVariant,
                            onClick = { onSelectVariant(variant) },
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = variant == state.selectedVariant,
                        onClick = { onSelectVariant(variant) },
                    )
                    Text(variant.label, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (state.selectedStream != null) {
            HorizontalDivider()
            OutlinedTextField(
                value = state.title,
                onValueChange = onTitleChanged,
                label = { Text("Save as") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Transcribe after download", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Writes a markdown transcript next to the MP4.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = state.alsoTranscribe, onCheckedChange = onAlsoTranscribeChanged)
            }

            Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                Text("Download as MP4")
            }
        }
    }
}

@Composable
private fun StreamRow(stream: DiscoveredStream, selected: Boolean, onSelect: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = onSelect)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stream.host.ifBlank { "stream" } +
                            if (stream.isMaster) " · master playlist" else "",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stream.url,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "via ${stream.source}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
