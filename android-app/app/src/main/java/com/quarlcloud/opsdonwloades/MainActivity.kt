package com.quarlcloud.opsdonwloades

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.quarlcloud.opsdonwloades.ui.screens.DownloadScreen
import com.quarlcloud.opsdonwloades.ui.screens.InfoScreen
import com.quarlcloud.opsdonwloades.ui.screens.DownloadedVideosScreen
import com.quarlcloud.opsdonwloades.ui.screens.SettingsScreen
import com.quarlcloud.opsdonwloades.ui.theme.OpSDonwloadesTheme
import com.quarlcloud.opsdonwloades.server.LocalServer
import com.quarlcloud.opsdonwloades.utils.PermissionManager
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import android.widget.Toast
import com.ironsource.mediationsdk.IronSource
import com.ironsource.mediationsdk.logger.IronSourceError
import com.ironsource.mediationsdk.model.Placement
import com.ironsource.mediationsdk.sdk.RewardedVideoListener
import com.packet.sdk.PacketSdk
import android.provider.Settings
import java.security.MessageDigest
import com.ironsource.mediationsdk.integration.IntegrationHelper

class MainActivity : ComponentActivity() {
    private lateinit var permissionManager: PermissionManager
    private var rewardedReady: Boolean = false
    private var rewardedPlacementName: String = "apoyo"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inicializar el gestor de permisos
        permissionManager = PermissionManager(this)
        
        // Usar el nombre del placement (LevelPlay) para rewarded
        rewardedPlacementName = "apoyo"
        
        // Iniciar PocketSDK (monetización)
        PacketSdk.setStatusListener { code, msg ->
            Log.d("PocketSDK", "Status: $code, $msg")
        }
        Log.d("PocketSDK-Debug", "MainActivity.onCreate() -> PacketSdk.start()")
        PacketSdk.start()

        // Inicializar LevelPlay (ironSource) desde Activity
        IronSource.setAdaptersDebug(isDebugBuild())
        IronSource.shouldTrackNetworkState(this, true)
        // Asignar User ID para S2S callbacks y atribución
        IronSource.setUserId(getUserId())
        IronSource.init(this, getIronSourceAppKey(), IronSource.AD_UNIT.REWARDED_VIDEO)
        Log.i("LevelPlay", "Init called appKey=${getIronSourceAppKey()} userId=${getUserId()}")

        // Configurar listener y solicitar carga de rewarded en LevelPlay
        IronSource.setRewardedVideoListener(object : RewardedVideoListener {
            override fun onRewardedVideoAvailabilityChanged(available: Boolean) {
                rewardedReady = available
                Log.i("LevelPlay", "Availability changed: $available")
            }

            override fun onRewardedVideoAdStarted() {
                Log.i("LevelPlay", "Rewarded started")
            }

            override fun onRewardedVideoAdEnded() {
                Log.i("LevelPlay", "Rewarded ended")
            }

            override fun onRewardedVideoAdOpened() {
                Log.i("LevelPlay", "Rewarded opened")
            }

            override fun onRewardedVideoAdClosed() {
                Log.i("LevelPlay", "Rewarded closed")
                rewardedReady = false
            }

            override fun onRewardedVideoAdRewarded(placement: Placement) {
                Log.i("LevelPlay", "Reward granted: ${placement.rewardName} ${placement.rewardAmount}")
                Toast.makeText(this@MainActivity, "¡Gracias por apoyarnos!", Toast.LENGTH_SHORT).show()
            }

            override fun onRewardedVideoAdShowFailed(error: IronSourceError) {
                Log.e("LevelPlay", "Show failed: ${error.errorCode} ${error.errorMessage}")
                rewardedReady = false
            }

            override fun onRewardedVideoAdClicked(placement: Placement) {
                Log.i("LevelPlay", "Rewarded clicked: ${placement.placementName}")
            }
        })

        // Validar integración de adapters al iniciar (para diagnóstico automático)
        IntegrationHelper.validateIntegration(this)

