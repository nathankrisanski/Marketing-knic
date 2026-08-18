package com.knicventures.mediakit.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.knicventures.mediakit.data.AppSettings
import com.knicventures.mediakit.data.SettingsStore
import com.knicventures.mediakit.hls.DiscoveredStream
import com.knicventures.mediakit.hls.HlsDownloader
import com.knicventures.mediakit.hls.PlaylistLoad
import com.knicventures.mediakit.hls.StreamResolver
import com.knicventures.mediakit.hls.Variant
import com.knicventures.mediakit.hls.WebViewSniffer
import com.knicventures.mediakit.work.Jobs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A file the user picked or shared in, ready to transcribe. */
data class PickedFile(val uri: Uri, val displayName: String)

data class UiState(
    val inputUrl: String = "",
    val isResolving: Boolean = false,
    val resolveStatus: String? = null,
    val discovered: List<DiscoveredStream> = emptyList(),
    val selectedStream: DiscoveredStream? = null,
    val variants: List<Variant> = emptyList(),
    val selectedVariant: Variant? = null,
    val isLoadingVariants: Boolean = false,
    val title: String = "",
    val alsoTranscribe: Boolean = false,
    val pickedFile: PickedFile? = null,
    val message: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsStore = SettingsStore(app)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val jobs: StateFlow<List<WorkInfo>> = Jobs.observeJobs(app)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onUrlChanged(value: String) {
        _uiState.value = _uiState.value.copy(inputUrl = value)
    }

    fun onTitleChanged(value: String) {
        _uiState.value = _uiState.value.copy(title = value)
    }

    fun onAlsoTranscribeChanged(value: Boolean) {
        _uiState.value = _uiState.value.copy(alsoTranscribe = value)
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    /** Handles a URL or media file arriving from the system share sheet. */
    fun onShared(text: String?, uri: Uri?) {
        text?.takeIf { it.isNotBlank() }?.let {
            _uiState.value = _uiState.value.copy(inputUrl = it.trim())
            resolve()
        }
        uri?.let { onFilePicked(it) }
    }

    /**
     * Finds playable streams behind [UiState.inputUrl].
     *
     * Static scraping runs first; if the page builds its playlist URL in
     * JavaScript, the off-screen WebView capture picks it up instead.
     */
    fun resolve() {
        val input = _uiState.value.inputUrl.trim()
        if (input.isEmpty()) {
            _uiState.value = _uiState.value.copy(message = "Paste a page URL or a direct .m3u8 link first.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isResolving = true,
                resolveStatus = "Looking for streams in the page…",
                discovered = emptyList(),
                selectedStream = null,
                variants = emptyList(),
                selectedVariant = null,
            )

            val current = _settings.value
            val resolver = StreamResolver(
                userAgent = current.userAgent,
                cookie = current.cookie.takeIf { it.isNotBlank() },
            )

            var found = withContext(Dispatchers.IO) {
                runCatching { resolver.verify(resolver.resolve(input)) }.getOrDefault(emptyList())
            }

            if (found.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    resolveStatus = "Nothing in the HTML — watching the player's network traffic…",
                )
                found = runCatching {
                    WebViewSniffer(getApplication<Application>()).sniff(input, current.userAgent)
                }.getOrDefault(emptyList())
            }

            if (found.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    isResolving = false,
                    resolveStatus = null,
                    message = "No stream found. If the video needs a login, paste its cookie in " +
                        "Settings, or open the player's network tab and paste the .m3u8 link directly.",
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                isResolving = false,
                resolveStatus = "Found ${found.size} stream${if (found.size == 1) "" else "s"}",
                discovered = found,
                title = _uiState.value.title.ifBlank { defaultTitle(input) },
            )
            selectStream(found.first())
        }
    }

    /** Selects a stream and, when it is a master playlist, lists its renditions. */
    fun selectStream(stream: DiscoveredStream) {
        _uiState.value = _uiState.value.copy(
            selectedStream = stream,
            variants = emptyList(),
            selectedVariant = null,
            isLoadingVariants = stream.url.contains(".m3u8", ignoreCase = true),
        )
        if (!stream.url.contains(".m3u8", ignoreCase = true)) return

        viewModelScope.launch {
            val current = _settings.value
            val downloader = HlsDownloader(
                userAgent = current.userAgent,
                cookie = current.cookie.takeIf { it.isNotBlank() },
            )
            val load = runCatching { downloader.loadPlaylist(stream.url, stream.referer) }.getOrNull()
            val variants = (load as? PlaylistLoad.Master)?.playlist?.variants.orEmpty()
            _uiState.value = _uiState.value.copy(
                isLoadingVariants = false,
                variants = variants,
                selectedVariant = if (current.preferHighestQuality) {
                    variants.maxByOrNull { it.bandwidth }
                } else {
                    variants.sortedBy { it.bandwidth }.getOrNull(variants.size / 2)
                },
            )
        }
    }

    fun selectVariant(variant: Variant) {
        _uiState.value = _uiState.value.copy(selectedVariant = variant)
    }

    fun startDownload() {
        val state = _uiState.value
        val stream = state.selectedStream ?: run {
            _uiState.value = state.copy(message = "Find a stream first.")
            return
        }
        Jobs.enqueueDownload(
            context = getApplication<Application>(),
            url = stream.url,
            referer = stream.referer,
            title = state.title.ifBlank { defaultTitle(stream.url) },
            variantUrl = state.selectedVariant?.url,
            alsoTranscribe = state.alsoTranscribe,
        )
        _uiState.value = state.copy(
            message = if (state.alsoTranscribe) {
                "Queued: download, then transcribe. Progress is under Jobs."
            } else {
                "Download queued. Progress is under Jobs."
            },
        )
    }

    fun onFilePicked(uri: Uri) {
        // The job may outlive this Activity, so hold on to read access where the
        // picker allows it. Share-sheet URIs are not persistable and just throw.
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val name = displayNameOf(uri)
        _uiState.value = _uiState.value.copy(pickedFile = PickedFile(uri, name))
    }

    fun startTranscription() {
        val picked = _uiState.value.pickedFile ?: run {
            _uiState.value = _uiState.value.copy(message = "Choose a video or audio file first.")
            return
        }
        Jobs.enqueueTranscription(
            context = getApplication<Application>(),
            sourceUri = picked.uri.toString(),
            title = picked.displayName.substringBeforeLast('.'),
        )
        _uiState.value = _uiState.value.copy(
            message = "Transcription queued. Progress is under Jobs.",
        )
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        settingsStore.save(updated)
    }

    fun cancelJob(info: WorkInfo) {
        Jobs.cancel(getApplication<Application>(), info.id)
    }

    fun clearFinishedJobs() {
        Jobs.pruneFinished(getApplication<Application>())
    }

    private fun displayNameOf(uri: Uri): String {
        if (uri.scheme == "file") return uri.lastPathSegment.orEmpty().ifBlank { "video" }
        val resolver = getApplication<Application>().contentResolver
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull().orEmpty().ifBlank { uri.lastPathSegment.orEmpty().ifBlank { "video" } }
    }

    /** A readable default name derived from the URL's last meaningful path part. */
    private fun defaultTitle(url: String): String {
        val path = url.substringBefore('?').trimEnd('/')
        val last = path.substringAfterLast('/')
        val stem = last.substringBeforeLast('.', last)
        return stem
            .replace('-', ' ')
            .replace('_', ' ')
            .trim()
            .ifBlank { runCatching { Uri.parse(url).host }.getOrNull().orEmpty() }
            .ifBlank { "video" }
    }
}
