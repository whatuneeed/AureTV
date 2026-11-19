package com.whatuneed.ovisiontv.kotlin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.data.TvApp
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryItem
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryReason
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoverySection
import com.whatuneed.ovisiontv.kotlin.ui.TvLeading
import com.whatuneed.ovisiontv.kotlin.ui.TvOs
import com.whatuneed.ovisiontv.kotlin.ui.TvScale
import com.whatuneed.ovisiontv.kotlin.ui.TvSpace
import com.whatuneed.ovisiontv.kotlin.ui.TvType

private const val INFO_BOTTOM = 340f

@Composable
internal fun EmptyHome(scale: TvScale, listingsDenied: Boolean) {
    Column(
        Modifier
            .padding(start = scale.dp(TvOs.SAFE_AREA), top = scale.dp(300f))
            .width(scale.dp(1000f)),
    ) {
        Text(
            text = stringResource(R.string.home_empty_title),
            color = Color.White,
            fontSize = scale.sp(TvType.largeTitle),
            lineHeight = scale.sp(64f),
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(scale.dp(18f)))
        Text(
            text = stringResource(R.string.home_empty_body),
            color = Color.White.copy(alpha = 0.62f),
            fontSize = scale.sp(TvType.subhead),
            lineHeight = scale.sp(42f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (listingsDenied) {
            Spacer(Modifier.height(scale.dp(18f)))
            Text(
                text = stringResource(R.string.shelf_listings_denied),
                color = Color.White.copy(alpha = 0.62f),
                fontSize = scale.sp(TvType.body),
                lineHeight = scale.sp(TvType.body * 1.3f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun TopShelfHeading(
    app: TvApp?,
    selected: DiscoveryItem?,
    scale: TvScale,
    top: Float,
    listingsDenied: Boolean = false,
) {
    // tvOS captions the shelf with the selected content, not with the app
    // category. The old "APPLICATION" line was product metadata leaking into
    // the most prominent corner of Home.
    val title = selected?.title?.takeIf { it.isNotBlank() }
        ?: app?.label
        ?: selected?.packageLabel
        ?: stringResource(R.string.shelf_context)
    Column(
        Modifier
            .padding(start = scale.dp(TvOs.SAFE_AREA), top = scale.dp(top))
            .width(scale.dp(900f)),
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = scale.sp(TvType.hero),
            lineHeight = scale.sp(78f),
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                shadow = Shadow(color = Color.Black.copy(alpha = 0.55f), blurRadius = 30f),
            ),
        )
        if (listingsDenied) {
            Spacer(Modifier.height(scale.dp(12f)))
            Text(
                text = stringResource(R.string.shelf_listings_denied),
                color = Color.White.copy(alpha = 0.66f),
                fontSize = scale.sp(TvType.callout),
                lineHeight = scale.sp(TvType.callout * 1.3f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun sectionLabel(section: DiscoverySection, appLabel: String?): String = when (section) {
    DiscoverySection.CONTINUE -> stringResource(R.string.discovery_section_continue)
    DiscoverySection.ONLINE -> stringResource(R.string.discovery_section_online)
    DiscoverySection.FOR_YOU -> stringResource(R.string.discovery_section_for_you)
    DiscoverySection.FROM_APP -> if (appLabel.isNullOrBlank()) {
        stringResource(R.string.discovery_section_from_current)
    } else {
        stringResource(R.string.discovery_section_from_app, appLabel)
    }
    DiscoverySection.RECENT -> stringResource(R.string.discovery_section_recent)
    DiscoverySection.FAVORITES -> stringResource(R.string.discovery_section_favorites)
}

@Composable
internal fun RecommendationInfo(card: DiscoveryItem, scale: TvScale) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = scale.dp(TvOs.SAFE_AREA),
                    bottom = scale.dp(INFO_BOTTOM),
                )
                .width(scale.dp(900f)),
            verticalArrangement = Arrangement.Bottom,
        ) {
            card.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                Spacer(Modifier.height(scale.dp(6f)))
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = scale.sp(TvType.callout),
                    lineHeight = scale.sp(TvLeading.infoSubtitle),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            card.synopsis?.takeIf { it.isNotBlank() }?.let { synopsis ->
                Spacer(Modifier.height(scale.dp(6f)))
                Text(
                    text = synopsis,
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = scale.sp(TvType.caption),
                    lineHeight = scale.sp(TvType.caption * 1.3f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            card.remainingMinutes?.let { minutes ->
                Spacer(Modifier.height(scale.dp(4f)))
                Text(
                    text = stringResource(R.string.watch_next_minutes_left, minutes),
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = scale.sp(TvType.caption),
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(scale.dp(8f)))
            Text(
                text = reasonLabel(card.reason),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = scale.sp(TvType.caption),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val progress = card.progress
            if (progress != null && progress > 0f) {
                Spacer(Modifier.height(scale.dp(TvSpace.md)))
                Box(
                    Modifier
                        .width(scale.dp(360f))
                        .height(scale.dp(4f))
                        .background(Color.White.copy(alpha = 0.28f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(scale.dp(4f))
                            .background(Color.White.copy(alpha = 0.9f)),
                    )
                }
            }
        }
    }
}

@Composable
internal fun reasonLabel(reason: DiscoveryReason): String = when (reason) {
    DiscoveryReason.CONTINUE_WATCHING -> stringResource(R.string.discovery_reason_continue)
    DiscoveryReason.RECENTLY_LAUNCHED -> stringResource(R.string.discovery_reason_recent)
    DiscoveryReason.FAVORITE -> stringResource(R.string.discovery_reason_favorite)
    DiscoveryReason.NEW_FROM_APP -> stringResource(R.string.discovery_reason_new)
    DiscoveryReason.OFTEN_USED -> stringResource(R.string.discovery_reason_often)
    DiscoveryReason.FROM_CURRENT_APP -> stringResource(R.string.discovery_reason_from_app)
    DiscoveryReason.TRENDING -> stringResource(R.string.discovery_reason_trending)
    DiscoveryReason.DIVERSE_PICK -> stringResource(R.string.discovery_reason_diverse)
}

// ---------------------------------------------------------------------------
// Artwork. Two decode sizes and two cache keys per item: the rail card is not
// allowed to pin the full-screen hero (and the hero is not downsampled to a
// card). Everything is bounds-first and never upscaled.
// ---------------------------------------------------------------------------

internal enum class ArtRole { CARD, HERO }

