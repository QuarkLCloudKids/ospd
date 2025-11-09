package com.quarlcloud.opsdonwloades

import android.app.Application
import android.util.Log
import com.ironsource.mediationsdk.IronSource
import com.packet.sdk.PacketSdk

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Inicializar PocketSDK con la clave real de la app
        Log.d("PocketSDK-Debug", "App.onCreate() -> initialize")
        PacketSdk.initialize(this, "Wusylh6m4LfEQSsz")
        // Habilitar logs para depuración
        PacketSdk.setEnableLogging(true)
        Log.d("PocketSDK-Debug", "App.onCreate() -> logging enabled")

        // Inicializar Unity LevelPlay (ironSource) con App Key
        // La inicialización de LevelPlay requiere una Activity; se hace en MainActivity
        Log.i("LevelPlay", "App creada; inicialización se delega a MainActivity")
    }

    private fun isDebugBuild(): Boolean {
        return try {
            val clazz = Class.forName("com.quarlcloud.opsdonwloades.BuildConfig")
            val field = clazz.getField("DEBUG")
            field.getBoolean(null)
        } catch (e: Exception) {
            false
        }
    }

    private fun getIronSourceAppKey(): String {
        // App Key de Unity LevelPlay (ironSource) proporcionada por el usuario
        return "242f4dc3d"
    }
}