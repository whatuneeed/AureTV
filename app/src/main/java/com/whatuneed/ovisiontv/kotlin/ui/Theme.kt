package com.whatuneed.ovisiontv.kotlin.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object LauncherColors {
    val backgroundTop = Color(0xFF101014)
    val backgroundBottom = Color(0xFF0E0E11)
    val scrim = Color(0xFF0E0E11)
    val textPrimary = Color.White
    val textSecondary = Color.White.copy(alpha = 0.5f)
    val cardFallbackTop = Color(0xFF1B1B21)
    val cardFallbackBottom = Color(0xFF26262D)
    // Destructive actions (uninstall, reset) stay one tint across surfaces.
    val destructive = Color(0xFFFF6B5E)
    // Confirmation marks (wizard summary rows). Pressed is deeper so a focused
    // row does not glare.
    val success = Color(0xFF30D158)
    val successPressed = Color(0xFF1E9E48)
}

@Composable
fun OVisionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color.White,
            onPrimary = Color.Black,
            background = LauncherColors.backgroundBottom,
            onBackground = Color.White,
            surface = LauncherColors.backgroundTop,
            onSurface = Color.White,
        ),
        content = content,
    )
}
