package it.nfctimetracker.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

val Teal = Color(0xFF6ADCC0)
val Foreground = Color(0xFFE6EEF7)
val Muted = Color(0xFFA5B4C4)
val Canvas = Color(0xFF0D141D)
val Amber = Color(0xFFF0BF78)
val ExitBlue = Color(0xFF98B9FF)

@Composable fun TrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Teal,
        onPrimary = Color(0xFF00382F),
        primaryContainer = Color(0xFF124D43),
        onPrimaryContainer = Color(0xFFA0F2DA),
        inversePrimary = Color(0xFF006B5A),
        secondary = Color(0xFFB6C9DF),
        onSecondary = Color(0xFF1A2B40),
        secondaryContainer = Color(0xFF20364D),
        onSecondaryContainer = Color(0xFFD7E7F8),
        background = Canvas,
        onBackground = Foreground,
        surface = Color(0xFF16212E),
        onSurface = Foreground,
        surfaceVariant = Color(0xFF233244),
        onSurfaceVariant = Muted,
        surfaceTint = Teal,
        surfaceContainerLowest = Color(0xFF080E15),
        surfaceContainerLow = Color(0xFF111B26),
        surfaceContainer = Color(0xFF192737),
        surfaceContainerHigh = Color(0xFF243449),
        surfaceContainerHighest = Color(0xFF2B3C50),
        inverseSurface = Foreground,
        inverseOnSurface = Canvas,
        outline = Color(0xFF8495A7),
        outlineVariant = Color(0xFF35465A),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
    ),
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp)), content = content)
}
