package com.quarlcloud.opsdonwloades.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionManager(private val activity: ComponentActivity) {
    
    companion object {
        private const val TAG = "PermissionManager"
        
        // Permisos necesarios según la versión de Android
        private val STORAGE_PERMISSIONS_LEGACY = arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        
        private val STORAGE_PERMISSIONS_ANDROID_13 = arrayOf(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_IMAGES
        )
        
        private val NOTIFICATION_PERMISSIONS = arrayOf(
            Manifest.permission.POST_NOTIFICATIONS
        )
    }
    
    private var onPermissionsGranted: (() -> Unit)? = null
    private var onPermissionsDenied: ((List<String>) -> Unit)? = null
    
    // Launcher para permisos múltiples
    private val multiplePermissionsLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            handlePermissionsResult(permissions)
        }
    
    // Launcher para configuración de almacenamiento (Android 11+)
    private val manageStorageLauncher: ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            checkAndRequestRemainingPermissions()
        }
    
    /**
     * Solicita todos los permisos necesarios para la aplicación
     */
    fun requestAllPermissions(
        onGranted: () -> Unit,
        onDenied: (List<String>) -> Unit = {}
    ) {
        this.onPermissionsGranted = onGranted
        this.onPermissionsDenied = onDenied
        
        Log.i(TAG, "Iniciando solicitud de permisos...")
        
        // Verificar si ya tenemos todos los permisos
        if (hasAllRequiredPermissions()) {
            Log.i(TAG, "Todos los permisos ya están concedidos")
            onGranted()
            return
        }
        
        // Solicitar permisos paso a paso
        requestStoragePermissions()
    }
    
    /**
     * Verifica si tenemos todos los permisos necesarios
     */
    fun hasAllRequiredPermissions(): Boolean {
        return hasStoragePermissions() && hasNotificationPermissions()
    }
    
    /**
     * Verifica permisos de almacenamiento
     */
    private fun hasStoragePermissions(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                // Android 11+ - Verificar MANAGE_EXTERNAL_STORAGE
                Environment.isExternalStorageManager() || hasPermissions(STORAGE_PERMISSIONS_ANDROID_13)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // Android 13+ - Verificar permisos de media
                hasPermissions(STORAGE_PERMISSIONS_ANDROID_13)
            }
            else -> {
                // Android 10 y anteriores
                hasPermissions(STORAGE_PERMISSIONS_LEGACY)
            }
        }
    }
    
    /**
     * Verifica permisos de notificaciones
     */
    private fun hasNotificationPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermissions(NOTIFICATION_PERMISSIONS)
        } else {
            true // No se necesita permiso explícito en versiones anteriores
        }
    }
    
    /**
     * Verifica si tenemos un conjunto de permisos
     */
    private fun hasPermissions(permissions: Array<String>): Boolean {
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Solicita permisos de almacenamiento
     */
    private fun requestStoragePermissions() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                // Android 11+ - Solicitar MANAGE_EXTERNAL_STORAGE
                if (!Environment.isExternalStorageManager()) {
                    Log.i(TAG, "Solicitando permiso MANAGE_EXTERNAL_STORAGE...")
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:${activity.packageName}")
                        }
                        manageStorageLauncher.launch(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al abrir configuración de almacenamiento: ${e.message}")
                        // Fallback a permisos normales
                        requestMediaPermissions()
                    }
                } else {
                    requestNotificationPermissions()
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // Android 13+ - Solicitar permisos de media
                requestMediaPermissions()
            }
            else -> {
                // Android 10 y anteriores - Permisos legacy
                requestLegacyStoragePermissions()
            }
        }
    }
    
    /**
     * Solicita permisos de media (Android 13+)
     */
    private fun requestMediaPermissions() {
        val missingPermissions = STORAGE_PERMISSIONS_ANDROID_13.filter { permission ->
            ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            Log.i(TAG, "Solicitando permisos de media: ${missingPermissions.joinToString()}")
            multiplePermissionsLauncher.launch(missingPermissions.toTypedArray())
        } else {
            requestNotificationPermissions()
        }
    }
    
    /**
     * Solicita permisos legacy de almacenamiento
     */
    private fun requestLegacyStoragePermissions() {
        val missingPermissions = STORAGE_PERMISSIONS_LEGACY.filter { permission ->
            ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            Log.i(TAG, "Solicitando permisos legacy de almacenamiento: ${missingPermissions.joinToString()}")
            multiplePermissionsLauncher.launch(missingPermissions.toTypedArray())
        } else {
            requestNotificationPermissions()
        }
    }
    
    /**
     * Solicita permisos de notificaciones
     */
    private fun requestNotificationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val missingPermissions = NOTIFICATION_PERMISSIONS.filter { permission ->
                ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
            }
            
            if (missingPermissions.isNotEmpty()) {
                Log.i(TAG, "Solicitando permisos de notificaciones: ${missingPermissions.joinToString()}")
                multiplePermissionsLauncher.launch(missingPermissions.toTypedArray())
            } else {
                checkFinalPermissions()
            }
        } else {
            checkFinalPermissions()
        }
    }
    
    /**
     * Verifica permisos restantes después de solicitar almacenamiento
     */
    private fun checkAndRequestRemainingPermissions() {
        if (hasStoragePermissions()) {
            Log.i(TAG, "Permisos de almacenamiento concedidos, solicitando notificaciones...")
            requestNotificationPermissions()
        } else {
            Log.w(TAG, "Permisos de almacenamiento no concedidos")
            // Intentar con permisos de media como fallback
            requestMediaPermissions()
        }
    }
    
    /**
     * Maneja el resultado de la solicitud de permisos múltiples
     */
    private fun handlePermissionsResult(permissions: Map<String, Boolean>) {
        val deniedPermissions = permissions.filter { !it.value }.keys.toList()
        
        if (deniedPermissions.isEmpty()) {
            Log.i(TAG, "Todos los permisos solicitados fueron concedidos")
            // Continuar con el siguiente paso
            when {
                !hasStoragePermissions() -> requestStoragePermissions()
                !hasNotificationPermissions() -> requestNotificationPermissions()
                else -> checkFinalPermissions()
            }
        } else {
            Log.w(TAG, "Permisos denegados: ${deniedPermissions.joinToString()}")
            
            // Verificar si podemos continuar sin algunos permisos
            if (hasStoragePermissions()) {
                // Si tenemos almacenamiento, continuar con notificaciones
                requestNotificationPermissions()
            } else {
                // Sin permisos de almacenamiento, no podemos continuar
                onPermissionsDenied?.invoke(deniedPermissions)
            }
        }
    }
    
    /**
     * Verificación final de todos los permisos
     */
    private fun checkFinalPermissions() {
        val missingPermissions = mutableListOf<String>()
        
        if (!hasStoragePermissions()) {
            missingPermissions.add("Almacenamiento")
        }
        
        if (!hasNotificationPermissions()) {
            missingPermissions.add("Notificaciones")
        }
        
        if (missingPermissions.isEmpty()) {
            Log.i(TAG, "✅ Todos los permisos necesarios han sido concedidos")
            onPermissionsGranted?.invoke()
        } else {
            Log.w(TAG, "❌ Faltan permisos: ${missingPermissions.joinToString()}")
            onPermissionsDenied?.invoke(missingPermissions)
        }
    }
    
    /**
     * Abre la configuración de la aplicación
     */
    fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error al abrir configuración de la app: ${e.message}")
        }
    }
}