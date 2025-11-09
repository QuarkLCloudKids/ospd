package com.quarlcloud.opsdonwloades.service

import android.app.Service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.*
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.quarlcloud.opsdonwloades.R
import com.quarlcloud.opsdonwloades.utils.TikTokDetector
import com.quarlcloud.opsdonwloades.utils.TikTokTextExtractor
import com.quarlcloud.opsdonwloades.utils.GenericUrlExtractor

/**
 * Servicio que muestra un widget flotante minimalista para pegar/enviar el enlace
 * y desaparecer automáticamente al terminar la descarga.
 */
class OverlayWidgetService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var params: WindowManager.LayoutParams? = null

    private var completionReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        // Convertir en servicio en primer plano para evitar restricciones de Android 8+
        createChannel()
        startForeground(2002, createNotification("Widget overlay activo"))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Autoriza dibujar sobre apps para abrir el widget", Toast.LENGTH_LONG).show()
            try {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (_: Exception) {}
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        attachOverlay()

        // Escuchar finalización de descarga para cerrar el widget
        completionReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.quarlcloud.opsdonwloades.DOWNLOAD_FINISHED") {
                    detachOverlay()
                    stopSelf()
                }
            }
        }
        // Android 13+ requiere especificar RECEIVER_* al registrar receivers no exclusivos
        try {
            val filter = IntentFilter("com.quarlcloud.opsdonwloades.DOWNLOAD_FINISHED")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(completionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(completionReceiver, filter)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error registrando receiver: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("overlay_widget", "Widget Overlay", NotificationManager.IMPORTANCE_LOW)
            channel.setShowBadge(false)
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        val openIntent = Intent(this, OverlayWidgetActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val openPending = PendingIntent.getActivity(
            this,
            2003,
            openIntent,
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0) or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, "overlay_widget")
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("OpSDonwloades")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openPending)
            .addAction(R.drawable.ic_download, "Abrir widget", openPending)
            .build()
    }

    private fun attachOverlay() {
        if (overlayView != null) return

        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_widget, null)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dpToPx(64)
            x = 0
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        }

        try {
            windowManager?.addView(overlayView, params)
            // Señal: overlay adjuntado correctamente
            try {
                val attached = Intent("com.quarlcloud.opsdonwloades.OVERLAY_ATTACHED").apply { setPackage(packageName) }
                sendBroadcast(attached)
            } catch (_: Exception) {}
        } catch (e: Exception) {
            // Fallback: si no podemos adjuntar overlay (OEM bloquea overlays), abre actividad
            Toast.makeText(this, "No se pudo mostrar el widget overlay, abriendo ventana flotante", Toast.LENGTH_LONG).show()
            try {
                val fallback = Intent(this, OverlayWidgetActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(fallback)
            } catch (_: Exception) {}
            stopSelf()
            return
        }

        bindUi()
    }

    private fun bindUi() {
        val input = overlayView!!.findViewById<EditText>(R.id.link_input)
        val paste = overlayView!!.findViewById<Button>(R.id.paste_button)
        val download = overlayView!!.findViewById<Button>(R.id.download_button)
        val close = overlayView!!.findViewById<ImageButton>(R.id.close_button)

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
                // Intentar desde portapapeles si el campo está vacío o no válido
                try { url = TikTokDetector(this).extractFromClipboard() } catch (_: Exception) {}
            }
            if (url != null) {
                try {
                    val intent = Intent(this, DownloadService::class.java).apply {
                        putExtra("video_url", url)
                        putExtra("source", "overlay_widget")
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

        close.setOnClickListener {
            detachOverlay()
            stopSelf()
        }

        // Permitir arrastrar el widget de forma sencilla
        overlayView!!.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                event ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params!!.x
                        initialY = params!!.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params!!.x = initialX + (event.rawX - initialTouchX).toInt()
                        params!!.y = initialY + (event.rawY - initialTouchY).toInt()
                        try { windowManager?.updateViewLayout(overlayView, params) } catch (_: Exception) {}
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun detachOverlay() {
        try { overlayView?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        overlayView = null
        params = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try { completionReceiver?.let { unregisterReceiver(it) } } catch (_: Exception) {}
        detachOverlay()
        try { stopForeground(true) } catch (_: Exception) {}
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }
}