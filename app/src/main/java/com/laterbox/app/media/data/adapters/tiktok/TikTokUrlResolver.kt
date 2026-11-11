package com.laterbox.app.media.data.adapters.tiktok

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

class TikTokUrlResolver(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {

    companion object {
        fun isTikTokShortLink(url: String): Boolean {
            return try {
                val uri = URI(url)
                val host = uri.host?.lowercase() ?: return false
                val path = uri.path ?: ""
                host == "vm.tiktok.com" || host == "vt.tiktok.com" ||
                    (host.endsWith("tiktok.com") && path.startsWith("/t/")) ||
                    ((host == "localhost" || host == "127.0.0.1") && path.startsWith("/t/"))
            } catch (_: Throwable) {
                false
            }
        }

        fun isTikTokUrl(url: String): Boolean {
            return try {
                val host = URI(url).host?.lowercase() ?: return false
                host == "tiktok.com" || host.endsWith(".tiktok.com")
            } catch (_: Throwable) {
                false
            }
        }
    }

    suspend fun resolveCanonicalUrl(url: String): String = withContext(Dispatchers.IO) {
        if (!isTikTokShortLink(url)) {
            return@withContext url
        }

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1")
                .head()
                .build()

            client.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString()
                if (finalUrl.isNotBlank() && finalUrl != url) {
                    finalUrl
                } else {
                    // Try GET if HEAD didn't follow or was rejected
                    val getRequest = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1")
                        .build()
                    client.newCall(getRequest).execute().use { getResp ->
                        val getFinalUrl = getResp.request.url.toString()
                        if (getFinalUrl.isNotBlank()) getFinalUrl else url
                    }
                }
            }
        } catch (t: Throwable) {
            // If network fails (e.g. DNS Errno 7), rethrow or return original URL so outer retry handler handles it
            url
        }
    }
}
