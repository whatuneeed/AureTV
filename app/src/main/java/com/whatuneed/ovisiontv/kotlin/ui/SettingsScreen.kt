package com.whatuneed.ovisiontv.kotlin.ui

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.whatuneed.ovisiontv.kotlin.BuildConfig
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.SetupActivity
import com.whatuneed.ovisiontv.kotlin.data.Adaptive
import com.whatuneed.ovisiontv.kotlin.data.AerialClipCache
import com.whatuneed.ovisiontv.kotlin.data.AerialCatalog
import com.whatuneed.ovisiontv.kotlin.data.AerialClip
import com.whatuneed.ovisiontv.kotlin.data.AerialRefresh
import com.whatuneed.ovisiontv.kotlin.data.AerialRefreshResult
import com.whatuneed.ovisiontv.kotlin.data.AccessibilityStatus
import com.whatuneed.ovisiontv.kotlin.data.AppRepository
import com.whatuneed.ovisiontv.kotlin.data.CityPlace
import com.whatuneed.ovisiontv.kotlin.data.ContentPreferences
import com.whatuneed.ovisiontv.kotlin.data.DeviceFacts
import com.whatuneed.ovisiontv.kotlin.data.DeviceFactsReader
import com.whatuneed.ovisiontv.kotlin.data.DeviceFormat
import com.whatuneed.ovisiontv.kotlin.data.HIDDEN_FOLDER_ID
import com.whatuneed.ovisiontv.kotlin.data.HomeItem
import com.whatuneed.ovisiontv.kotlin.data.HomeOrderStore
import com.whatuneed.ovisiontv.kotlin.data.KidsStore
import com.whatuneed.ovisiontv.kotlin.data.LocalePreference
import com.whatuneed.ovisiontv.kotlin.data.NetworkKind
import com.whatuneed.ovisiontv.kotlin.data.PerformanceProfile
import com.whatuneed.ovisiontv.kotlin.data.PermissionSummary
import com.whatuneed.ovisiontv.kotlin.data.Profile
import com.whatuneed.ovisiontv.kotlin.data.ProfileCatalog
import com.whatuneed.ovisiontv.kotlin.data.ProfileStore
import com.whatuneed.ovisiontv.kotlin.data.SaverDelay
import com.whatuneed.ovisiontv.kotlin.data.SaverPlaylist
import com.whatuneed.ovisiontv.kotlin.data.SaverPreferences
import com.whatuneed.ovisiontv.kotlin.data.SaverRefresh
import com.whatuneed.ovisiontv.kotlin.data.SaverTheme
import com.whatuneed.ovisiontv.kotlin.data.SleepControls
import com.whatuneed.ovisiontv.kotlin.data.StartupPreferences
import com.whatuneed.ovisiontv.kotlin.data.TvApp
import com.whatuneed.ovisiontv.kotlin.data.Weather
import com.whatuneed.ovisiontv.kotlin.data.WeatherPreferences
import com.whatuneed.ovisiontv.kotlin.data.hiddenApps
import com.whatuneed.ovisiontv.kotlin.data.kidsLeaveNeedsPin
import com.whatuneed.ovisiontv.kotlin.data.takeAppOutOfFolder
import com.whatuneed.ovisiontv.kotlin.ui.components.AerialPosterStore
import com.whatuneed.ovisiontv.kotlin.ui.components.AerialPreviewStore
import com.whatuneed.ovisiontv.kotlin.ui.components.ProfileAvatarPicker
import com.whatuneed.ovisiontv.kotlin.ui.components.PinPadMode
import com.whatuneed.ovisiontv.kotlin.ui.components.PinPadOverlay
import com.whatuneed.ovisiontv.kotlin.ui.components.RenameKeyboardOverlay
import com.whatuneed.ovisiontv.kotlin.ui.components.SaverPreview
import com.whatuneed.ovisiontv.kotlin.ui.components.SaverScreen
import com.whatuneed.ovisiontv.kotlin.ui.components.SectionGlyph
import com.whatuneed.ovisiontv.kotlin.ui.components.TvKeyRepeat
import com.whatuneed.ovisiontv.kotlin.ui.components.rememberTvKeyRepeat
import com.whatuneed.ovisiontv.kotlin.ui.components.saverThemeLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// tvOS 27 Settings is a two-pane screen: a centred title, one 534-point section
// icon on the left and a 776-point option list on the right (10_SETTINGS §2).
// The list geometry below is measured from the tvOS screenshot; TSK draws rows
// at runtime, so the fill (white 10%) and the 50% label tint are the documented
// values taken from SettingsListStyleSheet.xml.
private const val TITLE_TOP = 44f
private const val TITLE_FONT = TvType.largeTitle
private const val DESCRIPTION_TOP = 128f
private const val DESCRIPTION_FONT = TvType.callout
private const val DESCRIPTION_LINE_HEIGHT = 30f
private const val DESCRIPTION_WIDTH = 900f
private const val DESCRIPTION_LINE_GAP = 8f
// The list starts below the measured hint, not at a guessed offset: a two-line
// hint used to leave only ~50 pt of air, which read as the hint touching the
// first row. The gap is part of the page rhythm, not of the hint.
private const val DESCRIPTION_LIST_GAP = 88f
// Do not add a hint longer than two lines: the list offset is computed from the
// line count, and a third line would push the last rows past the screen anyway.
private const val DESCRIPTION_MAX_LINES = 2

private const val ICON_LEFT = 259f
private const val ICON_TOP = 247f
private const val ICON_SIZE = 534f
private const val ICON_RADIUS = 116f
private const val ICON_GLYPH = 236f
private const val ICON_PROFILE_DISC = 330f
private const val ICON_PROFILE_EMOJI = TvType.glyphHero

private const val LIST_LEFT = 1066f
private const val LIST_TOP = 198f
private const val LIST_WIDTH = 776f
private const val LIST_BOTTOM_INSET = 44f
private const val ROW_HEIGHT = 64f
private const val ROW_GAP = 6f
private const val ROW_RADIUS = 16f
private const val ROW_PADDING = 22f
private const val ROW_FONT = TvType.body
private const val ROW_VALUE_FONT = TvType.callout
private const val ROW_CHECK = 22f
private const val FOCUS_SCALE = 1.04f
private const val ROW_PITCH = ROW_HEIGHT + ROW_GAP

private const val HEADER_HEIGHT = 34f
private const val HEADER_GAP = 6f
private const val HEADER_FONT = TvType.callout

private const val DOC_LEFT = 260f
private const val DOC_TOP = 198f
private const val DOC_WIDTH = 1400f
private const val DOC_FONT = TvType.body
private const val DOC_LINE_HEIGHT = 38f
private const val DOC_PARAGRAPH_GAP = 16f

// Categories sit in a list of their own on the left; the grid keeps the right
// side. The bar above the tiles used to cover the first row once scrolled.
private const val SIDEBAR_LEFT = 170f
private const val SIDEBAR_WIDTH = 300f
private const val SIDEBAR_ROW_HEIGHT = 58f
private const val SIDEBAR_ROW_GAP = 8f
private const val SIDEBAR_ROW_RADIUS = 14f
private const val SIDEBAR_ROW_PADDING = 18f
// "Планета Земля" is the longest label; at 24 it left no air before the count.
private const val SIDEBAR_FONT = TvType.callout
private const val SIDEBAR_LABEL_GAP = 10f
private const val SIDEBAR_COUNT_FONT = TvType.caption
private const val SIDEBAR_FOCUS_SCALE = 1.03f
private const val SIDEBAR_HINT_GAP = 22f
private const val SIDEBAR_HINT_FONT = TvType.caption

// The grid begins below the hint band; the refusal note sits on the last line
// of that band.
private const val GRID_TOP = 300f
private const val GRID_LEFT = 520f
private const val GRID_WIDTH = 1230f
private const val GRID_COLUMNS = 3
private const val TILE_GAP_X = 28f
private const val TILE_WIDTH = (GRID_WIDTH - TILE_GAP_X * (GRID_COLUMNS - 1)) / GRID_COLUMNS
private const val TILE_HEIGHT = 220f
private const val TILE_LABEL_HEIGHT = 46f
private const val TILE_LABEL_INSET = 10f
private const val TILE_GAP_Y = 24f
private const val TILE_RADIUS = 18f
// The focused tile grows by 4% and draws a 3dp ring; a LazyColumn sized to the
// exact content width clipped both on the outer columns and on the first row.
// The list keeps a halo of empty space around the cards.
private const val GRID_HALO = 20f
// The saver is a full-screen surface, so the preview opens only after a
// deliberate hold, not on a normal OK press that toggles the clip.
private const val PREVIEW_HOLD_MS = 600L

private const val PAGE_ROOT = 0
private const val PAGE_INTERFACE = 1
private const val PAGE_HOME_SCREEN = 2
private const val PAGE_SAVER = 3
private const val PAGE_SAVER_CHOOSER = 4
private const val PAGE_SAVER_DELAY = 5
private const val PAGE_SAVER_THEMES = 6
private const val PAGE_SAVER_REFRESH = 7
private const val PAGE_NETWORK = 8
private const val PAGE_DEVICE = 9
private const val PAGE_STORAGE = 10
private const val PAGE_SYSTEM = 11
private const val PAGE_UPDATES = 12
private const val PAGE_ABOUT = 13
private const val PAGE_LEGAL = 14
private const val PAGE_LANGUAGE = 15
private const val PAGE_HIDDEN = 16
private const val PAGE_RESET = 17
private const val PAGE_DOC_TERMS = 18
private const val PAGE_DOC_PRIVACY = 19
private const val PAGE_DOC_AERIAL = 20
private const val PAGE_DOC_LICENSES = 21
private const val PAGE_STORAGE_CLEAR = 22
private const val PAGE_SAVER_RESET = 23
private const val PAGE_WEATHER = 24
private const val PAGE_WEATHER_LOCATION = 25
private const val PAGE_WEATHER_UNITS = 26
private const val PAGE_WEATHER_SOURCE = 27
private const val PAGE_WEATHER_REFRESH = 28
private const val PAGE_STARTUP = 29
private const val PAGE_STARTUP_WHEN = 30
private const val PAGE_PERMISSIONS = 31
private const val PAGE_PROFILES = 32
private const val PAGE_PROFILE_EDIT = 33
private const val PAGE_PROFILE_DELETE = 34
private const val PAGE_KIDS_PIN_REMOVE = 35
private const val PAGE_KIDS_APPS = 36
private const val PAGE_DEVICE_GROUP = 37
private const val PAGE_ABOUT_GROUP = 38

private val PROFILE_PAGES = setOf(
    PAGE_PROFILES,
    PAGE_PROFILE_EDIT,
    PAGE_PROFILE_DELETE,
    PAGE_KIDS_PIN_REMOVE,
    PAGE_KIDS_APPS,
)

// Index of the "Exit PIN" row on the profile edit page. The flows that change
// the PIN return the focus there, the way Java's showAndFocus did. НЕ МЕНЯТЬ
// вслепую: число связано с порядком строк в PAGE_PROFILE_EDIT (switch, name,
// avatar, kids toggle, exit PIN) — при изменении порядка пересчитать.
private const val EDIT_KIDS_PIN_ROW = 4

private val DOC_PAGES = setOf(
    PAGE_DOC_TERMS,
    PAGE_DOC_PRIVACY,
    PAGE_DOC_AERIAL,
    PAGE_DOC_LICENSES,
)

private val LANGUAGE_OPTIONS = listOf(
    LocalePreference.SYSTEM,
    LocalePreference.RUSSIAN,
    LocalePreference.ENGLISH,
)

private val WEATHER_UNITS = listOf(
    WeatherPreferences.UNIT_AUTO,
    WeatherPreferences.UNIT_C,
    WeatherPreferences.UNIT_F,
)

/**
 * Root sections. Six rows fit one screen, so there are no group headers any
 * more: a header over three rows added a level without adding meaning.
 */
private enum class Section(
    val glyph: ImageVector,
    @StringRes val title: Int,
) {
    INTERFACE(SectionGlyph.gear, R.string.settings_interface),
    HOME_SCREEN(SectionGlyph.home, R.string.settings_home_screen),
    PROFILES(SectionGlyph.person, R.string.settings_profiles),
    WEATHER(SectionGlyph.weather, R.string.weather_title),
    DEVICE(SectionGlyph.tv, R.string.settings_device),
    ABOUT(SectionGlyph.info, R.string.settings_about_system),
}

private data class SettingsRow(
    val title: String,
    val value: String? = null,
    val checked: Boolean = false,
    // One-of-many list: TalkBack announces it as a radio button, not a plain
    // button whose tick it has to infer from the label.
    val choice: Boolean = false,
    // An on/off row without a mark: null is "not a toggle", otherwise TalkBack
    // gets a Switch role and the On/Off state instead of a bare Button.
    val toggleState: Boolean? = null,
    val chevron: Boolean = false,
    val destructive: Boolean = false,
    val action: (() -> Unit)? = null,
    // TalkBack reads the click action as "double-tap to …"; a row whose title is
    // a noun (an app name) needs the verb stated separately.
    val actionLabel: String? = null,
)

private data class ListEntry(
    val top: Float,
    val header: String? = null,
    val row: SettingsRow? = null,
    val rowIndex: Int = -1,
)

/** One row of the chooser's category list: All or an enabled theme. */
private data class ChooserCategory(
    val label: String,
    val theme: SaverTheme?,
    val visible: Int,
    val total: Int,
) {
    val allHidden: Boolean get() = total > 0 && visible == 0
}

private data class CacheSizes(
    val stills: Long,
    val posters: Long,
) {
    val total: Long get() = stills + posters
}

