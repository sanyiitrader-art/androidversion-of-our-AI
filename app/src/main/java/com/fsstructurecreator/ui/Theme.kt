package com.fsstructurecreator.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val CharcoalBg = Color(0xFF161817)
val CharcoalElevated = Color(0xFF1E2120)
val CharcoalHover = Color(0xFF262A28)
val CharcoalInput = Color(0xFF1C1F1E)
val CharcoalBorder = Color(0xFF2B2F2D)

val Mint = Color(0xFF7EE8C0)
val MintDim = Color(0xFF5FC79F)
val MintSoft = Color(0x1F7EE8C0)

val TextPrimary = Color(0xFFECEFED)
val TextSecondary = Color(0xFF9AA39D)
val TextTertiary = Color(0xFF6B7570)

val DangerColor = Color(0xFFE0776D)

private val AppColorScheme = darkColorScheme(
    background = CharcoalBg,
    surface = CharcoalElevated,
    primary = Mint,
    onPrimary = CharcoalBg,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = DangerColor
)

private val AppTypography = Typography(
    bodyLarge = TextStyle(fontSize = 14.sp, color = TextPrimary),
    bodyMedium = TextStyle(fontSize = 13.sp, color = TextPrimary),
    bodySmall = TextStyle(fontSize = 12.sp, color = TextSecondary),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
)

private val AppShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
)

@Composable
fun FsStructureCreatorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}