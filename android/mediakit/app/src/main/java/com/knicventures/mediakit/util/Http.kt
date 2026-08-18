package com.knicventures.mediakit.util

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Shared OkHttp client plus the header set that keeps most CDNs from rejecting
 * us. Segment servers routinely 403 anything that does not look like a browser,
 * and many of them additionally require the Referer of the embedding page.
 */
object Http {

    const val DEFAULT_USER_AGENT: String =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/122.0.0.0 Mobile Safari/537.36"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun request(
        url: String,
        referer: String? = null,
        userAgent: String = DEFAULT_USER_AGENT,
        cookie: String? = null,
        range: String? = null,
    ): Request {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "*/*")
        referer?.takeIf { it.isNotBlank() }?.let {
            builder.header("Referer", it)
            originOf(it)?.let { origin -> builder.header("Origin", origin) }
        }
        cookie?.takeIf { it.isNotBlank() }?.let { builder.header("Cookie", it) }
        range?.let { builder.header("Range", it) }
        return builder.build()
    }

    /** Executes [request], throwing with the status line when the call fails. */
    fun execute(request: Request): Response {
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw HttpException(code, "HTTP $code for ${request.url}")
        }
        return response
    }

    fun getString(
        url: String,
        referer: String? = null,
        userAgent: String = DEFAULT_USER_AGENT,
        cookie: String? = null,
    ): String = execute(request(url, referer, userAgent, cookie)).use { response ->
        response.body?.string().orEmpty()
    }

    fun getBytes(
        url: String,
        referer: String? = null,
        userAgent: String = DEFAULT_USER_AGENT,
        cookie: String? = null,
        range: String? = null,
    ): ByteArray = execute(request(url, referer, userAgent, cookie, range)).use { response ->
        response.body?.bytes() ?: ByteArray(0)
    }

    fun originOf(url: String): String? = runCatching {
        val uri = java.net.URI(url)
        val scheme = uri.scheme ?: return@runCatching null
        val host = uri.host ?: return@runCatching null
        val port = if (uri.port > 0) ":${uri.port}" else ""
        "$scheme://$host$port"
    }.getOrNull()
}

class HttpException(val code: Int, message: String) : java.io.IOException(message)