/**
 * `apps` is a test hook: the screen otherwise loads package names itself and
 * deliberately skips artwork it would never draw here. `clips` is the same for
 * the saver picker, whose catalogue is bundled but may be updated at runtime.
 * `refreshKey` is a test hook for the package broadcast the screen cannot send.
 * `refreshCatalog` replaces the network refresh in tests, which cannot reach
 * Apple's feed and must still prove the three statuses are distinct.
 * `previewSurface` replaces the saver overlay in tests, which must not open
 * decoders, and lets them see which clip the hold selected.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    resumeTick: Int = 0,
    apps: List<TvApp>? = null,
    refreshKey: Int = 0,
    clips: List<AerialClip>? = null,
    refreshCatalog: (suspend () -> AerialRefreshResult)? = null,
    previewSurface: (@Composable (AerialClip) -> Unit)? = null,
    // True when the launcher's kids PIN gate already passed (the in-process
    // KidsEntryGrant). A direct start without the grant re-opens the gate
    // inside Settings for a kids profile with a PIN.
    kidsUnlocked: Boolean = false,
) {
    val scale = rememberTvScale()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val russian = configuration.locales[0]?.language == "ru"
    // Numbers follow the screen language, not the system locale: a Russian UI
    // must not print "1.5 MB" only because the TV itself is set to English.
    val formatLocale = configuration.locales[0] ?: Locale.getDefault()
    val language = remember { LocalePreference.get(context) }
    val profileStore = remember(context) { ProfileStore(context) }
    var profiles by remember { mutableStateOf(profileStore.list()) }
    var currentProfileId by remember { mutableStateOf(profileStore.current().id) }
    // The edit pages remember which profile they point at; the id survives a
    // recreation while the keyboard or the avatar grid is open.
    var editingProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    var nameEditorOpen by remember { mutableStateOf(false) }
    // null in [nameEditorId] means the editor is adding, not renaming.
    var nameEditorId by rememberSaveable { mutableStateOf<String?>(null) }
    var avatarPickerOpen by remember { mutableStateOf(false) }
    var askOnBoot by remember(resumeTick) { mutableStateOf(profileStore.askOnBoot()) }
    var askOnWake by remember(resumeTick) { mutableStateOf(profileStore.askOnWake()) }
    // Settings edits the current profile's layout: each profile owns its order,
    // so opening Settings under another profile must show that profile's apps.
    val orderStore = remember(context, currentProfileId) {
        HomeOrderStore(context, ProfileStore.homePrefsName(currentProfileId))
    }
    val currentProfile = profiles.firstOrNull { it.id == currentProfileId }
    val editingProfile = profiles.firstOrNull { it.id == editingProfileId }
    val reduceMotion = remember { !android.animation.ValueAnimator.areAnimatorsEnabled() }

    var catalog by remember(apps) { mutableStateOf(apps) }
    var items by remember { mutableStateOf<List<HomeItem>?>(null) }
    // The layout belongs to the profile that was current when it was loaded.
    // A switch creates a new order store before the new load finishes, so a
    // mutation in that window must not write the old layout into it (AQ-14).
    var itemsOwner by remember { mutableStateOf<String?>(null) }
    // A package installed or removed while this screen is open leaves the list
    // stale: the hidden folder would keep a row for an app that is already gone.
    var packageRefresh by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val replacing = intent?.getBooleanExtra(Intent.EXTRA_REPLACING, false) == true
                if (reloadsHomeFor(intent?.action, replacing)) packageRefresh++
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            packageChangeFilter(),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    LaunchedEffect(apps, packageRefresh, refreshKey, orderStore) {
        itemsOwner = null
        val owner = currentProfileId
        val loaded = apps ?: AppRepository(context).load(withArtwork = false)
        catalog = loaded
        items = orderStore.applyOrder(loaded)
        itemsOwner = owner
    }

    // The Home picker, the wizard and another Settings window can change the
    // profile list while this screen exists; a resume is when it may have
    // happened. Switching here also moves the layout the page lists.
    LaunchedEffect(resumeTick) {
        val fresh = profileStore.list()
        if (fresh != profiles) profiles = fresh
        val current = profileStore.current().id
        if (current != currentProfileId) currentProfileId = current
    }

    var page by rememberSaveable { mutableIntStateOf(PAGE_ROOT) }
    var focusIndex by rememberSaveable { mutableIntStateOf(0) }
    // Every page keeps the row the user left it on, so going back into a list
    // does not send a long hidden-apps list to its first row again.
    val pageFocus = remember { mutableStateMapOf<Int, Int>() }
    // The licenses document is reachable from both About and Legal, so the way
    // back is what the user actually walked, not a fixed parent per page.
    val history = remember { mutableStateListOf<Int>() }

    // The wizard and the Home screen write the same preferences while this
    // screen is paused; every one of them is re-read on resume so a row never
    // shows the value from the first composition.
    var saverDelay by remember(resumeTick) { mutableStateOf(SaverPreferences.delay(context)) }
    var contentRecommendations by remember(resumeTick) {
        mutableStateOf(ContentPreferences.recommendations(context))
    }
    var contentContinue by remember(resumeTick) {
        mutableStateOf(ContentPreferences.continueWatching(context))
    }
    // Grants change on system screens, so their rows are re-read on every
    // return instead of keeping the value from the first composition.
    var listingsAllowed by remember(resumeTick) {
        mutableStateOf(ContentPreferences.canReadListings(context))
    }
    var locationAllowed by remember(resumeTick) {
        mutableStateOf(DeviceFactsReader.hasLocation(context))
    }
    // Device admin or the connected accessibility service: both can lock the
    // screen, so this is the row's "allowed" state, not just the admin flag.
    var powerAllowed by remember(resumeTick) { mutableStateOf(SleepControls.hasAdmin(context)) }
    var defaultHome by remember(resumeTick) { mutableStateOf(DeviceFactsReader.isDefaultHome(context)) }
    var saverMusic by remember(resumeTick) { mutableStateOf(SaverPreferences.music(context)) }
    var saverRefresh by remember(resumeTick) { mutableStateOf(SaverPreferences.refresh(context)) }
    var saverThemes by remember(resumeTick) { mutableStateOf(SaverPreferences.themes(context)) }
    var hiddenClips by remember(resumeTick) { mutableStateOf(SaverPreferences.hidden(context)) }
    var weatherEnabled by remember(resumeTick) { mutableStateOf(WeatherPreferences.enabled(context)) }
    var weatherManual by remember(resumeTick) { mutableStateOf(WeatherPreferences.manual(context)) }
    var weatherPlace by remember(resumeTick) { mutableStateOf(WeatherPreferences.place(context)) }
    var weatherUnits by remember(resumeTick) { mutableStateOf(WeatherPreferences.units(context)) }
    var weatherSource by remember(resumeTick) { mutableStateOf(WeatherPreferences.source(context)) }
    var weatherRefresh by remember(resumeTick) { mutableStateOf(WeatherPreferences.refreshMinutes(context)) }
    var weatherUpdatedAt by remember(resumeTick) { mutableStateOf(WeatherPreferences.cachedAt(context)) }
    var weatherBusy by remember { mutableStateOf(false) }
    var citySearchOpen by remember { mutableStateOf(false) }
    var accessibility by remember(resumeTick) { mutableStateOf(AccessibilityStatus.state(context)) }
    var homeOverride by remember(resumeTick) { mutableStateOf(StartupPreferences.homeOverride(context)) }
    var startupTarget by remember(resumeTick) { mutableStateOf(StartupPreferences.target(context)) }
    var startupLabel by remember(resumeTick) { mutableStateOf(StartupPreferences.label(context)) }
    var startupOnWake by remember(resumeTick) { mutableStateOf(StartupPreferences.onWake(context)) }
    var clipList by remember(clips) { mutableStateOf(clips ?: emptyList()) }
    LaunchedEffect(clips) {
        if (clips == null) {
            clipList = withContext(Dispatchers.IO) { AerialCatalog.load(context) }
        }
    }
    var pickerRefused by remember { mutableStateOf(false) }
    // Category filter of the chooser is view state, not a stored setting: it
    // resets when Settings is reopened, while the hidden clips stay saved.
    var filterKey by rememberSaveable { mutableStateOf<String?>(null) }
    var inSidebar by rememberSaveable { mutableStateOf(false) }
    var sidebarIndex by rememberSaveable { mutableIntStateOf(0) }
    // A long OK opens the saver over the chooser; [previewClosing] reverses its
    // entrance before the overlay leaves, and [focusTick] hands the remote back
    // to the tile the user held.
    var previewClip by remember { mutableStateOf<AerialClip?>(null) }
    var previewClosing by remember { mutableStateOf(false) }
    var focusTick by remember { mutableIntStateOf(0) }
    var holdJob by remember { mutableStateOf<Job?>(null) }
    var holdHandled by remember { mutableStateOf(false) }
    var okDown by remember { mutableStateOf(false) }
    // The preview is a real decoder surface. Leaving Settings must stop it
    // instead of waiting for composition disposal (AQ-20).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, previewClip) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && previewClip != null) {
                previewClip = null
                previewClosing = false
                holdHandled = false
                focusTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Kids Mode. The entry gate is a snapshot taken when Settings opened: the
    // launcher passes kidsUnlocked after its own pad, a direct start (system
    // recents, adb) re-opens the gate here. The gate keeps the profile it was
    // opened for — a resume must not re-point the pad at another profile. A
    // PIN set later inside this session must not lock the person who is
    // setting it, and a profile switch on resume follows the Java rule of
    // checking once at the start.
    val kidsStore = remember(context) { KidsStore(context) }
    val kidsGateProfile = remember { currentProfile }
    // Keyed on kidsUnlocked: a singleTop warm start can consume the launcher's
    // grant after this composition, and the gate must then stand down.
    var kidsEntryLocked by remember(kidsUnlocked) {
        mutableStateOf(
            kidsGateProfile?.kids == true &&
                !kidsUnlocked &&
                KidsStore(context).hasPin(kidsGateProfile.id),
        )
    }
    // The section's own surfaces: the pad that sets a profile's PIN and the
    // two-step recovery editor (0 = hint, 1 = answer).
    var kidsPadFor by remember { mutableStateOf<String?>(null) }
    // The entry gate can fall back to the recovery word instead of the PIN.
    var kidsEntryRecover by remember { mutableStateOf(false) }
    // Leaving the kids profile from Settings waits for the same pad the picker
    // would show: without it a child could switch out with no PIN.
    var kidsLeaveFor by remember { mutableStateOf<String?>(null) }
    // The leave gate verifies the profile it was opened from, which may differ
    // from the entry snapshot after a switch inside this session (A->B->A).
    var kidsLeaveFrom by remember { mutableStateOf<String?>(null) }
    var kidsLeaveRecover by remember { mutableStateOf(false) }
    var recoveryFor by remember { mutableStateOf<String?>(null) }
    var recoverySession by remember { mutableIntStateOf(0) }
    var recoveryStep by remember { mutableStateOf<Int?>(null) }
    var recoveryHint by remember { mutableStateOf("") }
    // The allowlist page keeps its set in state and writes through to the
    // store, so every toggle repaints its row immediately.
    var kidsAllow by remember(editingProfileId, resumeTick) {
        mutableStateOf(
            profiles.firstOrNull { it.id == editingProfileId }
                ?.let { kidsStore.allowlist(it.id) }
                ?: emptySet(),
        )
    }

    var facts by remember { mutableStateOf<DeviceFacts?>(null) }
    var cacheSizes by remember { mutableStateOf<CacheSizes?>(null) }
    var cacheTick by remember { mutableIntStateOf(0) }
    var cacheBusy by remember { mutableStateOf(false) }
    var catalogBusy by remember { mutableStateOf(false) }
    var catalogStatus by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val keyRepeat = rememberTvKeyRepeat()

    // The launchers only report back; the rows read the provider's real state,
    // so a grant the system still treats as denied is never shown as allowed.
    val listingsPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        listingsAllowed = ContentPreferences.canReadListings(context)
    }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        locationAllowed = DeviceFactsReader.hasLocation(context)
    }

    LaunchedEffect(page, cacheTick, resumeTick) {
        // A system screen may have changed the Accessibility switch or the Home
        // app; every page entry re-reads the values its rows show.
        accessibility = AccessibilityStatus.state(context)
        homeOverride = StartupPreferences.homeOverride(context)
        startupTarget = StartupPreferences.target(context)
        startupLabel = StartupPreferences.label(context)
        startupOnWake = StartupPreferences.onWake(context)
        listingsAllowed = ContentPreferences.canReadListings(context)
        locationAllowed = DeviceFactsReader.hasLocation(context)
        powerAllowed = SleepControls.hasAdmin(context)
        defaultHome = DeviceFactsReader.isDefaultHome(context)
        when (page) {
            PAGE_STORAGE -> {
                facts = withContext(Dispatchers.IO) { DeviceFactsReader.read(context) }
                cacheSizes = withContext(Dispatchers.IO) {
                    CacheSizes(
                        stills = AerialPreviewStore.size(context),
                        posters = AerialPosterStore.size(context),
                    )
                }
            }

            PAGE_NETWORK, PAGE_DEVICE, PAGE_SYSTEM -> {
                facts = withContext(Dispatchers.IO) { DeviceFactsReader.read(context) }
            }

            // The root rows summarise the same facts, so they are read here
            // instead of each row reaching for a service on its own. A return
            // from system settings re-reads them, since Home may have changed.
            PAGE_ROOT -> {
                facts = withContext(Dispatchers.IO) { DeviceFactsReader.read(context) }
            }
        }
    }

    val units = listOf(
        stringResource(R.string.unit_b),
        stringResource(R.string.unit_kb),
        stringResource(R.string.unit_mb),
        stringResource(R.string.unit_gb),
        stringResource(R.string.unit_tb),
    )
    val uptimeWords = Triple(
        stringResource(R.string.unit_days),
        stringResource(R.string.unit_hours),
        stringResource(R.string.unit_minutes),
    )

    fun parentOf(current: Int): Int = when (current) {
        PAGE_HIDDEN, PAGE_RESET -> PAGE_HOME_SCREEN
        PAGE_LANGUAGE -> PAGE_INTERFACE
        PAGE_SAVER -> PAGE_INTERFACE
        PAGE_SAVER_CHOOSER, PAGE_SAVER_DELAY, PAGE_SAVER_THEMES, PAGE_SAVER_REFRESH,
        PAGE_SAVER_RESET,
        -> PAGE_SAVER
        PAGE_STORAGE_CLEAR -> PAGE_STORAGE
        PAGE_NETWORK, PAGE_DEVICE, PAGE_STORAGE, PAGE_PERMISSIONS, PAGE_SYSTEM ->
            PAGE_DEVICE_GROUP
        PAGE_WEATHER_LOCATION, PAGE_WEATHER_UNITS, PAGE_WEATHER_SOURCE, PAGE_WEATHER_REFRESH ->
            PAGE_WEATHER
        PAGE_STARTUP, PAGE_STARTUP_WHEN -> PAGE_SYSTEM
        PAGE_PROFILE_EDIT -> PAGE_PROFILES
        PAGE_PROFILE_DELETE -> PAGE_PROFILE_EDIT
        PAGE_KIDS_PIN_REMOVE, PAGE_KIDS_APPS -> PAGE_PROFILE_EDIT
        PAGE_UPDATES, PAGE_ABOUT, PAGE_LEGAL -> PAGE_ABOUT_GROUP
        PAGE_DOC_TERMS, PAGE_DOC_PRIVACY, PAGE_DOC_AERIAL, PAGE_DOC_LICENSES -> PAGE_LEGAL
        else -> PAGE_ROOT
    }

    fun open(target: Int, focus: Int? = null) {
        // A held arrow must not keep walking the page that just opened.
        keyRepeat.stopRepeat()
        pageFocus[page] = focusIndex
        if (target != page) history.add(page)
        page = target
        focusIndex = focus ?: pageFocus[target] ?: 0
        pickerRefused = false
        inSidebar = false
    }

    fun back() {
        keyRepeat.stopRepeat()
        pageFocus[page] = focusIndex
        inSidebar = false
        if (page == PAGE_ROOT && history.isEmpty()) {
            onClose()
            return
        }
        // After a recreation (a language switch) the history is gone but page
        // is saved, so the structural parent is still the correct way back.
        val previous = history.removeLastOrNull() ?: parentOf(page)
        page = previous
        focusIndex = pageFocus[previous] ?: 0
    }

    fun refreshProfiles() {
        profiles = profileStore.list()
        currentProfileId = profileStore.current().id
    }

    fun switchProfile(id: String) {
        // Same rule as the launcher's picker: leaving the kids profile needs
        // its PIN when one is set — a profile without a PIN must not trap
        // the child.
        val current = profiles.firstOrNull { it.id == currentProfileId }
        val target = profiles.firstOrNull { it.id == id }
        if (current != null && target != null &&
            kidsLeaveNeedsPin(current, target, kidsStore.hasPin(current.id))
        ) {
            kidsLeaveFrom = current.id
            kidsLeaveFor = id
            return
        }
        profileStore.setCurrent(id)
        refreshProfiles()
    }

    /** The keyboard serves both adding a profile and renaming the edited one. */
    fun saveProfileName(name: String) {
        val target = nameEditorId
        nameEditorOpen = false
        if (target == null) {
            val addedId = profileStore.add(name)?.id
            refreshProfiles()
            val index = profiles.indexOfFirst { it.id == addedId }
            if (index >= 0) focusIndex = index
        } else {
            profiles.firstOrNull { it.id == target }?.let {
                profileStore.update(it.copy(name = name))
            }
            refreshProfiles()
        }
    }

    fun saveProfileAvatar(emoji: String) {
        val target = editingProfileId ?: return
        profiles.firstOrNull { it.id == target }?.let {
            profileStore.update(it.copy(emoji = emoji, color = ProfileCatalog.colorFor(emoji)))
        }
        refreshProfiles()
        avatarPickerOpen = false
    }

    fun deleteEditingProfile() {
        val target = editingProfileId ?: return
        if (!profileStore.delete(target)) return
        refreshProfiles()
        editingProfileId = null
        // Confirming replaces the confirmation and the person's page with the
        // list, so history must not keep entries that point at them.
        while (history.lastOrNull() in PROFILE_PAGES) history.removeLastOrNull()
        page = PAGE_PROFILES
        focusIndex = 0
        pageFocus[PAGE_PROFILES] = 0
    }

    val hidden = items?.let(::hiddenApps).orEmpty()
    val hiddenCount = items?.let {
        pluralStringResource(R.plurals.settings_hidden_count, hidden.size, hidden.size)
    }
    val showOnHome = stringResource(R.string.menu_show_on_home)

    // Settings is a separate activity: the saved order is the hand-off to Home,
    // which reloads its grid only when this value actually changed.
    fun restoreHidden(packageName: String) {
        if (itemsOwner != currentProfileId) return
        val current = items ?: return
        val edit = takeAppOutOfFolder(current, HIDDEN_FOLDER_ID, packageName) ?: return
        items = edit.items
        orderStore.saveOrder(edit.items)
    }

    fun resetLayout() {
        if (itemsOwner != currentProfileId) return
        orderStore.clearOrder()
        items = catalog?.let(orderStore::applyOrder).orEmpty()
        // Confirming replaces the Reset page with the Home Screen page: the
        // history must not keep the page that no longer exists underneath.
        history.removeLastOrNull()
        page = PAGE_HOME_SCREEN
        focusIndex = 0
        pageFocus[PAGE_HOME_SCREEN] = 0
    }

    fun clearCaches(onDone: () -> Unit = {}) {
        if (cacheBusy) return
        cacheBusy = true
        scope.launch {
            withContext(Dispatchers.IO) {
                AerialClipCache.cleanup(context)
                AerialPreviewStore.clear(context)
                AerialPosterStore.clear(context)
            }
            cacheSizes = CacheSizes(0, 0)
            cacheTick++
            cacheBusy = false
            onDone()
        }
    }

    fun confirmClearCaches() {
        clearCaches { back() }
    }

    fun resetSaver() {
        SaverPreferences.reset(context)
        saverDelay = SaverPreferences.delay(context)
        saverMusic = SaverPreferences.music(context)
        saverRefresh = SaverPreferences.refresh(context)
        saverThemes = SaverPreferences.themes(context)
        hiddenClips = SaverPreferences.hidden(context)
        pickerRefused = false
        back()
    }

    // A settings change drops the cache key, so the next fetch must be asked
    // for explicitly; the engine silently keeps the previous forecast on error.
    fun reloadWeather() {
        if (!weatherEnabled) return
        scope.launch {
            Weather.refresh(context, true)
            weatherUpdatedAt = WeatherPreferences.cachedAt(context)
        }
    }

    fun refreshWeatherNow() {
        if (!weatherEnabled) {
            Toast.makeText(context, R.string.weather_turn_on_first, Toast.LENGTH_SHORT).show()
            return
        }
        if (weatherBusy) return
        val before = WeatherPreferences.cachedAt(context)
        weatherBusy = true
        Toast.makeText(context, R.string.weather_refreshing_status, Toast.LENGTH_SHORT).show()
        scope.launch {
            Weather.refresh(context, true)
            weatherUpdatedAt = WeatherPreferences.cachedAt(context)
            weatherBusy = false
            // A refresh that failed keeps the old timestamp: report it as a
            // failure instead of "updated" just because some cache exists.
            Toast.makeText(
                context,
                if (weatherUpdatedAt > before) {
                    R.string.weather_updated
                } else {
                    R.string.weather_update_failed
                },
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun weatherUpdatedDetail(): String {
        val at = weatherUpdatedAt
        if (at <= 0) return context.getString(R.string.weather_no_forecast)
        val clock = android.text.format.DateFormat.getTimeFormat(context).format(Date(at))
        val stale = System.currentTimeMillis() - at > weatherRefresh * 60_000L
        return context.getString(
            if (stale) R.string.weather_stale_at else R.string.weather_fresh_at,
            clock,
        )
    }

    fun toggleClip(clip: AerialClip) {
        val visibleClips = clipList.filter { it.theme in saverThemes }
        val visibleOn = visibleClips.count { it.id !in hiddenClips }
        val next = SaverPreferences.toggleHidden(context, clip.id, visibleOn)
        if (next == hiddenClips) {
            pickerRefused = true
        } else {
            hiddenClips = next
            pickerRefused = false
        }
    }

    fun categoryIds(theme: SaverTheme): Set<String> =
        clipList.filter { it.theme == theme && it.theme in saverThemes }
            .map { it.id }
            .toSet()

    /** One switch hides the whole category or brings every clip of it back. */
    fun toggleCategory(theme: SaverTheme) {
        val ids = categoryIds(theme)
        if (ids.isEmpty()) return
        val visibleTotal = clipList.count { it.theme in saverThemes && it.id !in hiddenClips }
        val next = SaverPreferences.toggleGroup(context, ids, visibleTotal)
        if (next == hiddenClips) {
            pickerRefused = true
        } else {
            hiddenClips = next
            pickerRefused = false
        }
    }

    fun selectFilter(theme: SaverTheme?) {
        filterKey = theme?.key
        focusIndex = 0
        pickerRefused = false
    }

    val themeSummary = if (saverThemes.size == SaverTheme.entries.size) {
        stringResource(R.string.saver_all_themes)
    } else {
        stringResource(R.string.saver_themes_count, saverThemes.size, SaverTheme.entries.size)
    }

    val languageSummary = languageLabel(language)
    val networkText = facts?.let { networkLabel(it.network) }
    val freeSpace = facts?.storageFree?.let { DeviceFormat.size(it, units, formatLocale) }
    val factsReady = facts != null
    // One counter for the Device group row and its Permissions page: taking the
    // Home button over counts as a grant, so the two screens cannot disagree.
    val permissionsSummary = run {
        val missing = PermissionSummary.missing(
            accessibilityConnected = accessibility == AccessibilityStatus.State.CONNECTED,
            accessibilityAvailable = AccessibilityStatus.available(context),
            listingsGranted = listingsAllowed,
            powerAllowed = powerAllowed,
            powerSupported = SleepControls.adminSupported(context),
            defaultHome = defaultHome,
            homeOverride = homeOverride,
            locationGranted = locationAllowed,
        )
        if (missing == 0) {
            stringResource(R.string.settings_perms_all_allowed)
        } else {
            stringResource(R.string.settings_perms_remaining, missing)
        }
    }

    val weatherAge = when {
        weatherUpdatedAt <= 0L -> stringResource(R.string.weather_no_data)
        else -> {
            val minutes = ((System.currentTimeMillis() - weatherUpdatedAt) / 60_000L)
                .coerceAtLeast(0)
            when {
                minutes < 1 -> stringResource(R.string.weather_just_now)
                minutes < 60 -> stringResource(R.string.weather_min_ago, minutes.toInt())
                else -> stringResource(R.string.weather_hour_ago, (minutes / 60).toInt())
            }
        }
    }
    val weatherSourceLabels = mapOf(
        Weather.SRC_OPEN_METEO to stringResource(R.string.weather_source_open_meteo),
        Weather.SRC_MET to stringResource(R.string.weather_source_met),
        Weather.SRC_WTTR to stringResource(R.string.weather_source_wttr),
    )

    val rows = when (page) {
        PAGE_SAVER -> listOf(
            SettingsRow(
                title = stringResource(R.string.saver_choose),
                value = stringResource(R.string.saver_aerials),
                chevron = true,
                action = { open(PAGE_SAVER_CHOOSER) },
            ),
            SettingsRow(
                title = stringResource(R.string.saver_start_after),
                value = saverDelayLabel(saverDelay),
                chevron = true,
                // Opening on the first row would let one OK press overwrite the
                // stored choice with a value the user never looked at.
                action = {
                    open(PAGE_SAVER_DELAY, focus = SaverDelay.entries.indexOf(saverDelay))
                },
            ),
            SettingsRow(
                title = stringResource(R.string.saver_during_music),
                value = stringResource(if (saverMusic) R.string.saver_on else R.string.saver_off),
                toggleState = saverMusic,
                action = {
                    saverMusic = !saverMusic
                    SaverPreferences.setMusic(context, saverMusic)
                },
            ),
            SettingsRow(
                title = stringResource(R.string.saver_themes),
                value = themeSummary,
                chevron = true,
                action = { open(PAGE_SAVER_THEMES) },
            ),
            SettingsRow(
                title = stringResource(R.string.saver_refresh),
                value = saverRefreshLabel(saverRefresh),
                chevron = true,
                action = {
                    open(PAGE_SAVER_REFRESH, focus = SaverRefresh.entries.indexOf(saverRefresh))
                },
            ),
            SettingsRow(
                title = stringResource(R.string.saver_reset),
                chevron = true,
                // Cancel is the safe default, as on the layout reset page.
                action = { open(PAGE_SAVER_RESET, focus = 1) },
            ),
        )

        PAGE_SAVER_RESET -> listOf(
            SettingsRow(
                title = stringResource(R.string.saver_reset_confirm),
                destructive = true,
                action = { resetSaver() },
            ),
            SettingsRow(
                title = stringResource(R.string.settings_cancel),
                action = { back() },
            ),
        )

        PAGE_SAVER_DELAY -> SaverDelay.entries.map { delay ->
            SettingsRow(
                title = saverDelayLabel(delay),
                checked = delay == saverDelay,
                choice = true,
                action = {
                    saverDelay = delay
                    SaverPreferences.setDelay(context, delay)
                },
            )
        }

        PAGE_SAVER_THEMES -> SaverTheme.entries.map { theme ->
            SettingsRow(
                title = saverThemeLabel(theme),
                checked = theme in saverThemes,
                action = { saverThemes = SaverPreferences.toggleTheme(context, theme, clipList) },
            )
        }

        PAGE_SAVER_REFRESH -> SaverRefresh.entries.map { refresh ->
            SettingsRow(
                title = saverRefreshLabel(refresh),
                checked = refresh == saverRefresh,
                choice = true,
                action = {
                    saverRefresh = refresh
                    SaverPreferences.setRefresh(context, refresh)
                },
            )
        }

        PAGE_INTERFACE -> listOf(
            SettingsRow(
                title = stringResource(R.string.settings_language),
                value = languageSummary,
                chevron = true,
                action = {
                    open(PAGE_LANGUAGE, focus = LANGUAGE_OPTIONS.indexOf(language).coerceAtLeast(0))
                },
            ),
            SettingsRow(
                title = stringResource(R.string.settings_saver),
                // The value is the stored delay, not a constant: "Never" also
                // tells the viewer the saver is off without opening the page.
                value = saverDelayLabel(saverDelay),
                chevron = true,
                action = { open(PAGE_SAVER) },
            ),
        )

        PAGE_PROFILES -> buildList {
            add(
                SettingsRow(
                    title = stringResource(R.string.profile_ask_boot),
                    value = stringResource(if (askOnBoot) R.string.saver_on else R.string.saver_off),
                    toggleState = askOnBoot,
                    action = {
                        askOnBoot = !askOnBoot
                        profileStore.setAskOnBoot(askOnBoot)
                    },
                ),
            )
            add(
                SettingsRow(
                    title = stringResource(R.string.profile_ask_wake),
                    value = stringResource(if (askOnWake) R.string.saver_on else R.string.saver_off),
                    toggleState = askOnWake,
                    action = {
                        askOnWake = !askOnWake
                        profileStore.setAskOnWake(askOnWake)
                    },
                ),
            )
            profiles.forEach { profile ->
                add(
                    SettingsRow(
                        title = profile.name,
                        value = profile.emoji,
                        checked = profile.id == currentProfileId,
                        chevron = true,
                        action = {
                            editingProfileId = profile.id
                            open(PAGE_PROFILE_EDIT)
                        },
                    ),
                )
            }
            add(
                SettingsRow(
                    title = stringResource(R.string.profile_add),
                    // At the limit the row explains itself instead of opening
                    // an editor whose save button could not do anything.
                    value = if (ProfileCatalog.canAdd(profiles)) {
                        null
                    } else {
                        stringResource(R.string.profile_max)
                    },
                    action = if (ProfileCatalog.canAdd(profiles)) {
                        {
                            nameEditorId = null
                            nameEditorOpen = true
                        }
                    } else {
                        null
                    },
                ),
            )
        }

        PAGE_PROFILE_EDIT -> {
            val person = editingProfile
            if (person == null) {
                emptyList()
            } else {
                buildList {
                    add(
                        SettingsRow(
                            title = stringResource(R.string.profile_switch),
                            value = if (person.id == currentProfileId) {
                                stringResource(R.string.profile_current)
                            } else {
                                null
                            },
                            checked = person.id == currentProfileId,
                            action = if (person.id == currentProfileId) {
                                null
                            } else {
                                { switchProfile(person.id) }
                            },
                        ),
                    )
                    add(
                        SettingsRow(
                            title = stringResource(R.string.profile_name),
                            value = person.name,
                            chevron = true,
                            action = {
                                nameEditorId = person.id
                                nameEditorOpen = true
                            },
                        ),
                    )
                    add(
                        SettingsRow(
                            title = stringResource(R.string.profile_avatar),
                            value = person.emoji,
                            chevron = true,
                            action = {
                                editingProfileId = person.id
                                avatarPickerOpen = true
                            },
                        ),
                    )
                    // Kids Mode rows edit the profile on this page, so a parent
                    // can prepare a kids profile before switching to it. Java
                    // shows the same set on its Profiles page.
                    val kidsId = person.id
                    val kidsOn = person.kids
                    val kidsHasPin = kidsStore.hasPin(kidsId)
                    add(
                        SettingsRow(
                            title = stringResource(R.string.kids_profile_toggle),
                            value = stringResource(
                                if (kidsOn) R.string.saver_on else R.string.saver_off,
                            ),
                            toggleState = kidsOn,
                            action = {
                                if (!kidsOn && !kidsStore.hasPin(kidsId)) {
                                    // Java rule: a kids profile without a PIN
                                    // cannot gate anything, so enabling asks
                                    // for the PIN first; the flag flips on the
                                    // next toggle.
                                    kidsPadFor = kidsId
                                } else {
                                    profiles.firstOrNull { it.id == kidsId }?.let {
                                        profileStore.update(it.copy(kids = !kidsOn))
                                    }
                                    refreshProfiles()
                                }
                            },
                        ),
                    )
                    add(
                        SettingsRow(
                            title = stringResource(R.string.kids_pin),
                            value = stringResource(
                                if (kidsHasPin) R.string.kids_pin_set else R.string.kids_pin_unset,
                            ),
                            chevron = true,
                            action = { kidsPadFor = kidsId },
                        ),
                    )
                    if (kidsHasPin) {
                        add(
                            SettingsRow(
                                title = stringResource(R.string.kids_pin_remove),
                                chevron = true,
                                destructive = true,
                                // Cancel is the safe default, as on the delete page.
                                action = { open(PAGE_KIDS_PIN_REMOVE, focus = 1) },
                            ),
                        )
                    }
                    add(
                        SettingsRow(
                            title = stringResource(R.string.kids_recovery),
                            value = stringResource(
                                if (kidsStore.hasRecovery(kidsId)) {
                                    R.string.kids_recovery_set
                                } else {
                                    R.string.kids_recovery_unset
                                },
                            ),
                            chevron = true,
                            action = {
                                recoverySession++
                                recoveryFor = kidsId
                                recoveryHint = ""
                                recoveryStep = 0
                            },
                        ),
                    )
                    add(
                        SettingsRow(
                            title = stringResource(R.string.kids_allowed),
                            value = kidsStore.allowlist(kidsId).size.toString(),
                            chevron = true,
                            action = { open(PAGE_KIDS_APPS) },
                        ),
                    )
                    if (profiles.size > 1) {
                        add(
                            SettingsRow(
                                title = stringResource(R.string.profile_delete),
                                chevron = true,
                                destructive = true,
                                // Cancel is the safe default: the destructive
                                // row must be reached, not fallen into.
                                action = { open(PAGE_PROFILE_DELETE, focus = 1) },
                            ),
                        )
                    }
                }
            }
        }

        PAGE_PROFILE_DELETE -> {
            val person = editingProfile
            if (person == null) {
                emptyList()
            } else {
                listOf(
                    SettingsRow(
                        title = stringResource(R.string.profile_delete_confirm),
                        destructive = true,
                        action = { deleteEditingProfile() },
                    ),
                    SettingsRow(
                        title = stringResource(R.string.settings_cancel),
                        action = { back() },
                    ),
                )
            }
        }

        PAGE_KIDS_PIN_REMOVE -> {
            val person = editingProfile
            if (person == null) {
                emptyList()
            } else {
                listOf(
                    SettingsRow(
                        title = stringResource(R.string.kids_pin_remove_confirm),
                        destructive = true,
                        action = {
                            // Java rule: a profile without a PIN cannot gate
                            // anything, so removing it also turns Kids Mode off.
                            if (person.kids) {
                                profiles.firstOrNull { it.id == person.id }?.let {
                                    profileStore.update(it.copy(kids = false))
                                }
                                refreshProfiles()
                            }
                            kidsStore.clearPin(person.id)
                            // The page that confirmed is gone; the edit page
                            // opens on the row that explains the new state.
                            history.removeLastOrNull()
                            page = PAGE_PROFILE_EDIT
                            focusIndex = EDIT_KIDS_PIN_ROW
                            pageFocus[PAGE_PROFILE_EDIT] = EDIT_KIDS_PIN_ROW
                        },
                    ),
                    SettingsRow(
                        title = stringResource(R.string.settings_cancel),
                        action = { back() },
                    ),
                )
            }
        }

        PAGE_KIDS_APPS -> {
            val person = editingProfile
            if (person == null) {
                emptyList()
            } else {
                kidsAllowlistApps(catalog.orEmpty(), context.packageName)
                    .map { app ->
                        val on = app.packageName in kidsAllow
                        SettingsRow(
                            title = app.label,
                            value = stringResource(
                                if (on) R.string.saver_on else R.string.saver_off,
                            ),
                            toggleState = on,
                            action = {
                                val next = kidsAllow.toMutableSet()
                                if (on) {
                                    next.remove(app.packageName)
                                } else {
                                    next.add(app.packageName)
                                }
                                kidsStore.setAllowlist(person.id, next)
                                kidsAllow = next
                            },
                        )
                    }
            }
        }

        PAGE_HOME_SCREEN -> listOf(
            SettingsRow(
                title = stringResource(R.string.settings_recommendations),
                value = stringResource(if (contentRecommendations) R.string.saver_on else R.string.saver_off),
                toggleState = contentRecommendations,
                action = {
                    contentRecommendations = !contentRecommendations
                    ContentPreferences.setRecommendations(context, contentRecommendations)
                },
            ),
            SettingsRow(
                title = stringResource(R.string.settings_continue_watching),
                value = stringResource(if (contentContinue) R.string.saver_on else R.string.saver_off),
                toggleState = contentContinue,
                action = {
                    contentContinue = !contentContinue
                    ContentPreferences.setContinueWatching(context, contentContinue)
                },
            ),
            SettingsRow(
                title = stringResource(R.string.settings_listings_access),
                value = stringResource(
                    if (listingsAllowed) {
                        R.string.settings_listings_granted
                    } else {
                        R.string.settings_listings_denied
                    },
                ),
                chevron = !listingsAllowed,
                action = if (listingsAllowed) null else ({ openAppSettings(context) }),
            ),
            SettingsRow(
                title = stringResource(R.string.settings_hidden_apps),
                value = hiddenCount,
                chevron = true,
                action = { open(PAGE_HIDDEN) },
            ),
            SettingsRow(
                title = stringResource(R.string.settings_reset_layout),
                chevron = true,
                // Cancel is the safe default: the destructive row must be reached.
                action = { open(PAGE_RESET, focus = 1) },
            ),
        )

        PAGE_HIDDEN -> hidden.map { app ->
            SettingsRow(
                title = app.label,
                value = showOnHome,
                actionLabel = showOnHome,
                action = { restoreHidden(app.packageName) },
            )
        }

        PAGE_RESET -> listOf(
            SettingsRow(
                title = stringResource(R.string.settings_reset_confirm),
                destructive = true,
                action = { resetLayout() },
            ),
            SettingsRow(
                title = stringResource(R.string.settings_cancel),
                action = { back() },
            ),
        )

        PAGE_LANGUAGE -> LANGUAGE_OPTIONS.map { option ->
            SettingsRow(
                title = languageLabel(option),
                checked = language == option,
                choice = true,
                action = { selectLanguage(context, option) },
            )
        }

        PAGE_WEATHER -> listOf(
            SettingsRow(
                title = stringResource(R.string.weather_show),
                value = stringResource(if (weatherEnabled) R.string.saver_on else R.string.saver_off),
                toggleState = weatherEnabled,
                action = {
                    weatherEnabled = !weatherEnabled
                    WeatherPreferences.setEnabled(context, weatherEnabled)
                    WeatherPreferences.changed(context)
                    if (weatherEnabled) reloadWeather()
                },
            ),
            SettingsRow(
                title = stringResource(R.string.weather_location),
                value = if (weatherManual) {
                    weatherPlace
                } else {
                    stringResource(R.string.weather_automatic)
                },
                chevron = true,
                action = { open(PAGE_WEATHER_LOCATION, focus = if (weatherManual) 1 else 0) },
            ),
            SettingsRow(
                title = stringResource(R.string.weather_temperature),
                value = weatherUnitsLabel(weatherUnits),
                chevron = true,
                action = {
                    open(PAGE_WEATHER_UNITS, focus = WEATHER_UNITS.indexOf(weatherUnits).coerceAtLeast(0))
                },
            ),
            SettingsRow(
                title = stringResource(R.string.weather_source),
                value = weatherSourceLabels[weatherSource] ?: Weather.sourceName(weatherSource),
                chevron = true,
                action = {
                    open(
                        PAGE_WEATHER_SOURCE,
                        focus = Weather.SOURCES.indexOf(weatherSource).coerceAtLeast(0),
                    )
                },
            ),
            SettingsRow(
                title = stringResource(R.string.weather_refresh_every),
                value = stringResource(R.string.weather_minutes, weatherRefresh),
                chevron = true,
                action = {
                    open(
                        PAGE_WEATHER_REFRESH,
                        focus = Weather.REFRESH_CHOICES.indexOf(weatherRefresh).coerceAtLeast(0),
                    )
                },
            ),
            SettingsRow(
                title = stringResource(R.string.weather_last_updated),
                value = weatherAge,
                action = {
                    Toast.makeText(context, weatherUpdatedDetail(), Toast.LENGTH_LONG).show()
                },
            ),
            SettingsRow(
                title = stringResource(R.string.weather_refresh_now),
                value = if (weatherBusy) stringResource(R.string.weather_refreshing_status) else null,
                action = { refreshWeatherNow() },
            ),
        )

        PAGE_WEATHER_LOCATION -> listOf(
            SettingsRow(
                title = stringResource(R.string.weather_automatic),
                checked = !weatherManual,
                choice = true,
                action = {
                    WeatherPreferences.clearPlace(context)
                    weatherEnabled = true
                    weatherManual = false
                    weatherPlace = ""
                    weatherUpdatedAt = WeatherPreferences.cachedAt(context)
                    reloadWeather()
                },
            ),
            SettingsRow(
                title = stringResource(R.string.weather_choose_city),
                value = if (weatherManual) weatherPlace else null,
                checked = weatherManual,
                choice = true,
                action = { citySearchOpen = true },
            ),
        )

        PAGE_WEATHER_UNITS -> WEATHER_UNITS.map { unit ->
            SettingsRow(
                title = weatherUnitsLabel(unit),
                checked = weatherUnits == unit,
                choice = true,
                action = {
                    if (weatherUnits != unit) {
                        weatherUnits = unit
                        WeatherPreferences.setUnits(context, unit)
                        WeatherPreferences.changed(context)
                        reloadWeather()
                    }
                },
            )
        }

        PAGE_WEATHER_SOURCE -> Weather.SOURCES.map { source ->
            SettingsRow(
                title = weatherSourceLabels[source] ?: Weather.sourceName(source),
                checked = weatherSource == source,
                choice = true,
                action = {
                    if (weatherSource != source) {
                        weatherSource = source
                        WeatherPreferences.setSource(context, source)
                        WeatherPreferences.changed(context)
                        reloadWeather()
                    }
                },
            )
        }

        PAGE_WEATHER_REFRESH -> Weather.REFRESH_CHOICES.map { minutes ->
            SettingsRow(
                title = stringResource(
                    if (minutes == WeatherPreferences.DEFAULT_REFRESH) {
                        R.string.weather_minutes_default
                    } else {
                        R.string.weather_minutes
                    },
                    minutes,
                ),
                checked = weatherRefresh == minutes,
                choice = true,
                action = {
                    weatherRefresh = minutes
                    WeatherPreferences.setRefresh(context, minutes)
                },
            )
        }

        PAGE_NETWORK -> listOf(
            SettingsRow(
                title = stringResource(R.string.settings_connection),
                value = networkText ?: stringResource(R.string.settings_loading),
            ),
            SettingsRow(
                title = stringResource(R.string.settings_ip_address),
                value = facts?.ipAddress ?: "—",
            ),
            SettingsRow(
                title = stringResource(R.string.settings_open_network_settings),
                chevron = true,
                action = { openNetworkSettings(context) },
            ),
        )

        PAGE_DEVICE -> deviceRows(facts, units, uptimeWords, formatLocale)

        PAGE_STORAGE -> storageRows(
            facts,
            cacheSizes,
            units,
            formatLocale,
            onClear = { open(PAGE_STORAGE_CLEAR, focus = 1) },
        )

        PAGE_STORAGE_CLEAR -> listOf(
            SettingsRow(
                title = stringResource(R.string.settings_cache_clear_confirm),
                destructive = true,
                action = { confirmClearCaches() },
            ),
            SettingsRow(
                title = stringResource(R.string.settings_cancel),
                action = { back() },
            ),
        )

        PAGE_SYSTEM -> {
            val defaultHome = facts?.defaultHome == true
            // Two steps, as in Java: the row turns the preference on, and the
            // next press sends the user to the switch it still needs.
            val homeAction: (() -> Unit)? = when {
                defaultHome -> null
                !homeOverride ->
                    ({ homeOverride = true; StartupPreferences.setHomeOverride(context, true) })
                accessibility != AccessibilityStatus.State.CONNECTED ->
                    ({ AccessibilityStatus.open(context) })
                else ->
                    ({ homeOverride = false; StartupPreferences.setHomeOverride(context, false) })
            }
            listOfNotNull(
                SettingsRow(
                    title = stringResource(R.string.settings_system_home),
                    value = facts?.let {
                        stringResource(if (it.defaultHome) R.string.settings_yes else R.string.settings_no)
                    } ?: stringResource(R.string.settings_loading),
                ),
                SettingsRow(
                    title = stringResource(R.string.settings_system_open),
                    chevron = true,
                    action = { openSystemSettings(context) },
                ),
                SettingsRow(
                    title = stringResource(R.string.settings_home_button),
                    value = stringResource(
                        when {
                            defaultHome -> R.string.settings_home_button_not_needed
                            !homeOverride -> R.string.settings_home_button_off
                            accessibility == AccessibilityStatus.State.CONNECTED ->
                                R.string.settings_home_button_on
                            accessibility == AccessibilityStatus.State.STALE ->
                                R.string.settings_home_button_stale
                            else -> R.string.settings_home_button_allow
                        },
                    ),
                    action = homeAction,
                ),
                SettingsRow(
                    title = stringResource(R.string.settings_start_open),
                    value = startupLabel,
                    chevron = true,
                    action = {
                        val apps = catalog.orEmpty().filter {
                            it.packageName.isNotEmpty() && it.packageName != context.packageName
                        }
                        val current = StartupPreferences.appPackage(startupTarget)
                        val index = apps.indexOfFirst { it.packageName == current }
                        open(PAGE_STARTUP, focus = if (index < 0) 0 else index + 1)
                    },
                ),
                if (startupTarget.isNotEmpty()) {
                    SettingsRow(
                        title = stringResource(R.string.settings_start_when),
                        value = stringResource(
                            if (startupOnWake) {
                                R.string.settings_start_when_wake
                            } else {
                                R.string.settings_start_when_boot
                            },
                        ),
                        chevron = true,
                        action = { open(PAGE_STARTUP_WHEN, focus = if (startupOnWake) 1 else 0) },
                    )
                } else {
                    null
                },
                // A wake launch needs the same switch as the Home override.
                if (startupTarget.isNotEmpty() &&
                    startupOnWake &&
                    accessibility != AccessibilityStatus.State.CONNECTED
                ) {
                    SettingsRow(
                        title = stringResource(R.string.settings_start_wake),
                        value = stringResource(
                            if (accessibility == AccessibilityStatus.State.STALE) {
                                R.string.settings_home_button_stale
                            } else {
                                R.string.settings_home_button_allow
                            },
                        ),
                        action = { AccessibilityStatus.open(context) },
                    )
                } else {
                    null
                },
                SettingsRow(
                    title = stringResource(R.string.settings_system_airplay),
                    value = facts?.let {
                        stringResource(
                            if (it.airplayInstalled) {
                                R.string.settings_airplay_installed
                            } else {
                                R.string.settings_airplay_missing
                            },
                        )
                    } ?: stringResource(R.string.settings_loading),
                    chevron = facts?.airplayInstalled == true,
                    action = if (facts?.airplayInstalled == true) {
                        { openAirPlay(context) }
                    } else {
                        null
                    },
                    actionLabel = stringResource(R.string.settings_airplay_open),
                ),
            )
        }

        PAGE_PERMISSIONS -> {
            val powerSupported = SleepControls.adminSupported(context)
            // The same rule feeds the root counter: taking the Home button over
            // is a real route, so the row must not ask for the system HOME too.
            val homeReady = PermissionSummary.homeReady(
                defaultHome = defaultHome,
                homeOverride = homeOverride,
                accessibilityConnected = accessibility == AccessibilityStatus.State.CONNECTED,
            )
            listOf(
                SettingsRow(
                    title = stringResource(R.string.settings_perms_home_button),
                    value = stringResource(
                        when (accessibility) {
                            AccessibilityStatus.State.CONNECTED -> R.string.settings_perms_allowed
                            AccessibilityStatus.State.STALE -> R.string.settings_home_button_stale
                            AccessibilityStatus.State.OFF -> R.string.settings_perms_allow
                        },
                    ),
                    action = { AccessibilityStatus.open(context) },
                ),
                SettingsRow(
                    title = stringResource(R.string.settings_perms_listings),
                    value = stringResource(
                        if (listingsAllowed) {
                            R.string.settings_perms_allowed
                        } else {
                            R.string.settings_perms_allow
                        },
                    ),
                    action = if (listingsAllowed) {
                        ({ openAppSettings(context) })
                    } else {
                        ({ listingsPermission.launch(ContentPreferences.READ_TV_LISTINGS) })
                    },
                ),
                SettingsRow(
                    title = stringResource(R.string.settings_perms_power),
                    value = stringResource(
                        when {
                            powerAllowed -> R.string.settings_perms_allowed
                            powerSupported -> R.string.settings_perms_allow
                            else -> R.string.settings_perms_unavailable
                        },
                    ),
                    action = when {
                        powerAllowed -> ({ openAppSettings(context) })
                        powerSupported -> ({ SleepControls.requestPermission(context) })
                        else -> null
                    },
                ),
                SettingsRow(
                    title = stringResource(R.string.settings_perms_home),
                    value = stringResource(
                        if (homeReady) R.string.settings_yes else R.string.settings_perms_setup,
                    ),
                    action = if (homeReady) {
                        null
                    } else {
                        ({ openSystemSettings(context) })
                    },
                ),
                SettingsRow(
                    title = stringResource(R.string.settings_perms_wifi),
                    value = stringResource(
                        if (locationAllowed) {
                            R.string.settings_perms_allowed
                        } else {
                            R.string.settings_perms_allow
                        },
                    ),
                    action = if (locationAllowed) {
                        ({ openAppSettings(context) })
                    } else {
                        ({
                            locationPermission.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                ),
                            )
                        })
                    },
                ),
            )
        }

        PAGE_STARTUP -> buildList {
            add(
                SettingsRow(
                    title = stringResource(R.string.settings_start_home),
                    checked = startupTarget.isEmpty(),
                    choice = true,
                    action = {
                        if (startupTarget.isNotEmpty()) {
                            StartupPreferences.set(context, StartupPreferences.TARGET_HOME, "")
                            startupTarget = StartupPreferences.TARGET_HOME
                            startupLabel = StartupPreferences.label(context)
                        }
                        back()
                    },
                ),
            )
            catalog.orEmpty()
                .filter { it.packageName.isNotEmpty() && it.packageName != context.packageName }
                .forEach { app ->
                    add(
                        SettingsRow(
                            title = app.label,
                            checked = startupTarget == StartupPreferences.targetFor(app.packageName),
                            choice = true,
                            action = {
                                StartupPreferences.set(
                                    context,
                                    StartupPreferences.targetFor(app.packageName),
                                    app.label,
                                )
                                startupTarget = StartupPreferences.targetFor(app.packageName)
                                startupLabel = app.label
                                back()
                            },
                        ),
                    )
                }
        }

        PAGE_STARTUP_WHEN -> listOf(
            SettingsRow(
                title = stringResource(R.string.settings_start_when_boot),
                checked = !startupOnWake,
                choice = true,
                action = {
                    if (startupOnWake) {
                        StartupPreferences.setOnWake(context, false)
                        startupOnWake = false
                    }
                },
            ),
            SettingsRow(
                title = stringResource(R.string.settings_start_when_wake),
                checked = startupOnWake,
                choice = true,
                action = {
                    if (!startupOnWake) {
                        StartupPreferences.setOnWake(context, true)
                        startupOnWake = true
                    }
                },
            ),
        )

        PAGE_DEVICE_GROUP -> deviceGroupRows(
            factsReady = factsReady,
            network = if (factsReady) networkText else stringResource(R.string.settings_loading),
            model = if (factsReady) facts?.model else null,
            freeSpace = freeSpace,
            permissions = permissionsSummary,
            onOpen = ::open,
        )

        PAGE_ABOUT_GROUP -> aboutGroupRows(
            version = BuildConfig.VERSION_NAME,
            onOpen = ::open,
            onRunSetup = {
                context.startActivity(
                    Intent(context, SetupActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        )

        PAGE_UPDATES -> updatesRows(
            clipCount = clipList.size,
            busy = catalogBusy,
            status = catalogStatus,
            onRefresh = {
                if (!catalogBusy) {
                    catalogBusy = true
                    catalogStatus = null
                    scope.launch {
                        // "Already current" is reserved for a feed that was
                        // read and matched; a network or TLS error is a third
                        // state, not a quiet success.
                        val result = refreshCatalog?.invoke()
                            ?: AerialRefresh.update(context)
                        if (clips == null) clipList = AerialCatalog.load(context)
                        catalogBusy = false
                        catalogStatus = when (result) {
                            AerialRefreshResult.UPDATED -> R.string.settings_catalog_updated
                            AerialRefreshResult.UNCHANGED -> R.string.settings_catalog_unchanged
                            AerialRefreshResult.FAILED -> R.string.settings_catalog_failed
                        }
                    }
                }
            },
        )

        PAGE_ABOUT -> aboutInfoRows(
            appName = context.getString(R.string.app_name),
            packageName = context.packageName,
        )

        PAGE_LEGAL -> legalRows(onOpen = ::open)

        PAGE_ROOT -> Section.entries.map { section ->
            SettingsRow(
                title = stringResource(section.title),
                value = when (section) {
                    Section.INTERFACE -> languageSummary
                    Section.HOME_SCREEN -> stringResource(
                        if (contentRecommendations) {
                            R.string.settings_shelf_state_on
                        } else {
                            R.string.settings_shelf_state_off
                        },
                    )
                    Section.PROFILES -> currentProfile?.name
                    Section.WEATHER -> when {
                        !weatherEnabled -> stringResource(R.string.saver_off)
                        weatherPlace.isNotEmpty() -> weatherPlace
                        else -> stringResource(R.string.saver_on)
                    }
                    Section.DEVICE -> if (factsReady) facts?.model else null
                    Section.ABOUT -> BuildConfig.VERSION_NAME
                },
                chevron = true,
                action = { open(pageOf(section)) },
            )
        }

        else -> emptyList()
    }

    val isGrid = page == PAGE_SAVER_CHOOSER
    val isDoc = page in DOC_PAGES
    val title = if (page == PAGE_PROFILE_EDIT) {
        // The page edits one person; its title names them.
        editingProfile?.name ?: stringResource(R.string.settings_profiles)
    } else {
        stringResource(
            when (page) {
                PAGE_ROOT -> R.string.settings_title
                PAGE_INTERFACE -> R.string.settings_interface
                PAGE_HOME_SCREEN -> R.string.settings_home_screen
                PAGE_PROFILES -> R.string.settings_profiles
                PAGE_PROFILE_DELETE -> R.string.profile_delete_title
                PAGE_KIDS_PIN_REMOVE -> R.string.kids_pin_remove_title
                PAGE_KIDS_APPS -> R.string.kids_allowed
                PAGE_SAVER -> R.string.settings_saver
                PAGE_SAVER_CHOOSER -> R.string.saver_choose
                PAGE_SAVER_DELAY -> R.string.saver_start_after
                PAGE_SAVER_THEMES -> R.string.saver_themes
                PAGE_SAVER_REFRESH -> R.string.saver_refresh
                PAGE_WEATHER -> R.string.weather_title
                PAGE_WEATHER_LOCATION -> R.string.weather_location
                PAGE_WEATHER_UNITS -> R.string.weather_temperature
                PAGE_WEATHER_SOURCE -> R.string.weather_source
                PAGE_WEATHER_REFRESH -> R.string.weather_refresh_every
                PAGE_NETWORK -> R.string.settings_network
                PAGE_DEVICE_GROUP -> R.string.settings_device
                PAGE_DEVICE -> R.string.settings_about_device
                PAGE_STORAGE -> R.string.settings_storage
                PAGE_SYSTEM -> R.string.settings_system
                PAGE_PERMISSIONS -> R.string.settings_permissions
                PAGE_STARTUP -> R.string.settings_start_title
                PAGE_STARTUP_WHEN -> R.string.settings_start_when_title
                PAGE_UPDATES -> R.string.settings_updates
                PAGE_ABOUT_GROUP -> R.string.settings_about_system
                PAGE_ABOUT -> R.string.settings_system_info
                PAGE_LEGAL -> R.string.settings_legal
                PAGE_LANGUAGE -> R.string.settings_language
                PAGE_HIDDEN -> R.string.settings_hidden_apps
                PAGE_RESET -> R.string.settings_reset_title
                PAGE_SAVER_RESET -> R.string.saver_reset_title
                PAGE_STORAGE_CLEAR -> R.string.settings_cache_clear_title
                PAGE_DOC_TERMS -> R.string.settings_terms
                PAGE_DOC_PRIVACY -> R.string.settings_privacy
                PAGE_DOC_AERIAL -> R.string.settings_aerial_sources
                else -> R.string.settings_licenses
            },
        )
    }

    val visibleClips = if (isGrid) clipList.filter { it.theme in saverThemes } else emptyList()
    val chooserThemes = if (isGrid) SaverTheme.entries.filter { it in saverThemes } else emptyList()
    val selectedFilter = if (isGrid) chooserThemes.firstOrNull { it.key == filterKey } else null
    val filteredClips = if (isGrid) {
        visibleClips.filter { selectedFilter == null || it.theme == selectedFilter }
    } else {
        emptyList()
    }
    val visibleOn = filteredClips.count { it.id !in hiddenClips }

    // A theme can be switched off on its own page while the filter still points
    // at it; the grid must fall back to "All" instead of staying empty.
    LaunchedEffect(filterKey, saverThemes) {
        if (filterKey != null && chooserThemes.none { it.key == filterKey }) {
            filterKey = null
            sidebarIndex = 0
            focusIndex = 0
        }
    }
    LaunchedEffect(isGrid, filteredClips.size) {
        if (isGrid && focusIndex > filteredClips.lastIndex) {
            focusIndex = filteredClips.lastIndex.coerceAtLeast(0)
        }
    }

    val allLabel = stringResource(R.string.saver_filter_all)
    val themeLabels = chooserThemes.associateWith { saverThemeLabel(it) }
    val categories = if (isGrid) {
        buildList {
            add(
                ChooserCategory(
                    label = allLabel,
                    theme = null,
                    visible = visibleClips.count { it.id !in hiddenClips },
                    total = visibleClips.size,
                ),
            )
            chooserThemes.forEach { theme ->
                val ids = categoryIds(theme)
                add(
                    ChooserCategory(
                        label = themeLabels.getValue(theme),
                        theme = theme,
                        visible = ids.count { it !in hiddenClips },
                        total = ids.size,
                    ),
                )
            }
        }
    } else {
        emptyList()
    }
    var descriptionLines by remember(page) { mutableIntStateOf(1) }
    val description = when (page) {
        PAGE_RESET -> stringResource(R.string.settings_reset_hint)
        PAGE_WEATHER -> stringResource(R.string.weather_hint)
        PAGE_PROFILES -> stringResource(R.string.profile_hint)
        PAGE_PROFILE_DELETE -> stringResource(R.string.profile_delete_hint)
        // Java shows the honest boundary note next to the Kids Mode rows; this
        // page holds those rows, so the note lives under its title.
        PAGE_PROFILE_EDIT -> stringResource(R.string.kids_boundary)
        PAGE_KIDS_PIN_REMOVE -> stringResource(R.string.kids_pin_remove_hint)
        PAGE_KIDS_APPS -> stringResource(R.string.kids_allowed_hint)
        PAGE_SAVER_RESET -> stringResource(R.string.saver_reset_hint)
        PAGE_STORAGE_CLEAR -> stringResource(R.string.settings_cache_clear_hint)
        PAGE_SAVER_THEMES -> stringResource(R.string.saver_themes_hint)
        PAGE_STORAGE -> stringResource(R.string.settings_storage_hint)
        PAGE_NETWORK -> stringResource(R.string.settings_network_hint)
        PAGE_SYSTEM -> stringResource(R.string.settings_system_hint)
        PAGE_STARTUP -> stringResource(R.string.settings_start_hint)
        PAGE_STARTUP_WHEN -> stringResource(R.string.settings_start_when_hint)
        PAGE_UPDATES -> stringResource(R.string.settings_updates_hint)
        PAGE_SAVER_CHOOSER -> pluralStringResource(
            R.plurals.settings_picker_count,
            visibleOn,
            visibleOn,
            filteredClips.size,
        )
        else -> null
    }
    val listTop = if (description != null) {
        DESCRIPTION_TOP + descriptionLines * DESCRIPTION_LINE_HEIGHT + DESCRIPTION_LIST_GAP
    } else {
        LIST_TOP
    }
    val listViewport = TvOs.SCREEN_HEIGHT - listTop - LIST_BOTTOM_INSET

    // Six root rows fit one screen; group headers would add a level without
    // adding meaning, so only subpages carry descriptions.
    val layout = remember(rows) { buildLayout(rows, emptyMap()) }
    val rowTops = remember(layout) {
        layout.filter { it.rowIndex >= 0 }.map { it.top }
    }
    val contentHeight = remember(layout) {
        layout.lastOrNull()?.let { it.top + ROW_HEIGHT } ?: 0f
    }

    val density = LocalDensity.current
    val listScroll = remember { Animatable(0f) }
    LaunchedEffect(page) { listScroll.snapTo(0f) }
    LaunchedEffect(focusIndex, scale, layout) {
        if (isGrid || isDoc) return@LaunchedEffect
        val viewport = with(density) { scale.dp(listViewport).toPx() }
        val content = with(density) { scale.dp(contentHeight).toPx() }
        if (content <= viewport) {
            listScroll.animateTo(0f, tween(TvOs.SCROLL_DURATION_MS))
            return@LaunchedEffect
        }
        val top = rowTops.getOrNull(focusIndex) ?: return@LaunchedEffect
        val topPx = with(density) { scale.dp(top).toPx() }
        val bottomPx = topPx + with(density) { scale.dp(ROW_HEIGHT).toPx() }
        val current = listScroll.value
        val target = when {
            topPx < current -> topPx
            bottomPx > current + viewport -> bottomPx - viewport
            else -> current
        }
        listScroll.animateTo(target, tween(TvOs.SCROLL_DURATION_MS))
    }

    // The documents are text, not a list of buttons: every paragraph is its
    // own focus target, which is what TalkBack reads, and the arrow keys step
    // through them.
    val docScroll = rememberScrollState()
    val docId = remember(page, russian) {
        if (page in DOC_PAGES) docResource(page, russian) else 0
    }
    val docText = remember(docId) {
        if (docId == 0) null else loadRawText(context, docId)
    }
    val docParagraphs = remember(docText) {
        docText.orEmpty().split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
    }
    var docFocus by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(page) {
        if (isDoc) {
            docFocus = 0
            docScroll.scrollTo(0)
        }
    }

    // Removing the row under focus (the last hidden app) must not leave the
    // index past the end of the new list.
    LaunchedEffect(rows.size) {
        if (focusIndex > rows.lastIndex) focusIndex = rows.lastIndex.coerceAtLeast(0)
    }

    val focusRequester = remember { FocusRequester() }
    // Rows are driven by the same custom engine as the grid; TalkBack on a TV
    // reads Android's input focus, so each row is made a real focus target.
    val rowRequesters = remember(page) { mutableMapOf<Int, FocusRequester>() }
    val sidebarRequesters = remember(page) { mutableMapOf<Int, FocusRequester>() }
    val tileRequesters = remember(page) { mutableMapOf<Int, FocusRequester>() }
    val docRequesters = remember(page) { mutableMapOf<Int, FocusRequester>() }
    val docBringIntoView = remember(page) { mutableMapOf<Int, BringIntoViewRequester>() }
    val docRequester = remember { FocusRequester() }
    val emptyRequester = remember { FocusRequester() }
    val gridState = rememberLazyListState()

    LaunchedEffect(
        page,
        focusIndex,
        docFocus,
        rows.size,
        filteredClips.size,
        inSidebar,
        sidebarIndex,
        filterKey,
        focusTick,
    ) {
        if (isDoc) {
            val paragraph = docFocus.coerceIn(0, docParagraphs.lastIndex.coerceAtLeast(0))
            docBringIntoView[paragraph]?.bringIntoView()
            withFrameNanos { }
            delay(32)
        } else if (isGrid && !inSidebar && filteredClips.isNotEmpty()) {
            val focusedRow = focusIndex.coerceIn(0, filteredClips.lastIndex) / GRID_COLUMNS
            val info = gridState.layoutInfo
            val rowItem = info.visibleItemsInfo.firstOrNull { it.index == focusedRow }
            // Scroll by the smallest amount that reveals the row. Aligning it
            // to the top (animateScrollToItem) moved the grid a whole row
            // while the focus moved the other way, and re-running it mid
            // animation read as a spring. A row outside the composed window
            // is estimated from the nearest visible one so it also lands where
            // the step left it instead of jumping to the top of the viewport.
            val delta = if (rowItem == null) {
                val anchor = info.visibleItemsInfo.lastOrNull()
                    ?.takeIf { focusedRow > it.index }
                    ?: info.visibleItemsInfo.firstOrNull()
                        ?.takeIf { focusedRow < it.index }
                if (anchor == null) {
                    gridState.animateScrollToItem(focusedRow)
                    withFrameNanos { }
                    delay(32)
                    null
                } else {
                    val pitch = anchor.size + with(density) { scale.dp(TILE_GAP_Y).toPx() }
                    offscreenRowDelta(
                        anchorIndex = anchor.index,
                        anchorTop = anchor.offset,
                        anchorSize = anchor.size,
                        pitch = pitch,
                        focusedRow = focusedRow,
                        viewportStart = info.viewportStartOffset,
                        viewportEnd = info.viewportEndOffset,
                    )
                }
            } else {
                rowScrollDelta(
                    rowTop = rowItem.offset,
                    rowBottom = rowItem.offset + rowItem.size,
                    viewportStart = info.viewportStartOffset,
                    viewportEnd = info.viewportEndOffset,
                )
            }
            if (delta != null) {
                gridState.animateScrollBy(delta, tween(TvOs.SCROLL_DURATION_MS))
                withFrameNanos { }
                delay(32)
            }
        } else if (!isGrid) {
            delay(60)
        }
        // A package change can empty the open list while its old row still
        // holds a requester; attaching focus to a removed node throws.
        when {
            isDoc -> {
                val paragraph = docFocus.coerceIn(0, docParagraphs.lastIndex.coerceAtLeast(0))
                val target = docRequesters[paragraph]
                    ?: if (docText == null) docRequester else null
                target?.requestFocus()
            }

            isGrid && inSidebar -> {
                val index = sidebarIndex.coerceIn(0, categories.lastIndex.coerceAtLeast(0))
                sidebarRequesters[index]?.requestFocus() ?: focusRequester.requestFocus()
            }

            isGrid -> tileRequesters[focusIndex]?.requestFocus()
                ?: focusRequester.requestFocus()

            page == PAGE_HIDDEN && items != null && rows.isEmpty() ->
                emptyRequester.requestFocus()

            focusIndex in rows.indices ->
                rowRequesters[focusIndex]?.requestFocus() ?: focusRequester.requestFocus()

            else -> focusRequester.requestFocus()
        }
    }

    fun focusSidebar() {
        inSidebar = true
        sidebarIndex = categories.indexOfFirst { it.theme?.key == filterKey }
            .takeIf { it >= 0 } ?: 0
    }

    fun showCategory(category: ChooserCategory) {
        selectFilter(category.theme)
    }

    /** One OK press hides the whole category or brings every clip of it back. */
    fun activateCategory(category: ChooserCategory) {
        val theme = category.theme ?: return
        toggleCategory(theme)
    }

    fun cancelHold() {
        holdJob?.cancel()
        holdJob = null
    }

    /** A focus move while OK is held must not also toggle the clip on release. */
    fun cancelHoldForMove() {
        if (holdJob != null) holdHandled = true
        cancelHold()
    }

    fun openPreview(clip: AerialClip) {
        cancelHold()
        keyRepeat.stopRepeat()
        holdHandled = true
        okDown = false
        previewClip = clip
        previewClosing = false
    }

    fun moveUp() {
        when {
            isDoc -> if (docFocus > 0) docFocus--
            isGrid && inSidebar -> if (sidebarIndex > 0) {
                sidebarIndex--
                showCategory(categories[sidebarIndex])
            }

            isGrid -> {
                cancelHoldForMove()
                if (focusIndex < GRID_COLUMNS) {
                    focusSidebar()
                } else {
                    focusIndex -= GRID_COLUMNS
                }
            }

            focusIndex > 0 -> focusIndex--
        }
    }

    fun moveDown() {
        when {
            isDoc -> if (docFocus < docParagraphs.lastIndex) docFocus++
            isGrid && inSidebar -> if (sidebarIndex < categories.lastIndex) {
                sidebarIndex++
                showCategory(categories[sidebarIndex])
            }

            isGrid -> {
                cancelHoldForMove()
                val last = filteredClips.lastIndex
                if (focusIndex + GRID_COLUMNS <= last) {
                    focusIndex += GRID_COLUMNS
                } else if (last >= 0 && focusIndex < last) {
                    focusIndex = last
                }
            }

            focusIndex < rows.lastIndex -> focusIndex++
        }
    }

    fun moveLeft() {
        when {
            isGrid && inSidebar -> Unit
            isGrid && focusIndex % GRID_COLUMNS == 0 -> {
                cancelHoldForMove()
                focusSidebar()
            }

            isGrid -> {
                cancelHoldForMove()
                focusIndex--
            }
        }
    }

    fun moveRight() {
        when {
            isGrid && inSidebar -> inSidebar = false
            isGrid && focusIndex % GRID_COLUMNS < GRID_COLUMNS - 1 &&
                focusIndex < filteredClips.lastIndex -> {
                cancelHoldForMove()
                focusIndex++
            }
        }
    }

    BackHandler(
        enabled = page != PAGE_ROOT && !citySearchOpen && !nameEditorOpen && !avatarPickerOpen &&
            kidsPadFor == null && recoveryStep == null && !kidsEntryLocked,
    ) { back() }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(LauncherColors.backgroundTop, LauncherColors.backgroundBottom),
                ),
            )
            .focusRequester(focusRequester)
            // Preview sees the release even when a layer consumes it, so the
            // next press of that key is not read as a repeat of the old one.
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) keyRepeat.release(event.key)
                false
            }
            .onKeyEvent { event ->
                // The preview owns the remote while it is up; its own handler
                // consumes every key, and this one only guards the gap.
                if (previewClip != null) return@onKeyEvent false
                // The city picker is a full-screen layer with its own engine.
                if (citySearchOpen) return@onKeyEvent false
                // Name and avatar layers own the remote the same way.
                if (nameEditorOpen || avatarPickerOpen) return@onKeyEvent false
                // Kids surfaces too: the entry gate, the PIN pad and the
                // recovery keyboard each hold the focus while they are up.
                if (kidsEntryLocked || kidsPadFor != null || recoveryStep != null ||
                    kidsLeaveFor != null
                ) {
                    return@onKeyEvent false
                }
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        // A held OK used to restart the preview timer on every
                        // auto-repeat; a held arrow used to cross five rows.
                        if (TvKeyRepeat.isOneShot(event.key) && !keyRepeat.firstPress(event)) {
                            return@onKeyEvent true
                        }
                        when (event.key) {
                            Key.DirectionUp -> {
                                moveUp()
                                keyRepeat.startRepeat(scope, ::moveUp)
                                true
                            }

                            Key.DirectionDown -> {
                                moveDown()
                                keyRepeat.startRepeat(scope, ::moveDown)
                                true
                            }

                            Key.DirectionLeft -> {
                                moveLeft()
                                keyRepeat.startRepeat(scope, ::moveLeft)
                                true
                            }

                            Key.DirectionRight -> {
                                moveRight()
                                keyRepeat.startRepeat(scope, ::moveRight)
                                true
                            }

                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                when {
                                    !isGrid -> rows.getOrNull(focusIndex)?.action?.invoke()
                                    inSidebar -> categories.getOrNull(sidebarIndex)
                                        ?.let(::activateCategory)

                                    else -> {
                                        val clip = filteredClips.getOrNull(focusIndex)
                                        if (clip != null) {
                                            okDown = true
                                            holdHandled = false
                                            cancelHold()
                                            holdJob = scope.launch {
                                                delay(PREVIEW_HOLD_MS)
                                                openPreview(clip)
                                            }
                                        }
                                    }
                                }
                                true
                            }

                            Key.Back -> {
                                back()
                                true
                            }

                            else -> false
                        }
                    }

                    KeyEventType.KeyUp -> when (event.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            cancelHold()
                            if (okDown) {
                                okDown = false
                                if (isGrid && !inSidebar && !holdHandled) {
                                    filteredClips.getOrNull(focusIndex)?.let(::toggleClip)
                                }
                            }
                            holdHandled = false
                            true
                        }

                        else -> false
                    }

                    else -> false
                }
            }
            .focusable(),
    ) {
        Text(
            text = title,
            color = LauncherColors.textPrimary,
            fontSize = scale.sp(TITLE_FONT),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = scale.dp(TITLE_TOP))
                .semantics { heading() },
        )

        // The hint and the picker's refusal note share one column: two separate
        // absolute offsets used to overlap when both were shown.
        if (description != null || (isGrid && pickerRefused)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(scale.dp(DESCRIPTION_LINE_GAP)),
                modifier = Modifier
                    .width(scale.dp(DESCRIPTION_WIDTH))
                    .align(Alignment.TopCenter)
                    .offset(y = scale.dp(DESCRIPTION_TOP)),
            ) {
                description?.let { text ->
                    Text(
                        text = text,
                        color = LauncherColors.textSecondary,
                        fontSize = scale.sp(DESCRIPTION_FONT),
                        lineHeight = scale.sp(DESCRIPTION_LINE_HEIGHT),
                        textAlign = TextAlign.Center,
                        maxLines = DESCRIPTION_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { result ->
                            descriptionLines = result.lineCount.coerceIn(1, DESCRIPTION_MAX_LINES)
                        },
                    )
                }
                if (isGrid && pickerRefused) {
                    Text(
                        text = stringResource(R.string.settings_last_clip_hint),
                        color = LauncherColors.destructive,
                        fontSize = scale.sp(DESCRIPTION_FONT),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        val iconSection = if (page == PAGE_ROOT) {
            Section.entries.getOrNull(focusIndex)
        } else {
            sectionOf(page)
        }
        // Profile pages show the person instead of the section glyph.
        val iconProfile = when (page) {
            PAGE_PROFILES -> currentProfile
            PAGE_PROFILE_EDIT, PAGE_PROFILE_DELETE -> editingProfile
            else -> null
        }
        if (iconSection != null && !isGrid && !isDoc && page != PAGE_SAVER) {
            val iconShape = RoundedCornerShape(scale.dp(ICON_RADIUS))
            Box(
                Modifier
                    .offset(x = scale.dp(ICON_LEFT), y = scale.dp(ICON_TOP))
                    .size(scale.dp(ICON_SIZE))
                    .clip(iconShape)
                    .background(
                        Brush.linearGradient(
                            listOf(TvInk.rowTop, TvInk.rowBottom),
                        ),
                    )
                    .border(2.dp, Color.White.copy(alpha = 0.08f), iconShape),
                contentAlignment = Alignment.Center,
            ) {
                if (iconProfile != null) {
                    val disc = ProfileCatalog.color(iconProfile.color)
                    Box(
                        Modifier
                            .size(scale.dp(ICON_PROFILE_DISC))
                            .clip(CircleShape)
                            .background(Color(disc.background)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = iconProfile.emoji,
                            color = Color(disc.text),
                            fontSize = scale.sp(ICON_PROFILE_EMOJI),
                        )
                    }
                } else {
                    Image(
                        imageVector = iconSection.glyph,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.92f)),
                        modifier = Modifier.size(scale.dp(ICON_GLYPH)),
                    )
                }
            }
        }

        // tvOS shows the current screensaver beside the list; the row only
        // configures it, so the preview is not focusable.
        if (page == PAGE_SAVER) {
            SaverPreview(
                scale = scale,
                clip = SaverPlaylist.previewClip(clipList, saverThemes, hiddenClips),
                themes = saverThemes,
            )
        }

        if (isDoc) {
            Column(
                modifier = Modifier
                    .offset(x = scale.dp(DOC_LEFT), y = scale.dp(DOC_TOP))
                    .width(scale.dp(DOC_WIDTH))
                    .height(scale.dp(TvOs.SCREEN_HEIGHT - DOC_TOP - LIST_BOTTOM_INSET))
                    .verticalScroll(docScroll),
            ) {
                if (docText == null) {
                    Text(
                        text = stringResource(R.string.settings_document_error),
                        color = LauncherColors.textSecondary,
                        fontSize = scale.sp(DOC_FONT),
                        modifier = Modifier
                            .focusRequester(docRequester)
                            .focusable()
                            .semantics(mergeDescendants = true) {},
                    )
                }
                docParagraphs.forEachIndexed { index, paragraph ->
                    if (index > 0) Spacer(Modifier.height(scale.dp(DOC_PARAGRAPH_GAP)))
                    val requester = remember(page, index) { FocusRequester() }
                    val bring = remember(page, index) { BringIntoViewRequester() }
                    docRequesters[index] = requester
                    docBringIntoView[index] = bring
                    // One paragraph per focus stop: TalkBack speaks the text of
                    // the focused node instead of an empty scroll view.
                    Text(
                        text = paragraph,
                        color = LauncherColors.textSecondary,
                        fontSize = scale.sp(DOC_FONT),
                        lineHeight = scale.sp(DOC_LINE_HEIGHT),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(bring)
                            .focusRequester(requester)
                            .focusable()
                            .semantics(mergeDescendants = true) {},
                    )
                }
            }
        } else if (isGrid) {
            // The category list owns the left column. Moving focus onto a row
            // is the filter change; OK then switches the whole category, so a
            // category takes one press and no longer covers the tiles above.
            Column(
                modifier = Modifier
                    .offset(x = scale.dp(SIDEBAR_LEFT), y = scale.dp(GRID_TOP))
                    .width(scale.dp(SIDEBAR_WIDTH)),
                verticalArrangement = Arrangement.spacedBy(scale.dp(SIDEBAR_ROW_GAP)),
            ) {
                categories.forEachIndexed { index, category ->
                    val requester = remember(page, index) { FocusRequester() }
                    sidebarRequesters[index] = requester
                    val filterSelected = category.theme?.key == filterKey ||
                        (category.theme == null && filterKey == null)
                    val count = stringResource(
                        R.string.saver_category_count,
                        category.visible,
                        category.total,
                    )
                    ChooserCategoryRow(
                        label = category.label,
                        count = count,
                        description = category.label + ", " + count,
                        spokenState = when {
                            category.theme == null -> if (filterSelected) {
                                stringResource(R.string.settings_selected)
                            } else {
                                null
                            }

                            category.allHidden && filterSelected ->
                                stringResource(R.string.saver_off) + ", " +
                                    stringResource(R.string.settings_selected)

                            category.allHidden -> stringResource(R.string.saver_off)
                            filterSelected ->
                                stringResource(R.string.saver_on) + ", " +
                                    stringResource(R.string.settings_selected)

                            else -> stringResource(R.string.saver_on)
                        },
                        actionLabel = if (category.theme == null) {
                            stringResource(R.string.saver_filter_all)
                        } else if (category.allHidden) {
                            stringResource(R.string.saver_category_show_all)
                        } else {
                            stringResource(R.string.saver_category_hide_all)
                        },
                        filterSelected = filterSelected,
                        focused = inSidebar && index == sidebarIndex,
                        scale = scale,
                        focusRequester = requester,
                        onActivate = { activateCategory(category) },
                    )
                }
                Spacer(Modifier.height(scale.dp(SIDEBAR_HINT_GAP)))
                Text(
                    text = stringResource(R.string.saver_preview_hint),
                    color = LauncherColors.textSecondary,
                    fontSize = scale.sp(SIDEBAR_HINT_FONT),
                    lineHeight = scale.sp(SIDEBAR_HINT_FONT + 8f),
                    modifier = Modifier
                        .width(scale.dp(SIDEBAR_WIDTH))
                        .padding(horizontal = scale.dp(SIDEBAR_ROW_PADDING)),
                )
            }
            if (filteredClips.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_clips_empty),
                    color = LauncherColors.textSecondary,
                    fontSize = scale.sp(ROW_FONT),
                    modifier = Modifier
                        .offset(x = scale.dp(GRID_LEFT), y = scale.dp(GRID_TOP)),
                )
            }
            CompositionLocalProvider(LocalBringIntoViewSpec provides GridBringIntoViewSpec) {
                LazyColumn(
                    state = gridState,
                    modifier = Modifier
                        .offset(
                            x = scale.dp(GRID_LEFT - GRID_HALO),
                            y = scale.dp(GRID_TOP - GRID_HALO),
                        )
                        .width(scale.dp(GRID_WIDTH + GRID_HALO * 2))
                        .height(
                            scale.dp(
                                TvOs.SCREEN_HEIGHT - GRID_TOP - LIST_BOTTOM_INSET + GRID_HALO * 2,
                            ),
                        )
                        .clipToBounds(),
                    contentPadding = PaddingValues(
                        horizontal = scale.dp(GRID_HALO),
                        vertical = scale.dp(GRID_HALO),
                    ),
                    verticalArrangement = Arrangement.spacedBy(scale.dp(TILE_GAP_Y)),
                ) {
                    val gridRows = (filteredClips.size + GRID_COLUMNS - 1) / GRID_COLUMNS
                    items(count = gridRows) { gridRow ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(scale.dp(TILE_GAP_X)),
                        ) {
                            repeat(GRID_COLUMNS) { column ->
                                val index = gridRow * GRID_COLUMNS + column
                                val clip = filteredClips.getOrNull(index)
                                if (clip != null) {
                                    val requester = remember(page, index) { FocusRequester() }
                                    tileRequesters[index] = requester
                                    SaverTile(
                                        clip = clip,
                                        language = configuration.locales[0]?.language.orEmpty(),
                                        enabled = clip.id !in hiddenClips,
                                        focused = !inSidebar && index == focusIndex,
                                        scale = scale,
                                        focusRequester = requester,
                                        onToggle = { toggleClip(clip) },
                                        onPreview = { openPreview(clip) },
                                    )
                                } else {
                                    Spacer(Modifier.width(scale.dp(TILE_WIDTH)))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .offset(y = scale.dp(listTop))
                    .height(scale.dp(listViewport))
                    .clipToBounds(),
            ) {
                Box(
                    modifier = Modifier
                        .offset(x = scale.dp(LIST_LEFT))
                        .width(scale.dp(LIST_WIDTH))
                        .wrapContentHeight(Alignment.Top, unbounded = true)
                        // Read in the layout phase: the scroll animation must move
                        // the list without recomposing the page every frame.
                        .offset { IntOffset(0, -listScroll.value.roundToInt()) }
                        .then(
                            // Radio rows in one list are one selectable group.
                            if (page == PAGE_LANGUAGE || page == PAGE_SAVER_DELAY ||
                                page == PAGE_SAVER_REFRESH || page == PAGE_WEATHER_LOCATION ||
                                page == PAGE_WEATHER_UNITS || page == PAGE_WEATHER_SOURCE ||
                                page == PAGE_WEATHER_REFRESH
                            ) {
                                Modifier.selectableGroup()
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    if (page == PAGE_HIDDEN && items != null && rows.isEmpty()) {
                        // The only content of the page; without a focus target
                        // TalkBack read the screen as empty.
                        Column(
                            Modifier
                                .offset(y = scale.dp(8f))
                                .focusRequester(emptyRequester)
                                .focusable()
                                .semantics(mergeDescendants = true) {},
                        ) {
                            Text(
                                text = stringResource(R.string.settings_hidden_empty),
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = scale.sp(TvType.subhead),
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.height(scale.dp(10f)))
                            Text(
                                text = stringResource(R.string.settings_hidden_empty_hint),
                                color = LauncherColors.textSecondary,
                                fontSize = scale.sp(TvType.body),
                            )
                        }
                    }
                    layout.forEach { entry ->
                        if (entry.header != null) {
                            Box(
                                Modifier
                                    .offset(y = scale.dp(entry.top))
                                    .height(scale.dp(HEADER_HEIGHT)),
                                contentAlignment = Alignment.BottomStart,
                            ) {
                                Text(
                                    text = entry.header,
                                    color = LauncherColors.textSecondary,
                                    fontSize = scale.sp(HEADER_FONT),
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                        val row = entry.row ?: return@forEach
                        val index = entry.rowIndex
                        val rowRequester = remember(page, index) { FocusRequester() }
                        rowRequesters[index] = rowRequester
                        SettingsRowCard(
                            row = row,
                            focused = index == focusIndex,
                            scale = scale,
                            focusRequester = rowRequester,
                            modifier = Modifier.offset(y = scale.dp(entry.top)),
                        )
                    }
                }
            }
        }
    }

    // The name and avatar layers are the same full-screen surfaces the rest of
    // the launcher uses, so no stock dialog appears in the middle of tvOS rows.
    if (nameEditorOpen) {
        RenameKeyboardOverlay(
            title = stringResource(
                if (nameEditorId == null) R.string.profile_add else R.string.profile_name,
            ),
            initial = if (nameEditorId == null) {
                ""
            } else {
                profiles.firstOrNull { it.id == nameEditorId }?.name.orEmpty()
            },
            scale = scale,
            onDone = { name -> saveProfileName(name) },
            onCancel = { nameEditorOpen = false },
            placeholder = stringResource(R.string.profile_name_hint),
        )
    }

    val avatarOwner = editingProfileId
    if (avatarPickerOpen && avatarOwner != null) {
        ProfileAvatarPicker(
            profiles = profiles,
            editingId = avatarOwner,
            reduceMotion = reduceMotion,
            onChoose = { emoji -> saveProfileAvatar(emoji) },
            onDismiss = { avatarPickerOpen = false },
        )
    }

    // The weather city picker is the same full-screen search the wizard uses;
    // picking a city writes the same preference the Home capsule reads.
    if (citySearchOpen) {
        WeatherCitySearch(
            language = language,
            scale = scale,
            reduceMotion = reduceMotion,
            onClose = { citySearchOpen = false },
            onPick = { place: CityPlace ->
                WeatherPreferences.setPlace(context, place.name, place.lat, place.lon)
                weatherEnabled = true
                weatherManual = true
                weatherPlace = place.name
                weatherUpdatedAt = WeatherPreferences.cachedAt(context)
                citySearchOpen = false
                reloadWeather()
            },
        )
    }

    // The preview is the real saver surface, so it shows the clip as the user
    // will see it. A hold selected this clip; any key hands the chooser back.
    previewClip?.let { clip ->
        val surface = previewSurface
        if (surface != null) {
            surface(clip)
        } else {
            SaverScreen(
                items = listOf(clip),
                closing = previewClosing,
                // Opened by holding OK: the rest of that press (repeats and
                // its release) must not count as the key that leaves.
                swallowOpeningPress = true,
                onCloseRequested = { previewClosing = true },
                onDismissed = {
                    previewClip = null
                    previewClosing = false
                    holdHandled = false
                    focusTick++
                },
            )
        }
    }

    // The pad that sets a profile's PIN (the toggle opens it too, following
    // the Java rule of asking for the PIN before Kids Mode turns on).
    kidsPadFor?.let { id ->
        PinPadOverlay(
            mode = PinPadMode.SET,
            profileId = id,
            reduceMotion = reduceMotion,
            onPassed = { kidsPadFor = null },
            onCancel = { kidsPadFor = null },
        )
    }

    // Leaving the kids profile from Settings goes through the same pad the
    // picker uses: the PIN is the child's lock, so the switch must not happen
    // behind the child's back.
    kidsLeaveFor?.let { target ->
        PinPadOverlay(
            mode = if (kidsLeaveRecover) PinPadMode.RECOVER else PinPadMode.VERIFY,
            profileId = kidsLeaveFrom ?: currentProfileId,
            reduceMotion = reduceMotion,
            onPassed = {
                profileStore.setCurrent(target)
                refreshProfiles()
                kidsLeaveFor = null
                kidsLeaveFrom = null
                kidsLeaveRecover = false
            },
            // Recovery replaces the PIN; the child stays on the kids profile.
            onRecovered = {
                kidsLeaveFor = null
                kidsLeaveFrom = null
                kidsLeaveRecover = false
            },
            onCancel = {
                kidsLeaveFor = null
                kidsLeaveFrom = null
                kidsLeaveRecover = false
            },
            onForgot = if (!kidsLeaveRecover &&
                kidsStore.hasRecovery(kidsLeaveFrom ?: currentProfileId)
            ) {
                { kidsLeaveRecover = true }
            } else {
                null
            },
            subtitle = if (kidsLeaveRecover) null else stringResource(R.string.kids_gate_leave),
        )
    }

    // Recovery word: the hint first, then the answer, the way Java's
    // TextActivity walked the parent through two screens. The keyed step gives
    // the answer a fresh keyboard: the remembered field would otherwise
    // carry the typed hint into the answer field.
    recoveryStep?.let { step ->
        val owner = recoveryFor
        val session = recoverySession
        if (owner != null) {
            key(owner, session, step) {
                val recoveryScope = rememberCoroutineScope()
                var recoveryJob by remember { mutableStateOf<Job?>(null) }
                RenameKeyboardOverlay(
                    title = stringResource(
                        if (step == 0) {
                            R.string.kids_recovery_hint_title
                        } else {
                            R.string.kids_recovery_answer_title
                        },
                    ),
                    initial = "",
                    scale = scale,
                    note = stringResource(
                        if (step == 0) {
                            R.string.kids_recovery_hint_note
                        } else {
                            R.string.kids_recovery_answer_note
                        },
                    ),
                    placeholder = stringResource(
                        if (step == 0) {
                            R.string.kids_recovery_hint_placeholder
                        } else {
                            R.string.kids_recovery_answer_placeholder
                        },
                    ),
                    onDone = { text ->
                        if (recoveryFor == owner && recoverySession == session &&
                            recoveryStep == step && recoveryJob == null
                        ) {
                            if (step == 0) {
                                recoveryHint = text
                                recoveryStep = 1
                            } else {
                                val hint = recoveryHint
                                // Cancel owns this job even before the next composition.
                                recoveryJob = recoveryScope.launch {
                                    kidsStore.setRecovery(owner, hint, text)
                                    if (recoveryFor == owner && recoverySession == session) {
                                        recoveryStep = null
                                        recoveryFor = null
                                    }
                                }
                            }
                        }
                    },
                    onCancel = {
                        recoveryJob?.cancel()
                        if (recoveryFor == owner && recoverySession == session) {
                            recoveryStep = null
                            recoveryFor = null
                        }
                    },
                )
            }
        }
    }

    // The entry gate covers the whole screen, the preview included: while it
    // is up nothing behind it can be reached. Back leaves Settings — the
    // honest equivalent of the Java gate that refused the activity outright.
    if (kidsEntryLocked) {
        PinPadOverlay(
            mode = if (kidsEntryRecover) PinPadMode.RECOVER else PinPadMode.VERIFY,
            profileId = kidsGateProfile?.id ?: currentProfileId,
            reduceMotion = reduceMotion,
            onPassed = {
                kidsEntryLocked = false
                kidsEntryRecover = false
            },
            // Recovery replaces the PIN and returns to the Settings list; the
            // gate is done either way, so cancel must clear the recover flag
            // too or a reopened gate would jump straight into recovery.
            onRecovered = {
                kidsEntryLocked = false
                kidsEntryRecover = false
                onClose()
            },
            onCancel = {
                kidsEntryRecover = false
                onClose()
            },
            // Recovery is offered only when the parent configured it; without
            // a word the pad would lead to a dead end.
            onForgot = if (!kidsEntryRecover && kidsStore.hasRecovery(kidsGateProfile?.id ?: currentProfileId)) {
                { kidsEntryRecover = true }
            } else {
                null
            },
            // The recovering pad must show the stored hint, not the gate copy.
            subtitle = if (kidsEntryRecover) null else stringResource(R.string.kids_gate_settings),
        )
    }
}

@Composable
private fun SettingsRowCard(
    row: SettingsRow,
    focused: Boolean,
    scale: TvScale,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    // One progress drives scale, fill and border, so the row cannot grow before
    // its highlight appears.
    val focusAmount by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(TvOs.FOCUS_MS),
        label = "settingsRowFocus",
    )
    val shape = RoundedCornerShape(scale.dp(ROW_RADIUS))
    // Rows are drawn as separate Texts; a single cleared node keeps TalkBack from
    // stopping twice on the label and its value. Compose 1.7 does not map the
    // `selected` semantics to AccessibilityNodeInfo, so the check is announced
    // through stateDescription instead.
    val announcement = listOfNotNull(row.title, row.value).joinToString(", ")
    val selectedState = stringResource(R.string.settings_selected)
    val onState = stringResource(R.string.settings_state_on)
    val offState = stringResource(R.string.settings_state_off)
    val titleColor = if (row.destructive) {
        LauncherColors.destructive
    } else {
        lerp(Color.White.copy(alpha = 0.95f), Color.Black, focusAmount)
    }
    val valueColor = lerp(Color.White.copy(alpha = 0.5f), Color.Black.copy(alpha = 0.6f), focusAmount)

    Box(
        modifier
            .size(scale.dp(LIST_WIDTH), scale.dp(ROW_HEIGHT))
            .focusRequester(focusRequester)
            .focusable()
            .clearAndSetSemantics {
                contentDescription = announcement
                row.toggleState?.let { on ->
                    stateDescription = if (on) onState else offState
                    toggleableState = if (on) ToggleableState.On else ToggleableState.Off
                }
                if (row.checked) {
                    stateDescription = selectedState
                    selected = true
                }
                // Read-only facts must not promise an action that OK and
                // TalkBack cannot perform.
                row.action?.let { action ->
                    role = when {
                        row.toggleState != null -> Role.Switch
                        row.choice -> Role.RadioButton
                        row.checked -> Role.Checkbox
                        else -> Role.Button
                    }
                    // TalkBack's double-tap does not reach the key handler above.
                    onClick(label = row.actionLabel ?: row.title) { action(); true }
                }
            }
            .graphicsLayer {
                val grow = 1f + (FOCUS_SCALE - 1f) * focusAmount
                scaleX = grow
                scaleY = grow
            }
            .clip(shape)
            .background(
                lerp(Color.White.copy(alpha = 0.10f), Color.White, focusAmount),
            )
            .padding(horizontal = scale.dp(ROW_PADDING)),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.title,
                color = titleColor,
                fontSize = scale.sp(ROW_FONT),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            row.value?.let { value ->
                Text(
                    text = value,
                    color = valueColor,
                    fontSize = scale.sp(ROW_VALUE_FONT),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(
                        max = scale.dp(LIST_WIDTH - ROW_PADDING * 2f - 200f),
                    ),
                )
            }
            if (row.checked) {
                Spacer(Modifier.width(scale.dp(14f)))
                RowCheck(focused = focused, scale = scale)
            }
            if (row.chevron) {
                Spacer(Modifier.width(scale.dp(10f)))
                Image(
                    imageVector = SectionGlyph.chevron,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(
                        lerp(
                            Color.White.copy(alpha = 0.5f),
                            Color.Black.copy(alpha = 0.5f),
                            focusAmount,
                        ),
                    ),
                    modifier = Modifier.size(scale.dp(22f)),
                )
            }
        }
    }
}

@Composable
private fun RowCheck(focused: Boolean, scale: TvScale) {
    val fill = if (focused) Color.Black else Color.White
    val tint = if (focused) Color.White else Color.Black
    Box(
        Modifier
            .size(scale.dp(ROW_CHECK))
            .clip(CircleShape)
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            imageVector = SectionGlyph.check,
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier.size(scale.dp(ROW_CHECK - 4f)),
        )
    }
}

@Composable
private fun ChooserCategoryRow(
    label: String,
    count: String,
    description: String,
    spokenState: String?,
    actionLabel: String,
    filterSelected: Boolean,
    focused: Boolean,
    scale: TvScale,
    focusRequester: FocusRequester,
    onActivate: () -> Unit,
) {
    val focusAmount by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(TvOs.FOCUS_MS),
        label = "chooserCategoryFocus",
    )
    val shape = RoundedCornerShape(scale.dp(SIDEBAR_ROW_RADIUS))
    val fill = when {
        focused -> Color.White
        filterSelected -> Color.White.copy(alpha = 0.2f)
        else -> Color.White.copy(alpha = 0.06f)
    }
    val textColor = when {
        focused -> Color.Black
        filterSelected -> Color.White
        else -> Color.White.copy(alpha = 0.72f)
    }
    val countColor = if (focused) {
        Color.Black.copy(alpha = 0.6f)
    } else {
        Color.White.copy(alpha = 0.5f)
    }
    Box(
        Modifier
            .size(scale.dp(SIDEBAR_WIDTH), scale.dp(SIDEBAR_ROW_HEIGHT))
            .focusRequester(focusRequester)
            .focusable()
            .clearAndSetSemantics {
                contentDescription = description
                spokenState?.let { stateDescription = it }
                if (filterSelected) selected = true
                role = Role.Button
                onClick(label = actionLabel) { onActivate(); true }
            }
            .graphicsLayer {
                val grow = 1f + (SIDEBAR_FOCUS_SCALE - 1f) * focusAmount
                scaleX = grow
                scaleY = grow
            }
            .clip(shape)
            .background(fill)
            .border(
                width = 2.dp,
                color = Color.White.copy(
                    alpha = if (filterSelected && !focused) 0.5f else 0f,
                ),
                shape = shape,
            )
            .padding(horizontal = scale.dp(SIDEBAR_ROW_PADDING)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(scale.dp(SIDEBAR_LABEL_GAP)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = label,
                color = textColor,
                fontSize = scale.sp(SIDEBAR_FONT),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = count,
                color = countColor,
                fontSize = scale.sp(SIDEBAR_COUNT_FONT),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SaverTile(
    clip: AerialClip,
    language: String,
    enabled: Boolean,
    focused: Boolean,
    scale: TvScale,
    focusRequester: FocusRequester,
    onToggle: () -> Unit,
    onPreview: () -> Unit,
) {
    val focusAmount by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(TvOs.FOCUS_MS),
        label = "saverTileFocus",
    )
    val frame = rememberThumbnail(clip.previewUrl)
    val shape = RoundedCornerShape(scale.dp(TILE_RADIUS))
    val onState = stringResource(R.string.saver_on)
    val offState = stringResource(R.string.saver_off)
    val previewLabel = stringResource(R.string.saver_preview_action)
    val name = clip.name(language)

    Column(
        Modifier
            .width(scale.dp(TILE_WIDTH))
            // A real focus target: TalkBack reads the tile instead of the page
            // box. The custom engine still drives the visual focus below.
            .focusRequester(focusRequester)
            .focusable()
            .clearAndSetSemantics {
                contentDescription = name
                stateDescription = if (enabled) onState else offState
                role = Role.Checkbox
                onClick(label = name) { onToggle(); true }
                // A remote hold is the same gesture TalkBack calls a
                // double-tap-and-hold.
                onLongClick(label = previewLabel) { onPreview(); true }
            }
            .graphicsLayer {
                val grow = 1f + (FOCUS_SCALE - 1f) * focusAmount
                scaleX = grow
                scaleY = grow
            },
    ) {
        Box(
            Modifier
                .size(scale.dp(TILE_WIDTH), scale.dp(TILE_HEIGHT))
                .clip(shape)
                .background(TvInk.previewTop)
                .border(
                    width = scale.dp(TvFocus.TILE_STROKE),
                    color = Color.White.copy(alpha = focusAmount),
                    shape = shape,
                ),
        ) {
            frame?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (!enabled) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.62f)),
                )
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(scale.dp(14f))
                    .size(scale.dp(34f))
                    .clip(CircleShape)
                    .background(
                        if (enabled) Color.White else Color.Black.copy(alpha = 0.55f),
                    )
                    .border(
                        width = 2.dp,
                        color = Color.White.copy(alpha = 0.8f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (enabled) {
                    Image(
                        imageVector = SectionGlyph.check,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(Color.Black),
                        modifier = Modifier.size(scale.dp(22f)),
                    )
                }
            }
        }
        Spacer(Modifier.height(scale.dp(TvSpace.sm)))
        Text(
            text = name,
            color = if (enabled) LauncherColors.textPrimary else LauncherColors.textSecondary,
            fontSize = scale.sp(TvType.callout),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(scale.dp(TILE_LABEL_HEIGHT - 8f)),
        )
        // TalkBack paints its focus line inside the node bounds, which used to
        // cut through the glyphs; this margin keeps the line under the text.
        Spacer(Modifier.height(scale.dp(TILE_LABEL_INSET)))
    }
}

/** A tile only exists while it is near the viewport, so only those download. */
@Composable
private fun rememberThumbnail(url: String): Bitmap? {
    val context = LocalContext.current.applicationContext
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url) {
        if (url.isEmpty() || bitmap != null) return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) { AerialPreviewStore.loadThumbnail(context, url) }
    }
    return bitmap
}

// Java sorted the allowlist by label case-insensitively and skipped the
// launcher itself; the same rule feeds the Settings page.
internal fun kidsAllowlistApps(apps: List<TvApp>, ownPackage: String): List<TvApp> =
    apps
        .filter { it.packageName != ownPackage }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

// Negative when the row sits above the viewport, positive when below, null
// when it is already fully visible and the grid must stay put.
internal fun rowScrollDelta(
    rowTop: Int,
    rowBottom: Int,
    viewportStart: Int,
    viewportEnd: Int,
): Float? = when {
    rowTop < viewportStart -> (rowTop - viewportStart).toFloat()
    rowBottom > viewportEnd -> (rowBottom - viewportEnd).toFloat()
    else -> null
}

// A row past the composed window has no layout of its own yet; rows are
// uniform, so its position follows from the nearest visible one and the pitch.
internal fun offscreenRowDelta(
    anchorIndex: Int,
    anchorTop: Int,
    anchorSize: Int,
    pitch: Float,
    focusedRow: Int,
    viewportStart: Int,
    viewportEnd: Int,
): Float? {
    val top = anchorTop + ((focusedRow - anchorIndex) * pitch).toInt()
    return rowScrollDelta(
        rowTop = top,
        rowBottom = top + anchorSize,
        viewportStart = viewportStart,
        viewportEnd = viewportEnd,
    )
}

// Focus asks the list to reveal the focused tile on its own, and the Android
// default spec pulls that tile to a third of the viewport with a second
// animation. On top of the row tween above it read as a spring: the grid
// moved by our delta, then slid again. Reveal only what is actually clipped
// instead, so a fully visible row stays put and the tween is the only motion.
@OptIn(ExperimentalFoundationApi::class)
@Suppress("DEPRECATION")
internal val GridBringIntoViewSpec: BringIntoViewSpec = object : BringIntoViewSpec {
    override val scrollAnimationSpec: AnimationSpec<Float> = tween(TvOs.SCROLL_DURATION_MS)

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val above = -offset
        val below = containerSize - offset - size
        return when {
            above > 0f -> above
            below < 0f -> below
            else -> 0f
        }
    }
}

