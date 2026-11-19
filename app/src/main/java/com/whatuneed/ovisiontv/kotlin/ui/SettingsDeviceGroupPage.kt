package com.whatuneed.ovisiontv.kotlin.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.data.DeviceFacts
import com.whatuneed.ovisiontv.kotlin.data.KidsMode
import com.whatuneed.ovisiontv.kotlin.update.UpdateStateStore

/**
 * Device group page. Maintenance rows stay at Device level; Kids Mode hides
 * Update but keeps Backup, exactly as before the extraction.
 */
@Composable
internal fun settingsDeviceGroupRows(
    factsReady: Boolean,
    networkText: String?,
    facts: DeviceFacts?,
    freeSpace: String?,
    permissionsSummary: String,
    context: Context,
    resumeTick: Int,
    onOpen: (Int) -> Unit,
    onUpdate: () -> Unit,
    onBackup: () -> Unit,
): List<SettingsRow> {
    val kidsActive = remember(context) { KidsMode.isKids(context) }
    val updateSummary = remember(context, resumeTick) {
        UpdateStateStore(context).summary()
    }
    return deviceGroupRows(
        factsReady = factsReady,
        network = if (factsReady) networkText else stringResource(R.string.settings_loading),
        model = if (factsReady) facts?.model else null,
        freeSpace = freeSpace,
        permissions = permissionsSummary,
        updateValue = if (kidsActive) {
            null
        } else {
            updateSummary?.let {
                stringResource(R.string.settings_software_update_available)
            } ?: stringResource(R.string.settings_software_update_manual)
        },
        onUpdate = if (kidsActive) null else onUpdate,
        onBackup = onBackup,
        onOpen = onOpen,
    )
}
