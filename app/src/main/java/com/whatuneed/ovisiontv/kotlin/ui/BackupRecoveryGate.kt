package com.whatuneed.ovisiontv.kotlin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.flow.StateFlow
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.backup.BackupRecovery

/**
 * Holds a screen back only while an interrupted restore is being rolled back.
 * With no journal the state is already true, so normal startup renders exactly
 * as before.
 */
@Composable
internal fun BackupRecoveryGate(
    ready: StateFlow<Boolean> = BackupRecovery.isReady,
    content: @Composable () -> Unit,
) {
    val recovered by ready.collectAsState()
    if (recovered) content() else BackupRecoverySplash()
}

@Composable
internal fun BackupRecoverySplash() {
    val scale = rememberTvScale()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LauncherColors.backgroundTop),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.backup_recovery_wait),
            color = Color.White.copy(alpha = 0.82f),
            fontSize = scale.sp(TvType.title),
            textAlign = TextAlign.Center,
        )
    }
}