private fun buildLayout(
    rows: List<SettingsRow>,
    headers: Map<Int, String>,
): List<ListEntry> {
    val entries = mutableListOf<ListEntry>()
    var top = 0f
    rows.forEachIndexed { index, row ->
        headers[index]?.let { header ->
            entries += ListEntry(top = top, header = header)
            top += HEADER_HEIGHT + HEADER_GAP
        }
        entries += ListEntry(top = top, row = row, rowIndex = index)
        top += ROW_PITCH
    }
    return entries
}

// The page builders below are separate composables on purpose: SettingsScreen
// already sits close to the JVM 64 KB method limit, and inlining another set of
// rows pushes the compiler over it.

@Composable
private fun deviceGroupRows(
    factsReady: Boolean,
    network: String?,
    model: String?,
    freeSpace: String?,
    permissions: String,
    onOpen: (Int) -> Unit,
): List<SettingsRow> = listOf(
    SettingsRow(
        title = stringResource(R.string.settings_network),
        value = network,
        chevron = true,
        action = { onOpen(PAGE_NETWORK) },
    ),
    SettingsRow(
        title = stringResource(R.string.settings_about_device),
        value = model,
        chevron = true,
        action = { onOpen(PAGE_DEVICE) },
    ),
    SettingsRow(
        title = stringResource(R.string.settings_storage),
        value = freeSpace,
        chevron = true,
        action = { onOpen(PAGE_STORAGE) },
    ),
    SettingsRow(
        title = stringResource(R.string.settings_permissions),
        value = permissions,
        chevron = true,
        action = { onOpen(PAGE_PERMISSIONS) },
    ),
    SettingsRow(
        // The Android version and build channel live on the System page, not
        // on this row: one fact, one place.
        title = stringResource(R.string.settings_system),
        chevron = true,
        action = { onOpen(PAGE_SYSTEM) },
    ),
)

