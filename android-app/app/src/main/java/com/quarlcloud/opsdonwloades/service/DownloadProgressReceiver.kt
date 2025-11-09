package com.quarlcloud.opsdonwloades.service

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.quarlcloud.opsdonwloades.R
import kotlinx.coroutines.*

class DownloadProgressReceiver : BroadcastReceiver() {
    
    companion object {
        private const val CHANNEL_ID = "download_progress_channel"
        private const val NOTIFICATION_ID = 1001
        private val activeDownloads = mutableMapOf<Long, Job>()
        
        fun startMonitoring(context: Context, downloadId: Long, title: String) {
            // Cancelar monitoreo anterior si existe
            activeDownloads[downloadId]?.cancel()
            
            // Crear canal de notificación
            createNotificationChannel(context)
            
            // Iniciar monitoreo en corrutina
            val job = CoroutineScope(Dispatchers.IO).launch {
                monitorDownloadProgress(context, downloadId, title)
            }
            
            activeDownloads[downloadId] = job
        }
        
        private fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Progreso de Descargas",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Muestra el progreso de las descargas de videos"
                    setSound(null, null)
                }
                
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }
        
        private suspend fun monitorDownloadProgress(context: Context, downloadId: Long, title: String) {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            var isCompleted = false
            var lastProgress = -1
            
            while (!isCompleted && currentCoroutineContext().isActive) {
                try {
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor: Cursor? = downloadManager.query(query)
                    
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                            val bytesDownloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            val bytesTotal = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                            val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                            
                            when (status) {
                                DownloadManager.STATUS_RUNNING -> {
                                    if (bytesTotal > 0) {
                                        val progress = ((bytesDownloaded * 100) / bytesTotal).toInt()
                                        
                                        // Solo actualizar si el progreso cambió significativamente
                                        if (progress != lastProgress && progress >= 0) {
                                            lastProgress = progress
                                            
                                            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                                                .setSmallIcon(R.drawable.ic_download)
                                                .setContentTitle("Descargando: $title")
                                                .setContentText("${formatBytes(bytesDownloaded)} / ${formatBytes(bytesTotal)} ($progress%)")
                                                .setProgress(100, progress, false)
                                                .setOngoing(true)
                                                .setSilent(true)
                                                .build()
                                            
                                            notificationManager.notify(NOTIFICATION_ID, notification)
                                            
                                            Log.d("DownloadProgress", "Download $downloadId: $progress% ($bytesDownloaded/$bytesTotal bytes)")
                                        }
                                    } else {
                                        // Tamaño desconocido, mostrar progreso indeterminado
                                        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                                            .setSmallIcon(R.drawable.ic_download)
                                            .setContentTitle("Descargando: $title")
                                            .setContentText("Descargando... ${formatBytes(bytesDownloaded)}")
                                            .setProgress(0, 0, true)
                                            .setOngoing(true)
                                            .setSilent(true)
                                            .build()
                                        
                                        notificationManager.notify(NOTIFICATION_ID, notification)
                                    }
                                    Unit
                                }
                                
                                DownloadManager.STATUS_SUCCESSFUL -> {
                                    isCompleted = true
                                    
                                    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                                        .setSmallIcon(R.drawable.ic_check_circle)
                                        .setContentTitle("✅ Descarga completada")
                                        .setContentText("$title - ${formatBytes(bytesTotal)}")
                                        .setAutoCancel(true)
                                        .build()
                                    
                                    notificationManager.notify(NOTIFICATION_ID, notification)
                                    
                                    Log.i("DownloadProgress", "Download $downloadId completed successfully")
                                }
                                
                                DownloadManager.STATUS_FAILED -> {
                                    isCompleted = true
                                    
                                    val errorMessage = getErrorMessage(reason)
                                    
                                    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                                        .setSmallIcon(R.drawable.ic_error)
                                        .setContentTitle("❌ Error en descarga")
                                        .setContentText("$title - $errorMessage")
                                        .setAutoCancel(true)
                                        .build()
                                    
                                    notificationManager.notify(NOTIFICATION_ID, notification)
                                    
                                    Log.e("DownloadProgress", "Download $downloadId failed: $errorMessage (reason: $reason)")
                                }
                                
                                DownloadManager.STATUS_PAUSED -> {
                                    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                                        .setSmallIcon(R.drawable.ic_pause)
                                        .setContentTitle("⏸️ Descarga pausada")
                                        .setContentText("$title - Esperando conexión...")
                                        .setOngoing(true)
                                        .setSilent(true)
                                        .build()
                                    
                                    notificationManager.notify(NOTIFICATION_ID, notification)
                                    
                                    Log.w("DownloadProgress", "Download $downloadId paused")
                                }
                                
                                else -> {
                                    // Estado desconocido o pendiente
                                    Log.d("DownloadProgress", "Download $downloadId status: $status")
                                }
                            }
                        } else {
                            // La descarga ya no existe en el sistema
                            isCompleted = true
                            Log.w("DownloadProgress", "Download $downloadId not found in system")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("DownloadProgress", "Error monitoring download $downloadId: ${e.message}", e)
                    delay(2000) // Esperar más tiempo si hay error
                }
                
                if (!isCompleted) {
                    delay(1000) // Actualizar cada segundo
                }
            }
            
            // Limpiar el trabajo activo
            activeDownloads.remove(downloadId)
        }
        
        private fun formatBytes(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
                else -> "${bytes / (1024 * 1024 * 1024)} GB"
            }
        }
        
        private fun getErrorMessage(reason: Int): String {
            return when (reason) {
                DownloadManager.ERROR_CANNOT_RESUME -> "No se puede reanudar"
                DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Almacenamiento no encontrado"
                DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "El archivo ya existe"
                DownloadManager.ERROR_FILE_ERROR -> "Error de archivo"
                DownloadManager.ERROR_HTTP_DATA_ERROR -> "Error de datos HTTP"
                DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Espacio insuficiente"
                DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Demasiadas redirecciones"
                DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Código HTTP no manejado"
                DownloadManager.ERROR_UNKNOWN -> "Error desconocido"
                else -> "Error de descarga ($reason)"
            }
        }
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            DownloadManager.ACTION_DOWNLOAD_COMPLETE -> {
                val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (downloadId != -1L) {
                    Log.i("DownloadProgressReceiver", "Download completed: $downloadId")
                    // El monitoreo ya maneja la finalización
                }
            }
        }
    }
}