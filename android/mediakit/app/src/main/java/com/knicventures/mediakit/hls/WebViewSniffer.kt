package com.knicventures.mediakit.hls

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.knicventures.mediakit.util.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Loads a page in an off-screen WebView and records every playlist request the
 * page's own JavaScript makes.
 *
 * This is the fallback for players that assemble the playlist URL at runtime —
 * signed URLs, token handshakes, DRM-free but obfuscated players — where there
 * is nothing in the served HTML for [StreamResolver] to find.
 */
class WebViewSniffer(private val context: Context) {

    /**
     * @param pageUrl page to load.
     * @param timeoutMs how long to let the page run before giving up.
     * @param settleMs quiet period after the first hit before returning early.
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun sniff(
        pageUrl: String,
        userAgent: String = Http.DEFAULT_USER_AGENT,
        timeoutMs: Long = 25_000,
        settleMs: Long = 2_500,
    ): List<DiscoveredStream> {
        // shouldInterceptRequest fires on WebView worker threads, so keep this concurrent.
        val hits = ConcurrentHashMap<String, DiscoveredStream>()
        val lastHitAt = AtomicLong(0L)

        val webView = withContext(Dispatchers.Main) {
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.userAgentString = userAgent
                settings.loadsImagesAutomatically = false
                settings.blockNetworkImage = true

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? {
                        val url = request.url.toString()
                        val path = url.substringBefore('?').lowercase()
                        if (path.endsWith(".m3u8") || path.endsWith(".mp4") || path.endsWith(".mpd")) {
                            val referer = request.requestHeaders["Referer"] ?: pageUrl
                            hits.putIfAbsent(url, DiscoveredStream(url, referer, "network capture"))
                            lastHitAt.set(System.currentTimeMillis())
                        }
                        // Never intercept: let the page keep loading normally.
                        return null
                    }
                }
            }
        }

        try {
            withContext(Dispatchers.Main) { webView.loadUrl(pageUrl) }
            withTimeoutOrNull(timeoutMs) {
                while (true) {
                    delay(250)
                    val last = lastHitAt.get()
                    if (last != 0L && System.currentTimeMillis() - last > settleMs) return@withTimeoutOrNull
                }
            }
        } finally {
            withContext(Dispatchers.Main) {
                webView.stopLoading()
                webView.destroy()
            }
        }

        // A master playlist is more useful than the variant the player picked.
        return hits.values.sortedByDescending { it.url.contains(".m3u8", ignoreCase = true) }
    }
}