@Composable
private fun aboutGroupRows(
    version: String,
    onOpen: (Int) -> Unit,
    onRunSetup: () -> Unit,
): List<SettingsRow> = listOf(
    SettingsRow(
        title = stringResource(R.string.settings_updates),
        value = version,
        chevron = true,
        action = { onOpen(PAGE_UPDATES) },
    ),
    SettingsRow(
        title = stringResource(R.string.settings_system_info),
        chevron = true,
        action = { onOpen(PAGE_ABOUT) },
    ),
    SettingsRow(
        title = stringResource(R.string.settings_legal),
        chevron = true,
        action = { onOpen(PAGE_LEGAL) },
    ),
    SettingsRow(
        // The wizard keeps current values and asks nothing twice; this row is
        // the way back after a language or shelf mistake.
        title = stringResource(R.string.settings_run_setup),
        chevron = true,
        action = onRunSetup,
    ),
)

@Composable
private fun updatesRows(
    clipCount: Int,
    busy: Boolean,
    @StringRes status: Int?,
    onRefresh: () -> Unit,
): List<SettingsRow> = listOf(
    SettingsRow(
        title = stringResource(R.string.settings_aerial_catalog),
        value = pluralStringResource(R.plurals.settings_clips_count, clipCount, clipCount),
    ),
    SettingsRow(
        title = stringResource(R.string.settings_aerial_update),
        value = when {
            busy -> stringResource(R.string.settings_catalog_checking)
            status != null -> stringResource(status)
            else -> null
        },
        action = onRefresh,
    ),
)

