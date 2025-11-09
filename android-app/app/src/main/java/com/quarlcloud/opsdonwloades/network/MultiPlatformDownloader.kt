package com.quarlcloud.opsdonwloades.network

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.net.URLDecoder

class MultiPlatformDownloader(private val context: Context) {
    companion object {
        private const val TAG = "MultiPlatformDownloader"
    }

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun downloadInstagram(url: String, preferHd: Boolean, onProgress: (Float) -> Unit): DownloadResult {
        return try {
            val ddUrl = if (url.contains("ddinstagram.com")) url else "https://ddinstagram.com/?url=" + URLEncoder.encode(url, "UTF-8")
            Log.d(TAG, "Usando ddinstagram: $ddUrl")
            val request = Request.Builder().url(ddUrl).get()
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Mobile")
                .addHeader("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .build()
            val resp = withContext(Dispatchers.IO) { client.newCall(request).execute() }
            if (!resp.isSuccessful) {
                Log.w(TAG, "ddinstagram respondió: HTTP ${resp.code}; intentando fallback SnapInsta")
                throw IOException("IG primaria HTTP ${resp.code}")
            }
            val html = resp.body?.string() ?: ""
            val candidates = mutableListOf<String>()

            // 1) Extraer directamente URLs .mp4 del HTML
            val urlRegex = Regex("https?://[^\\s\\\"'<>]+\\.mp4[^\\s\\\"'<>]*", RegexOption.IGNORE_CASE)
            urlRegex.findAll(html).forEach { candidates.add(it.value) }

            // 2) Extraer metatags og:video y og:video:secure_url
            val metaRegex = Regex("property=\\\"og:(?:video|video:secure_url)\\\"\\s+content=\\\"([^\\\"]+)\\\"", RegexOption.IGNORE_CASE)
            metaRegex.findAll(html).forEach { m ->
                val u = m.groupValues.getOrNull(1)
                if (u != null && u.startsWith("http")) candidates.add(u)
            }

            val best = chooseByQuality(candidates, preferHd)
            if (best != null) {
                return downloadFileFromUrl(best, "instagram_${System.currentTimeMillis()}.mp4", onProgress)
            }
            // Fallback: SnapInsta
            try {
                Log.d(TAG, "IG fallback a SnapInsta")
                val form = FormBody.Builder().add("url", url).build()
                val req = Request.Builder()
                    .url("https://snapinsta.app/action.php")
                    .post(form)
                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Mobile")
                    .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .addHeader("X-Requested-With", "XMLHttpRequest")
                    .addHeader("Origin", "https://snapinsta.app")
                    .addHeader("Referer", "https://snapinsta.app/")
                    .addHeader("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                    .build()
                val r2 = withContext(Dispatchers.IO) { client.newCall(req).execute() }
                if (r2.isSuccessful) {
                    val h2 = r2.body?.string() ?: ""
                    val c2 = mutableListOf<String>()
                    val urlRegex2 = Regex("https?://[^\\s\\\"'<>]+\\.mp4[^\\s\\\"'<>]*", RegexOption.IGNORE_CASE)
                    urlRegex2.findAll(h2).forEach { c2.add(it.value) }
                    val best2 = chooseByQuality(c2, preferHd)
                    if (best2 != null) {
                        return downloadFileFromUrl(best2, "instagram_${System.currentTimeMillis()}.mp4", onProgress)
                    }
                } else {
                    Log.w(TAG, "SnapInsta respondió: HTTP ${r2.code}")
                }
            } catch (e2: Exception) {
                Log.w(TAG, "IG fallback SnapInsta error: ${e2.message}")
            }
            // Fallback adicional: intentar extraer desde la página original de Instagram
            runCatching { tryInstagramDirect(url, preferHd, onProgress) }.getOrNull()?.let { return it }
            DownloadResult(false, null, "IG: ningún enlace válido (ddinstagram/SnapInsta/página IG)")
        } catch (e: Exception) {
            Log.e(TAG, "IG error primario", e)
            return try {
                Log.d(TAG, "IG fallback a SnapInsta tras excepción")
                val form = FormBody.Builder().add("url", url).build()
                val req = Request.Builder()
                    .url("https://snapinsta.app/action.php")
                    .post(form)
                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Mobile")
                    .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .addHeader("X-Requested-With", "XMLHttpRequest")
                    .addHeader("Origin", "https://snapinsta.app")
                    .addHeader("Referer", "https://snapinsta.app/")
                    .addHeader("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                    .build()
                val r2 = withContext(Dispatchers.IO) { client.newCall(req).execute() }
                if (r2.isSuccessful) {
                    val h2 = r2.body?.string() ?: ""
                    val c2 = mutableListOf<String>()
                    val urlRegex2 = Regex("https?://[^\\s\\\"'<>]+\\.mp4[^\\s\\\"'<>]*", RegexOption.IGNORE_CASE)
                    urlRegex2.findAll(h2).forEach { c2.add(it.value) }
                    val best2 = chooseByQuality(c2, preferHd)
                    if (best2 != null) {
                        return downloadFileFromUrl(best2, "instagram_${System.currentTimeMillis()}.mp4", onProgress)
                    }
                } else {
                    Log.w(TAG, "SnapInsta respondió: HTTP ${r2.code}")
                }
                // Fallback adicional: intentar extraer desde la página original de Instagram
                runCatching { tryInstagramDirect(url, preferHd, onProgress) }.getOrNull()?.let { return it }
                // No exponer el error de ddinstagram si el fallback también falla
                DownloadResult(false, null, "IG: proveedores fallaron (ddinstagram/SnapInsta/página IG)")
            } catch (e2: Exception) {
                Log.e(TAG, "IG fallback SnapInsta tras excepción falló", e2)
                // Último intento directo
                runCatching { tryInstagramDirect(url, preferHd, onProgress) }.getOrNull()?.let { return it }
                DownloadResult(false, null, "IG: proveedores fallaron (ddinstagram/SnapInsta/página IG)")
            }
        }
    }

    suspend fun downloadFacebook(url: String, preferHd: Boolean, onProgress: (Float) -> Unit): DownloadResult {
        return try {
            val resolved = runCatching { resolveFinalUrl(url) }.getOrElse { url }
            val form = FormBody.Builder().add("q", resolved).build()
            val req = Request.Builder()
                .url("https://snapsave.app/api/ajaxSearch")
                .post(form)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .addHeader("Origin", "https://snapsave.app")
                .addHeader("Referer", "https://snapsave.app/")
                .addHeader("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .build()
            val resp = withContext(Dispatchers.IO) { client.newCall(req).execute() }
            if (!resp.isSuccessful) {
                Log.w(TAG, "SnapSave respondió: HTTP ${resp.code}; intentando fallback FDown")
                throw IOException("FB primaria HTTP ${resp.code}")
            }
            val body = resp.body?.string() ?: ""
            val urls = extractMp4UrlsFromJson(body)
            val best = chooseByQuality(urls, preferHd)
            if (best != null) return downloadFileFromUrl(best, "facebook_${System.currentTimeMillis()}.mp4", onProgress)
            // Fallback: FDown
            try {
                Log.d(TAG, "FB fallback a FDown")
                val form2 = FormBody.Builder().add("URL", resolved).build()
                val req2 = Request.Builder()
                    .url("https://fdown.net/download.php")
                    .post(form2)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .addHeader("Origin", "https://fdown.net")
                    .addHeader("Referer", "https://fdown.net/")
                    .addHeader("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                    .build()
                val r2 = withContext(Dispatchers.IO) { client.newCall(req2).execute() }
                if (r2.isSuccessful) {
                    val html = r2.body?.string() ?: ""
                    val candidates = mutableListOf<String>()
                    val urlRegex = Regex("https?://[^\\s\\\"'<>]+\\.mp4[^\\s\\\"'<>]*", RegexOption.IGNORE_CASE)
                    urlRegex.findAll(html).forEach { candidates.add(it.value) }
                    val best2 = chooseByQuality(candidates, preferHd)
                    if (best2 != null) return downloadFileFromUrl(best2, "facebook_${System.currentTimeMillis()}.mp4", onProgress)
                } else {
                    Log.w(TAG, "FDown respondió: HTTP ${r2.code}")
                }
            } catch (e2: Exception) {
                Log.w(TAG, "FB fallback FDown error: ${e2.message}")
            }
            // Intentar un fallback adicional con GetFVid si FDown no devuelve enlaces
            try {
                Log.d(TAG, "FB fallback adicional a GetFVid")
                val form3 = FormBody.Builder().add("url", resolved).build()
                val req3 = Request.Builder()
                    .url("https://getfvid.com/downloader")
                    .post(form3)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .addHeader("Origin", "https://getfvid.com")
                    .addHeader("Referer", "https://getfvid.com/")
                    .addHeader("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                    .build()
                val r3 = withContext(Dispatchers.IO) { client.newCall(req3).execute() }
                if (r3.isSuccessful) {
                    val html3 = r3.body?.string() ?: ""
                    val candidates3 = mutableListOf<String>()
                    val urlRegex3 = Regex("https?://[^\\s\\\"'<>]+\\.mp4[^\\s\\\"'<>]*", RegexOption.IGNORE_CASE)
                    urlRegex3.findAll(html3).forEach { candidates3.add(it.value) }
                    val best3 = chooseByQuality(candidates3, preferHd)
                    if (best3 != null) return downloadFileFromUrl(best3, "facebook_${System.currentTimeMillis()}.mp4", onProgress)
                } else {
                    Log.w(TAG, "GetFVid respondió: HTTP ${r3.code}")
                }
            } catch (e3: Exception) {
                Log.w(TAG, "FB fallback GetFVid error: ${e3.message}")
            }
            // Fallback directo: mbasic.facebook.com
            runCatching { tryFacebookMbasic(resolved, preferHd, onProgress) }.getOrNull()?.let { return it }
            DownloadResult(false, null, "FB: ningún enlace válido (SnapSave/FDown/GetFVid/mbasic)")
        } catch (e: Exception) {
            Log.e(TAG, "FB error primario", e)
            return try {
                Log.d(TAG, "FB fallback a FDown tras excepción")
                val resolved = runCatching { resolveFinalUrl(url) }.getOrElse { url }
                val form2 = FormBody.Builder().add("URL", resolved).build()
                val req2 = Request.Builder()
                    .url("https://fdown.net/download.php")
                    .post(form2)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .addHeader("Origin", "https://fdown.net")
                    .addHeader("Referer", "https://fdown.net/")
                    .build()
                val r2 = withContext(Dispatchers.IO) { client.newCall(req2).execute() }
                if (r2.isSuccessful) {
                    val html = r2.body?.string() ?: ""
                    val candidates = mutableListOf<String>()
                    val urlRegex = Regex("https?://[^\\s\\\"'<>]+\\.mp4[^\\s\\\"'<>]*", RegexOption.IGNORE_CASE)
                    urlRegex.findAll(html).forEach { candidates.add(it.value) }
                    val best2 = chooseByQuality(candidates, preferHd)
                    if (best2 != null) return downloadFileFromUrl(best2, "facebook_${System.currentTimeMillis()}.mp4", onProgress)
                } else {
                    Log.w(TAG, "FDown respondió: HTTP ${r2.code}")
                }
                // Fallback adicional: GetFVid
                try {
                    Log.d(TAG, "FB fallback adicional a GetFVid tras excepción")
                    val form3 = FormBody.Builder().add("url", resolved).build()
                    val req3 = Request.Builder()
                        .url("https://getfvid.com/downloader")
                        .post(form3)
                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .addHeader("Origin", "https://getfvid.com")
                        .addHeader("Referer", "https://getfvid.com/")
                        .build()
                    val r3 = withContext(Dispatchers.IO) { client.newCall(req3).execute() }
                    if (r3.isSuccessful) {
                        val html3 = r3.body?.string() ?: ""
                        val candidates3 = mutableListOf<String>()
                        val urlRegex3 = Regex("https?://[^\\s\\\"'<>]+\\.mp4[^\\s\\\"'<>]*", RegexOption.IGNORE_CASE)
                        urlRegex3.findAll(html3).forEach { candidates3.add(it.value) }
                        val best3 = chooseByQuality(candidates3, preferHd)
                        if (best3 != null) return downloadFileFromUrl(best3, "facebook_${System.currentTimeMillis()}.mp4", onProgress)
                    } else {
                        Log.w(TAG, "GetFVid respondió: HTTP ${r3.code}")
                    }
                } catch (e3: Exception) {
                    Log.w(TAG, "FB fallback GetFVid tras excepción error: ${e3.message}")
                }
                // Fallback directo: mbasic.facebook.com
                runCatching { tryFacebookMbasic(resolved, preferHd, onProgress) }.getOrNull()?.let { return it }
                DownloadResult(false, null, "FB: proveedores fallaron (SnapSave/FDown/GetFVid)")
            } catch (e2: Exception) {
                Log.e(TAG, "FB fallback FDown tras excepción falló", e2)
                DownloadResult(false, null, "FB: proveedores fallaron (SnapSave/FDown/GetFVid)")
            }
        }
    }

    // Eliminado soporte para X/Twitter según requerimiento

    suspend fun downloadYouTube(videoId: String, preferHd: Boolean, onProgress: (Float) -> Unit): DownloadResult {
        return try {
            // Probar múltiples instancias públicas de Piped
            val bases = listOf(
                "https://piped.kavin.rocks/api/v1/streams/",
                "https://piped.video/api/v1/streams/",
                "https://piped.projectsegfau.lt/api/v1/streams/",
                "https://piped.tokhmi.xyz/api/v1/streams/",
                "https://piped.hostuxp.fr/api/v1/streams/",
                "https://piped.sudovanilla.com/api/v1/streams/",
                "https://piped.garudalinux.org/api/v1/streams/",
                "https://piped.in.projectsegfau.lt/api/v1/streams/",
                "https://pipedapi.kavin.rocks/api/v1/streams/",
                "https://piped.yt/api/v1/streams/"
            )
            var obj: JSONObject? = null
            var lastCode: Int? = null
            for (base in bases) {
                val api = base + videoId
                val req = Request.Builder().url(api).get()
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .addHeader("Accept", "application/json, text/plain, */*")
                    .addHeader("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                    .build()
                val resp = withContext(Dispatchers.IO) { client.newCall(req).execute() }
                if (!resp.isSuccessful) { lastCode = resp.code; continue }
                val contentType = resp.header("Content-Type") ?: ""
                val bodyStr = resp.body?.string() ?: ""
                val startsJsonLike = bodyStr.trimStart().startsWith("{") || bodyStr.trimStart().startsWith("[")
                if (!contentType.contains("json", true) || !startsJsonLike) {
                    // Respuesta HTML (Cloudflare/proxy), intentar siguiente instancia
                    continue
                }
                obj = runCatching { JSONObject(bodyStr) }.getOrNull()
                if (obj != null) break
            }
            if (obj == null) {
                // Fallback: Invidious API
                val invBases = listOf(
                    "https://yewtu.be/api/v1/videos/",
                    "https://inv.riverside.rocks/api/v1/videos/",
                    "https://vid.puffyan.us/api/v1/videos/"
                )
                val urlsInv = mutableListOf<String>()
                for (base in invBases) {
                    val api = base + videoId
                    val req = Request.Builder().url(api).get()
                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .addHeader("Accept", "application/json, text/plain, */*")
                        .addHeader("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                        .build()
                    val resp = withContext(Dispatchers.IO) { client.newCall(req).execute() }
                    if (!resp.isSuccessful) continue
                    val bodyStr = resp.body?.string() ?: ""
                    val j = runCatching { JSONObject(bodyStr) }.getOrNull() ?: continue
                    val arrays = listOf("formatStreams", "adaptiveFormats")
                    for (arrName in arrays) {
                        val arr = j.optJSONArray(arrName) ?: continue
                        for (i in 0 until arr.length()) {
                            val it = arr.optJSONObject(i) ?: continue
                            val url = it.optString("url", "")
                            val type = it.optString("type", it.optString("mimeType", ""))
                            val container = it.optString("container", "")
                            if (url.isNotBlank() && url.startsWith("http") && (container.contains("mp4", true) || type.contains("mp4", true))) {
                                urlsInv.add(url)
                            }
                        }
                    }
                    if (urlsInv.isNotEmpty()) break
                }
                val bestInv = chooseByQuality(urlsInv, preferHd)
                if (bestInv != null) return downloadFileFromUrl(bestInv, "youtube_${System.currentTimeMillis()}.mp4", onProgress)
                return DownloadResult(false, null, "YouTube: no pudimos obtener JSON de Piped/Invidious (instancias agotadas${lastCode?.let { ", última HTTP $it" } ?: ""})")
            }

            val urls = mutableListOf<String>()
            fun collect(arrName: String) {
                if (!obj!!.has(arrName)) return
                val arr = obj!!.optJSONArray(arrName) ?: return
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val url = item.optString("url", "")
                    val container = item.optString("container", "")
                    if (url.isNotBlank() && url.startsWith("http") && container.contains("mp4", true)) {
                        urls.add(url)
                    }
                }
            }
            collect("muxedStreams")
            collect("videoStreams")
            val best = chooseByQuality(urls, preferHd)
            if (best != null) return downloadFileFromUrl(best, "youtube_${System.currentTimeMillis()}.mp4", onProgress)
            DownloadResult(false, null, "No se encontró stream MP4 en Piped")
        } catch (e: Exception) {
            Log.e(TAG, "YT error", e)
            DownloadResult(false, null, "Error YT: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    // Helpers específicos de plataformas
    private suspend fun resolveFinalUrl(url: String): String {
        return try {
            val req = Request.Builder().url(url).get()
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Mobile")
                .addHeader("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .build()
            val resp = withContext(Dispatchers.IO) { client.newCall(req).execute() }
            resp.request.url.toString()
        } catch (_: Exception) {
            url
        }
    }

    private suspend fun tryFacebookMbasic(url: String, preferHd: Boolean, onProgress: (Float) -> Unit): DownloadResult? {
        return try {
            var u = url
            if (u.contains("facebook.com") || u.contains("fb.watch")) {
                u = u.replace("www.facebook.com", "mbasic.facebook.com")
                    .replace("m.facebook.com", "mbasic.facebook.com")
                    .replace("fb.watch", "mbasic.facebook.com")
            }
            val req = Request.Builder().url(u).get()
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Mobile")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .addHeader("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .build()
            val resp = withContext(Dispatchers.IO) { client.newCall(req).execute() }
            if (!resp.isSuccessful) return null
            val html = resp.body?.string() ?: ""
            val candidates = mutableListOf<String>()
            val redir = Regex("video_redirect/\\?src=([^\"'&]+)", RegexOption.IGNORE_CASE)
            redir.findAll(html).forEach {
                val enc = it.groupValues.getOrNull(1)
                val dec = enc?.let { s -> URLDecoder.decode(s, "UTF-8") }
                if (!dec.isNullOrBlank() && dec.startsWith("http")) candidates.add(dec)
            }
            val metaVid = Regex("property=\\\"og:video\\\"\\s+content=\\\"([^\\\"]+)\\\"", RegexOption.IGNORE_CASE)
            metaVid.findAll(html).forEach { m ->
                val u2 = m.groupValues.getOrNull(1)
                if (!u2.isNullOrBlank() && u2.startsWith("http")) candidates.add(u2)
            }
            // Extraer desde JSON embebido
            val jsonHd = Regex("\\\"playable_url_quality_hd\\\"\\s*:\\s*\\\"(https?:[^\\\"]+)\\\"", RegexOption.IGNORE_CASE)
            jsonHd.findAll(html).forEach { m ->
                val u2 = m.groupValues.getOrNull(1)
                if (!u2.isNullOrBlank()) candidates.add(u2)
            }
            val jsonSd = Regex("\\\"playable_url\\\"\\s*:\\s*\\\"(https?:[^\\\"]+)\\\"", RegexOption.IGNORE_CASE)
            jsonSd.findAll(html).forEach { m ->
                val u2 = m.groupValues.getOrNull(1)
                if (!u2.isNullOrBlank()) candidates.add(u2)
            }
            val best = chooseByQuality(candidates, preferHd)
            if (best != null) return downloadFileFromUrl(best, "facebook_${System.currentTimeMillis()}.mp4", onProgress)
            null
        } catch (_: Exception) { null }
    }

    private suspend fun tryInstagramDirect(url: String, preferHd: Boolean, onProgress: (Float) -> Unit): DownloadResult? {
        return try {
            val req = Request.Builder().url(url).get()
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Mobile")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .addHeader("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .build()
            val resp = withContext(Dispatchers.IO) { client.newCall(req).execute() }
            if (!resp.isSuccessful) return null
            val html = resp.body?.string() ?: ""
            val candidates = mutableListOf<String>()
            val urlRegex = Regex("https?://[^\\s\\\"'<>]+\\.mp4[^\\s\\\"'<>]*", RegexOption.IGNORE_CASE)
            urlRegex.findAll(html).forEach { candidates.add(it.value) }
            val metaRegex = Regex("property=\\\"og:(?:video|video:secure_url)\\\"\\s+content=\\\"([^\\\"]+)\\\"", RegexOption.IGNORE_CASE)
            metaRegex.findAll(html).forEach { m ->
                val u = m.groupValues.getOrNull(1)
                if (!u.isNullOrBlank() && u.startsWith("http")) candidates.add(u)
            }
            // Buscar URLs en JSON embebido: hd_url, video_url, contentUrl
            val jsonHd = Regex("\\\"hd_url\\\"\\s*:\\s*\\\"(https?:[^\\\"]+mp4[^\\\"]*)\\\"", RegexOption.IGNORE_CASE)
            jsonHd.findAll(html).forEach { m ->
                val u = m.groupValues.getOrNull(1)
                if (!u.isNullOrBlank()) candidates.add(u)
            }
            val jsonVideo = Regex("\\\"video_url\\\"\\s*:\\s*\\\"(https?:[^\\\"]+mp4[^\\\"]*)\\\"", RegexOption.IGNORE_CASE)
            jsonVideo.findAll(html).forEach { m ->
                val u = m.groupValues.getOrNull(1)
                if (!u.isNullOrBlank()) candidates.add(u)
            }
            val contentUrl = Regex("\\\"contentUrl\\\"\\s*:\\s*\\\"(https?:[^\\\"]+mp4[^\\\"]*)\\\"", RegexOption.IGNORE_CASE)
            contentUrl.findAll(html).forEach { m ->
                val u = m.groupValues.getOrNull(1)
                if (!u.isNullOrBlank()) candidates.add(u)
            }
            val best = chooseByQuality(candidates, preferHd)
            if (best != null) return downloadFileFromUrl(best, "instagram_${System.currentTimeMillis()}.mp4", onProgress)
            null
        } catch (_: Exception) { null }
    }

    private fun extractMp4UrlsFromJson(json: String): List<String> {
        val urls = mutableListOf<String>()
        try {
            // Intentar parsear JSON y luego, como fallback, regex de URLs
            runCatching {
                val obj = JSONObject(json)
                fun dive(o: Any?) {
                    when (o) {
                        is JSONObject -> {
                            val it = o.keys()
                            while (it.hasNext()) {
                                val k = it.next()
                                dive(o.opt(k))
                            }
                        }
                        is org.json.JSONArray -> {
                            for (i in 0 until o.length()) dive(o.opt(i))
                        }
                        is String -> {
                            if (o.startsWith("http") && o.contains(".mp4")) urls.add(o)
                        }
                    }
                }
                dive(obj)
            }
            if (urls.isEmpty()) {
                val urlRegex = Regex("https?://[^\\s\\\"'<>]+\\.mp4[^\\s\\\"'<>]*", RegexOption.IGNORE_CASE)
                urlRegex.findAll(json).forEach { urls.add(it.value) }
            }
        } catch (_: Exception) {}
        return urls.distinct()
    }

    private fun chooseByQuality(urls: List<String>, preferHd: Boolean): String? {
        val candidates = urls.filter { it.startsWith("http") }
        if (candidates.isEmpty()) return null
        val sorted = candidates.sortedByDescending { u ->
            when {
                u.contains("1080", true) -> 3
                u.contains("720", true) -> 2
                u.contains("480", true) -> 1
                else -> 0
            }
        }
        return if (preferHd) sorted.firstOrNull() else sorted.lastOrNull() ?: sorted.firstOrNull()
    }

    private suspend fun downloadFileFromUrl(url: String, fileName: String, onProgress: (Float) -> Unit): DownloadResult {
        return withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder().url(url).get()
                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Mobile")
                    .build()
                val resp = client.newCall(req).execute()
                if (!resp.isSuccessful) return@withContext DownloadResult(false, null, "HTTP ${resp.code}")

                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "OPSDownloader")
                if (!dir.exists()) dir.mkdirs()
                val output = File(dir, fileName)

                resp.body?.byteStream().use { inputStream ->
                    java.io.FileOutputStream(output).use { outputStream ->
                        val buffer = ByteArray(8 * 1024)
                        var total = 0L
                        val len = resp.body?.contentLength() ?: -1L
                        var read: Int
                        while (true) {
                            read = inputStream?.read(buffer) ?: -1
                            if (read <= 0) break
                            outputStream.write(buffer, 0, read)
                            total += read
                            if (len > 0) onProgress(0.5f + (total.toFloat() / len * 0.5f))
                        }
                        outputStream.flush()
                    }
                }

                if (output.exists() && output.length() > 0) {
                    try {
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(output.absolutePath),
                            arrayOf("video/mp4"),
                            null
                        )
                    } catch (_: Exception) {}
                    DownloadResult(true, output.absolutePath, null)
                } else {
                    throw IOException("Archivo vacío")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error descargando archivo", e)
                DownloadResult(false, null, e.message ?: "Error de descarga")
            }
        }
    }
}