package com.quarlcloud.opsdonwloades.utils

import android.content.Context
import android.content.SharedPreferences

object SettingsManager {
    private const val PREFS_NAME = "ops_settings"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Calidad: "1080p" o "max"
    fun getQualityPreference(context: Context): String =
        prefs(context).getString("quality_pref", "max") ?: "max"

    fun setQualityPreference(context: Context, value: String) {
        prefs(context).edit().putString("quality_pref", value).apply()
    }

    // Forzar sin marca de agua
    fun getForceNoWatermark(context: Context): Boolean =
        prefs(context).getBoolean("force_no_wm", true)

    fun setForceNoWatermark(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("force_no_wm", value).apply()
    }

    // Activar guía del Tile (solo UI)
    fun getTileEnabled(context: Context): Boolean =
        prefs(context).getBoolean("tile_enabled", false)

    fun setTileEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("tile_enabled", value).apply()
    }

    // Tema: "system" | "light" | "dark"
    fun getThemeMode(context: Context): String =
        prefs(context).getString("theme_mode", "system") ?: "system"

    fun setThemeMode(context: Context, value: String) {
        prefs(context).edit().putString("theme_mode", value).apply()
    }

    // Ahorro de Datos (dummy)
    fun getDataSaver(context: Context): Boolean =
        prefs(context).getBoolean("data_saver", false)

    fun setDataSaver(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("data_saver", value).apply()
    }

    // Multiplataforma (beta)
    fun getMultiPlatformEnabled(context: Context): Boolean =
        prefs(context).getBoolean("mp_enabled", false)

    fun setMultiPlatformEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("mp_enabled", value).apply()
    }

    fun getFacebookEnabled(context: Context): Boolean =
        prefs(context).getBoolean("mp_fb", false)

    fun setFacebookEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("mp_fb", value).apply()
    }

    fun getInstagramEnabled(context: Context): Boolean =
        prefs(context).getBoolean("mp_ig", false)

    fun setInstagramEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("mp_ig", value).apply()
    }

    fun getYouTubeEnabled(context: Context): Boolean =
        prefs(context).getBoolean("mp_yt", false)

    fun setYouTubeEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("mp_yt", value).apply()
    }

    fun getXEnabled(context: Context): Boolean =
        prefs(context).getBoolean("mp_x", false)

    fun setXEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("mp_x", value).apply()
    }

    // DLOAD Audio HD (beta, deshabilitada)
    fun getDloadAudioHdEnabled(context: Context): Boolean =
        prefs(context).getBoolean("dload_audio_hd", false)

    fun setDloadAudioHdEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("dload_audio_hd", value).apply()
    }
}