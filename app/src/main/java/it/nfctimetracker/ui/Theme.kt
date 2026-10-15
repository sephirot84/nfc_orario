package it.nfctimetracker.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

val Teal = Color(0xFF0C8276)
val Ink = Color(0xFF17293B)
val Muted = Color(0xFF607181)
val Canvas = Color(0xFFF4F6F9)
val Amber = Color(0xFF9E5C12)
@Composable fun TrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Teal, onPrimary = Color.White, primaryContainer = Color(0xFFDCF3EB),
        secondary = Ink, background = Canvas, surface = Color.White, onSurface = Ink, onBackground = Ink,
        surfaceVariant = Color(0xFFEBEFF4), outline = Color(0xFFB9C5CF)),
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp)), content = content)
}
