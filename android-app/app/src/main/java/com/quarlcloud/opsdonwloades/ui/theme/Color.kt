package com.quarlcloud.opsdonwloades.ui.theme

import androidx.compose.ui.graphics.Color

// Paleta minimalista: blanco, negro y un acento
val Black = Color(0xFF000000)
val White = Color(0xFFFFFFFF)

// Color acento (eléctrico) para resaltar acciones y elementos activos
val Accent = Color(0xFF00D4FF)

// Superficies según tema
val DarkBackground = Black
val DarkSurface = Color(0xFF0F0F10) // negro profundo para superficies
val LightBackground = White
val LightSurface = Color(0xFFF9F9F9) // blanco suave para superficies

// Texto según tema
val LightText = White
val DarkText = Black

// Tonos utilitarios derivados (mantienen restricción usando blanco/negro con alfa)
val OutlineLight = Black.copy(alpha = 0.12f)
val OutlineDark = White.copy(alpha = 0.12f)