package com.quarlcloud.opsdonwloades.service

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.quarlcloud.opsdonwloades.utils.TikTokDetector
import com.quarlcloud.opsdonwloades.utils.TikTokTextExtractor
import com.quarlcloud.opsdonwloades.utils.GenericUrlExtractor

/**
 * Actividad receptora de compartir (ACTION_SEND) para iniciar descargas automáticamente
 * desde el menú Compartir de TikTok sin necesidad de copiar el enlace.
 */
class ShareReceiverActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = intent?.action
        val type = intent?.type
        var sharedText: String? = null

        if (Intent.ACTION_SEND == action) {
            if (type != null && type.startsWith("text/")) {
                sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            }
        }

        val detector = TikTokDetector(this)
        val extracted = sharedText?.let { TikTokTextExtractor.extract(it) ?: GenericUrlExtractor.extract(it) }
        val finalUrl = extracted ?: detector.extractFromClipboard()

        if (finalUrl != null) {
            val downloadIntent = Intent(this, DownloadService::class.java).apply {
                putExtra("video_url", finalUrl)
                putExtra("source", "share_intent")
            }
            try {
                startForegroundService(downloadIntent)
                Toast.makeText(this, "Descarga iniciada desde Compartir", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "No se detectó enlace válido (TikTok/Instagram/YouTube/X/Facebook)", Toast.LENGTH_LONG).show()
        }

        finish()
    }
}