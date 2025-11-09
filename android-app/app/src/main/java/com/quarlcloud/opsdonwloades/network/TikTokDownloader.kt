package com.quarlcloud.opsdonwloades.network

import android.content.Context
import android.os.Environment
import android.media.MediaScannerConnection
import android.util.Log
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.*
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.regex.Pattern

data class VideoInfo(
    val title: String,
    val author: String,
    val duration: String,
    val downloadUrl: String,
    val thumbnailUrl: String?,
    val videoId: String?
)

data class DownloadResult(
    val success: Boolean,
    val filePath: String? = null,
    val error: String? = null
)

class TikTokDownloader(private val context: Context) {
    
    companion object {
        private const val TAG = "TikTokDownloader"
        private const val CONNECT_TIMEOUT_SECONDS = 60L
        private const val READ_TIMEOUT_SECONDS = 300L
        private const val WRITE_TIMEOUT_SECONDS = 300L
        private const val MAX_RETRIES = 3
    }

    // CookieJar simple en memoria para mantener sesiones entre peticiones
    private val inMemoryCookieJar = object : CookieJar {
        private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val key = url.host
            val existing = cookieStore.getOrPut(key) { mutableListOf() }
            // Reemplazar cookies con mismo nombre
            cookies.forEach { newCookie ->
                existing.removeAll { it.name == newCookie.name }
                existing.add(newCookie)
            }
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val key = url.host
            val cookies = cookieStore[key] ?: mutableListOf()
            return cookies.filter { cookie ->
                // Filtrado básico por dominio y path
                val domainMatch = url.host.endsWith(cookie.domain.removePrefix("."))
                val pathMatch = url.encodedPath.startsWith(cookie.path)
                val secureOk = !cookie.secure || url.isHttps
                domainMatch && pathMatch && secureOk
            }
        }
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(inMemoryCookieJar)
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Descarga un video usando múltiples servicios de terceros como fallback
     */
    suspend fun downloadVideo(
        videoInfo: VideoInfo,
        preferHd: Boolean = true,
        forceNoWm: Boolean = true,
        onProgress: (Float) -> Unit = {}
    ): DownloadResult {
        Log.d(TAG, "=== INICIANDO DESCARGA CON SERVICIOS DE TERCEROS ===")
        Log.d(TAG, "Video: ${videoInfo.title}")
        Log.d(TAG, "URL original: ${videoInfo.downloadUrl}")
        
        return withContext(Dispatchers.IO) {
            val tikwm: suspend (String, (Float) -> Unit) -> DownloadResult = { url, prog ->
                tryTikWMDownload(url, prog, preferHd, forceNoWm)
            }
            val snaptik: suspend (String, (Float) -> Unit) -> DownloadResult = { url, prog ->
                trySnapTikDownload(url, prog, preferHd, forceNoWm)
            }
            val ssstik: suspend (String, (Float) -> Unit) -> DownloadResult = { url, prog ->
                trySSSTikDownload(url, prog, preferHd, forceNoWm)
            }
            val tikmate: suspend (String, (Float) -> Unit) -> DownloadResult = { url, prog ->
                tryTikMateDownload(url, prog, preferHd, forceNoWm)
            }
            val musicaldown: suspend (String, (Float) -> Unit) -> DownloadResult = { url, prog ->
                tryMusicalDownDownload(url, prog, preferHd, forceNoWm)
            }
            val tiktokio: suspend (String, (Float) -> Unit) -> DownloadResult = { url, prog ->
                tryTikTokIODownload(url, prog, preferHd, forceNoWm)
            }

            val services: List<Pair<String, suspend (String, (Float) -> Unit) -> DownloadResult>> = listOf(
                "TikWM" to tikwm,
                "SnapTik" to snaptik,
                "SSSTik" to ssstik,
                "TikMate" to tikmate,
                "MusicalDown" to musicaldown,
                "TikTok.io" to tiktokio
            )
            
            for ((serviceName, serviceFunction) in services) {
                try {
                    Log.d(TAG, "Intentando descarga con $serviceName...")
                    onProgress(0.1f)
                    
                    val result = serviceFunction(videoInfo.downloadUrl, onProgress)
                    if (result.success) {
                        Log.d(TAG, "¡Descarga exitosa con $serviceName!")
                        return@withContext result
                    } else {
                        Log.w(TAG, "$serviceName falló: ${result.error}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error con $serviceName", e)
                }
                
                // Pequeña pausa entre intentos
                delay(1000)
            }
            
            Log.e(TAG, "Todos los servicios fallaron")
            return@withContext DownloadResult(
                success = false,
                filePath = null,
                error = "Todos los servicios de descarga fallaron"
            )
        }
    }

    /**
     * Intenta descargar usando SnapTik
     */
    private suspend fun trySnapTikDownload(
        tiktokUrl: String,
        onProgress: (Float) -> Unit,
        preferHd: Boolean,
        forceNoWm: Boolean
    ): DownloadResult {
        return try {
            Log.d(TAG, "Usando SnapTik para: $tiktokUrl")
            
            val encodedUrl = URLEncoder.encode(tiktokUrl, "UTF-8")
            val snapTikUrl = "https://snaptik.app/abc?url=$encodedUrl"
            
            val request = Request.Builder()
                .url(snapTikUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .addHeader("Accept-Language", "en-US,en;q=0.5")
                .addHeader("Referer", "https://snaptik.app/")
                .build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return DownloadResult(false, null, "SnapTik HTTP error: ${response.code}")
            }
            
            val html = response.body?.string() ?: ""
            val downloadUrl = extractSnapTikDownloadUrl(html, preferHd, forceNoWm)
            
            if (downloadUrl != null) {
                return downloadFileFromUrl(downloadUrl, "tiktok_snaptik_${System.currentTimeMillis()}.mp4", onProgress)
            } else {
                return DownloadResult(false, null, "No se encontró URL de descarga en SnapTik")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error en SnapTik", e)
            DownloadResult(false, null, "Error SnapTik: ${e.message}")
        }
    }

    /**
     * Intenta descargar usando SSSTik
     */
    private suspend fun trySSSTikDownload(
        tiktokUrl: String,
        onProgress: (Float) -> Unit,
        preferHd: Boolean,
        forceNoWm: Boolean
    ): DownloadResult {
        return try {
            Log.d(TAG, "Usando SSSTik para: $tiktokUrl")

            // Paso 1: obtener token dinámico y cookies de la página principal
            val bootstrapReq = Request.Builder()
                .url("https://ssstik.io/")
                .get()
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .addHeader("Referer", "https://ssstik.io/")
                .build()

            val bootstrapRes = client.newCall(bootstrapReq).execute()
            if (!bootstrapRes.isSuccessful) {
                return DownloadResult(false, null, "SSSTik bootstrap error: ${bootstrapRes.code}")
            }
            val bootstrapHtml = bootstrapRes.body?.string() ?: ""

            // Token suele estar en input hidden name="tt" o en scripts
            val tokenRegexes = listOf(
                "name=\\\"tt\\\" value=\\\"([^\\\"]+)\\\"",
                "tt:'([A-Za-z0-9]+)'",
                "data-tt=\\\"([^\\\"]+)\\\""
            )
            var token: String? = null
            for (pattern in tokenRegexes) {
                val m = java.util.regex.Pattern.compile(pattern).matcher(bootstrapHtml)
                if (m.find()) { token = m.group(1); break }
            }
            if (token == null) {
                Log.w(TAG, "No se pudo extraer token de SSSTik; probando sin token")
            }

            // Paso 2: hacer POST al endpoint con id, locale y token
            val formBody = FormBody.Builder()
                .add("id", tiktokUrl)
                .add("locale", "en")
                .apply { if (token != null) add("tt", token!!) }
                .build()

            val request = Request.Builder()
                .url("https://ssstik.io/abc?url=dl")
                .post(formBody)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .addHeader("Accept", "*/*")
                .addHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .addHeader("Origin", "https://ssstik.io")
                .addHeader("Referer", "https://ssstik.io/")
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return DownloadResult(false, null, "SSSTik HTTP error: ${response.code}")
            }

            val html = response.body?.string() ?: ""
            val downloadUrl = extractSSSTikDownloadUrl(html, preferHd, forceNoWm)

            if (downloadUrl != null) {
                return downloadFileFromUrl(downloadUrl, "tiktok_ssstik_${System.currentTimeMillis()}.mp4", onProgress)
            } else {
                return DownloadResult(false, null, "No se encontró URL de descarga en SSSTik")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error en SSSTik", e)
            DownloadResult(false, null, "Error SSSTik: ${e.message}")
        }
    }

    /**
     * Intenta descargar usando TikMate
     */
    private suspend fun tryTikMateDownload(
        tiktokUrl: String,
        onProgress: (Float) -> Unit,
        preferHd: Boolean,
        forceNoWm: Boolean
    ): DownloadResult {
        return try {
            Log.d(TAG, "Usando TikMate para: $tiktokUrl")
            
            val formBody = FormBody.Builder()
                .add("url", tiktokUrl)
                .build()
            
            val request = Request.Builder()
                .url("https://tikmate.online/download")
                .post(formBody)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("Origin", "https://tikmate.online")
                .addHeader("Referer", "https://tikmate.online/")
                .build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return DownloadResult(false, null, "TikMate HTTP error: ${response.code}")
            }
            
            val html = response.body?.string() ?: ""
            val downloadUrl = extractTikMateDownloadUrl(html, preferHd, forceNoWm)
            
            if (downloadUrl != null) {
                return downloadFileFromUrl(downloadUrl, "tiktok_tikmate_${System.currentTimeMillis()}.mp4", onProgress)
            } else {
                return DownloadResult(false, null, "No se encontró URL de descarga en TikMate")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error en TikMate", e)
            DownloadResult(false, null, "Error TikMate: ${e.message}")
        }
    }

    /**
     * Intenta descargar usando MusicalDown
     */
    private suspend fun tryMusicalDownDownload(
        tiktokUrl: String,
        onProgress: (Float) -> Unit,
        preferHd: Boolean,
        forceNoWm: Boolean
    ): DownloadResult {
        return try {
            Log.d(TAG, "Usando MusicalDown para: $tiktokUrl")
            
            val formBody = FormBody.Builder()
                .add("link", tiktokUrl)
                .build()
            
            val request = Request.Builder()
                .url("https://musicaldown.com/download")
                .post(formBody)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("Origin", "https://musicaldown.com")
                .addHeader("Referer", "https://musicaldown.com/")
                .build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return DownloadResult(false, null, "MusicalDown HTTP error: ${response.code}")
            }
            
            val html = response.body?.string() ?: ""
            val downloadUrl = extractMusicalDownDownloadUrl(html, preferHd, forceNoWm)
            
            if (downloadUrl != null) {
                return downloadFileFromUrl(downloadUrl, "tiktok_musicaldown_${System.currentTimeMillis()}.mp4", onProgress)
            } else {
                return DownloadResult(false, null, "No se encontró URL de descarga en MusicalDown")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error en MusicalDown", e)
            DownloadResult(false, null, "Error MusicalDown: ${e.message}")
        }
    }

    /**
     * Intenta descargar usando TikTok.io
     */
    private suspend fun tryTikTokIODownload(
        tiktokUrl: String,
        onProgress: (Float) -> Unit,
        preferHd: Boolean,
        forceNoWm: Boolean
    ): DownloadResult {
        return try {
            Log.d(TAG, "Usando TikTok.io para: $tiktokUrl")
            
            val formBody = FormBody.Builder()
                .add("query", tiktokUrl)
                .add("lang", "en")
                .build()
            
            val request = Request.Builder()
                .url("https://tiktok.io/api/v1/tk-htmx")
                .post(formBody)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .addHeader("Accept", "*/*")
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("Origin", "https://tiktok.io")
                .addHeader("Referer", "https://tiktok.io/")
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .addHeader("HX-Request", "true")
                .build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return DownloadResult(false, null, "TikTok.io HTTP error: ${response.code}")
            }
            
            val html = response.body?.string() ?: ""
            val downloadUrl = extractTikTokIODownloadUrl(html, preferHd, forceNoWm)
            
            if (downloadUrl != null) {
                return downloadFileFromUrl(downloadUrl, "tiktok_tiktokio_${System.currentTimeMillis()}.mp4", onProgress)
            } else {
                return DownloadResult(false, null, "No se encontró URL de descarga en TikTok.io")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error en TikTok.io", e)
            DownloadResult(false, null, "Error TikTok.io: ${e.message}")
        }
    }

    /**
     * Intenta descargar usando TikWM (API JSON muy estable)
     */
    private suspend fun tryTikWMDownload(
        tiktokUrl: String,
        onProgress: (Float) -> Unit,
        preferHd: Boolean,
        forceNoWm: Boolean
    ): DownloadResult {
        return try {
            Log.d(TAG, "Usando TikWM para: $tiktokUrl")

            val formBody = FormBody.Builder()
                .add("url", tiktokUrl)
                .build()

            val request = Request.Builder()
                .url("https://www.tikwm.com/api/")
                .post(formBody)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .addHeader("Accept", "application/json, text/plain, */*")
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("Origin", "https://www.tikwm.com")
                .addHeader("Referer", "https://www.tikwm.com/")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return DownloadResult(false, null, "TikWM HTTP error: ${response.code}")
            }

            val bodyStr = response.body?.string() ?: ""

            // Intentar parseo robusto del JSON de TikWM
            val dataObj = try {
                val root = JSONObject(bodyStr)
                // TikWM suele envolver datos en { data: { ... } }
                root.optJSONObject("data") ?: root
            } catch (e: Exception) {
                null
            }

            val hdUrl = dataObj?.optString("hdplay")?.takeIf { it.isNotBlank() }
            val nowmUrl = dataObj?.optString("play")?.takeIf { it.isNotBlank() }
            val wmUrl = dataObj?.optString("wmplay")?.takeIf { it.isNotBlank() }

            if (preferHd && hdUrl != null) {
                return downloadFileFromUrl(hdUrl, "tiktok_tikwm_${System.currentTimeMillis()}.mp4", onProgress)
            }

            if (nowmUrl != null) {
                return downloadFileFromUrl(nowmUrl, "tiktok_tikwm_${System.currentTimeMillis()}.mp4", onProgress)
            }

            if (!forceNoWm && wmUrl != null) {
                return downloadFileFromUrl(wmUrl, "tiktok_tikwm_${System.currentTimeMillis()}.mp4", onProgress)
            }

            // Fallback a regex si el JSON no estaba presente o incompleto
            val hdMatcher = java.util.regex.Pattern.compile("\\\"hdplay\\\":\\s*\\\"([^\\\"]+)\\\"").matcher(bodyStr)
            val nwMatcher = java.util.regex.Pattern.compile("\\\"play\\\":\\s*\\\"([^\\\"]+)\\\"").matcher(bodyStr)
            val wmMatcher = java.util.regex.Pattern.compile("\\\"wmplay\\\":\\s*\\\"([^\\\"]+)\\\"").matcher(bodyStr)

            if (preferHd && hdMatcher.find()) {
                val u = hdMatcher.group(1)
                if (u != null) return downloadFileFromUrl(u, "tiktok_tikwm_${System.currentTimeMillis()}.mp4", onProgress)
            }
            if (nwMatcher.find()) {
                val u = nwMatcher.group(1)
                if (u != null) return downloadFileFromUrl(u, "tiktok_tikwm_${System.currentTimeMillis()}.mp4", onProgress)
            }
            if (!forceNoWm && wmMatcher.find()) {
                val u = wmMatcher.group(1)
                if (u != null) return downloadFileFromUrl(u, "tiktok_tikwm_${System.currentTimeMillis()}.mp4", onProgress)
            }

            if (forceNoWm) {
                return DownloadResult(false, null, "Sin marca de agua no disponible en TikWM")
            }

            return DownloadResult(false, null, "No se encontró URL de reproducción en TikWM")

        } catch (e: Exception) {
            Log.e(TAG, "Error en TikWM", e)
            DownloadResult(false, null, "Error TikWM: ${e.message}")
        }
    }

    // Métodos para extraer URLs de descarga de cada servicio
    private fun extractSnapTikDownloadUrl(html: String, preferHd: Boolean, forceNoWm: Boolean): String? {
        val patterns = listOf(
            "href=\"([^\"]*download[^\"]*\\.mp4[^\"]*)\"",
            "data-downloadurl=\"([^\"]*\\.mp4[^\"]*)\"",
            "\"download_url\":\"([^\"]*\\.mp4[^\"]*)\""
        )
        val candidates = mutableSetOf<String>()
        for (pattern in patterns) {
            val regex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            val matcher = regex.matcher(html)
            while (matcher.find()) {
                val url = matcher.group(1)?.replace("\\u002F", "/")
                if (url != null && url.startsWith("http")) candidates.add(url)
            }
        }
        return chooseBestQualityUrl(candidates.toList(), preferHd, forceNoWm)
    }

    private fun extractSSSTikDownloadUrl(html: String, preferHd: Boolean, forceNoWm: Boolean): String? {
        val patterns = listOf(
            "href=\"([^\"]*download[^\"]*\\.mp4[^\"]*)\"",
            "data-tiktok=\"([^\"]*\\.mp4[^\"]*)\"",
            "\"url\":\"([^\"]*\\.mp4[^\"]*)\""
        )
        val candidates = mutableSetOf<String>()
        for (pattern in patterns) {
            val regex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            val matcher = regex.matcher(html)
            while (matcher.find()) {
                val url = matcher.group(1)?.replace("\\u002F", "/")
                if (url != null && url.startsWith("http")) candidates.add(url)
            }
        }
        return chooseBestQualityUrl(candidates.toList(), preferHd, forceNoWm)
    }

    private fun extractTikMateDownloadUrl(html: String, preferHd: Boolean, forceNoWm: Boolean): String? {
        val patterns = listOf(
            "href=\"([^\"]*download[^\"]*\\.mp4[^\"]*)\"",
            "data-link=\"([^\"]*\\.mp4[^\"]*)\"",
            "\"video_url\":\"([^\"]*\\.mp4[^\"]*)\""
        )
        val candidates = mutableSetOf<String>()
        for (pattern in patterns) {
            val regex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            val matcher = regex.matcher(html)
            while (matcher.find()) {
                val url = matcher.group(1)?.replace("\\u002F", "/")
                if (url != null && url.startsWith("http")) candidates.add(url)
            }
        }
        return chooseBestQualityUrl(candidates.toList(), preferHd, forceNoWm)
    }

    private fun extractMusicalDownDownloadUrl(html: String, preferHd: Boolean, forceNoWm: Boolean): String? {
        val patterns = listOf(
            "href=\"([^\"]*download[^\"]*\\.mp4[^\"]*)\"",
            "data-link=\"([^\"]*\\.mp4[^\"]*)\"",
            "\"download_url\":\"([^\"]*\\.mp4[^\"]*)\""
        )
        val candidates = mutableSetOf<String>()
        for (pattern in patterns) {
            val regex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            val matcher = regex.matcher(html)
            while (matcher.find()) {
                val url = matcher.group(1)?.replace("\\u002F", "/")
                if (url != null && url.startsWith("http")) candidates.add(url)
            }
        }
        return chooseBestQualityUrl(candidates.toList(), preferHd, forceNoWm)
    }

    private fun extractTikTokIODownloadUrl(html: String, preferHd: Boolean, forceNoWm: Boolean): String? {
        val patterns = listOf(
            "href=\"([^\"]*download[^\"]*\\.mp4[^\"]*)\"",
            "data-video-url=\"([^\"]*\\.mp4[^\"]*)\"",
            "\"video\":\"([^\"]*\\.mp4[^\"]*)\""
        )
        val candidates = mutableSetOf<String>()
        for (pattern in patterns) {
            val regex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            val matcher = regex.matcher(html)
            while (matcher.find()) {
                val url = matcher.group(1)?.replace("\\u002F", "/")
                if (url != null && url.startsWith("http")) candidates.add(url)
            }
        }
        return chooseBestQualityUrl(candidates.toList(), preferHd, forceNoWm)
    }

    // Heurística para elegir la mejor calidad según preferencias
    private fun chooseBestQualityUrl(urls: List<String>, preferHd: Boolean, forceNoWm: Boolean): String? {
        if (urls.isEmpty()) return null
        val filtered = if (forceNoWm) urls.filterNot { hasWatermark(it) } else urls
        if (filtered.isEmpty()) return null
        return filtered.maxByOrNull { scoreQuality(it, preferHd) }
    }

    private fun hasWatermark(url: String): Boolean {
        val u = url.lowercase()
        // Señales típicas de marca de agua
        return u.contains("wm") && !u.contains("nowm")
    }

    private fun scoreQuality(url: String, preferHd: Boolean): Int {
        var score = 0
        val u = url.lowercase()
        // Preferir no watermark
        if (u.contains("nowm") || u.contains("no-watermark") || u.contains("nowatermark") || u.contains("no_wm") || u.contains("nw")) score += 50
        if (u.contains("wm") && !u.contains("nowm")) score -= 25
        // Preferir resoluciones altas si se solicita
        if (preferHd) {
            if (u.contains("1080") || u.contains("fhd") || u.contains("fullhd") || u.contains("hdplay")) score += 40
            if (u.contains("720") || u.contains("hd")) score += 20
        } else {
            // Cuando no se prefiere HD, valorar menos las pistas de alta resolución
            if (u.contains("1080") || u.contains("fhd") || u.contains("fullhd") || u.contains("hdplay")) score += 10
            if (u.contains("720") || u.contains("hd")) score += 5
        }
        // Penalizar enlaces que parecen previsualización o baja calidad
        if (u.contains("preview") || u.contains("low")) score -= 10
        return score
    }

    /**
     * Descarga un archivo desde una URL específica
     */
    private suspend fun downloadFileFromUrl(
        url: String, 
        fileName: String, 
        onProgress: (Float) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Descargando archivo desde: $url")
            
            // Crear directorio de descarga
            val downloadsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "OpSDonwloades")
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
                Log.d(TAG, "Directorio creado: ${downloadsDir.absolutePath}")
            }
            
