package com.quarlcloud.opsdonwloades.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    // Acento + blanco/negro
    primary = Accent,
    onPrimary = White,
    primaryContainer = Black,
    onPrimaryContainer = White,

    secondary = Accent,
    onSecondary = White,
    secondaryContainer = Black,
    onSecondaryContainer = White,

    tertiary = Accent,
    onTertiary = White,
    tertiaryContainer = Black,
    onTertiaryContainer = White,

    background = DarkBackground,
    onBackground = LightText,
    surface = DarkSurface,
    onSurface = LightText,

    surfaceVariant = Color(0xFF111111),
    onSurfaceVariant = LightText.copy(alpha = 0.8f),
    outline = OutlineDark,
    error = LightText,
    onError = DarkText,
    errorContainer = DarkSurface,
    onErrorContainer = LightText,
)

private val LightColorScheme = lightColorScheme(
    // Acento + blanco/negro
    primary = Accent,
    onPrimary = Black,
    primaryContainer = White,
    onPrimaryContainer = Black,

    secondary = Accent,
    onSecondary = Black,
    secondaryContainer = LightSurface,
    onSecondaryContainer = DarkText,

    tertiary = Accent,
    onTertiary = Black,
    tertiaryContainer = LightSurface,
    onTertiaryContainer = DarkText,

    background = LightBackground,
    onBackground = DarkText,
    surface = LightSurface,
    onSurface = DarkText,

    surfaceVariant = Color(0xFFF2F2F2),
    onSurfaceVariant = DarkText.copy(alpha = 0.7f),
    outline = OutlineLight,
    error = DarkText,
    onError = LightText,
    errorContainer = LightSurface,
    onErrorContainer = DarkText,
)

@Composable
fun OpSDonwloadesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            // Light status bar (iconos oscuros) solo cuando el fondo es claro
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}