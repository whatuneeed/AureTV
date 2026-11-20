package app.aure.tv.ui

import android.content.Context
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import app.aure.tv.R
import app.aure.tv.data.HomeEdit
import app.aure.tv.data.HomeItem
import app.aure.tv.data.TvApp
import app.aure.tv.data.hideApp
import app.aure.tv.data.isHiddenFolder
import app.aure.tv.data.removeFolder
import app.aure.tv.data.takeAppOutOfFolder
import kotlinx.coroutines.Job
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/**
 * Grid state and the mutations that belong to it: cursor, edit mode, drag,
 * folder opening and the item list itself. The focus state is passed in so the
 * composable keeps `rememberSaveable` behaviour.
 */
internal class HomeGridState(
    initialItems: List<HomeItem>,
    private val focusState: MutableIntState,
) {
    var items by mutableStateOf(initialItems)
    var editing by mutableStateOf(false)
    var pickupActive by mutableStateOf(false)
    var pickupOrigin by mutableStateOf<Int?>(null)
    var pickupSnapshot by mutableStateOf<List<HomeItem>?>(null)
    var pickupStart by mutableIntStateOf(-1)
    var mergeVictim by mutableStateOf<Int?>(null)
    var confirmIndex by mutableStateOf<Int?>(null)
    var holdJob by mutableStateOf<Job?>(null)
    var longPressHandled by mutableStateOf(false)
    var openFolder by mutableStateOf<OpenFolder?>(null)
    var folderClosing by mutableStateOf(false)
    var launching by mutableStateOf<Launching?>(null)

    var focusIndex: Int
        get() = focusState.intValue
        set(value) {
            focusState.intValue = value
        }

    private val columns get() = TvOs.COLUMNS

    fun targetFor(dx: Int, dy: Int): Int {
        val row = focusIndex / columns
        val column = focusIndex % columns
        return if (dx != 0) {
            val nextColumn = column + dx
            if (nextColumn !in 0 until columns) {
                focusIndex
            } else {
                (focusIndex + dx).takeIf { it < items.size } ?: focusIndex
            }
        } else {
            val targetRow = row + dy
            if (targetRow < 0) {
                focusIndex
            } else {
                // An incomplete last row has no cell under the same column;
                // tvOS moves to the nearest cell of that row instead of
                // refusing to leave the current one.
                (targetRow * columns + column)
                    .takeIf { it <= items.lastIndex }
                    ?: items.lastIndex.coerceAtLeast(0)
            }
        }
    }

    fun moveFocus(dx: Int, dy: Int) {
        val target = targetFor(dx, dy)
        if (target == focusIndex) return
        // A press that is still counting belongs to the cell that had focus:
        // moving while OK is held must not launch the cell that receives focus.
        if (holdJob != null) longPressHandled = true
        holdJob?.cancel()
        holdJob = null
        confirmIndex = null
        // A picked-up tile takes the new slot at once, so every neighbour
        // between the two cells slides over instead of waiting for the drop
        // (14_MOTION_DESIGN: the grid follows the drag, it does not jump).
        val origin = pickupOrigin
        if (pickupActive && origin != null && origin in items.indices && target in items.indices) {
            val list = items.toMutableList()
            val moving = list.removeAt(origin)
            list.add(target, moving)
            items = list
            // The tile that was under the cursor now sits where the drag
            // started; lingering on it is the folder gesture.
            mergeVictim = if (target > origin) target - 1 else target + 1
            pickupOrigin = target
            focusIndex = target
            return
        }
        focusIndex = target
    }

    fun endPickup() {
        pickupActive = false
        pickupOrigin = null
        pickupSnapshot = null
        pickupStart = -1
        mergeVictim = null
        confirmIndex = null
    }

    // Dropping only commits the order the drag already produced; the folder
    // gesture belongs to the dwell timer, not to the release.
    fun dropPickup(onOrderChanged: (List<HomeItem>) -> Unit) {
        if (pickupSnapshot != null && pickupSnapshot !== items) onOrderChanged(items)
        endPickup()
    }

    // 05_FOLDERS: two apps fold into one tile, an app dropped on a folder joins
    // it. Both tiles are moved by the drag itself, so the merge works on the
    // current positions.
    fun mergePickupInto(
        victimIndex: Int,
        context: Context,
        onOrderChanged: (List<HomeItem>) -> Unit,
    ) {
        if (!pickupActive || pickupOrigin != focusIndex) return
        val dragged = (items.getOrNull(focusIndex) as? HomeItem.AppItem)?.app ?: return
        when (val victim = items.getOrNull(victimIndex)) {
            is HomeItem.AppItem -> {
                val folder = HomeItem.FolderItem(
                    id = UUID.randomUUID().toString(),
                    name = context.getString(R.string.folder_default),
                    apps = listOf(dragged, victim.app),
                )
                val list = items.toMutableList()
                list.removeAt(max(focusIndex, victimIndex))
                list.removeAt(min(focusIndex, victimIndex))
                val insertAt = min(focusIndex, victimIndex).coerceIn(0, list.size)
                list.add(insertAt, folder)
                items = list
                focusIndex = insertAt
            }

            is HomeItem.FolderItem -> {
                val list = items.toMutableList()
                list.removeAt(focusIndex)
                val folderPos = list.indexOfFirst {
                    it is HomeItem.FolderItem && it.id == victim.id
                }
                if (folderPos < 0) return
                val folder = list[folderPos] as HomeItem.FolderItem
                list[folderPos] = folder.copy(apps = folder.apps + dragged)
                items = list
                focusIndex = folderPos
            }

            else -> return
        }
        endPickup()
        onOrderChanged(items)
    }

    fun togglePickup(onOrderChanged: (List<HomeItem>) -> Unit) {
        if (pickupActive) {
            dropPickup(onOrderChanged)
        } else {
            // The hidden folder is a fixed bucket: dragging it would let the
            // auto-created tile drift away from the end of the grid.
            if (isHiddenFolder(items.getOrNull(focusIndex))) return
            pickupActive = true
            pickupOrigin = focusIndex
            pickupStart = focusIndex
            pickupSnapshot = items
        }
    }

    fun openFolderAt(index: Int, center: Offset) {
        val folder = items.getOrNull(index) as? HomeItem.FolderItem ?: return
        editing = false
        folderClosing = false
        openFolder = OpenFolder(folder, center.x, center.y)
    }

    fun activate(
        index: Int,
        centerOf: (Int) -> Offset,
        startLaunch: (TvApp, Offset) -> Unit,
        onOrderChanged: (List<HomeItem>) -> Unit,
    ) {
        if (launching != null || openFolder != null) return
        if (index !in items.indices) return
        focusIndex = index
        if (editing) {
            togglePickup(onOrderChanged)
            return
        }
        when (val item = items[index]) {
            is HomeItem.AppItem -> startLaunch(item.app, centerOf(index))
            is HomeItem.FolderItem -> openFolderAt(index, centerOf(index))
        }
    }

    fun updateFolderApps(
        folderId: String,
        apps: List<TvApp>,
        onOrderChanged: (List<HomeItem>) -> Unit,
    ) {
        val index = items.indexOfFirst { it is HomeItem.FolderItem && it.id == folderId }
        if (index < 0) return
        val current = items[index] as HomeItem.FolderItem
        val updated = current.copy(apps = apps)
        val list = items.toMutableList().also { it[index] = updated }
        items = list
        openFolder?.let { open ->
            if (open.folder.id == folderId) openFolder = open.copy(folder = updated)
        }
        onOrderChanged(list)
    }

    fun renameFolder(
        folderId: String,
        name: String,
        onOrderChanged: (List<HomeItem>) -> Unit,
    ) {
        val index = items.indexOfFirst { it is HomeItem.FolderItem && it.id == folderId }
        if (index < 0) return
        val current = items[index] as HomeItem.FolderItem
        if (current.name == name) return
        val updated = current.copy(name = name)
        val list = items.toMutableList().also { it[index] = updated }
        items = list
        openFolder?.let { open ->
            if (open.folder.id == folderId) openFolder = open.copy(folder = updated)
        }
        onOrderChanged(list)
    }

    fun applyEdit(edit: HomeEdit, onOrderChanged: (List<HomeItem>) -> Unit) {
        items = edit.items
        focusIndex = edit.focusIndex.coerceIn(0, max(edit.items.lastIndex, 0))
        onOrderChanged(edit.items)
    }

    fun removeFolderById(folderId: String, onOrderChanged: (List<HomeItem>) -> Unit) {
        applyEdit(removeFolder(items, folderId) ?: return, onOrderChanged)
    }

    // Android TV launchers let the user hide an app from the grid without
    // uninstalling it; the tile waits in the hidden folder.
    fun hideAppFromHome(
        app: TvApp,
        context: Context,
        onOrderChanged: (List<HomeItem>) -> Unit,
    ) {
        applyEdit(
            hideApp(items, app.packageName, context.getString(R.string.hidden_folder_name))
                ?: return,
            onOrderChanged,
        )
    }

    fun applyAppLabel(packageName: String, label: String) {
        fun rename(app: TvApp): TvApp =
            if (app.packageName == packageName) app.copy(label = label) else app
        val list = items.map { item ->
            when (item) {
                is HomeItem.AppItem -> HomeItem.AppItem(rename(item.app))
                is HomeItem.FolderItem -> item.copy(apps = item.apps.map(::rename))
            }
        }
        items = list
        openFolder?.let { open ->
            val fresh = list.firstOrNull {
                it is HomeItem.FolderItem && it.id == open.folder.id
            } as? HomeItem.FolderItem
            if (fresh != null) openFolder = open.copy(folder = fresh)
        }
    }

    fun moveAppToHome(
        folderId: String,
        app: TvApp,
        onOrderChanged: (List<HomeItem>) -> Unit,
    ) {
        applyEdit(takeAppOutOfFolder(items, folderId, app.packageName) ?: return, onOrderChanged)
    }

    // "Move to Folder…" from the app menu: the tile leaves the grid and joins a
    // user folder. Hidden apps keep their home in the "More Apps" bucket, so the
    // picker never offers that one.
    fun moveAppIntoFolder(
        app: TvApp,
        folderId: String,
        onOrderChanged: (List<HomeItem>) -> Unit,
    ) {
        val from = items.indexOfFirst {
            it is HomeItem.AppItem && it.app.packageName == app.packageName
        }
        if (from < 0) return
        if (items.none { it is HomeItem.FolderItem && it.id == folderId }) return
        val list = items.toMutableList()
        list.removeAt(from)
        val target = list.indexOfFirst { it is HomeItem.FolderItem && it.id == folderId }
        if (target < 0) return
        val folder = list[target] as HomeItem.FolderItem
        list[target] = folder.copy(apps = folder.apps + app)
        items = list
        focusIndex = target.coerceIn(0, max(list.lastIndex, 0))
        onOrderChanged(list)
    }

    // "New Folder…" wraps the app in a named folder that takes its slot, so the
    // rest of the grid does not shift; the Java launcher 1.1.17 made the same move.
    fun createFolderWithApp(
        name: String,
        app: TvApp,
        context: Context,
        onOrderChanged: (List<HomeItem>) -> Unit,
    ) {
        val at = items.indexOfFirst {
            it is HomeItem.AppItem && it.app.packageName == app.packageName
        }
        if (at < 0) return
        val label = name.trim().ifEmpty { context.getString(R.string.folder_default) }
        val folder = HomeItem.FolderItem(UUID.randomUUID().toString(), label, listOf(app))
        val list = items.toMutableList().also { it[at] = folder }
        items = list
        focusIndex = at
        onOrderChanged(list)
    }
}
