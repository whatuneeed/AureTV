package app.aure.tv.ui

import android.content.Context
import androidx.compose.runtime.Composable
import app.aure.tv.R
import app.aure.tv.data.AppLabelStore
import app.aure.tv.data.TvApp
import app.aure.tv.ui.components.RenameKeyboardOverlay

/** New-folder and app-rename keyboards; both close their overlay on submit. */
@Composable
internal fun HomeKeyboards(
    overlays: HomeOverlayState,
    context: Context,
    scale: TvScale,
    prefsName: String,
    createFolderWithApp: (String, TvApp) -> Unit,
    renameAppInHome: (TvApp, String) -> Unit,
) {
    overlays.newFolderApp?.let { app ->
        RenameKeyboardOverlay(
            title = context.getString(R.string.new_folder_title),
            initial = "",
            scale = scale,
            onDone = { name ->
                overlays.newFolderApp = null
                createFolderWithApp(name, app)
            },
            onCancel = { overlays.newFolderApp = null },
            placeholder = context.getString(R.string.keyboard_placeholder),
            note = context.getString(R.string.new_folder_note),
        )
    }

    overlays.renameApp?.let { app ->
        RenameKeyboardOverlay(
            title = context.getString(R.string.rename_app_title),
            initial = AppLabelStore.label(context, app.packageName, prefsName) ?: app.label,
            scale = scale,
            onDone = { name -> renameAppInHome(app, name) },
            onCancel = { overlays.renameApp = null },
            placeholder = context.getString(R.string.keyboard_placeholder),
            note = context.getString(R.string.rename_app_note),
        )
    }
}
