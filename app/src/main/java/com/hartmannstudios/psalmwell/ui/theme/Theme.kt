package com.hartmannstudios.psalmwell.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Deep = Color(0xFF0F4C5C)
val Lagoon = Color(0xFF2C7A7B)
val Amber = Color(0xFFE0A458)
val Sand = Color(0xFFF6EFE3)
val Ink = Color(0xFF1D2A2E)

private val Light = lightColorScheme(
    primary = Deep, onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE6E4), onPrimaryContainer = Color(0xFF062A33),
    secondary = Color(0xFF8A5A1F), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF7DDB8), onSecondaryContainer = Color(0xFF2E1A00),
    tertiary = Lagoon,
    background = Sand, onBackground = Ink,
    surface = Color(0xFFFFFBF5), onSurface = Ink,
    surfaceVariant = Color(0xFFE9E1D3), onSurfaceVariant = Color(0xFF4D5557),
    surfaceContainer = Color(0xFFF1E9DC),
    outline = Color(0xFF7D8587),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8FD0CC), onPrimary = Color(0xFF003734),
    primaryContainer = Color(0xFF0F4C5C), onPrimaryContainer = Color(0xFFCDE6E4),
    secondary = Amber, onSecondary = Color(0xFF3F2700),
    secondaryContainer = Color(0xFF5B3D10), onSecondaryContainer = Color(0xFFF7DDB8),
    tertiary = Color(0xFF7FC4C4),
    background = Color(0xFF0E1517), onBackground = Color(0xFFE3E7E6),
    surface = Color(0xFF141C1E), onSurface = Color(0xFFE3E7E6),
    surfaceVariant = Color(0xFF2B3437), onSurfaceVariant = Color(0xFFBFC8C9),
    surfaceContainer = Color(0xFF1B2426),
    outline = Color(0xFF899294),
)

val ScriptureFont = FontFamily.Serif

private val Type = Typography(
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
)

@Composable
fun PsalmwellTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, typography = Type, content = content)
}
