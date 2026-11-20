package app.aure.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun PageDots(
    pageCount: Int,
    current: Int,
    scale: TvScale,
) {
    Box(
        Modifier
            .size(
                width = scale.dp(pageCount * 20f),
                height = scale.dp(10f),
            ),
    ) {
        for (index in 0 until pageCount) {
            Box(
                Modifier
                    .offset(x = scale.dp(index * 20f))
                    .size(scale.dp(8f))
                    .clip(CircleShape)
                    .background(
                        Color.White.copy(alpha = if (index == current) 0.85f else 0.30f),
                    ),
            )
        }
    }
}

// The panel's tiles enter with a frame-delayed layout; retry against the
// composition clock instead of guessing a delay in milliseconds.
internal suspend fun FocusRequester.requestFolderFocusWhenAttached(): Boolean {
    repeat(3) {
        if (runCatching { requestFocus() }.isSuccess) return true
        withFrameNanos { }
    }
    return false
}
