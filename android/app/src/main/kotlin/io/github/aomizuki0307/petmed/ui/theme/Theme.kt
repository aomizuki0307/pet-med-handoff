package io.github.aomizuki0307.petmed.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// LPと同じ「あたたかい紙面×柿色×深緑」
val Persimmon = Color(0xFFD95E32)
val PersimmonDeep = Color(0xFFB94A22)
val Pine = Color(0xFF3E6B4F)
val PineSoft = Color(0xFFE4EDE6)
val Paper = Color(0xFFFAF5EC)
val PaperDeep = Color(0xFFF3EADA)
val Ink = Color(0xFF33291F)
val InkSoft = Color(0xFF6B5D4D)
val Amber = Color(0xFFF2B441)
val WarnBg = Color(0xFFFBEFE3)

private val LightColors = lightColorScheme(
    primary = Persimmon,
    onPrimary = Color.White,
    primaryContainer = WarnBg,
    onPrimaryContainer = PersimmonDeep,
    secondary = Pine,
    onSecondary = Color.White,
    secondaryContainer = PineSoft,
    onSecondaryContainer = Pine,
    tertiary = Amber,
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = PaperDeep,
    onSurfaceVariant = InkSoft,
    error = Color(0xFFB3261E),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

@Composable
fun PetMedTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        shapes = AppShapes,
        content = content,
    )
}