@Composable
private fun aboutInfoRows(appName: String, packageName: String): List<SettingsRow> = listOf(
    SettingsRow(
        title = stringResource(R.string.settings_launcher_name),
        value = appName,
    ),
    SettingsRow(
        title = stringResource(R.string.settings_developer),
        value = stringResource(R.string.settings_developer_value),
    ),
    SettingsRow(
        title = stringResource(R.string.settings_version),
        value = BuildConfig.VERSION_NAME,
    ),
    SettingsRow(
        title = stringResource(R.string.settings_build),
        value = BuildConfig.VERSION_CODE.toString(),
    ),
    SettingsRow(
        title = stringResource(R.string.settings_build_channel),
        value = stringResource(
            if (BuildConfig.DEBUG) R.string.settings_build_debug else R.string.settings_build_stable,
        ),
    ),
    SettingsRow(
        title = stringResource(R.string.settings_package),
        value = packageName,
    ),
)

@Composable
private fun legalRows(onOpen: (Int) -> Unit): List<SettingsRow> = listOf(
    SettingsRow(
        title = stringResource(R.string.settings_terms),
        chevron = true,
        action = { onOpen(PAGE_DOC_TERMS) },
    ),
    SettingsRow(
        title = stringResource(R.string.settings_privacy),
        chevron = true,
        action = { onOpen(PAGE_DOC_PRIVACY) },
    ),
    SettingsRow(
        title = stringResource(R.string.settings_aerial_sources),
        chevron = true,
        action = { onOpen(PAGE_DOC_AERIAL) },
    ),
    SettingsRow(
        title = stringResource(R.string.settings_licenses),
        chevron = true,
        action = { onOpen(PAGE_DOC_LICENSES) },
    ),
)

