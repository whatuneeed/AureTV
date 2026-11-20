package app.aure.tv.ui

import android.animation.ValueAnimator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/**
 * The system Reduce Motion switch, available to every component that animates
 * focus. Surfaces that receive the flag explicitly still use motionDuration;
 * leaf controls read this so a new card cannot silently ignore the setting.
 */
val LocalReduceMotion = compositionLocalOf { false }

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
    val reduceMotion = remember { !ValueAnimator.areAnimatorsEnabled() }
    CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
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
}
