package com.aifigurepaint.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val StudioNavy = Color(0xFF24272D)
internal val StudioNavySoft = Color(0xFF4F535B)
internal val StudioTeal = Color(0xFFC85F76)
internal val StudioMint = Color(0xFFFBE7EC)
internal val StudioGold = Color(0xFFD7A15E)
internal val StudioIvory = Color(0xFFF7F5F2)
internal val StudioBorder = Color(0xFFDEDCD8)

private val StudioColors = lightColorScheme(
    primary = StudioTeal,
    onPrimary = Color.White,
    primaryContainer = StudioMint,
    onPrimaryContainer = StudioNavy,
    secondary = Color(0xFF657184),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEFF1F4),
    onSecondaryContainer = StudioNavy,
    tertiary = StudioGold,
    onTertiary = StudioNavy,
    tertiaryContainer = Color(0xFFF8F0E5),
    onTertiaryContainer = StudioNavy,
    background = StudioIvory,
    onBackground = StudioNavy,
    surface = Color(0xFFFFFFFF),
    onSurface = StudioNavy,
    surfaceVariant = Color(0xFFF2F1EF),
    onSurfaceVariant = Color(0xFF6E7076),
    outline = StudioBorder,
    outlineVariant = Color(0xFFF0ECE9),
    error = Color(0xFFB3261E),
)

private val StudioTypography = Typography(
    headlineLarge = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontSize = 11.5.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 12.5.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 11.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 10.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
)

private val StudioShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
)

@Composable
fun AIFigurePaintTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StudioColors,
        typography = StudioTypography,
        shapes = StudioShapes,
        content = content,
    )
}
