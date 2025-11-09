package com.quarlcloud.opsdonwloades.utils

import android.content.Context
import android.content.SharedPreferences

object TikTokAutomation {
    private const val PREFS = "tiktok_automation_prefs"
    private const val KEY_AUTO_COPY = "auto_copy_requested"
    private const val KEY_TIMESTAMP = "auto_copy_ts"

    fun requestAutoCopy(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit().putBoolean(KEY_AUTO_COPY, true).putLong(KEY_TIMESTAMP, System.currentTimeMillis()).apply()
    }

    fun shouldAutoCopy(context: Context): Boolean {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val requested = sp.getBoolean(KEY_AUTO_COPY, false)
        val ts = sp.getLong(KEY_TIMESTAMP, 0L)
        // Válido por 25 segundos desde la solicitud
        return requested && (System.currentTimeMillis() - ts) < 25_000
    }

    fun clearAutoCopy(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit().putBoolean(KEY_AUTO_COPY, false).apply()
    }
}