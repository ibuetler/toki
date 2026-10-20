package com.hackinglab.toki.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF126C5D), onPrimary = Color.White,
    primaryContainer = Color(0xFFD9F3E8), onPrimaryContainer = Color(0xFF123B33),
    secondary = Color(0xFF526860), secondaryContainer = Color(0xFFE5EEE8),
    background = Color(0xFFF5F6F2), surface = Color(0xFFFEFFFB),
    onBackground = Color(0xFF182D29), onSurface = Color(0xFF182D29),
    onSurfaceVariant = Color(0xFF596A63), outlineVariant = Color(0xFFDCE5DE)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF91DFC2), onPrimary = Color(0xFF073B30),
    primaryContainer = Color(0xFF204D40), onPrimaryContainer = Color(0xFFB8F2D8),
    secondary = Color(0xFFB2C8BC), secondaryContainer = Color(0xFF2D4038),
    background = Color(0xFF101B18), surface = Color(0xFF192722),
    onBackground = Color(0xFFE5F0E8), onSurface = Color(0xFFE5F0E8),
    onSurfaceVariant = Color(0xFFB4C5BB), outlineVariant = Color(0xFF34473E)
)
@Composable
fun TOKITheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp)),
        typography = Typography, content = content)
}