private fun pageOf(section: Section): Int = when (section) {    Section.INTERFACE -> PAGE_INTERFACE
    Section.HOME_SCREEN -> PAGE_HOME_SCREEN
    Section.PROFILES -> PAGE_PROFILES
    Section.WEATHER -> PAGE_WEATHER
    Section.DEVICE -> PAGE_DEVICE_GROUP
    Section.ABOUT -> PAGE_ABOUT_GROUP
}

private fun sectionOf(page: Int): Section? = when (page) {
    PAGE_INTERFACE, PAGE_LANGUAGE,
    PAGE_SAVER, PAGE_SAVER_CHOOSER, PAGE_SAVER_DELAY, PAGE_SAVER_THEMES, PAGE_SAVER_REFRESH,
    PAGE_SAVER_RESET,
    -> Section.INTERFACE
    PAGE_HOME_SCREEN, PAGE_HIDDEN, PAGE_RESET -> Section.HOME_SCREEN
    PAGE_PROFILES, PAGE_PROFILE_EDIT, PAGE_PROFILE_DELETE, PAGE_KIDS_PIN_REMOVE, PAGE_KIDS_APPS ->
        Section.PROFILES
    PAGE_WEATHER, PAGE_WEATHER_LOCATION, PAGE_WEATHER_UNITS, PAGE_WEATHER_SOURCE,
    PAGE_WEATHER_REFRESH,
    -> Section.WEATHER
    PAGE_DEVICE_GROUP, PAGE_NETWORK, PAGE_DEVICE, PAGE_STORAGE, PAGE_STORAGE_CLEAR,
    PAGE_SYSTEM, PAGE_STARTUP, PAGE_STARTUP_WHEN, PAGE_PERMISSIONS,
    -> Section.DEVICE
    PAGE_ABOUT_GROUP, PAGE_UPDATES, PAGE_ABOUT, PAGE_LEGAL,
    PAGE_DOC_TERMS, PAGE_DOC_PRIVACY, PAGE_DOC_AERIAL, PAGE_DOC_LICENSES,
    -> Section.ABOUT
    else -> null
}