        setContent {
            var themeMode by remember { mutableStateOf(com.quarlcloud.opsdonwloades.utils.SettingsManager.getThemeMode(this)) }
            val isDark = when (themeMode) {
                "system" -> isSystemInDarkTheme()
                "dark" -> true
                else -> false
            }
            OpSDonwloadesTheme(darkTheme = isDark) {
                MainApp(this@MainActivity, onThemeChange = { mode ->
                    themeMode = mode
                })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Reintentar arranque del SDK en el ciclo de vida por si la App se lanzó fría
        Log.d("PocketSDK-Debug", "MainActivity.onStart() -> PacketSdk.start()")
        PacketSdk.start()
        // Notificar ciclo de vida a LevelPlay y asegurar carga
        // En modo auto-load, no invocar cargas manuales
    }
    
    fun startLocalServer() {
        try {
            LocalServer.start()
            Log.i("MainActivity", "Local server started successfully")
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start local server: ${e.message}", e)
        }
    }
    
    fun getPermissionManager(): PermissionManager {
        return permissionManager
    }
    
    override fun onDestroy() {
        super.onDestroy()
        LocalServer.stop()
    }

    fun showRewardedAd() {
        val available = IronSource.isRewardedVideoAvailable()
        if (!available && !rewardedReady) {
            Toast.makeText(this, "Cargando anuncio... inténtalo en unos segundos", Toast.LENGTH_SHORT).show()
            Log.w("LevelPlay", "Rewarded no listo; espera disponibilidad (auto-load)")
            return
        }
        try {
            Log.i("LevelPlay", "Mostrando rewarded con placement por defecto")
            IronSource.showRewardedVideo()
        } catch (e: Exception) {
            Log.w("LevelPlay", "Fallo al mostrar rewarded por defecto", e)
        }
    }

    fun isDebugBuild(): Boolean {
        return try {
            val clazz = Class.forName("com.quarlcloud.opsdonwloades.BuildConfig")
            val field = clazz.getField("DEBUG")
            field.getBoolean(null)
        } catch (e: Exception) {
            false
        }
    }

    private fun getIronSourceAppKey(): String = "242f4dc3d"

    private fun getUserId(): String {
        val raw = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        return sha256(raw)
    }

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    override fun onResume() {
        super.onResume()
        IronSource.onResume(this)
    }

    override fun onPause() {
        IronSource.onPause(this)
        super.onPause()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(activity: MainActivity, onThemeChange: (String) -> Unit) {
    val navController = rememberNavController()
    val permissionsGranted = remember { mutableStateOf(false) }
    val showPermissionDialog = remember { mutableStateOf(false) }
    val deniedPermissions = remember { mutableStateOf<List<String>>(emptyList()) }
    
    // Solicitar permisos al iniciar la app
    LaunchedEffect(Unit) {
        activity.getPermissionManager().requestAllPermissions(
            onGranted = {
                Log.i("MainActivity", "✅ Todos los permisos concedidos, iniciando servidor...")
                permissionsGranted.value = true
                activity.startLocalServer()
            },
            onDenied = { denied ->
                Log.w("MainActivity", "❌ Permisos denegados: ${denied.joinToString()}")
                deniedPermissions.value = denied
                showPermissionDialog.value = true
            }
        )
    }
    
    // Diálogo de permisos denegados
    if (showPermissionDialog.value) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog.value = false },
            title = { Text("Permisos necesarios") },
            text = { 
                Text(
                    "La aplicación necesita los siguientes permisos para funcionar correctamente:\n\n" +
                    "• Almacenamiento: Para guardar los videos descargados\n" +
                    "• Notificaciones: Para mostrar el progreso de descarga\n\n" +
                    "Permisos faltantes: ${deniedPermissions.value.joinToString(", ")}"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog.value = false
                        activity.getPermissionManager().openAppSettings()
                    }
                ) {
                    Text("Abrir configuración")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPermissionDialog.value = false }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
    
    // Diálogo de apoyo
    val showSupportDialog = remember { mutableStateOf(false) }
    
    val items = listOf(
        NavigationItem("download", "Descargar", Icons.Filled.Download),
        NavigationItem("downloaded_videos", "Videos descargados", Icons.Filled.VideoLibrary),
        NavigationItem("settings", "Configuración", Icons.Filled.Settings),
        NavigationItem("info", "Info", Icons.Filled.Info)
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "OPSD",
                        style = MaterialTheme.typography.headlineSmall
                    ) 
                },
                actions = {
                    TextButton(onClick = { showSupportDialog.value = true }) {
                        Text("¡Apóyanos!")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .shadow(10.dp, RoundedCornerShape(28.dp)),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp
                ) {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination

                    items.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.title) },
                            label = null,
                            alwaysShowLabel = false,
                            selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            ),
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        if (showSupportDialog.value) {
            AlertDialog(
                onDismissRequest = { showSupportDialog.value = false },
                title = { Text("Apóyanos") },
                text = {
                    Text("Vas a ver un anuncio para apoyar nuestro trabajo. Así lo mantenemos totalmente gratis para el público. ¡Gracias por apoyarnos!")
                },
                confirmButton = {
                    TextButton(onClick = {
                        showSupportDialog.value = false
                        activity.showRewardedAd()
                    }) {
                        Text("Ver anuncio")
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = { showSupportDialog.value = false }) {
                            Text("Cancelar")
                        }
                        TextButton(onClick = { IntegrationHelper.validateIntegration(activity) }) {
                            Text("Test Ads")
                        }
                    }
                }
            )
        }
        NavHost(
            navController = navController,
            startDestination = "download",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("download") {
                DownloadScreen()
            }
            composable("downloaded_videos") {
                DownloadedVideosScreen()
            }
            composable("settings") {
                SettingsScreen(onThemeChange = { mode -> onThemeChange(mode) })
            }
            composable("info") {
                InfoScreen()
            }
        }
    }
}

data class NavigationItem(
    val route: String,
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)