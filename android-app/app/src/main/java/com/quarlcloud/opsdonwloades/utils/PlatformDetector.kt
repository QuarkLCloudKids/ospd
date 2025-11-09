package com.quarlcloud.opsdonwloades.utils

import android.net.Uri

enum class Platform { TIKTOK, INSTAGRAM, YOUTUBE, FACEBOOK, UNKNOWN }

object PlatformDetector {
    fun detect(url: String?): Platform {
        if (url.isNullOrBlank()) return Platform.UNKNOWN
        val host = try { Uri.parse(normalize(url)).host?.lowercase() } catch (_: Exception) { null }
        host ?: return Platform.UNKNOWN
        return when {
            host.contains("tiktok") -> Platform.TIKTOK
            host.contains("instagram") || host.contains("ig.me") || host.contains("ddinstagram") -> Platform.INSTAGRAM
            host.contains("youtube") || host == "youtu.be" -> Platform.YOUTUBE
            host.contains("twitter") || host.contains("x.com") -> Platform.UNKNOWN
            host.contains("facebook") || host == "fb.watch" || host.contains("fbcdn") -> Platform.FACEBOOK
            else -> Platform.UNKNOWN
        }
    }

    fun normalize(url: String): String {
        var u = url.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "https://" + u.removePrefix("//")
        }
        return u
    }

    fun clean(url: String): String {
        val normalized = normalize(url)
        return try {
            val uri = Uri.parse(normalized)
            Uri.Builder()
                .scheme(uri.scheme ?: "https")
                .authority(uri.authority ?: return normalized)
                .path(uri.path)
                .query(uri.query)
                .fragment(null)
                .build()
                .toString()
        } catch (_: Exception) {
            normalized
        }
    }

    fun extractYouTubeId(url: String): String? {
        val u = normalize(url)
        return try {
            val uri = Uri.parse(u)
            // watch?v=ID
            val v = uri.getQueryParameter("v")
            if (!v.isNullOrBlank()) return v
            val p = uri.path ?: return null
            // youtu.be/ID
            if (uri.host?.equals("youtu.be", true) == true) {
                return p.trim('/').split('/').firstOrNull()
            }
            // shorts/ID
            if (p.contains("/shorts/")) {
                return p.substringAfter("/shorts/").substringBefore('/')
            }
            null
        } catch (_: Exception) { null }
    }
}