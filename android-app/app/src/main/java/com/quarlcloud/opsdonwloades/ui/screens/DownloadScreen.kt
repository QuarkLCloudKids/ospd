package com.quarlcloud.opsdonwloades.ui.screens

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.quarlcloud.opsdonwloades.utils.SettingsManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quarlcloud.opsdonwloades.utils.PlatformDetector
import com.quarlcloud.opsdonwloades.utils.GenericUrlExtractor
import com.quarlcloud.opsdonwloades.utils.Platform
import com.quarlcloud.opsdonwloades.network.TikTokDownloader
import com.quarlcloud.opsdonwloades.network.MultiPlatformDownloader
import com.quarlcloud.opsdonwloades.network.DownloadResult
import com.quarlcloud.opsdonwloades.network.VideoInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen() {
    var urlText by remember { mutableStateOf("") }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadStatus by remember { mutableStateOf("") }
    var downloadProgress by remember { mutableStateOf(0f) }
    val ctx = LocalContext.current
    var preferHd by remember {
        mutableStateOf(SettingsManager.getQualityPreference(ctx).let { it == "max" || it == "1080p" })
    }
    var forceNoWm by remember { mutableStateOf(SettingsManager.getForceNoWatermark(ctx)) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Descargas directas desde la UI (sin servicio/notification)
    val mpDownloader = remember { MultiPlatformDownloader(context) }
    val ttDownloader = remember { TikTokDownloader(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header con gradiente
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.VideoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "OPSD",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // URL Input Section mejorada
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "URL",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedTextField(
                    value = urlText,
                    onValueChange = {
                        urlText = it
                        if (downloadStatus.isNotEmpty()) {
                            downloadStatus = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text("Pega la URL aquí")
                    },
                    shape = RoundedCornerShape(16.dp),
                    maxLines = 3,
                    isError = run {
                        val extracted = GenericUrlExtractor.extract(urlText)
                        val platform = if (extracted != null) PlatformDetector.detect(extracted) else Platform.UNKNOWN
                        urlText.isNotEmpty() && (extracted == null || platform == Platform.UNKNOWN)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )

                run {
                    val extracted = GenericUrlExtractor.extract(urlText)
                    val platform = if (extracted != null) PlatformDetector.detect(extracted) else Platform.UNKNOWN
                    if (urlText.isNotEmpty() && (extracted == null || platform == Platform.UNKNOWN)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚠️",
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Esta no parece ser una URL válida soportada",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // Preferencias de descarga
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Preferir 1080p/HD",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(checked = preferHd, onCheckedChange = { preferHd = it })
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Forzar sin marca de agua",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(checked = forceNoWm, onCheckedChange = { forceNoWm = it })
                        }
                    }
                }

                // Botón de pegar mejorado
                FilledTonalButton(
                    onClick = {
                        clipboardManager.getText()?.text?.let { clipText ->
                            urlText = clipText.trim()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Pegar del portapapeles",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Barra de progreso
        if (isDownloading) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Descargando...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = downloadProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${(downloadProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Botón de descarga mejorado
        Button(
            onClick = {
                val extracted = GenericUrlExtractor.extract(urlText)
                val platform = if (extracted != null) PlatformDetector.detect(extracted) else Platform.UNKNOWN
                val canDownload = extracted != null && platform != Platform.UNKNOWN
                if (canDownload) {
                    scope.launch {
                        isDownloading = true
                        downloadProgress = 0f
                        downloadStatus = "Iniciando descarga..."
                        val finalUrl = extracted ?: urlText
                        try {
                            // Preferencias del usuario
                            val qualityPref = SettingsManager.getQualityPreference(context)
                            val dataSaver = SettingsManager.getDataSaver(context)
                            var forceNoWmPref = SettingsManager.getForceNoWatermark(context)
                            val preferHd = !dataSaver && (qualityPref == "max" || qualityPref == "1080p")
                            if (dataSaver) forceNoWmPref = false

                            val result: DownloadResult = when (platform) {
                                Platform.TIKTOK -> {
                                    val info = VideoInfo(
                                        title = "TikTok Video",
                                        author = "",
                                        duration = "",
                                        downloadUrl = finalUrl,
                                        thumbnailUrl = null,
                                        videoId = null
                                    )
                                    ttDownloader.downloadVideo(info, preferHd = preferHd, forceNoWm = forceNoWmPref) { p ->
                                        downloadProgress = p.coerceIn(0f, 1f)
                                    }
                                }
                                Platform.INSTAGRAM -> {
                                    mpDownloader.downloadInstagram(finalUrl, preferHd) { p ->
                                        downloadProgress = p.coerceIn(0f, 1f)
                                    }
                                }
                                Platform.FACEBOOK -> {
                                    mpDownloader.downloadFacebook(finalUrl, preferHd) { p ->
                                        downloadProgress = p.coerceIn(0f, 1f)
                                    }
                                }
                                
                                Platform.YOUTUBE -> {
                                    val vid = PlatformDetector.extractYouTubeId(finalUrl)
                                    if (vid.isNullOrBlank()) DownloadResult(false, null, "No se pudo extraer ID de YouTube")
                                    else mpDownloader.downloadYouTube(vid, preferHd) { p ->
                                        downloadProgress = p.coerceIn(0f, 1f)
                                    }
                                }
                                else -> DownloadResult(false, null, "Plataforma no soportada")
                            }

                            if (result.success && result.filePath != null) {
                                downloadStatus = "✅ Descarga completada: ${result.filePath}"
                                urlText = ""
                            } else {
                                downloadStatus = "❌ Error en la descarga: ${result.error ?: "Fallo desconocido"}"
                            }
                        } catch (e: Exception) {
                            downloadStatus = "❌ Error en la descarga: ${e.message}"
                        } finally {
                            isDownloading = false
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            enabled = run {
                val extracted = GenericUrlExtractor.extract(urlText)
                val platform = if (extracted != null) PlatformDetector.detect(extracted) else Platform.UNKNOWN
                urlText.isNotBlank() && !isDownloading && extracted != null && platform != Platform.UNKNOWN
            },
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            if (isDownloading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Descargando...", 
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Descargar Video", 
                    fontSize = 18.sp, 
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Status Display mejorado
        if (downloadStatus.isNotEmpty()) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = when {
                        downloadStatus.contains("❌") || downloadStatus.contains("Error") -> 
                            MaterialTheme.colorScheme.errorContainer
                        downloadStatus.contains("✅") -> 
                            MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (downloadStatus.contains("✅")) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Text(
                        text = downloadStatus,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = when {
                            downloadStatus.contains("❌") || downloadStatus.contains("Error") -> 
                                MaterialTheme.colorScheme.onErrorContainer
                            downloadStatus.contains("✅") -> 
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tip mejorado
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "💡",
                    fontSize = 24.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Consejo Útil",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Agrega el tile de acceso rápido para descargas rápidas",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
        }
    }
}