package com.quarlcloud.opsdonwloades.utils

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

class TikTokDetector(private val context: Context) {
    private val TAG = "TikTokDetector"

    fun isTikTokActive(): Boolean {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
            val processes = am?.runningAppProcesses ?: return false
            processes.any { proc ->
                proc.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                        proc.processName.contains("tiktok", ignoreCase = true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "isTikTokActive check failed", e)
            false
        }
    }

    fun extractCurrentTikTokUrl(): String? {
        val fromClipboard = extractFromClipboard()
        val cleaned = fromClipboard?.let { cleanTikTokUrl(it) }
        return try {
            cleaned?.let { TikTokUrlResolver.resolve(it) } ?: cleaned
        } catch (e: Exception) {
            Log.w(TAG, "URL resolve failed, using cleaned", e)
            cleaned
        }
    }

    fun extractFromClipboard(): String? {
        return try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
            val clip: ClipData? = cm?.primaryClip
            val itemCount = clip?.itemCount ?: 0
            for (i in 0 until itemCount) {
                val item = clip!!.getItemAt(i)
                // 1) Texto directo (coerceToText maneja text/html/uri)
                val text = item.coerceToText(context)?.toString()
                if (!text.isNullOrBlank()) {
                    TikTokTextExtractor.extract(text)?.let { return it }
                }
                // 2) Texto plano explícito
                val plain = item.text?.toString()
                if (!plain.isNullOrBlank()) {
                    TikTokTextExtractor.extract(plain)?.let { return it }
                }
                // 2b) HTML enriquecido
                val html = item.htmlText
                if (!html.isNullOrBlank()) {
                    TikTokTextExtractor.extract(html)?.let { return it }
                }
                // 3) URI copiado como enlace
                val uriStr = item.uri?.toString()
                if (!uriStr.isNullOrBlank()) {
                    TikTokTextExtractor.extract(uriStr)?.let { return it }
                }
                // 4) Intent con EXTRA_TEXT
                val it = item.intent
                val extraText = it?.getStringExtra(Intent.EXTRA_TEXT)
                if (!extraText.isNullOrBlank()) {
                    TikTokTextExtractor.extract(extraText)?.let { return it }
                }
                val dataString = it?.dataString
                if (!dataString.isNullOrBlank()) {
                    TikTokTextExtractor.extract(dataString)?.let { return it }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Clipboard extraction failed", e)
            null
        }
    }

    fun isTikTokUrl(url: String): Boolean {
        return try {
            val host = Uri.parse(url.trim()).host?.lowercase() ?: return false
            host == "tiktok.com" || host.endsWith(".tiktok.com")
        } catch (_: Exception) {
            false
        }
    }

    fun cleanTikTokUrl(url: String): String? {
        val trimmed = url.trim()
        if (!isTikTokUrl(trimmed)) return null
        val normalized = if (trimmed.startsWith("http://")) trimmed.replaceFirst("http://", "https://") else trimmed
        return try {
            val uri = Uri.parse(normalized)
            // Mantener esquema y path, descartar query/fragment para evitar ruido
            Uri.Builder()
                .scheme(uri.scheme ?: "https")
                .authority(uri.authority ?: return normalized)
                .path(uri.path)
                .build()
                .toString()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clean URL, returning original", e)
            normalized
        }
    }
}