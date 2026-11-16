package com.dvdiso.sunbi.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dvdiso.sunbi.Prefs

/** Escolha de cor observável (mudar em Ajustes recompõe o app inteiro). */
object ThemeState {
    val dynamic = mutableStateOf(true)
    fun load() { dynamic.value = Prefs.dynamicColor }
}

// Paleta Girassol: tons derivados de #F5B301, usada no Quest e quando a pessoa desliga as cores do sistema.
private val SunLight = lightColorScheme(
    primary = Color(0xFF7A5900), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDEA1), onPrimaryContainer = Color(0xFF261900),
    secondary = Color(0xFF6B5D3F), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF5E0BB), onSecondaryContainer = Color(0xFF241A04),
    tertiary = Color(0xFF4A6547), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCCEBC4), onTertiaryContainer = Color(0xFF07200A),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F2), onBackground = Color(0xFF1F1B13),
    surface = Color(0xFFFFF8F2), onSurface = Color(0xFF1F1B13),
    surfaceVariant = Color(0xFFEDE1CF), onSurfaceVariant = Color(0xFF4D4639),
    outline = Color(0xFF7F7667), outlineVariant = Color(0xFFD0C5B4),
    inverseSurface = Color(0xFF353027), inverseOnSurface = Color(0xFFF9EFE2), inversePrimary = Color(0xFFF8BD2A),
    surfaceDim = Color(0xFFE2D9CC), surfaceBright = Color(0xFFFFF8F2),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFCF2E5),
    surfaceContainer = Color(0xFFF6ECDF), surfaceContainerHigh = Color(0xFFF1E7D9),
    surfaceContainerHighest = Color(0xFFEBE1D4),
)

private val SunDark = darkColorScheme(
    primary = Color(0xFFF8BD2A), onPrimary = Color(0xFF402D00),
    primaryContainer = Color(0xFF5C4300), onPrimaryContainer = Color(0xFFFFDEA1),
    secondary = Color(0xFFD8C4A0), onSecondary = Color(0xFF3A2F15),
    secondaryContainer = Color(0xFF52452A), onSecondaryContainer = Color(0xFFF5E0BB),
    tertiary = Color(0xFFB0CFA9), onTertiary = Color(0xFF1D361C),
    tertiaryContainer = Color(0xFF334D31), onTertiaryContainer = Color(0xFFCCEBC4),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF17130B), onBackground = Color(0xFFEBE1D4),
    surface = Color(0xFF17130B), onSurface = Color(0xFFEBE1D4),
    surfaceVariant = Color(0xFF4D4639), onSurfaceVariant = Color(0xFFD0C5B4),
    outline = Color(0xFF998F80), outlineVariant = Color(0xFF4D4639),
    inverseSurface = Color(0xFFEBE1D4), inverseOnSurface = Color(0xFF353027), inversePrimary = Color(0xFF7A5900),
    surfaceDim = Color(0xFF17130B), surfaceBright = Color(0xFF3E382F),
    surfaceContainerLowest = Color(0xFF110E07), surfaceContainerLow = Color(0xFF1F1B13),
    surfaceContainer = Color(0xFF231F17), surfaceContainerHigh = Color(0xFF2E2921),
    surfaceContainerHighest = Color(0xFF393429),
)

// Cantos mais arredondados, no espírito do Expressive.
private val SunShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/** Cores para a escala de humor: azul para baixo, âmbar para cima (legível nos dois temas). */
object MoodColors {
    fun down(cs: ColorScheme, dark: Boolean) = if (dark) Color(0xFF8AB4F8) else Color(0xFF3D6FD6)
    fun up(cs: ColorScheme, dark: Boolean) = if (dark) Color(0xFFFFB866) else Color(0xFFC26A00)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SunBiTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val dynamic = ThemeState.dynamic.value && Build.VERSION.SDK_INT >= 31
    val scheme = when {
        dynamic && dark -> dynamicDarkColorScheme(ctx)
        dynamic -> dynamicLightColorScheme(ctx)
        dark -> SunDark
        else -> SunLight
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        shapes = SunShapes,
        content = content,
    )
}
