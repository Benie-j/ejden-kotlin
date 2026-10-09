package com.ejden.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class ThemeMode { Dark, Light }

class EjdenColors(
    val primary: Color, val primaryDark: Color, val primarySoft: Color,
    val background: Color, val surface: Color,
    val text: Color, val textSecondary: Color, val textLight: Color,
    val border: Color,
    val success: Color, val successSoft: Color,
    val warning: Color, val warningSoft: Color,
    val error: Color, val errorSoft: Color,
)

val LightColors = EjdenColors(
    primary = Color(0xFF176B87), primaryDark = Color(0xFF12566C), primarySoft = Color(0xFFEAF4F7),
    background = Color(0xFFF5F7F8), surface = Color(0xFFFFFFFF),
    text = Color(0xFF172126), textSecondary = Color(0xFF68777D), textLight = Color(0xFF8D9A9F),
    border = Color(0xFFE2E8EA),
    success = Color(0xFF168A5B), successSoft = Color(0xFFE9F6F0),
    warning = Color(0xFFD98A16), warningSoft = Color(0xFFFFF5E5),
    error = Color(0xFFD64545), errorSoft = Color(0xFFFCECEC),
)

val DarkColors = EjdenColors(
    primary = Color(0xFF176B87), primaryDark = Color(0xFF12566C), primarySoft = Color(0xFF183A46),
    background = Color(0xFF10181C), surface = Color(0xFF172126),
    text = Color(0xFFF0F4F5), textSecondary = Color(0xFF9BA9AE), textLight = Color(0xFF748288),
    border = Color(0xFF29373D),
    success = Color(0xFF168A5B), successSoft = Color(0xFF15372B),
    warning = Color(0xFFD98A16), warningSoft = Color(0xFF3B2C17),
    error = Color(0xFFD64545), errorSoft = Color(0xFF3B2020),
)

object Radius {
    val sm = RoundedCornerShape(9.dp)
    val md = RoundedCornerShape(13.dp)
    val lg = RoundedCornerShape(17.dp)
}

val LocalEjden = staticCompositionLocalOf { DarkColors }

object Ejden {
    val colors: EjdenColors
        @Composable @ReadOnlyComposable get() = LocalEjden.current
}

@Composable
fun EjdenTheme(mode: ThemeMode = ThemeMode.Dark, content: @Composable () -> Unit) {
    val c = if (mode == ThemeMode.Dark) DarkColors else LightColors
    val scheme = if (mode == ThemeMode.Dark) {
        darkColorScheme(primary = c.primary, background = c.background, surface = c.surface,
            onBackground = c.text, onSurface = c.text, error = c.error, outline = c.border)
    } else {
        lightColorScheme(primary = c.primary, background = c.background, surface = c.surface,
            onBackground = c.text, onSurface = c.text, error = c.error, outline = c.border)
    }
    CompositionLocalProvider(LocalEjden provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
