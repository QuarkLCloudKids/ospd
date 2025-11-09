package com.quarlcloud.opsdonwloades.service

import android.app.Activity
import android.content.*
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import com.quarlcloud.opsdonwloades.R
import com.quarlcloud.opsdonwloades.utils.TikTokDetector
import com.quarlcloud.opsdonwloades.utils.TikTokTextExtractor
import com.quarlcloud.opsdonwloades.utils.GenericUrlExtractor

/**
 * Actividad transparente como fallback para el widget overlay
 */
class OverlayWidgetActivity : Activity() {
    private var completionReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Forzar visibilidad por encima de paneles del sistema en HONOR/MagicOS
        try {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        } catch (_: Exception) {}
        try { sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)) } catch (_: Exception) {}
        setContentView(R.layout.overlay_widget)
        try { android.widget.Toast.makeText(this, "Abriendo widget…", android.widget.Toast.LENGTH_SHORT).show() } catch (_: Exception) {}

        bindUi()

        completionReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.quarlcloud.opsdonwloades.DOWNLOAD_FINISHED") {
                    finish()
                }
            }
        }
        registerReceiver(completionReceiver, IntentFilter("com.quarlcloud.opsdonwloades.DOWNLOAD_FINISHED"))
    }

    private fun bindUi() {
        val input = findViewById<EditText>(R.id.link_input)
        val paste = findViewById<Button>(R.id.paste_button)
        val download = findViewById<Button>(R.id.download_button)
        val close = findViewById<ImageButton>(R.id.close_button)

        paste.setOnClickListener {
            try {
                val detector = TikTokDetector(this)
                val clip = detector.extractFromClipboard()
                if (clip != null) {
                    input.setText(clip)
                    input.setSelection(input.text.length)
                } else {
                    Toast.makeText(this, "No se detectó enlace en el portapapeles", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error leyendo portapapeles", Toast.LENGTH_SHORT).show()
            }
        }

        download.setOnClickListener {
            val text = input.text?.toString()?.trim() ?: ""
            var url = TikTokTextExtractor.extract(text) ?: GenericUrlExtractor.extract(text)
            if (url == null) {
                try { url = TikTokDetector(this).extractFromClipboard() } catch (_: Exception) {}
            }
            if (url != null) {
                try {
                    val intent = Intent(this, DownloadService::class.java).apply {
                        putExtra("video_url", url)
                        putExtra("source", "overlay_activity")
                    }
                    startForegroundService(intent)
                    Toast.makeText(this, "Descarga iniciada", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Error iniciando descarga: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "Pega un enlace válido de video (TikTok/Instagram/YouTube/X/Facebook)", Toast.LENGTH_SHORT).show()
            }
        }

        close.setOnClickListener { finish() }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { completionReceiver?.let { unregisterReceiver(it) } } catch (_: Exception) {}
    }
}