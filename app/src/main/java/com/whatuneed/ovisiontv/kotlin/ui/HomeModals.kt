package com.whatuneed.ovisiontv.kotlin.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryItem
import com.whatuneed.ovisiontv.kotlin.ui.components.LaunchOverlay
import com.whatuneed.ovisiontv.kotlin.ui.components.OnlineInfoOverlay
import com.whatuneed.ovisiontv.kotlin.ui.components.TrailerOverlay

/**
 * Full-screen content modals that sit above Home: trailer preview, online
 * information card and the launch zoom. They draw last so the zoom also covers
 * the folder panel it starts from (T5).
 */
@Composable
internal fun HomeContentModals(
    playingTrailer: PlayingTrailer?,
    onlineInfo: DiscoveryItem?,
    onlineInfoTarget: DiscoveryItem?,
    launching: Launching?,
    launchProgress: Animatable<Float, AnimationVector1D>,
    scale: TvScale,
    onOpenTrailerTarget: (DiscoveryItem) -> Unit,
    onDismissTrailer: () -> Unit,
    onOpenOnlineTarget: (DiscoveryItem) -> Unit,
    onDismissOnlineInfo: () -> Unit,
) {
    playingTrailer?.let { trailer ->
        val actionLabel = trailer.target?.let {
            stringResource(R.string.trailer_open_in, trailer.appLabel ?: it.packageName)
        }
        TrailerOverlay(
            title = trailer.title,
            url = trailer.url,
            synopsis = trailer.synopsis,
            actionLabel = actionLabel,
            onAction = trailer.target?.let { target ->
                {
                    // The trailer is only a preview step. The second OK
                    // finally opens the provider row in its own app.
                    onOpenTrailerTarget(target)
                }
            },
            onDismiss = onDismissTrailer,
        )
    }

    onlineInfo?.let { card ->
        val target = onlineInfoTarget
        val actionLabel = target?.let {
            stringResource(R.string.trailer_open_in, it.packageLabel ?: it.packageName)
        }
        OnlineInfoOverlay(
            item = card,
            actionLabel = actionLabel,
            onAction = target?.let { provider ->
                {
                    onOpenOnlineTarget(provider)
                }
            },
            onDismiss = onDismissOnlineInfo,
        )
    }

    launching?.let { active ->
        LaunchOverlay(
            app = active.app,
            cellCenter = active.cellCenter,
            progress = { launchProgress.value },
            scale = scale,
        )
    }
}
