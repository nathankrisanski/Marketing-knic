package com.knicventures.mediakit.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private enum class Tab(val label: String, val icon: ImageVector) {
    FETCH("Fetch", Icons.Filled.CloudDownload),
    TRANSCRIBE("Transcribe", Icons.Filled.Article),
    JOBS("Jobs", Icons.Filled.Sync),
    SETTINGS("Settings", Icons.Filled.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaKitApp(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.FETCH) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("MediaKit") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = { Icon(entry.icon, contentDescription = entry.label) },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            when (tab) {
                Tab.FETCH -> FetchScreen(
                    state = uiState,
                    onUrlChanged = viewModel::onUrlChanged,
                    onTitleChanged = viewModel::onTitleChanged,
                    onResolve = viewModel::resolve,
                    onSelectStream = viewModel::selectStream,
                    onSelectVariant = viewModel::selectVariant,
                    onAlsoTranscribeChanged = viewModel::onAlsoTranscribeChanged,
                    onDownload = viewModel::startDownload,
                )

                Tab.TRANSCRIBE -> TranscribeScreen(
                    state = uiState,
                    settings = settings,
                    onFilePicked = viewModel::onFilePicked,
                    onTranscribe = viewModel::startTranscription,
                )

                Tab.JOBS -> JobsScreen(
                    jobs = jobs,
                    onCancel = viewModel::cancelJob,
                    onClearFinished = viewModel::clearFinishedJobs,
                )

                Tab.SETTINGS -> SettingsScreen(
                    settings = settings,
                    onChange = viewModel::updateSettings,
                )
            }
        }
    }
}
