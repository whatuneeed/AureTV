package com.whatuneed.ovisiontv.kotlin.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whatuneed.ovisiontv.kotlin.data.SaverTheme
import com.whatuneed.ovisiontv.kotlin.ui.LauncherColors
import com.whatuneed.ovisiontv.kotlin.ui.TvInk
import com.whatuneed.ovisiontv.kotlin.ui.TvScale
import com.whatuneed.ovisiontv.kotlin.ui.TvType

/**
 * The caption is presentation only: the caller owns the item, the language and
 * the fade window, so the saver's timeline stays in one place.
 */
@Composable
internal fun BoxScope.SaverCaption(
    title: String,
    theme: SaverTheme,
    alpha: Float,
    scale: TvScale,
) {
    Column(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(start = scale.dp(72f), bottom = scale.dp(64f))
            .graphicsLayer { this.alpha = alpha },
    ) {
        Text(
            text = title,
            color = LauncherColors.textPrimary,
            fontSize = scale.sp(TvType.subhead),
            fontWeight = FontWeight.SemiBold,
            style = TextStyle(
                shadow = Shadow(TvInk.shadow, Offset(0f, 2f), 12f),
            ),
        )
        Text(
            text = saverThemeLabel(theme),
            color = LauncherColors.textSecondary,
            fontSize = scale.sp(TvType.callout),
            style = TextStyle(
                shadow = Shadow(TvInk.shadow, Offset(0f, 2f), 10f),
            ),
        )
    }
}
