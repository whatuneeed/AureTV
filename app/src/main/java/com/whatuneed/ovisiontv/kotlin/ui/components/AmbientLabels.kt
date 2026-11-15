package com.whatuneed.ovisiontv.kotlin.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.ambient.AmbientMode

@Composable
internal fun ambientModeLabel(mode: AmbientMode): String = stringResource(
    when (mode) {
        AmbientMode.AERIAL -> R.string.saver_mode_aerial
        AmbientMode.CLOCK -> R.string.saver_mode_clock
        AmbientMode.MINIMAL -> R.string.saver_mode_minimal
    },
)
