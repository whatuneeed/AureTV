package com.whatuneed.ovisiontv.kotlin.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.data.TvApp
import com.whatuneed.ovisiontv.kotlin.ui.TvScale
import com.whatuneed.ovisiontv.kotlin.ui.TvType

@Composable
fun AppCard(
    app: TvApp,
    focused: Boolean,
    scale: TvScale,
    modifier: Modifier = Modifier,
    editing: Boolean = false,
    pickedUp: Boolean = false,
    confirming: Boolean = false,
    semanticsEnabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    onActivate: (() -> Unit)? = null,
) {
    CardShell(
        focused = focused,
        scale = scale,
        label = app.label,
        modifier = modifier,
        editing = editing,
        pickedUp = pickedUp,
        confirming = confirming,
        semanticsEnabled = semanticsEnabled,
        focusRequester = focusRequester,
        onActivate = onActivate,
    ) {
        AppArtwork(app = app, scale = scale)
        if (!app.available) {
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
            )
            Text(
                text = stringResource(R.string.app_unavailable),
                color = Color.White.copy(alpha = 0.9f),
                fontWeight = FontWeight.SemiBold,
                fontSize = scale.sp(TvType.caption),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = scale.dp(10f))
                    .background(
                        Color.Black.copy(alpha = 0.65f),
                        RoundedCornerShape(scale.dp(8f)),
                    )
                    .padding(horizontal = scale.dp(10f), vertical = scale.dp(4f)),
            )
        }
    }
}

/** Banner, icon or initial — shared so the launch zoom starts on the same pixels. */
@Composable
internal fun BoxScope.AppArtwork(app: TvApp, scale: TvScale) {
    val banner = rememberArt(app.banner)
    val icon = rememberArt(app.icon)
    when {
        banner != null -> Image(
            bitmap = banner,
            // The card's merged semantics owns the announcement; the pixels here
            // are decorative or duplicated by the launch overlay.
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.Medium,
        )

        icon != null -> Image(
            bitmap = icon,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.38f)
                .aspectRatio(1f),
            contentScale = ContentScale.Fit,
            // The icon is already rasterised at ~256 px and drawn near 1:1; the
            // extra filtering taps of High cost GPU time without visible gain.
            filterQuality = FilterQuality.Medium,
        )

        else -> Text(
            text = app.label.take(1).uppercase(),
            color = Color.White.copy(alpha = 0.85f),
            fontWeight = FontWeight.SemiBold,
            fontSize = scale.sp(TvType.hero),
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
