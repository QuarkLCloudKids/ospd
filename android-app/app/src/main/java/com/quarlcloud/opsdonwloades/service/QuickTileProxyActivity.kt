package com.quarlcloud.opsdonwloades.service

import android.app.Activity
import android.app.NotificationChannel
import android.app.PendingIntent
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import android.content.ClipboardManager
import android.content.ClipData
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.quarlcloud.opsdonwloades.utils.TikTokDetector

/**
 * Actividad transparente para colapsar el panel de control y disparar la descarga.
 * No muestra UI y finaliza inmediatamente.
 */
class QuickTileProxyActivity : Activity() {

    // Usar un ID diferente al del TileService para evitar colisiones
    private val feedbackNotificationId = 1002
    private var clipboardManager: ClipboardManager? = null
    private var clipListener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private var overlayAttachedReceiver: BroadcastReceiver? = null
    private fun ensureFeedbackChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "tile_feedback"
            val channelName = "OpSD Tile Feedback"
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = "Notificaciones de confirmación del tile"
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun showFeedbackProcessing() {
        ensureFeedbackChannel()
        val openFallbackIntent = Intent(this, OverlayWidgetActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val openFallbackPending = PendingIntent.getActivity(
            this,
            1001,
            openFallbackIntent,
            ((if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0) or PendingIntent.FLAG_UPDATE_CURRENT)
        )

        val builder = NotificationCompat.Builder(this, "tile_feedback")
            .setSmallIcon(com.quarlcloud.opsdonwloades.R.drawable.ic_download)
            .setContentTitle("OpSDonwloades")
            .setContentText("Tile tocado: procesando…")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setTimeoutAfter(3500)
            .setContentIntent(openFallbackPending)
            .addAction(com.quarlcloud.opsdonwloades.R.drawable.ic_download, "Abrir widget", openFallbackPending)
        NotificationManagerCompat.from(this).notify(feedbackNotificationId, builder.build())
    }

    private fun updateFeedbackNotification(message: String) {
        ensureFeedbackChannel()
        val openFallbackIntent = Intent(this, OverlayWidgetActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val openFallbackPending = PendingIntent.getActivity(
            this,
            1002,
            openFallbackIntent,
            ((if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0) or PendingIntent.FLAG_UPDATE_CURRENT)
        )

        val builder = NotificationCompat.Builder(this, "tile_feedback")
            .setSmallIcon(com.quarlcloud.opsdonwloades.R.drawable.ic_download)
            .setContentTitle("OpSDonwloades")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setTimeoutAfter(5000)
            .setContentIntent(openFallbackPending)
            .addAction(com.quarlcloud.opsdonwloades.R.drawable.ic_download, "Abrir widget", openFallbackPending)
        NotificationManagerCompat.from(this).notify(feedbackNotificationId, builder.build())
    }

    private fun showPersistentFeedback(message: String, durationMs: Long = 8000) {
        ensureFeedbackChannel()
        val builder = NotificationCompat.Builder(this, "tile_feedback")
            .setSmallIcon(com.quarlcloud.opsdonwloades.R.drawable.ic_download)
            .setContentTitle("OpSDonwloades")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)

        NotificationManagerCompat.from(this).notify(feedbackNotificationId, builder.build())
        // Cancelar la notificación persistente tras el tiempo indicado
        Handler(Looper.getMainLooper()).postDelayed({
            NotificationManagerCompat.from(this).cancel(feedbackNotificationId)
        }, durationMs)
    }

    private fun isAccessibilityEnabledForAutoCopy(): Boolean {
        return try {
            val enabled = Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
            if (!enabled) return false
            val settingValue = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val expected = "$packageName/${TikTokAccessibilityService::class.java.name}"
            settingValue.split(":").any { it.trim().equals(expected, ignoreCase = true) }
        } catch (_: Exception) {
            false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Señal rápida para que el TileService sepa que la actividad se inició
        try {
            val started = Intent("com.quarlcloud.opsdonwloades.TILE_PROXY_STARTED").apply {
                setPackage(packageName)
            }
            sendBroadcast(started)
        } catch (_: Exception) {}
        // Asegurar colapso del panel de control en todos los fabricantes
        try {
            sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
            Handler(Looper.getMainLooper()).postDelayed({
                try { sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)) } catch (_: Exception) {}
            }, 150)
            Handler(Looper.getMainLooper()).postDelayed({
                try { sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)) } catch (_: Exception) {}
            }, 300)
        } catch (_: Exception) {}
        // Si la intención es sólo lanzar el overlay, hazlo y termina
        val launchOverlay = intent?.getBooleanExtra("launchOverlay", false) == true
        if (launchOverlay) {
            showFeedbackProcessing()
            // Esperar confirmación de overlay adjuntado o abrir fallback si no llega
            try {
                overlayAttachedReceiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        if (intent?.action == "com.quarlcloud.opsdonwloades.OVERLAY_ATTACHED") {
                            // Overlay visible, cerrar la proxy
                            try { unregisterReceiver(this) } catch (_: Exception) {}
                            overlayAttachedReceiver = null
                            Handler(Looper.getMainLooper()).postDelayed({ finish() }, 100)
                        }
                    }
                }
                // En Android 13+, especificar flag al registrar receivers
                val filter = IntentFilter("com.quarlcloud.opsdonwloades.OVERLAY_ATTACHED")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(overlayAttachedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    @Suppress("DEPRECATION")
                    registerReceiver(overlayAttachedReceiver, filter)
                }
            } catch (_: Exception) {}

            // Iniciar overlay service
            try {
                val intent = Intent(this, OverlayWidgetService::class.java)
                startForegroundService(intent)
                updateFeedbackNotification("Widget abierto: pega el enlace")
            } catch (e: Exception) {
                updateFeedbackNotification("Error abriendo widget")
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }

            // En dispositivos HONOR/HUAWEI (MagicOS/EMUI), abrir actividad de fallback inmediatamente
            try {
                val manufacturer = (Build.MANUFACTURER ?: "").lowercase()
                if (manufacturer.contains("honor") || manufacturer.contains("huawei")) {
                    val fallback = Intent(this, OverlayWidgetActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    startActivity(fallback)
                }
            } catch (_: Exception) {}

            // Fallback: si no hay confirmación en 800ms, abrir actividad transparente
            Handler(Looper.getMainLooper()).postDelayed({
                if (overlayAttachedReceiver != null) {
                    try { unregisterReceiver(overlayAttachedReceiver) } catch (_: Exception) {}
                    overlayAttachedReceiver = null
                    try {
                        val fallback = Intent(this, OverlayWidgetActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        startActivity(fallback)
                    } catch (_: Exception) {}
                    // Cerrar la proxy tras lanzar el fallback
                    Handler(Looper.getMainLooper()).postDelayed({ finish() }, 100)
                }
            }, 800)
            return
        }
        // Si se indica saltar extracción, sólo colapsar y salir
        val skip = intent?.getBooleanExtra("skipExtraction", false) == true
        showFeedbackProcessing()
        if (skip) {
            // Modo simplificado: usar sólo el portapapeles, sin retraso
            performClipboardOnlyDownload()
            return
        }
        // Ejecutar extracción inmediata en primer plano
        performExtractionAndDownload()
    }

    private fun performClipboardOnlyDownload() {
        val detector = TikTokDetector(this)
        val first = try { detector.extractFromClipboard() } catch (_: Exception) { null }
        val firstUrl = first?.let { detector.cleanTikTokUrl(it) }

        if (firstUrl != null) {
            val downloadIntent = Intent(this, DownloadService::class.java).apply {
                putExtra("video_url", firstUrl)
                putExtra("source", "quick_tile_clipboard")
            }
            try {
                startForegroundService(downloadIntent)
                updateFeedbackNotification("Enlace detectado: descarga iniciada")
                Toast.makeText(this, "Enlace copiado, descargando…", Toast.LENGTH_SHORT).show()
                Handler(Looper.getMainLooper()).postDelayed({ finish() }, 500)
            } catch (e: Exception) {
                updateFeedbackNotification("Error iniciando descarga")
                Toast.makeText(this, "Error iniciando descarga: ${e.message}", Toast.LENGTH_LONG).show()
                Handler(Looper.getMainLooper()).postDelayed({ finish() }, 800)
            }
            return
        }

        // Segundo intento breve tras colapsar el panel (algunos OEM tardan en exponer el portapapeles)
        Handler(Looper.getMainLooper()).postDelayed({
            val delayed = try { detector.extractFromClipboard() } catch (_: Exception) { null }
            val delayedUrl = delayed?.let { detector.cleanTikTokUrl(it) }
            if (delayedUrl != null) {
                val downloadIntent = Intent(this, DownloadService::class.java).apply {
                    putExtra("video_url", delayedUrl)
                    putExtra("source", "quick_tile_clipboard")
                }
                try {
                    startForegroundService(downloadIntent)
                    updateFeedbackNotification("Enlace detectado: descarga iniciada")
                    Toast.makeText(this, "Enlace copiado, descargando…", Toast.LENGTH_SHORT).show()
                    Handler(Looper.getMainLooper()).postDelayed({ finish() }, 600)
                } catch (e: Exception) {
                    updateFeedbackNotification("Error iniciando descarga")
                    Toast.makeText(this, "Error iniciando descarga: ${e.message}", Toast.LENGTH_LONG).show()
                    Handler(Looper.getMainLooper()).postDelayed({ finish() }, 900)
                }
            } else {
                val msg = "Esperando enlace copiado…"
                showPersistentFeedback(msg, 12000)
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                startClipboardWatch(timeoutMs = 15000)
            }
        }, 250)
    }

    private fun performExtractionAndDownload() {
        val detector = TikTokDetector(this)
        val url = detector.extractCurrentTikTokUrl()

        if (url != null) {
            // Iniciar servicio de descarga directamente
            val downloadIntent = Intent(this, DownloadService::class.java).apply {
                putExtra("video_url", url)
                putExtra("source", "quick_tile")
            }
            try {
                startForegroundService(downloadIntent)
                updateFeedbackNotification("Descarga iniciada desde el tile")
                Toast.makeText(this, "Descarga iniciada", Toast.LENGTH_SHORT).show()
                // Finalizar tras un pequeño margen para asegurar entrega de notificaciones/toasts
                Handler(Looper.getMainLooper()).postDelayed({ finish() }, 500)
            } catch (e: Exception) {
                updateFeedbackNotification("Error iniciando descarga")
                Toast.makeText(this, "Error iniciando descarga: ${e.message}", Toast.LENGTH_LONG).show()
                Handler(Looper.getMainLooper()).postDelayed({ finish() }, 800)
            }
        } else {
            // No hay URL inmediato; dejamos feedback y escuchamos portapapeles mientras el servicio
            // de accesibilidad intenta copiar enlace automáticamente.
            val autoCopyEnabled = isAccessibilityEnabledForAutoCopy()
            val msg = if (autoCopyEnabled) {
                "Procesando… Intentando obtener el enlace automáticamente"
            } else {
                "Activa Accesibilidad para copiar enlace automático (Ajustes → Accesibilidad)"
            }
            showPersistentFeedback(msg, 8000)
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

            // Activar solicitud de autocopia si el servicio está habilitado
            if (autoCopyEnabled) {
                try { com.quarlcloud.opsdonwloades.utils.TikTokAutomation.requestAutoCopy(this) } catch (_: Exception) {}
            }
            startClipboardWatch(timeoutMs = 20000)
        }
    }

    private fun startClipboardWatch(timeoutMs: Long) {
        clipboardManager = getSystemService(ClipboardManager::class.java)
        val detector = TikTokDetector(this)
        clipListener = ClipboardManager.OnPrimaryClipChangedListener {
            try {
                val url = detector.extractFromClipboard()?.let { detector.cleanTikTokUrl(it) }
                if (url != null) {
                    val downloadIntent = Intent(this, DownloadService::class.java).apply {
                        putExtra("video_url", url)
                        putExtra("source", "quick_tile_clipboard")
                    }
                    startForegroundService(downloadIntent)
                    updateFeedbackNotification("Enlace detectado: descarga iniciada")
                    Toast.makeText(this, "Enlace copiado, descargando…", Toast.LENGTH_SHORT).show()
                    stopClipboardWatch()
                    Handler(Looper.getMainLooper()).postDelayed({ finish() }, 600)
                }
            } catch (e: Exception) {
                updateFeedbackNotification("Error leyendo portapapeles")
                Toast.makeText(this, "Error leyendo portapapeles", Toast.LENGTH_SHORT).show()
            }
        }
        clipboardManager?.addPrimaryClipChangedListener(clipListener!!)
        // Timeout para finalizar escucha
        Handler(Looper.getMainLooper()).postDelayed({
            if (clipListener != null) {
                updateFeedbackNotification("Tiempo agotado. Copia el enlace y vuelve a intentar")
                stopClipboardWatch()
                finish()
            }
        }, timeoutMs)
    }

    private fun stopClipboardWatch() {
        try {
            clipListener?.let { clipboardManager?.removePrimaryClipChangedListener(it) }
        } catch (_: Exception) {}
        clipListener = null
        clipboardManager = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopClipboardWatch()
        try { overlayAttachedReceiver?.let { unregisterReceiver(it) } } catch (_: Exception) {}
        overlayAttachedReceiver = null
    }
}