package com.whatuneed.ovisiontv.kotlin.ui

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import com.whatuneed.ovisiontv.kotlin.data.HomeItem
import com.whatuneed.ovisiontv.kotlin.data.TvApp
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryItem

// tvOS does not expose the long-press delay; Android reports a hold as ~500 ms
// with releases that can land near 430 ms, so 350 ms is a safe project value.
internal const val HOME_TAG = "OVisionHome"
internal const val MENU_HOLD_MS = 350L

internal data class PlayingTrailer(
    val title: String,
    val url: String,
    /** The provider row whose app should open after the preview. */
    val target: DiscoveryItem? = null,
    val appLabel: String? = null,
    val synopsis: String? = null,
)

// Phase 08: only a resting cursor is remembered; a 400 ms pause reads as an
// intentional stop and is shorter than the time it takes to leave the room.
internal const val FOCUS_SAVE_MS = 400L

// 14_MOTION_DESIGN §2.3: folder creation needs 0.75 s of hover, then a 0.3 s
// confirmation (M8), after which the two apps become a folder.
internal const val FOLDER_HOLD_MS = 750L
internal const val FOLDER_CONFIRM_MS = 300L

// 05_FOLDERS §5: opening and closing both take 0.5 s on the same curve.
internal const val FOLDER_ZOOM_MS = 500

// 14_MOTION_DESIGN M18: the tvOS parade advances every 10 s. A manual step
// restarts the clock, as the Java hero did.
internal const val SHELF_ADVANCE_MS = 10_000L
// A hero must stay focused briefly before it counts as seen; otherwise a fast
// pass through a rail writes prefs for every card that never really showed.
internal const val SHOWN_SETTLE_MS = 500L

// 02_HOME_SCREEN §3: in the app library the first row starts near the safe area,
// not at the dock band. Both values are project decisions; the RESEARCH gives
// the dock geometry and row pitch, not a separate library screen.
internal const val LIBRARY_TOP = 150f
internal const val LIBRARY_SHIFT = TvOs.ROW_TOP - LIBRARY_TOP
internal const val LIBRARY_MOTION_MS = 300
internal const val LIBRARY_FADE_MS = 220

// Reordering is not measured for tvOS; 200 ms is the project value, aligned with
// the shared focus rhythm so the neighbour does not jump after the drop.
internal const val TILE_MOVE_MS = 200

internal data class OpenFolder(
    val folder: HomeItem.FolderItem,
    val tileX: Float,
    val tileY: Float,
)

// T1/T5 start from the focused cell, so the cell centre is captured with the app.
internal data class Launching(
    val app: TvApp,
    val cellCenter: Offset,
)

// A requester created in this composition is attached in the following layout
// pass, so a data change (app installed, folder emptied) would make an immediate
// request throw. Retrying on the next frame keeps the cursor moving without a
// blind millisecond delay.
internal suspend fun FocusRequester.requestHomeFocusWhenAttached(): Boolean {
    repeat(3) {
        if (runCatching { requestFocus() }.isSuccess) return true
        withFrameNanos { }
    }
    return false
}