private fun docResource(page: Int, russian: Boolean): Int = when (page) {
    PAGE_DOC_TERMS -> if (russian) R.raw.terms_ru else R.raw.terms_en
    PAGE_DOC_PRIVACY -> if (russian) R.raw.privacy_ru else R.raw.privacy_en
    PAGE_DOC_AERIAL -> if (russian) R.raw.aerial_sources_ru else R.raw.aerial_sources_en
    else -> R.raw.open_source_licenses
}

private fun loadRawText(context: Context, resource: Int): String? = runCatching {
    context.resources.openRawResource(resource)
        .bufferedReader()
        .use { it.readText() }
}.getOrNull()

private fun openNetworkSettings(context: Context) {
    val opened = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess
    if (!opened) showUnavailable(context)
}

// Not every TV build has a dedicated Home-screen settings screen, so fall back
// to the system settings entry instead of doing nothing.
private fun openSystemSettings(context: Context) {
    val opened = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess || runCatching {
        context.startActivity(
            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess
    if (!opened) showUnavailable(context)
}

// Revoking a normal permission is a system screen, not a launcher dialog: what
// the user changes there is the real grant state, not a copy of it.
private fun openAppSettings(context: Context) {
    val opened = runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess
    if (!opened) showUnavailable(context)
}

private fun openAirPlay(context: Context) {
    val intent = context.packageManager
        .getLaunchIntentForPackage(DeviceFactsReader.AIRPLAY_PACKAGE)
    if (intent == null) {
        showUnavailable(context)
        return
    }
    val opened = runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
    if (!opened) showUnavailable(context)
}

// A row that cannot open anything must say so; a silent no-op reads as a
// broken remote.
private fun showUnavailable(context: Context) {
    Toast.makeText(context, R.string.settings_system_unavailable, Toast.LENGTH_SHORT).show()
}

@Composable
private fun deviceRows(
    facts: DeviceFacts?,
    units: List<String>,
    words: Triple<String, String, String>,
    locale: Locale,
): List<SettingsRow> {
    if (facts == null) {
        return listOf(SettingsRow(title = stringResource(R.string.settings_loading)))
    }
    val dash = "—"
    // A display the system did not report is unknown, not a 0 × 0 panel.
    val display = if (facts.screenWidth != null && facts.screenHeight != null) {
        stringResource(
            R.string.settings_device_display_value,
            facts.screenWidth,
            facts.screenHeight,
            DeviceFormat.hertz(facts.refreshHz, locale),
        )
    } else {
        dash
    }
    val memory = if (facts.ramFree != null && facts.ramTotal != null) {
        stringResource(
            R.string.settings_device_memory_value,
            DeviceFormat.size(facts.ramFree, units, locale),
            DeviceFormat.size(facts.ramTotal, units, locale),
        )
    } else {
        dash
    }
    return buildList {
        add(SettingsRow(stringResource(R.string.settings_device_manufacturer), facts.manufacturer))
        add(SettingsRow(stringResource(R.string.settings_device_model), facts.model))
        add(
            SettingsRow(
                stringResource(R.string.settings_device_android),
                stringResource(
                    R.string.settings_device_android_value,
                    facts.android,
                    "API ${facts.api}",
                ),
            ),
        )
        add(SettingsRow(stringResource(R.string.settings_device_patch), facts.securityPatch))
        add(SettingsRow(stringResource(R.string.settings_device_build), facts.buildDisplay))
        add(SettingsRow(stringResource(R.string.settings_device_display), display))
        add(SettingsRow(stringResource(R.string.settings_device_abi), facts.abis))
        add(SettingsRow(stringResource(R.string.settings_device_memory), memory))
        add(
            SettingsRow(
                stringResource(R.string.settings_device_uptime),
                DeviceFormat.uptime(facts.uptimeMs, words.first, words.second, words.third),
            ),
        )
        add(SettingsRow(stringResource(R.string.settings_device_locale), facts.locale))
        add(SettingsRow(stringResource(R.string.settings_device_timezone), facts.timeZone))
        // Read-only: the profile is derived from the heap limit and the panel's
        // thermal state, not a preference the owner could pick well.
        add(
            SettingsRow(
                stringResource(R.string.settings_device_performance),
                stringResource(
                    when (Adaptive.current) {
                        PerformanceProfile.HIGH -> R.string.settings_device_performance_high
                        PerformanceProfile.BALANCED -> R.string.settings_device_performance_balanced
                        PerformanceProfile.LOW -> R.string.settings_device_performance_low
                    },
                ),
            ),
        )
        if (facts.lowRam) {
            add(
                SettingsRow(
                    stringResource(R.string.settings_device_low_ram),
                    stringResource(R.string.settings_yes),
                ),
            )
        }
    }
}

@Composable
private fun storageRows(
    facts: DeviceFacts?,
    caches: CacheSizes?,
    units: List<String>,
    locale: Locale,
    onClear: () -> Unit,
): List<SettingsRow> {
    if (facts == null) {
        return listOf(SettingsRow(title = stringResource(R.string.settings_loading)))
    }
    val dash = "—"
    val total = facts.storageTotal
    val free = facts.storageFree
    return buildList {
        add(
            SettingsRow(
                stringResource(R.string.settings_storage_total),
                total?.let { DeviceFormat.size(it, units, locale) } ?: dash,
            ),
        )
        add(
            SettingsRow(
                stringResource(R.string.settings_storage_free),
                free?.let { DeviceFormat.size(it, units, locale) } ?: dash,
            ),
        )
        add(
            SettingsRow(
                stringResource(R.string.settings_storage_used),
                if (total != null && free != null) {
                    DeviceFormat.size((total - free).coerceAtLeast(0L), units, locale)
                } else {
                    dash
                },
            ),
        )
        add(
            SettingsRow(
                stringResource(R.string.settings_storage_stills),
                caches?.let { DeviceFormat.size(it.stills, units, locale) } ?: dash,
            ),
        )
        add(
            SettingsRow(
                stringResource(R.string.settings_storage_posters),
                caches?.let { DeviceFormat.size(it.posters, units, locale) } ?: dash,
            ),
        )
        add(
            SettingsRow(
                title = stringResource(R.string.settings_storage_clear),
                value = caches?.let { DeviceFormat.size(it.total, units, locale) },
                destructive = true,
                action = onClear,
            ),
        )
    }
}

@Composable
private fun networkLabel(kind: NetworkKind): String = stringResource(
    when (kind) {
        NetworkKind.WIFI -> R.string.settings_network_wifi
        NetworkKind.ETHERNET -> R.string.settings_network_ethernet
        NetworkKind.MOBILE -> R.string.settings_network_mobile
        NetworkKind.OTHER -> R.string.settings_network_other
        NetworkKind.NONE -> R.string.settings_network_none
    },
)

@Composable
private fun languageLabel(language: String): String = stringResource(
    when (language) {
        LocalePreference.RUSSIAN -> R.string.settings_language_russian
        LocalePreference.ENGLISH -> R.string.settings_language_english
        else -> R.string.settings_language_system
    },
)

@Composable
private fun weatherUnitsLabel(unit: String): String = stringResource(
    when (unit) {
        WeatherPreferences.UNIT_C -> R.string.weather_units_c
        WeatherPreferences.UNIT_F -> R.string.weather_units_f
        else -> R.string.weather_automatic
    },
)

@Composable
private fun saverDelayLabel(delay: SaverDelay): String = stringResource(
    when (delay) {
        SaverDelay.TWO -> R.string.saver_delay_2
        SaverDelay.FIVE -> R.string.saver_delay_5
        SaverDelay.TEN -> R.string.saver_delay_10
        SaverDelay.TWENTY -> R.string.saver_delay_20
        SaverDelay.NEVER -> R.string.saver_never
    },
)

@Composable
private fun saverRefreshLabel(refresh: SaverRefresh): String = stringResource(
    when (refresh) {
        SaverRefresh.DAILY -> R.string.saver_refresh_daily
        SaverRefresh.WEEKLY -> R.string.saver_refresh_weekly
        SaverRefresh.MONTHLY -> R.string.saver_refresh_monthly
        SaverRefresh.NEVER -> R.string.saver_refresh_never
    },
)

private fun selectLanguage(context: Context, language: String) {
    if (LocalePreference.get(context) == language) return
    LocalePreference.set(context, language)
    (context as? Activity)?.recreate()
}
