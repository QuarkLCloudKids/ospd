package com.quarlcloud.opsdonwloades.utils

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Resuelve enlaces de TikTok acortados (vm.tiktok.com/vt.tiktok.com)
 * siguiendo redirecciones para obtener la URL canónica (www.tiktok.com/.../video/ID).
 * Si falla, devuelve la URL original.
 */
object TikTokUrlResolver {
    private const val TAG = "TikTokUrlResolver"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    fun resolve(url: String): String {
        return try {
            val req = Request.Builder()
                .url(url)
                .get()
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Mobile")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            client.newCall(req).execute().use { resp ->
                val finalUrl = resp.request.url.toString()
                if (finalUrl != url) {
                    Log.d(TAG, "Resolved TikTok URL: $finalUrl")
                }
                finalUrl
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve TikTok URL, using original: ${e.message}")
            url
        }
    }
}