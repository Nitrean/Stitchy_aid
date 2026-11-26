package io.github.nitrean.stitchyaid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// High-contrast colours: every text/background pair is at least 7:1 (WCAG AAA).
private val Light = lightColorScheme(
    primary = Color(0xFF0B4F8A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E6F7),
    onPrimaryContainer = Color(0xFF00213F),
    secondary = Color(0xFF8A3B0B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE3D1),
    onSecondaryContainer = Color(0xFF3A1300),
    background = Color(0xFFFFFBF2),
    onBackground = Color(0xFF151515),
    surface = Color(0xFFFFFBF2),
    onSurface = Color(0xFF151515),
    surfaceVariant = Color(0xFFEDE7DA),
    onSurfaceVariant = Color(0xFF2B2B2B),
    outline = Color(0xFF3D3D3D),
    error = Color(0xFFA0001C),
    onError = Color.White,
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA9D1FF),
    onPrimary = Color(0xFF00213F),
    primaryContainer = Color(0xFF0B4F8A),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFFFC09A),
    onSecondary = Color(0xFF3A1300),
    secondaryContainer = Color(0xFF6B2C05),
    onSecondaryContainer = Color.White,
    background = Color(0xFF111111),
    onBackground = Color(0xFFF4F4F4),
    surface = Color(0xFF111111),
    onSurface = Color(0xFFF4F4F4),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFE6E6E6),
    outline = Color(0xFFCFCFCF),
    error = Color(0xFFFFB3B8),
    onError = Color(0xFF5C0010),
)

// Large type everywhere. These are sp, so the tablet's own font size setting makes them bigger still.
private val BigType = Typography(
    displaySmall = TextStyle(fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 22.sp, lineHeight = 32.sp),
    bodyMedium = TextStyle(fontSize = 20.sp, lineHeight = 28.sp),
    bodySmall = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    labelLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
)

/** Sizes that keep everything easy to see and easy to hit with a finger. */
object Sizes {
    val TouchTarget = 64.dp
    val BigButtonHeight = 76.dp
    val Gap = 16.dp
    val ScreenPadding = 24.dp
    val Icon = 32.dp
}

@Composable
fun StitchyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = BigType,
        content = content,
    )
}
