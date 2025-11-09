package com.quarlcloud.opsdonwloades.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quarlcloud.opsdonwloades.utils.SettingsManager

@Composable
fun SettingsScreen(onThemeChange: (String) -> Unit = {}) {
    val context = LocalContext.current

    var quality by remember { mutableStateOf(SettingsManager.getQualityPreference(context)) }
    var forceNoWm by remember { mutableStateOf(SettingsManager.getForceNoWatermark(context)) }
    var tileEnabled by remember { mutableStateOf(SettingsManager.getTileEnabled(context)) }
    var themeMode by remember { mutableStateOf(SettingsManager.getThemeMode(context)) }
    var dataSaver by remember { mutableStateOf(SettingsManager.getDataSaver(context)) }

    var mpEnabled by remember { mutableStateOf(SettingsManager.getMultiPlatformEnabled(context)) }
    var mpFb by remember { mutableStateOf(SettingsManager.getFacebookEnabled(context)) }
    var mpIg by remember { mutableStateOf(SettingsManager.getInstagramEnabled(context)) }
    var mpYt by remember { mutableStateOf(SettingsManager.getYouTubeEnabled(context)) }
    var mpX by remember { mutableStateOf(SettingsManager.getXEnabled(context)) }
    val dloadAudioHdEnabled = remember { SettingsManager.getDloadAudioHdEnabled(context) }

    var showTileTutorial by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Settings, contentDescription = "Configuración")
            Spacer(Modifier.width(8.dp))
            Text(text = "Configuración", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        // Calidad preferida
        ElevatedCard { 
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.VideoLibrary, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Calidad preferida", fontWeight = FontWeight.SemiBold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = quality == "1080p", onClick = {
                        quality = "1080p"
                        SettingsManager.setQualityPreference(context, "1080p")
                    })
                    Spacer(Modifier.width(6.dp))
                    Text("1080p")
                    Spacer(Modifier.width(20.dp))
                    RadioButton(selected = quality == "max", onClick = {
                        quality = "max"
                        SettingsManager.setQualityPreference(context, "max")
                    })
                    Spacer(Modifier.width(6.dp))
                    Text("Calidad máxima")
                }
                Text(
                    "Se intentará descargar sin marca de agua y en la mejor calidad disponible.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Forzar sin marca de agua
        ElevatedCard { 
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Forzar sin marca de agua", fontWeight = FontWeight.SemiBold)
                    }
                    Text("Quitará la marca de agua cuando sea posible", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = forceNoWm, onCheckedChange = {
                    forceNoWm = it
                    SettingsManager.setForceNoWatermark(context, it)
                })
            }
        }

        // Activar Tile + tutorial
        ElevatedCard { 
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Settings, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Activar tile de acceso rápido", fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "Al activar, verás un tutorial para agregar el tile al centro de control.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = tileEnabled, onCheckedChange = {
                    tileEnabled = it
                    SettingsManager.setTileEnabled(context, it)
                    if (it) showTileTutorial = true
                })
            }
        }

        // Cambiar tema
        ElevatedCard { 
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Settings, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Tema", fontWeight = FontWeight.SemiBold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = themeMode == "system", onClick = {
                        themeMode = "system"
                        SettingsManager.setThemeMode(context, "system")
                        onThemeChange("system")
                    })
                    Spacer(Modifier.width(6.dp))
                    Text("Sistema")
                    Spacer(Modifier.width(18.dp))
                    RadioButton(selected = themeMode == "light", onClick = {
                        themeMode = "light"
                        SettingsManager.setThemeMode(context, "light")
                        onThemeChange("light")
                    })
                    Spacer(Modifier.width(6.dp))
                    Text("Claro")
                    Spacer(Modifier.width(18.dp))
                    RadioButton(selected = themeMode == "dark", onClick = {
                        themeMode = "dark"
                        SettingsManager.setThemeMode(context, "dark")
                        onThemeChange("dark")
                    })
                    Spacer(Modifier.width(6.dp))
                    Text("Oscuro")
                }
            }
        }

        // Ahorro de Datos
        ElevatedCard { 
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Ahorro de Datos", fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "Reduce el uso de datos: limita la calidad y evita descargas pesadas",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = dataSaver, onCheckedChange = {
                    dataSaver = it
                    SettingsManager.setDataSaver(context, it)
                })
            }
        }

        // Multiplataforma (Beta)
        ElevatedCard { 
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.VideoLibrary, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Multiplataforma (Beta)", fontWeight = FontWeight.SemiBold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Habilitar Multiplataforma")
                        Text("Facebook, Instagram, YouTube, X (experimental)", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = mpEnabled, onCheckedChange = {
                        mpEnabled = it
                        SettingsManager.setMultiPlatformEnabled(context, it)
                    })
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Facebook", modifier = Modifier.weight(1f))
                        Switch(checked = mpFb, enabled = mpEnabled, onCheckedChange = {
                            mpFb = it
                            SettingsManager.setFacebookEnabled(context, it)
                        })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Instagram", modifier = Modifier.weight(1f))
                        Switch(checked = mpIg, enabled = mpEnabled, onCheckedChange = {
                            mpIg = it
                            SettingsManager.setInstagramEnabled(context, it)
                        })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("YouTube", modifier = Modifier.weight(1f))
                        Switch(checked = mpYt, enabled = mpEnabled, onCheckedChange = {
                            mpYt = it
                            SettingsManager.setYouTubeEnabled(context, it)
                        })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("X (Twitter)", modifier = Modifier.weight(1f))
                        Switch(checked = mpX, enabled = mpEnabled, onCheckedChange = {
                            mpX = it
                            SettingsManager.setXEnabled(context, it)
                        })
                    }
                }

                Divider()

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Habilitar DLOAD Audio HD (Música)")
                        Text("Beta: no disponible", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = dloadAudioHdEnabled, enabled = false, onCheckedChange = {})
                }
            }
        }
    }

    if (showTileTutorial) {
        AlertDialog(
            onDismissRequest = { showTileTutorial = false },
            title = { Text("Cómo activar el tile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1) Copia el enlace del video en la app (TikTok/Instagram/YouTube/X/Facebook).")
                    Text("2) Desliza hacia abajo para abrir el centro de control.")
                    Text("3) Toca 'Editar' y agrega el tile 'OpSDonwloades'.")
                    Text("4) Toca el tile: detecta el enlace del portapapeles y descarga.")
                    Spacer(Modifier.height(8.dp))
                    Text("Permisos recomendados:")
                    Text("• Almacenamiento / Medios: guardar videos")
                    Text("• Notificaciones: ver progreso")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    // Abrir detalles de la app
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    showTileTutorial = false
                }) { Text("Abrir configuración de app") }
            },
            dismissButton = {
                TextButton(onClick = { showTileTutorial = false }) { Text("Cerrar") }
            }
        )
    }
}