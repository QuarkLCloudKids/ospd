package com.quarlcloud.opsdonwloades.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.quarlcloud.opsdonwloades.MainActivity
import com.quarlcloud.opsdonwloades.R
import com.quarlcloud.opsdonwloades.network.TikTokDownloader
import com.quarlcloud.opsdonwloades.network.MultiPlatformDownloader
import com.quarlcloud.opsdonwloades.network.VideoInfo
import com.quarlcloud.opsdonwloades.network.DownloadResult
import com.quarlcloud.opsdonwloades.utils.TikTokDetector
import com.quarlcloud.opsdonwloades.utils.TikTokUrlResolver
import com.quarlcloud.opsdonwloades.utils.SettingsManager
import com.quarlcloud.opsdonwloades.utils.PlatformDetector
import com.quarlcloud.opsdonwloades.utils.GenericUrlExtractor
import kotlinx.coroutines.*

class DownloadService : Service() {
    companion object {
        private const val TAG = "DownloadService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "download_channel"
        private const val CHANNEL_NAME = "Descargas"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var tikTokDownloader: TikTokDownloader
    private lateinit var mpDownloader: MultiPlatformDownloader
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        tikTokDownloader = TikTokDownloader(this)
        mpDownloader = MultiPlatformDownloader(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        Log.d(TAG, "DownloadService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return try {
            val rawUrl = intent?.getStringExtra("video_url")
            val source = intent?.getStringExtra("source") ?: "app"

            if (rawUrl != null) {
                // Detectar plataforma y normalizar enlace
                val platform = PlatformDetector.detect(rawUrl)
                val normalized = PlatformDetector.clean(rawUrl)
                val finalUrl = try {
                    if (platform == com.quarlcloud.opsdonwloades.utils.Platform.TIKTOK) {
                        TikTokUrlResolver.resolve(normalized)
                    } else normalized
                } catch (e: Exception) {
                    Log.w(TAG, "Resolver failed, proceeding with normalized", e)
                    normalized
                }

                Log.d(TAG, "Starting download for URL: $finalUrl from source: $source")
                // Proteger inicio foreground para evitar crash por notificación malformada
                try {
                    startForeground(NOTIFICATION_ID, createNotification("Iniciando descarga...", 0))
                } catch (nf: Exception) {
                    Log.e(TAG, "startForeground failed, showing normal notification", nf)
                    try {
                        notificationManager.notify(NOTIFICATION_ID, createNotification("Iniciando descarga...", 0))
                    } catch (_: Exception) {}
                }

                serviceScope.launch {
                    try {
                        downloadVideo(finalUrl, source)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error durante la descarga", e)
                        showErrorNotification("Error en la descarga: ${e.message}")
                    } finally {
                        stopSelf(startId)
                    }
                }
            } else {
                Log.w(TAG, "No video URL provided")
                stopSelf(startId)
            }

            START_NOT_STICKY
        } catch (e: Exception) {
            Log.e(TAG, "onStartCommand fatal", e)
            showErrorNotification("Error iniciando servicio: ${e.message}")
            stopSelf(startId)
            START_NOT_STICKY
        }
    }

    private suspend fun downloadVideo(videoUrl: String, source: String) {
        Log.d(TAG, "Starting download process for URL: $videoUrl from source: $source")

        try {
            updateNotification("Iniciando descarga con servicios de terceros...", 10)

            // Preferencias del usuario
            val qualityPref = SettingsManager.getQualityPreference(this)
            val dataSaver = SettingsManager.getDataSaver(this)
            var forceNoWm = SettingsManager.getForceNoWatermark(this)
            // Ahorro de Datos: evita HD y operaciones pesadas
            val preferHd = !dataSaver && (qualityPref == "max" || qualityPref == "1080p")
            if (dataSaver) forceNoWm = false

            updateNotification("Probando servicios de descarga...", 20)

            val platform = PlatformDetector.detect(videoUrl)

            val result = when (platform) {
                com.quarlcloud.opsdonwloades.utils.Platform.TIKTOK -> {
                    val videoInfo = VideoInfo(
                        title = "TikTok Video ${System.currentTimeMillis()}",
                        author = "TikTok User",
                        duration = "Unknown",
                        downloadUrl = videoUrl,
                        thumbnailUrl = null,
                        videoId = null
                    )
                    tikTokDownloader.downloadVideo(videoInfo, preferHd = preferHd, forceNoWm = forceNoWm) { progress ->
                        val notificationProgress = (20 + (progress * 70)).toInt()
                        Log.d(TAG, "Download progress: ${(progress * 100).toInt()}%")
                        updateNotification("Descargando video...", notificationProgress)
                    }
                }
                com.quarlcloud.opsdonwloades.utils.Platform.INSTAGRAM -> {
                    mpDownloader.downloadInstagram(videoUrl, preferHd) { progress ->
                        val p = (20 + (progress * 70)).toInt(); updateNotification("Descargando video de Instagram...", p)
                    }
                }
                com.quarlcloud.opsdonwloades.utils.Platform.FACEBOOK -> {
                    mpDownloader.downloadFacebook(videoUrl, preferHd) { progress ->
                        val p = (20 + (progress * 70)).toInt(); updateNotification("Descargando video de Facebook...", p)
                    }
                }
                
                com.quarlcloud.opsdonwloades.utils.Platform.YOUTUBE -> {
                    val videoId = PlatformDetector.extractYouTubeId(videoUrl)
                    if (videoId.isNullOrBlank()) {
                        DownloadResult(false, null, "No se pudo extraer ID de YouTube")
                    } else {
                        mpDownloader.downloadYouTube(videoId, preferHd) { progress ->
                            val p = (20 + (progress * 70)).toInt(); updateNotification("Descargando video de YouTube...", p)
                        }
                    }
                }
                else -> {
                    DownloadResult(false, null, "Enlace no reconocido: pega un enlace de TikTok/Instagram/YouTube/X/Facebook")
                }
            }

            if (result.success && result.filePath != null) {
                updateNotification("Finalizando...", 95)
                delay(500)
                showSuccessNotification(
                    "Descarga completada",
                    "Video descargado exitosamente",
                    result.filePath
                )
                Log.d(TAG, "Download completed successfully: ${result.filePath}")
                return
            } else {
                Log.e(TAG, "All third-party services failed: ${result.error}")
                throw Exception(result.error ?: "Todos los servicios de descarga fallaron")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during download process", e)
            showErrorNotification("Error en la descarga: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificaciones de descarga de videos"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(title: String, progress: Int): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("OpSDonwloades")
            .setSmallIcon(R.drawable.ic_download)
            .setContentIntent(pendingIntent)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setSilent(false)
            .build()
    }

    private fun updateNotification(title: String, progress: Int) {
        val notification = createNotification(title, progress)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showSuccessNotification(title: String, content: String, filePath: String?) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_download_done)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
        // Avisar a posibles overlays para cerrar
        try {
            val br = Intent("com.quarlcloud.opsdonwloades.DOWNLOAD_FINISHED").apply {
                putExtra("success", true)
                putExtra("path", filePath)
            }
            sendBroadcast(br)
        } catch (_: Exception) {}
    }

    private fun showErrorNotification(error: String) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Error en descarga")
            .setContentText(error)
            .setSmallIcon(R.drawable.ic_error)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 2, notification)
        // Avisar a posibles overlays para cerrar
        try {
            val br = Intent("com.quarlcloud.opsdonwloades.DOWNLOAD_FINISHED").apply {
                putExtra("success", false)
                putExtra("error", error)
            }
            sendBroadcast(br)
        } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "DownloadService destroyed")
    }
}