package com.dvdiso.sunflowerclocks.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

object Sun {
    val bg = Color(0xFF17130F)
    val panel = Color(0xFF221C16)
    val line = Color(0xFF3A2F24)
    val ink = Color(0xFFF3E6CF)
    val dim = Color(0xFFA8977C)
    val sun = Color(0xFFF5B301)
    val sun2 = Color(0xFFFFD54A)
    val leaf = Color(0xFF7FB34A)
    val off = Color(0xFF6B5A46)
    val dark = Color(0xFF1A1208)
}

private val Mono = FontFamily.Monospace

@Composable
fun SunflowerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Sun.sun,
            onPrimary = Sun.dark,
            secondary = Sun.leaf,
            background = Sun.bg,
            onBackground = Sun.ink,
            surface = Sun.panel,
            onSurface = Sun.ink,
            surfaceVariant = Sun.panel,
            onSurfaceVariant = Sun.dim,
            outline = Sun.line,
        ),
        typography = Typography(
            bodyLarge = TextStyle(fontFamily = Mono, fontSize = 17.sp),
            bodyMedium = TextStyle(fontFamily = Mono, fontSize = 15.sp),
            bodySmall = TextStyle(fontFamily = Mono, fontSize = 13.sp),
            titleLarge = TextStyle(fontFamily = Mono, fontSize = 22.sp),
            titleMedium = TextStyle(fontFamily = Mono, fontSize = 17.sp),
            labelLarge = TextStyle(fontFamily = Mono, fontSize = 15.sp),
            labelMedium = TextStyle(fontFamily = Mono, fontSize = 13.sp),
        ),
        content = content,
    )
}
