package com.quarlcloud.opsdonwloades.utils

import android.util.Log

/**
 * Extrae de un texto compartido el primer enlace válido de TikTok.
 * Soporta dominios: www/m/vm/vt.tiktok.com y corta en espacio.
 */
object TikTokTextExtractor {
    private const val TAG = "TikTokTextExtractor"

    private val regex = Regex(
        pattern = """https?://(?:[\w-]+\.)?tiktok\.com/[^\s]+""",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    // Fallback: a veces el texto no incluye el esquema (http/https)
    private val regexNoScheme = Regex(
        pattern = """(?:[\w-]+\.)?tiktok\.com/[^\s]+""",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    fun extract(text: String): String? {
        return try {
            val match = regex.find(text)
            var url = match?.value?.trim()
            if (url == null) {
                val noSchemeMatch = regexNoScheme.find(text)
                val candidate = noSchemeMatch?.value?.trim()
                if (candidate != null) {
                    url = "https://$candidate"
                }
            }
            // HTML: href="...tiktok.com/..." o href='...'
            if (url == null) {
                val hrefDouble = Regex("""href\s*=\s*\"([^\"]*tiktok\.com/[^\"]+)\""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1)
                val hrefSingle = Regex("""href\s*=\s*'([^']*tiktok\.com/[^']+)'""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1)
                url = hrefDouble?.trim() ?: hrefSingle?.trim()
            }
            // URL-encoded dentro de texto (https%3A%2F%2Fvm.tiktok.com%2F...)
            if (url == null && text.contains("tiktok.com", ignoreCase = true) && text.contains("%2F")) {
                try {
                    val decoded = java.net.URLDecoder.decode(text, "UTF-8")
                    val m2 = regex.find(decoded)
                    url = m2?.value?.trim()
                    if (url == null) {
                        val ns2 = regexNoScheme.find(decoded)?.value?.trim()
                        if (ns2 != null) url = "https://$ns2"
                    }
                } catch (_: Exception) {}
            }
            // Fallback adicional: buscar tokens que contengan 'tiktok.com' y limpiar signos
            if (url == null) {
                val tokens = text.split(Regex("""\s+"""))
                for (t in tokens) {
                    if (t.contains("tiktok.com", ignoreCase = true)) {
                        var cand = t.trim().trim('"', '\'', '“', '”', '‘', '’', '(', ')', ',', '.', '…')
                        if (!cand.startsWith("http")) {
                            cand = "https://" + cand.removePrefix("//")
                        }
                        // Validar patrón básico
                        if (cand.contains("tiktok.com/")) {
                            url = cand
                            break
                        }
                    }
                }
            }
            if (url != null) {
                Log.d(TAG, "Extracted TikTok URL: $url")
            }
            url
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract TikTok URL from text", e)
            null
        }
    }
}