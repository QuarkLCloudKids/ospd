package com.quarlcloud.opsdonwloades.service

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.quarlcloud.opsdonwloades.MainActivity
import com.quarlcloud.opsdonwloades.utils.TikTokDetector
import com.quarlcloud.opsdonwloades.utils.TikTokAutomation

class TikTokDownloaderTileService : TileService() {

    private lateinit var tikTokDetector: TikTokDetector
    private val feedbackNotificationId = 1001

    override fun onCreate() {
        super.onCreate()
        tikTokDetector = TikTokDetector(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        // Feedback rápido y colapso de panel
        showFeedbackProcessing()
        try { sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)) } catch (_: Exception) {}

        // Abrir directamente la actividad del widget y colapsar QS
        try {
            val intent = Intent(this, OverlayWidgetActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            try {
                startActivityAndCollapse(intent)
            } catch (_: Throwable) {
                startActivity(intent)
            }
        } catch (e: Exception) {
            updateFeedbackNotification("Error abriendo widget")
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // No abrimos la app; el tile solo inicia la descarga desde el portapapeles

    private fun updateTile() {
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            label = "OpSDonwloades"
            subtitle = "Usa el portapapeles"
            updateTile()
        }
    }

    // Eliminado: el tile no intentará extracción automática ni flujo complejo

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
        val builder = NotificationCompat.Builder(this, "tile_feedback")
            .setSmallIcon(com.quarlcloud.opsdonwloades.R.drawable.ic_download)
            .setContentTitle("OpSDonwloades")
            .setContentText("Tile tocado: procesando…")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setTimeoutAfter(2500)
        NotificationManagerCompat.from(this).notify(feedbackNotificationId, builder.build())
    }

    private fun updateFeedbackNotification(message: String) {
        ensureFeedbackChannel()
        val builder = NotificationCompat.Builder(this, "tile_feedback")
            .setSmallIcon(com.quarlcloud.opsdonwloades.R.drawable.ic_download)
            .setContentTitle("OpSDonwloades")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setTimeoutAfter(4000)
        NotificationManagerCompat.from(this).notify(feedbackNotificationId, builder.build())
    }

    override fun onStopListening() {
        super.onStopListening()
        // Resetear el tile cuando no esté visible
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "OpSDonwloades"
            subtitle = null
            updateTile()
        }
    }
}