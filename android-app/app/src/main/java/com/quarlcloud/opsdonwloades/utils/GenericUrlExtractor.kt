package com.quarlcloud.opsdonwloades.utils

import android.util.Log

/**
 * Extrae el primer enlace http(s) del texto, sin limitar a TikTok.
 */
object GenericUrlExtractor {
    private const val TAG = "GenericUrlExtractor"

    private val anyUrlRegex = Regex(
        pattern = """https?://[^\s'"<>]+""",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    fun extract(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return try {
            val m = anyUrlRegex.find(text)
            val raw = m?.value?.trim()?.trim('"', '\'', '“', '”', '‘', '’', '(', ')', ',', '.', '…')
            raw?.also { Log.d(TAG, "URL genérica extraída: $it") }
        } catch (e: Exception) {
            Log.w(TAG, "Fallo extrayendo URL genérica", e)
            null
        }
    }
}