            // Crear archivo de destino
            val outputFile = File(downloadsDir, fileName)
            Log.d(TAG, "Archivo destino: ${outputFile.absolutePath}")
            
            // Realizar descarga
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .addHeader("Accept", "*/*")
                .addHeader("Accept-Language", "en-US,en;q=0.9")
                .addHeader("Accept-Encoding", "identity")
                .addHeader("Connection", "keep-alive")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                throw IOException("HTTP error: ${response.code}")
            }
            
            val responseBody = response.body ?: throw IOException("Response body is null")
            val contentLength = responseBody.contentLength()
            
            Log.d(TAG, "Tamaño del archivo: $contentLength bytes")
            
            // Escribir archivo con seguimiento de progreso
            responseBody.byteStream().use { input ->
                outputFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var totalBytesRead = 0L
                    var bytesRead: Int
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        // Actualizar progreso (50% a 100%)
                        if (contentLength > 0) {
                            val progress = 0.5f + (totalBytesRead.toFloat() / contentLength * 0.5f)
                            onProgress(progress)
                            
                            if (totalBytesRead % (1024 * 1024) == 0L) { // Log cada MB
                                Log.d(TAG, "Progreso: ${(progress * 100).toInt()}% ($totalBytesRead/$contentLength bytes)")
                            }
                        }
                    }
                    
                    output.flush()
                }
            }
            
            if (outputFile.exists() && outputFile.length() > 0) {
                Log.d(TAG, "=== DESCARGA COMPLETADA ===")
                Log.d(TAG, "Archivo: ${outputFile.absolutePath}")
                Log.d(TAG, "Tamaño final: ${outputFile.length()} bytes")
                // Registrar en MediaStore para que aparezca en la galería y apps como WhatsApp
                try {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(outputFile.absolutePath),
                        arrayOf("video/mp4"),
                        null
                    )
                    Log.d(TAG, "MediaStore actualizado para: ${outputFile.absolutePath}")
                } catch (scanErr: Exception) {
                    Log.w(TAG, "No se pudo escanear el archivo en MediaStore", scanErr)
                }
                
                return@withContext DownloadResult(
                    success = true,
                    filePath = outputFile.absolutePath,
                    error = null
                )
            } else {
                throw IOException("El archivo descargado está vacío o no existe")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error descargando archivo desde URL", e)
            return@withContext DownloadResult(
                success = false,
                filePath = null,
                error = "Error descargando: ${e.message}"
            )
        }
    }

    /**
     * Extrae información básica del video desde HTML usando regex
     */
    fun extractBasicInfoWithRegex(html: String): VideoInfo? {
        return try {
            val titlePattern = Pattern.compile("\"desc\":\"([^\"]*)\"|<title>([^<]*)</title>", Pattern.CASE_INSENSITIVE)
            val authorPattern = Pattern.compile("\"author\":\"([^\"]*)\"|\"uniqueId\":\"([^\"]*)\"|@([a-zA-Z0-9_.]+)", Pattern.CASE_INSENSITIVE)
            
            val titleMatcher = titlePattern.matcher(html)
            val authorMatcher = authorPattern.matcher(html)
            
            val title = if (titleMatcher.find()) {
                titleMatcher.group(1) ?: titleMatcher.group(2) ?: "TikTok Video"
            } else "TikTok Video"
            
            val author = if (authorMatcher.find()) {
                authorMatcher.group(1) ?: authorMatcher.group(2) ?: authorMatcher.group(3) ?: "Unknown"
            } else "Unknown"
            
            VideoInfo(
                title = title,
                author = author,
                duration = "Unknown",
                downloadUrl = "",
                thumbnailUrl = null,
                videoId = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting basic info", e)
            null
        }
    }
}