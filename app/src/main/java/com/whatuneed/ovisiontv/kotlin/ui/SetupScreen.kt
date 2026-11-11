package com.whatuneed.ovisiontv.kotlin.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.data.AccessibilityStatus
import com.whatuneed.ovisiontv.kotlin.data.CityPlace
import com.whatuneed.ovisiontv.kotlin.data.CitySearch
import com.whatuneed.ovisiontv.kotlin.data.ContentPreferences
import com.whatuneed.ovisiontv.kotlin.data.DeviceFactsReader
import com.whatuneed.ovisiontv.kotlin.data.LocalePreference
import com.whatuneed.ovisiontv.kotlin.data.Profile
import com.whatuneed.ovisiontv.kotlin.data.ProfileCatalog
import com.whatuneed.ovisiontv.kotlin.data.ProfileStore
import com.whatuneed.ovisiontv.kotlin.data.SaverDelay
import com.whatuneed.ovisiontv.kotlin.data.SaverPreferences
import com.whatuneed.ovisiontv.kotlin.data.SaverTheme
import com.whatuneed.ovisiontv.kotlin.data.SetupProgress
import com.whatuneed.ovisiontv.kotlin.data.Wallpaper
import com.whatuneed.ovisiontv.kotlin.data.WeatherPreferences
import com.whatuneed.ovisiontv.kotlin.ui.components.ProfileAvatarPicker
import com.whatuneed.ovisiontv.kotlin.ui.components.RenameKeyboardOverlay
import com.whatuneed.ovisiontv.kotlin.ui.components.SectionGlyph
import com.whatuneed.ovisiontv.kotlin.ui.components.TvKeyRepeat
import com.whatuneed.ovisiontv.kotlin.ui.components.TvResultRow
import com.whatuneed.ovisiontv.kotlin.ui.components.TvSearchField
import com.whatuneed.ovisiontv.kotlin.ui.components.rememberTvKeyRepeat
import com.whatuneed.ovisiontv.kotlin.ui.components.CONTENT_PADDING
import com.whatuneed.ovisiontv.kotlin.ui.components.KeyAction
import com.whatuneed.ovisiontv.kotlin.ui.components.KeyboardMode
import com.whatuneed.ovisiontv.kotlin.ui.components.PANEL_HEIGHT
import com.whatuneed.ovisiontv.kotlin.ui.components.KeyboardPanel
import com.whatuneed.ovisiontv.kotlin.ui.components.KeyboardState
import com.whatuneed.ovisiontv.kotlin.ui.components.KeyboardKeys
import com.whatuneed.ovisiontv.kotlin.ui.components.keysHeight
import com.whatuneed.ovisiontv.kotlin.ui.components.rememberKeyLabels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import java.util.Locale

// Every number here is the Java wizard's design unit doubled: its canvas is
// 960×540 and one unit is two points on a 1080p screen, while this file already
// works in those 1920×1080 points. That is what keeps the two wizards the same
// size — title 34→68, row 46→92, list 440→880. Values that only this file has
// (the greeting, which Java drew at 64) are marked where they appear.
private const val LIST_WIDTH = 880f
private const val ROW_HEIGHT = 92f
private const val ROW_HEIGHT_DETAIL = 112f
private const val ROW_GAP = 12f
private const val ROW_RADIUS = 24f
private const val ROW_PADDING = 36f
private const val ROW_PADDING_END = 32f
private const val ROW_FONT = TvType.subhead
private const val ROW_DETAIL_FONT = TvType.callout
private const val ROW_VALUE_FONT = TvType.body
private const val ROW_MARK = 36f
private const val ROW_FOCUS_SCALE = 1.04f

private const val TITLE_FONT = TvType.hero
private const val SUBTITLE_FONT = TvType.body
private const val SUBTITLE_WIDTH = 1040f
// Fits every current hint in both languages: the longest Russian one (profile)
// takes three lines at this width, and the finish summary lists four values.
private const val SUBTITLE_LINES = 4
private const val TITLE_TOP = 108f
private const val TITLE_GAP = 20f
private const val SUBTITLE_LIST_GAP = 44f
// Java raised the greeting to 64 units and its hint to 17.
private const val GREETING_FONT = TvType.display
private const val GREETING_HINT_FONT = TvType.subhead
// Java's finish disc: 64 units across, the tick stroked at 9% of it.
private const val FINISH_DISC = 128f
private const val FINISH_DISC_GAP = 36f

private const val BUTTON_HEIGHT = 80f
private const val BUTTON_RADIUS = 40f
private const val BUTTON_FONT = TvType.subhead
private const val BUTTON_MIN_WIDTH = 300f
private const val BUTTON_PADDING = 52f
private const val BUTTON_GAP = 16f
private const val BUTTON_FOCUS_SCALE = 1.08f
private const val LIST_BUTTON_GAP = 20f
// Java always kept at least 16 units between the buttons and the dots.
private const val BUTTON_DOTS_GAP = 32f

private const val DOT_SIZE = 12f
private const val DOT_GAP = 8f

// The page transition moves the whole page as one piece: a 30-point travel on
// x plus a fade, exactly the two timings the Java wizard used — the shorter one
// on the very first paint, 240 ms on every page after (Motion.d). A
// per-element cascade reads as lag on a TV, so nothing here staggers.
private const val PAGE_SLIDE = 30f
private const val PAGE_FIRST_MS = 120

// The wizard's backdrop is the launcher's only colour field, drawn as
// decoration at the alpha Java used for every page but the Look step (0.5).
private const val BACKDROP_DIM = 0.5f

// The city picker reuses the app's Search split: field and keyboard on the
// left, the results column on the right (09_SEARCH §7: tvOS builds Search in
// TVML at runtime, so these are project values, not measured Apple ones).
private const val CITY_TITLE_X = 90f
private const val CITY_TITLE_Y = 58f
private const val CITY_FIELD_X = 90f
private const val CITY_FIELD_Y = 140f
private const val CITY_KEYBOARD_X = 60f
private const val CITY_KEYBOARD_Y = 470f
private const val CITY_KEYBOARD_WIDTH = 1040f
private const val CITY_RESULTS_X = 1140f
private const val CITY_RESULTS_Y = 250f
private const val CITY_RESULTS_WIDTH = 720f
private const val CITY_RESULTS_HEIGHT = 760f
private const val CITY_ROW_HEIGHT = 96f
private const val CITY_ROW_GAP = 12f
private const val CITY_ROW_PITCH = CITY_ROW_HEIGHT + CITY_ROW_GAP
private const val CITY_VISIBLE_ROWS = 7
private const val CITY_DEBOUNCE_MS = 350L
private const val CITY_MIN_QUERY = 2

private const val PAGE_WELCOME = 0
private const val PAGE_LANGUAGE = 1
private const val PAGE_PROFILE = 2
private const val PAGE_HOME = 3
private const val PAGE_WEATHER = 4
private const val PAGE_SAVER = 5
private const val PAGE_SAVER_THEMES = 6
private const val PAGE_ACCESS = 7
private const val PAGE_FINISH = 8
private const val PAGE_COUNT = 9

private enum class RowKind { TOGGLE, RADIO, ACTION }

private data class SetupRow(
    val title: String,
    val detail: String?,
    val kind: RowKind,
    val checked: Boolean = false,
    val value: String? = null,
    // An ACTION row that is already done: the value turns into the green
    // check state the Java wizard painted on its permission rows.
    val ok: Boolean = false,
    val action: (() -> Unit)? = null,
)

private data class SetupPage(
    val title: String,
    val subtitle: String,
    val rows: List<SetupRow>,
    val primary: String,
    val onPrimary: () -> Unit,
    // tvOS opens with a buttonless "Hello": OK anywhere continues. Only the
    // welcome page uses it; every other page keeps its explicit buttons.
    val pressAnywhere: Boolean = false,
    // The Java wizard put a weight-1 spacer above the pages that carry only a
    // few rows (language, hello, finish), so the block sits in the middle of
    // the screen instead of hanging from the top edge.
    val centered: Boolean = false,
    // The greeting's hint is the one subtitle Java sized a step larger.
    val subtitleFont: Float = SUBTITLE_FONT,
)

/**
 * First run of OVisionTV_Kotlin. The Java launcher asked eight questions; the
 * Kotlin wizard keeps the pages that change something real: language, the
 * shelf, the weather card, the screen saver, and the Home/listings access.
 * Nothing here grants a permission by itself: the system screens open only
 * after the viewer chooses the row, and the weather needs none at all — the
 * place comes from the internet address.
 *
 * The order follows the dissected tvOS TVSetup.app (TVOS27_RESEARCH/22): a
 * buttonless welcome that answers OK, then language. Resume, re-run and page
 * order follow PHASE 26: the step is saved on every page, a re-run starts from
 * the first page and keeps every current value.
 */
@Composable
fun SetupScreen(
    resumeTick: Int,
    onLanguageChanged: (String) -> Unit,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val scale = rememberTvScale()
    val keyRepeat = rememberTvKeyRepeat()
    val scope = rememberCoroutineScope()
    val rerun = remember { SetupProgress.done(context) }
    val profileStore = remember(context) { ProfileStore(context) }

    var page by rememberSaveable {
        mutableIntStateOf(
            SetupProgress.resumeStep(
                done = rerun,
                step = SetupProgress.step(context),
                version = SetupProgress.stepVersion(context),
                currentVersion = SetupProgress.STEP_VERSION,
                count = PAGE_COUNT,
            ),
        )
    }
    var focus by remember(page) { mutableIntStateOf(0) }
    var inButtons by remember(page) { mutableStateOf(false) }
    var buttonIndex by remember(page) { mutableIntStateOf(0) }

    // The profile page edits the profile the wizard created (or the current
    // one on a re-run). Its two editors are the launcher's own surfaces, not
    // stock dialogs, so they behave the same here as in Settings.
    var profile by remember(resumeTick) { mutableStateOf(profileStore.current()) }
    var profileNameOpen by remember { mutableStateOf(false) }
    var profileAvatarOpen by remember { mutableStateOf(false) }

    // The GUI-code backdrop: the same aurora field Java drew behind every page.
    // It is built once, off the main thread, and never rebuilt — the wizard has
    // no Look step, so the style cannot change while it is up.
    var backdrop by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(Unit) {
        backdrop = withContext(Dispatchers.IO) { Wallpaper.render(context, Wallpaper.DEFAULT) }
    }

    // Which way the page slides in. Java pushed forward pages in from the right
    // and Back pages from the left, so the direction is read from the page
    // change itself rather than assumed.
    var lastPage by remember { mutableIntStateOf(page) }
    var slideFrom by remember { mutableIntStateOf(1) }
    if (page != lastPage) {
        slideFrom = if (page > lastPage) 1 else -1
        lastPage = page
    }

    var language by remember(resumeTick) { mutableStateOf(LocalePreference.get(context)) }
    var recommendations by remember(resumeTick) {
        mutableStateOf(ContentPreferences.recommendations(context))
    }
    var continueWatching by remember(resumeTick) {
        mutableStateOf(ContentPreferences.continueWatching(context))
    }
    var delay by remember { mutableStateOf(SaverPreferences.delay(context)) }
    var themes by remember { mutableStateOf(SaverPreferences.themes(context)) }
    var listingsGranted by remember(resumeTick) {
        mutableStateOf(ContentPreferences.canReadListings(context))
    }
    var defaultHome by remember(resumeTick) {
        mutableStateOf(DeviceFactsReader.isDefaultHome(context))
    }
    var accessibility by remember(resumeTick) {
        mutableStateOf(AccessibilityStatus.state(context))
    }
    var weatherEnabled by remember(resumeTick) {
        mutableStateOf(WeatherPreferences.enabled(context))
    }
    var weatherManual by remember(resumeTick) {
        mutableStateOf(WeatherPreferences.manual(context))
    }
    var weatherPlace by remember(resumeTick) {
        mutableStateOf(WeatherPreferences.place(context))
    }
    // The city search replaces the weather page for a moment, exactly as the
    // Java wizard swapped its page content; leaving the page drops the state.
    var citySearchOpen by remember(page) { mutableStateOf(false) }

    val listingsPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // The provider itself reports the final state; a grant that the system
        // still treats as denied must not be shown as allowed.
        listingsGranted = ContentPreferences.canReadListings(context)
    }

    LaunchedEffect(page) {
        SetupProgress.saveStep(context, page)
        focus = 0
    }

    fun next() {
        if (page < PAGE_FINISH) page++ else finish(context, onFinish)
    }

    fun back() {
        if (page > 0) page-- else if (rerun) onFinish()
    }

    fun chooseLanguage(value: String) {
        if (language == value) return
        language = value
        onLanguageChanged(value)
    }

    fun requestAccess() {
        if (listingsGranted) {
            // The wizard is the only place that asks; re-running setup must not
            // re-ask a granted permission, so an allowed row opens App info.
            openSystem(
                context,
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(android.net.Uri.parse("package:${context.packageName}")),
                fallback = false,
            )
        } else {
            listingsPermission.launch(ContentPreferences.READ_TV_LISTINGS)
        }
    }

    @Composable
    fun buildPage(): SetupPage = when (page) {
        PAGE_WELCOME -> SetupPage(
            title = stringResource(R.string.setup_welcome_title),
            subtitle = stringResource(R.string.setup_welcome_hint),
            primary = "",
            onPrimary = ::next,
            pressAnywhere = true,
            centered = true,
            subtitleFont = GREETING_HINT_FONT,
            rows = emptyList(),
        )

        PAGE_LANGUAGE -> SetupPage(
            title = stringResource(R.string.setup_language_title),
            subtitle = stringResource(R.string.setup_language_hint),
            primary = stringResource(R.string.setup_continue),
            onPrimary = ::next,
            centered = true,
            rows = listOf(
                SetupRow(
                    title = stringResource(R.string.setup_language_system),
                    detail = systemLanguage(),
                    kind = RowKind.RADIO,
                    checked = language == LocalePreference.SYSTEM,
                    action = { chooseLanguage(LocalePreference.SYSTEM) },
                ),
                SetupRow(
                    title = stringResource(R.string.settings_language_russian),
                    detail = null,
                    kind = RowKind.RADIO,
                    checked = language == LocalePreference.RUSSIAN,
                    action = { chooseLanguage(LocalePreference.RUSSIAN) },
                ),
                SetupRow(
                    title = stringResource(R.string.settings_language_english),
                    detail = null,
                    kind = RowKind.RADIO,
                    checked = language == LocalePreference.ENGLISH,
                    action = { chooseLanguage(LocalePreference.ENGLISH) },
                ),
            ),
        )

        PAGE_PROFILE -> SetupPage(
            // The Java wizard's second question: name and avatar, one profile
            // per run. A re-run edits the existing profile instead of adding one.
            title = stringResource(
                if (rerun) R.string.setup_profile_title else R.string.setup_profile_create,
            ),
            subtitle = stringResource(R.string.setup_profile_hint),
            primary = stringResource(R.string.setup_continue),
            onPrimary = ::next,
            rows = listOf(
                SetupRow(
                    title = stringResource(R.string.profile_avatar),
                    detail = null,
                    kind = RowKind.ACTION,
                    value = profile.emoji.ifEmpty { ProfileCatalog.initials(profile.name) }
                        .ifEmpty { stringResource(R.string.profile_avatar_placeholder) },
                    action = { profileAvatarOpen = true },
                ),
                SetupRow(
                    title = stringResource(R.string.profile_name),
                    detail = null,
                    kind = RowKind.ACTION,
                    value = profile.name,
                    action = { profileNameOpen = true },
                ),
            ),
        )

        PAGE_HOME -> SetupPage(
            title = stringResource(R.string.setup_home_title),
            subtitle = stringResource(R.string.setup_home_hint),
            primary = stringResource(R.string.setup_continue),
            onPrimary = ::next,
            rows = listOf(
                SetupRow(
                    title = stringResource(R.string.settings_recommendations),
                    detail = stringResource(R.string.setup_recommendations_detail),
                    kind = RowKind.TOGGLE,
                    checked = recommendations,
                    action = {
                        recommendations = !recommendations
                        ContentPreferences.setRecommendations(context, recommendations)
                    },
                ),
                SetupRow(
                    title = stringResource(R.string.settings_continue_watching),
                    detail = stringResource(R.string.setup_continue_detail),
                    kind = RowKind.TOGGLE,
                    checked = continueWatching,
                    action = {
                        continueWatching = !continueWatching
                        ContentPreferences.setContinueWatching(context, continueWatching)
                    },
                ),
            ),
        )

        PAGE_WEATHER -> {
            // The three answers of the Java wizard's weather step: the internet
            // address, a city picked by name, or nothing. The location needs no
            // permission — the capsule appears on Home in a later stage.
            SetupPage(
                title = stringResource(R.string.setup_weather_title),
                subtitle = stringResource(R.string.setup_weather_hint),
                primary = stringResource(R.string.setup_continue),
                onPrimary = ::next,
                rows = listOf(
                    SetupRow(
                        title = stringResource(R.string.setup_weather_auto),
                        detail = stringResource(R.string.setup_weather_auto_detail),
                        kind = RowKind.RADIO,
                        checked = weatherEnabled && !weatherManual,
                        action = {
                            WeatherPreferences.clearPlace(context)
                            weatherEnabled = true
                            weatherManual = false
                            weatherPlace = ""
                        },
                    ),
                    SetupRow(
                        title = stringResource(R.string.setup_weather_city),
                        detail = if (weatherManual) {
                            weatherPlace
                        } else {
                            stringResource(R.string.setup_weather_search)
                        },
                        kind = RowKind.RADIO,
                        checked = weatherEnabled && weatherManual,
                        action = { citySearchOpen = true },
                    ),
                    SetupRow(
                        title = stringResource(R.string.setup_weather_off),
                        detail = null,
                        kind = RowKind.RADIO,
                        checked = !weatherEnabled,
                        action = {
                            WeatherPreferences.setEnabled(context, false)
                            weatherEnabled = false
                        },
                    ),
                ),
            )
        }

        PAGE_SAVER -> SetupPage(
            // When, not what: nine rows on one page stop fitting once the rows
            // are the Java wizard's 92 points, so the themes get the next page
            // — the same split Settings makes.
            title = stringResource(R.string.setup_saver_title),
            subtitle = stringResource(R.string.setup_saver_hint),
            primary = stringResource(R.string.setup_continue),
            onPrimary = ::next,
            rows = SaverDelay.entries.map { option ->
                SetupRow(
                    title = delayLabel(option),
                    detail = null,
                    kind = RowKind.RADIO,
                    checked = delay == option,
                    action = {
                        delay = option
                        SaverPreferences.setDelay(context, option)
                    },
                )
            },
        )

        PAGE_SAVER_THEMES -> SetupPage(
            // tvOS never lets the last aerial category go: with every theme off
            // the saver would have nothing to play, so the row refuses the tap.
            title = stringResource(R.string.setup_saver_themes_title),
            subtitle = stringResource(R.string.setup_saver_themes_hint),
            primary = stringResource(R.string.setup_continue),
            onPrimary = ::next,
            rows = SaverTheme.entries.map { theme ->
                SetupRow(
                    title = themeLabel(theme),
                    detail = null,
                    kind = RowKind.TOGGLE,
                    checked = theme in themes,
                    action = {
                        themes = SaverPreferences.toggleTheme(context, theme)
                    },
                )
            },
        )

        PAGE_ACCESS -> SetupPage(
            title = stringResource(R.string.setup_access_title),
            subtitle = stringResource(R.string.setup_access_hint),
            primary = stringResource(R.string.setup_continue),
            onPrimary = ::next,
            rows = listOf(
                SetupRow(
                    title = stringResource(R.string.setup_home_app),
                    detail = stringResource(R.string.setup_home_app_detail),
                    kind = RowKind.ACTION,
                    ok = defaultHome,
                    value = stringResource(
                        if (defaultHome) R.string.setup_state_done else R.string.setup_home_setup,
                    ),
                    action = {
                        // Read back on resume: the system Home picker runs in
                        // another activity, so the old value is still current
                        // for a moment after this returns.
                        openSystem(context, Intent(Settings.ACTION_HOME_SETTINGS))
                    },
                ),
                SetupRow(
                    title = stringResource(R.string.setup_accessibility),
                    detail = stringResource(R.string.setup_accessibility_detail),
                    kind = RowKind.ACTION,
                    ok = accessibility == AccessibilityStatus.State.CONNECTED,
                    value = stringResource(
                        when (accessibility) {
                            AccessibilityStatus.State.CONNECTED -> R.string.setup_accessibility_on
                            AccessibilityStatus.State.STALE -> R.string.setup_accessibility_stale
                            AccessibilityStatus.State.OFF -> R.string.setup_allow_action
                        },
                    ),
                    action = { AccessibilityStatus.open(context) },
                ),
                SetupRow(
                    title = stringResource(R.string.settings_listings_access),
                    detail = stringResource(R.string.setup_access_detail),
                    kind = RowKind.ACTION,
                    ok = listingsGranted,
                    value = stringResource(
                        if (listingsGranted) {
                            R.string.settings_listings_granted
                        } else {
                            R.string.setup_allow_action
                        },
                    ),
                    action = ::requestAccess,
                ),
            ),
        )

        else -> SetupPage(
            title = stringResource(R.string.setup_done_title),
            subtitle = finishSummary(
                language = language,
                recommendations = recommendations,
                continueWatching = continueWatching,
                weatherEnabled = weatherEnabled,
                weatherManual = weatherManual,
                weatherPlace = weatherPlace,
                delay = delay,
                themes = themes.size,
            ),
            primary = stringResource(
                if (rerun) R.string.setup_close else R.string.setup_open_home,
            ),
            onPrimary = { finish(context, onFinish) },
            rows = emptyList(),
        )
    }

    val current = buildPage()
    val rowCount = current.rows.size

    // A page with no rows (welcome, finish) still has buttons; start on the
    // main one there so OK continues instead of reaching into an empty list.
    // Every page with rows starts on its first row. The welcome page keeps no
    // buttons at all, so nothing here may steal focus from the root.
    LaunchedEffect(page, rowCount) {
        if (!current.pressAnywhere) {
            buttonIndex = if (rowCount == 0) 1 else 0
            inButtons = rowCount == 0
        }
    }

    // Rows and buttons are separate focus stops, as in the Java wizard: Down
    // from the last row reaches the buttons. It lands on the forward action,
    // not on Back, so the usual Down-OK flow keeps moving through the pages.
    // The welcome page is the tvOS "Hello": no stops at all, OK continues.
    fun moveDown() {
        if (current.pressAnywhere || inButtons) return
        if (focus < rowCount - 1) {
            focus++
        } else {
            inButtons = true
            buttonIndex = 1
        }
    }

    fun moveUp() {
        // Up from the first row is a dead end, as in the Java wizard: only Back
        // leaves the page, so a stray Up never skips a step of setup.
        if (current.pressAnywhere) return
        if (!inButtons) {
            if (focus > 0) focus--
        } else {
            inButtons = false
            focus = rowCount - 1
        }
    }

    fun activate() {
        if (current.pressAnywhere) {
            next()
        } else if (inButtons) {
            if (buttonIndex == 0 && page > 0) back() else current.onPrimary()
        } else {
            current.rows.getOrNull(focus)?.action?.invoke()
        }
    }

    val rowRequesters = remember(page) { mutableMapOf<Int, FocusRequester>() }
    val backRequester = remember(page) { FocusRequester() }
    val primaryRequester = remember(page) { FocusRequester() }
    val rootRequester = remember { FocusRequester() }
    val showBack = page > 0

    LaunchedEffect(page, focus, inButtons, buttonIndex, rowCount, citySearchOpen, profileNameOpen, profileAvatarOpen) {
        // While the city search, the name editor or the avatar grid is up it
        // owns the focus through its own keys; the rows behind are not reached.
        if (citySearchOpen || profileNameOpen || profileAvatarOpen) return@LaunchedEffect
        when {
            current.pressAnywhere -> rootRequester.requestFocus()

            inButtons || rowCount == 0 -> {
                if (showBack && buttonIndex == 0) {
                    backRequester.requestFocus()
                } else {
                    primaryRequester.requestFocus()
                }
            }
            else -> rowRequesters[focus]?.requestFocus() ?: rootRequester.requestFocus()
        }
    }

    BackHandler { back() }

    // The page appears as one piece: alpha plus a 30-point travel on x. The
    // first page (the buttonless Hello) has no travel and the shorter timing;
    // every later page slides in from the side it was entered from, 240 ms.
    // Reduce Motion drops the travel but keeps the fade (motionDuration).
    val reduceMotion = remember { !android.animation.ValueAnimator.areAnimatorsEnabled() }
    val appear = remember(page) { Animatable(if (page == 0) 0.6f else 0f) }
    val slide = remember(page) {
        Animatable(if (page == 0) 0f else PAGE_SLIDE * slideFrom)
    }
    LaunchedEffect(page) {
        val ms = motionDuration(
            if (page == 0) PAGE_FIRST_MS else TvOs.SURFACE_ENTER_MS,
            reduceMotion,
        )
        appear.animateTo(1f, tween(ms))
        slide.animateTo(0f, tween(ms))
    }
    // Java dropped the wallpaper on the last page so the tick and its summary
    // sit on plain dark ground; the scrim stays, as its `dim` view did.
    val backdropAlpha by animateFloatAsState(
        targetValue = if (page == PAGE_FINISH) 0f else BACKDROP_DIM,
        animationSpec = tween(motionDuration(300, reduceMotion)),
        label = "setupBackdrop",
    )

    Box(
        Modifier
            .fillMaxSize()
            // The dark base under the backdrop: a page whose aurora has not
            // been built yet (or a device where it failed) still reads dark,
            // never white.
            .background(LauncherColors.backgroundBottom)
            .focusRequester(rootRequester)
            // Preview sees the release even when a layer consumes it, so the
            // next press of that key is not read as a repeat of the old one.
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) keyRepeat.release(event.key)
                false
            }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) {
                    return@onKeyEvent false
                }
                // A held OK used to fire the row on every auto-repeat and a
                // held arrow crossed five rows; both now wait for the repeat.
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
                        if (inButtons && showBack) buttonIndex = 0
                        true
                    }

                    Key.DirectionRight -> {
                        if (inButtons) buttonIndex = 1
                        true
                    }

                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        activate()
                        true
                    }

                    Key.Back -> {
                        back()
                        true
                    }

                    else -> false
                }
            }
            .focusable(),
    ) {
        // The aurora backdrop under the page, then the scrim Java laid over it:
        // a top-to-bottom 0x99 → 0x66 → 0xB3 (its `dim` view). The 0.5 alpha is
        // the page dim; both together are what keep white body text readable
        // over the brightest part of the field.
        backdrop?.let { image ->
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = backdropAlpha },
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(TvInk.veilTop, TvInk.veilMiddle, TvInk.veilBottom),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = appear.value
                    // The scope is a Density, so the slide converts here.
                    translationX = slide.value.dp.toPx()
                }
                .padding(top = scale.dp(TITLE_TOP), bottom = scale.dp(56f)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Java centred the pages that carry only a few rows by putting a
            // weight-1 spacer above them; the list pages still start at the top.
            if (current.centered) Spacer(Modifier.weight(1f))
            if (page == PAGE_FINISH) {
                // Java's finish mark: the system-green disc with a white
                // stroked tick, not a white plate with a black glyph.
                Canvas(Modifier.size(scale.dp(FINISH_DISC))) {
                    drawCircle(LauncherColors.success)
                    val tick = Path().apply {
                        moveTo(size.width * 0.29f, size.height * 0.52f)
                        lineTo(size.width * 0.44f, size.height * 0.67f)
                        lineTo(size.width * 0.72f, size.height * 0.37f)
                    }
                    drawPath(
                        path = tick,
                        color = Color.White,
                        style = Stroke(
                            width = size.width * 0.09f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
                Spacer(Modifier.height(scale.dp(FINISH_DISC_GAP)))
            }
            if (page == PAGE_WELCOME) {
                GreetingCycle(scale)
            } else {
                Text(
                    text = current.title,
                    color = LauncherColors.textPrimary,
                    fontSize = scale.sp(TITLE_FONT),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Spacer(Modifier.height(scale.dp(TITLE_GAP)))
            Text(
                text = current.subtitle,
                color = LauncherColors.textSecondary,
                fontSize = scale.sp(current.subtitleFont),
                textAlign = TextAlign.Center,
                lineHeight = scale.sp(current.subtitleFont * 1.15f),
                // The wizard page does not scroll: a longer translation would
                // push the action button off the screen instead of wrapping.
                maxLines = SUBTITLE_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(scale.dp(SUBTITLE_WIDTH)),
            )
            Spacer(Modifier.height(scale.dp(SUBTITLE_LIST_GAP)))

            Column(modifier = Modifier.width(scale.dp(LIST_WIDTH))) {
                for ((index, row) in current.rows.withIndex()) {
                    key(index) {
                        val requester = remember(page, index) { FocusRequester() }
                        rowRequesters[index] = requester
                        SetupRowCard(
                            row = row,
                            focused = !inButtons && index == focus,
                            scale = scale,
                            focusRequester = requester,
                        )
                        if (index != current.rows.lastIndex) {
                            Spacer(Modifier.height(scale.dp(ROW_GAP)))
                        }
                    }
                }
            }

            if (current.rows.isNotEmpty()) {
                Spacer(Modifier.height(scale.dp(LIST_BUTTON_GAP)))
            } else {
                Spacer(Modifier.height(scale.dp(20f)))
            }

            if (!current.pressAnywhere) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(scale.dp(BUTTON_GAP)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showBack) {
                        SetupButton(
                            text = stringResource(R.string.setup_back),
                            primary = false,
                            focused = inButtons && buttonIndex == 0,
                            scale = scale,
                            focusRequester = backRequester,
                        )
                    }
                    SetupButton(
                        text = current.primary,
                        primary = true,
                        focused = inButtons && (!showBack || buttonIndex == 1),
                        scale = scale,
                        focusRequester = primaryRequester,
                    )
                }
            }

            // Java kept at least 16 units here so the dots never sat right under
            // the buttons on the tallest page.
            Spacer(Modifier.height(scale.dp(BUTTON_DOTS_GAP)))
            Spacer(Modifier.weight(1f))
            Row(
                horizontalArrangement = Arrangement.spacedBy(scale.dp(DOT_GAP)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (i in 0 until PAGE_COUNT) {
                    Box(
                        Modifier
                            .size(scale.dp(DOT_SIZE))
                            .clip(CircleShape)
                            .background(
                                if (i == page) Color.White else Color.White.copy(alpha = 0.35f),
                            ),
                    )
                }
            }
        }

        if (page == PAGE_WEATHER && citySearchOpen) {
            WeatherCitySearch(
                language = language,
                scale = scale,
                reduceMotion = reduceMotion,
                onClose = { citySearchOpen = false },
                onPick = { place ->
                    WeatherPreferences.setPlace(context, place.name, place.lat, place.lon)
                    weatherEnabled = true
                    weatherManual = true
                    weatherPlace = place.name
                    citySearchOpen = false
                },
            )
        }

        // The profile page's two editors are the launcher's own surfaces: the
        // shared TV keyboard for the name, the avatar grid for the picture.
        // Editing writes straight through the store, so Home reads the same
        // values the moment setup finishes.
        if (profileNameOpen) {
            RenameKeyboardOverlay(
                title = stringResource(R.string.profile_name),
                initial = profile.name,
                scale = scale,
                onDone = { name ->
                    profileStore.update(profile.copy(name = name))
                    profile = profileStore.current()
                    profileNameOpen = false
                },
                onCancel = { profileNameOpen = false },
                placeholder = stringResource(R.string.profile_name_hint),
            )
        }
        if (profileAvatarOpen) {
            ProfileAvatarPicker(
                profiles = profileStore.list(),
                editingId = profile.id,
                reduceMotion = reduceMotion,
                onChoose = { emoji ->
                    profileStore.update(profile.copy(emoji = emoji))
                    profile = profileStore.current()
                    profileAvatarOpen = false
                },
                onDismiss = { profileAvatarOpen = false },
            )
        }
    }
}

private fun finish(context: Context, onFinish: () -> Unit) {
    SetupProgress.markDone(context)
    onFinish()
}

/** A vendor TV may hide the exact system page; Android Settings is the way out. */
private fun openSystem(context: Context, intent: Intent, fallback: Boolean = true) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .recoverCatching {
            if (fallback) {
                context.startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
}

private fun systemLanguage(): String {
    // The launcher may be running in its own language while the device stays
    // on another one; the "Same as System" row must name the device language,
    // so it reads Locale.getDefault() instead of the wrapped context.
    val locale = Locale.getDefault()
    return locale.getDisplayLanguage(locale).replaceFirstChar { it.uppercase(locale) }
}

// The tvOS "Hello" answers with one word at a time; the Java wizard cycled the
// same tour of world greetings, so the port keeps both the words and the
// rhythm — 2.4 s on the localized greeting, then a hop every 2.2 s.
private val GREETINGS = listOf(
    "Добро пожаловать", "Bienvenue", "Bienvenido", "Willkommen", "ようこそ",
    "Benvenuto", "Bem-vindo", "欢迎", "Ласкаво просимо", "Witamy", "Vítejte", "Hoş geldiniz",
)

@Composable
private fun GreetingCycle(scale: TvScale) {
    val hello = stringResource(R.string.setup_hello)
    // null keeps the localized greeting up until the tour starts.
    var index by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        delay(2400)
        while (true) {
            index = ((index ?: -1) + 1) % GREETINGS.size
            delay(2200)
        }
    }
    AnimatedContent(
        targetState = index?.let { GREETINGS[it] } ?: hello,
        transitionSpec = { fadeIn(tween(450)) togetherWith fadeOut(tween(350)) },
        label = "greeting",
    ) { word ->
        Text(
            text = word,
            color = LauncherColors.textPrimary,
            fontSize = scale.sp(GREETING_FONT),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                // The tour hops every couple of seconds; TalkBack must read the
                // greeting once, not announce every change.
                .clearAndSetSemantics {
                    heading()
                    contentDescription = hello
                },
        )
    }
}

private enum class CityZone { KEYS, RESULTS }

/**
 * The city picker of the weather step, as its own full-screen step: the Java
 * wizard swapped its page content for a field and live results, the port
 * reuses the app's Search surfaces — the shared keyboard, a field, and a
 * results column fed by the Open-Meteo geocoder. With an empty query the
 * column shows the biggest cities first, so the usual answer needs no typing.
 */
@Composable
internal fun WeatherCitySearch(
    language: String,
    scale: TvScale,
    reduceMotion: Boolean,
    onClose: () -> Unit,
    onPick: (CityPlace) -> Unit,
) {
    val context = LocalContext.current
    val labels = rememberKeyLabels()
    val russian = LocalConfiguration.current.locales[0].language == "ru"
    val keyboard = remember {
        KeyboardState("", if (russian) KeyboardMode.CYRILLIC else KeyboardMode.LATIN)
    }
    val rows = keyboard.rows(labels)
    val query = keyboard.text
    // null: no query yet — the presets answer the usual case without typing.
    var found by remember { mutableStateOf<List<CityPlace>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var zone by remember { mutableStateOf(CityZone.KEYS) }
    var resultIndex by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val keyRepeat = rememberTvKeyRepeat()
    val scope = rememberCoroutineScope()
    var entered by remember { mutableStateOf(false) }
    val enterAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(motionDuration(TvOs.SURFACE_ENTER_MS, reduceMotion)),
        label = "cityEnter",
    )
    LaunchedEffect(Unit) { entered = true }

    val geoLanguage = when (language) {
        LocalePreference.RUSSIAN -> "ru"
        LocalePreference.ENGLISH -> "en"
        else -> Locale.getDefault().language
    }

    // The debounce lives in the effect key: a new keystroke cancels both the
    // wait and the request of the previous one, as the Java wizard's 350 ms
    // pause between keystrokes did.
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < CITY_MIN_QUERY) {
            searching = false
            found = null
            return@LaunchedEffect
        }
        delay(CITY_DEBOUNCE_MS)
        searching = true
        found = withContext(Dispatchers.IO) { CitySearch.search(q, geoLanguage) }
        searching = false
    }

    val trimmed = query.trim()
    val places: List<CityPlace> = if (trimmed.length < CITY_MIN_QUERY) {
        listOf(
            CityPlace(stringResource(R.string.city_moscow), stringResource(R.string.city_russia), 55.7558, 37.6173),
            CityPlace(stringResource(R.string.city_spb), stringResource(R.string.city_russia), 59.9343, 30.3351),
            CityPlace(stringResource(R.string.city_novosibirsk), stringResource(R.string.city_russia), 55.0084, 82.9357),
            CityPlace(stringResource(R.string.city_ekaterinburg), stringResource(R.string.city_russia), 56.8389, 60.6057),
            CityPlace(stringResource(R.string.city_kazan), stringResource(R.string.city_russia), 55.7963, 49.1088),
            CityPlace(stringResource(R.string.city_sochi), stringResource(R.string.city_russia), 43.5855, 39.7231),
        )
    } else {
        found.orEmpty()
    }
    val note: String? = when {
        trimmed.length < CITY_MIN_QUERY -> null
        searching -> stringResource(R.string.setup_city_searching)
        found == null -> stringResource(R.string.setup_city_offline)
        places.isEmpty() -> stringResource(R.string.setup_city_nothing)
        else -> null
    }

    LaunchedEffect(places.size) {
        resultIndex = resultIndex.coerceAtMost(places.lastIndex.coerceAtLeast(0))
        if (places.isEmpty()) zone = CityZone.KEYS
    }

    fun openResults() {
        if (places.isEmpty()) return
        zone = CityZone.RESULTS
        resultIndex = resultIndex.coerceIn(0, places.lastIndex)
    }

    fun runKey(action: KeyAction) {
        if (action == KeyAction.Done) openResults() else keyboard.apply(action)
    }

    BackHandler { onClose() }

    val windowStart = (resultIndex / CITY_VISIBLE_ROWS) * CITY_VISIBLE_ROWS
    val visibleRows = places.drop(windowStart).take(CITY_VISIBLE_ROWS)

    // The zone cursor lives in the engine; the rows become real focus targets
    // so TalkBack reads the highlighted city instead of silence.
    val rowRequesters = remember { mutableMapOf<Int, FocusRequester>() }
    LaunchedEffect(zone, resultIndex, places) {
        if (zone == CityZone.RESULTS) rowRequesters[resultIndex]?.requestFocus()
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // The chosen city keeps its check while the list is open, as the Java
    // wizard marked the current row with a tick.
    val chosen = WeatherPreferences.manual(context)
    val chosenLat = WeatherPreferences.latitude(context)
    val chosenLon = WeatherPreferences.longitude(context)

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = enterAlpha }
            .background(
                Brush.verticalGradient(
                    listOf(LauncherColors.backgroundTop, LauncherColors.backgroundBottom),
                ),
            )
            .focusRequester(focusRequester)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) {
                    keyRepeat.release(event.key)
                    return@onKeyEvent false
                }
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                if (TvKeyRepeat.isOneShot(event.key) && !keyRepeat.firstPress(event)) {
                    return@onKeyEvent true
                }
                fun repeatVertically(step: Int) {
                    keyboard.move(0, step, rows)
                    keyRepeat.startRepeat(scope) { keyboard.move(0, step, rows) }
                }
                fun repeatHorizontally(step: Int) {
                    keyboard.move(step, 0, rows)
                    keyRepeat.startRepeat(scope) { keyboard.move(step, 0, rows) }
                }
                when (event.key) {
                    Key.DirectionLeft -> {
                        if (zone == CityZone.RESULTS) {
                            keyRepeat.stopRepeat()
                            zone = CityZone.KEYS
                        } else {
                            repeatHorizontally(-1)
                        }
                        true
                    }

                    Key.DirectionRight -> {
                        if (zone == CityZone.KEYS) repeatHorizontally(1)
                        true
                    }

                    Key.DirectionUp -> {
                        when {
                            zone == CityZone.RESULTS -> {
                                if (resultIndex > 0) resultIndex--
                                keyRepeat.startRepeat(scope) {
                                    if (resultIndex > 0) resultIndex--
                                }
                            }

                            keyboard.row == 0 -> {
                                keyRepeat.stopRepeat()
                                openResults()
                            }

                            else -> repeatVertically(-1)
                        }
                        true
                    }

                    Key.DirectionDown -> {
                        if (zone == CityZone.RESULTS) {
                            if (resultIndex < places.lastIndex) resultIndex++
                            keyRepeat.startRepeat(scope) {
                                if (resultIndex < places.lastIndex) resultIndex++
                            }
                        } else {
                            repeatVertically(1)
                        }
                        true
                    }

                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        keyRepeat.stopRepeat()
                        if (zone == CityZone.RESULTS) {
                            places.getOrNull(resultIndex)?.let(onPick)
                        } else {
                            runKey(rows[keyboard.row][keyboard.column].action)
                        }
                        true
                    }

                    Key.Back -> {
                        keyRepeat.stopRepeat()
                        onClose()
                        true
                    }

                    Key.Backspace, Key.Delete -> {
                        // Text editing keeps the platform's native repeat.
                        keyboard.backspace()
                        true
                    }

                    else -> {
                        val code = event.nativeKeyEvent.unicodeChar
                        val char = code.toChar()
                        if (code > 0 && !char.isISOControl()) {
                            zone = CityZone.KEYS
                            keyboard.type(char.toString())
                            true
                        } else {
                            false
                        }
                    }
                }
            }
            .focusable(),
    ) {
        Text(
            text = stringResource(R.string.setup_city_title),
            color = LauncherColors.textPrimary,
            fontSize = scale.sp(TvType.title),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .offset(x = scale.dp(CITY_TITLE_X), y = scale.dp(CITY_TITLE_Y))
                .semantics { heading() },
        )

        TvSearchField(
            query = query,
            placeholder = stringResource(R.string.setup_city_hint),
            scale = scale,
            modifier = Modifier.offset(x = scale.dp(CITY_FIELD_X), y = scale.dp(CITY_FIELD_Y)),
        )

        KeyboardPanel(
            scale = scale,
            width = CITY_KEYBOARD_WIDTH,
            modifier = Modifier.offset(x = scale.dp(CITY_KEYBOARD_X), y = scale.dp(CITY_KEYBOARD_Y)),
        ) {
            val keysTop = (PANEL_HEIGHT - keysHeight(rows)) / 2f
            KeyboardKeys(
                state = keyboard,
                labels = labels,
                scale = scale,
                top = keysTop,
                width = CITY_KEYBOARD_WIDTH - CONTENT_PADDING * 2,
                showFocus = zone == CityZone.KEYS,
                onAction = ::runKey,
            )
        }

        if (note != null) {
            Text(
                text = note,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = scale.sp(TvType.body),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.offset(
                    x = scale.dp(CITY_RESULTS_X),
                    y = scale.dp(CITY_RESULTS_Y - 52f),
                ),
            )
        }

        Box(
            Modifier
                .offset(x = scale.dp(CITY_RESULTS_X), y = scale.dp(CITY_RESULTS_Y))
                .size(scale.dp(CITY_RESULTS_WIDTH), scale.dp(CITY_RESULTS_HEIGHT))
                .clipToBounds(),
        ) {
            visibleRows.forEachIndexed { offset, place ->
                val index = windowStart + offset
                val rowRequester = remember(windowStart, offset) { FocusRequester() }
                rowRequesters[index] = rowRequester
                CityResultRow(
                    place = place,
                    selected = chosen &&
                        abs(place.lat - chosenLat) < 0.05 &&
                        abs(place.lon - chosenLon) < 0.05,
                    focused = zone == CityZone.RESULTS && index == resultIndex,
                    scale = scale,
                    focusRequester = rowRequester,
                    onActivate = { onPick(place) },
                    modifier = Modifier.offset(y = scale.dp(offset * CITY_ROW_PITCH)),
                )
            }
        }
    }
}

@Composable
private fun CityResultRow(
    place: CityPlace,
    selected: Boolean,
    focused: Boolean,
    scale: TvScale,
    focusRequester: FocusRequester,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val announcement = listOf(place.name, place.detail)
        .filter { it.isNotEmpty() }
        .joinToString(", ")
    TvResultRow(
        scale = scale,
        focused = focused,
        focusRequester = focusRequester,
        contentDescription = announcement,
        onClickLabel = place.name,
        onActivate = onActivate,
        modifier = modifier,
    ) { focusAmount ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = scale.dp(24f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Image(
                    imageVector = SectionGlyph.check,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(LauncherColors.success),
                    modifier = Modifier.size(scale.dp(22f)),
                )
                Spacer(Modifier.width(scale.dp(TvSpace.md)))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = place.name,
                    color = lerp(Color.White.copy(alpha = 0.92f), TvInk.focusInk, focusAmount),
                    fontSize = scale.sp(TvType.subhead),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (place.detail.isNotEmpty()) {
                    Text(
                        text = place.detail,
                        color = lerp(
                            Color.White.copy(alpha = 0.5f),
                            TvInk.focusInk.copy(alpha = 0.6f),
                            focusAmount,
                        ),
                        fontSize = scale.sp(TvType.callout),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun delayLabel(delay: SaverDelay): String = when (delay) {
    SaverDelay.TWO -> stringResource(R.string.saver_delay_2)
    SaverDelay.FIVE -> stringResource(R.string.saver_delay_5)
    SaverDelay.TEN -> stringResource(R.string.saver_delay_10)
    SaverDelay.TWENTY -> stringResource(R.string.saver_delay_20)
    SaverDelay.NEVER -> stringResource(R.string.saver_never)
}

@Composable
private fun themeLabel(theme: SaverTheme): String = when (theme) {
    SaverTheme.LANDSCAPES -> stringResource(R.string.saver_theme_landscapes)
    SaverTheme.CITIES -> stringResource(R.string.saver_theme_cities)
    SaverTheme.UNDERWATER -> stringResource(R.string.saver_theme_underwater)
    SaverTheme.EARTH -> stringResource(R.string.saver_theme_earth)
}

@Composable
private fun finishSummary(
    language: String,
    recommendations: Boolean,
    continueWatching: Boolean,
    weatherEnabled: Boolean,
    weatherManual: Boolean,
    weatherPlace: String,
    delay: SaverDelay,
    themes: Int,
): String {
    val languageText = when (language) {
        LocalePreference.RUSSIAN -> stringResource(R.string.settings_language_russian)
        LocalePreference.ENGLISH -> stringResource(R.string.settings_language_english)
        else -> stringResource(R.string.setup_language_system)
    }
    val on = stringResource(R.string.setup_state_on)
    val off = stringResource(R.string.setup_state_off)
    val weatherText = when {
        weatherEnabled && weatherManual -> weatherPlace
        weatherEnabled -> stringResource(R.string.setup_summary_weather_auto)
        else -> off
    }
    val parts = listOf(
        stringResource(R.string.setup_summary_language, languageText),
        stringResource(
            R.string.setup_summary_shelf,
            if (recommendations) on else off,
            if (continueWatching) on else off,
        ),
        stringResource(
            R.string.setup_summary_saver,
            delayLabel(delay),
            themes,
            SaverTheme.entries.size,
        ),
        stringResource(R.string.setup_summary_weather, weatherText),
    )
    return parts.joinToString("\n")
}

@Composable
private fun SetupRowCard(
    row: SetupRow,
    focused: Boolean,
    scale: TvScale,
    focusRequester: FocusRequester,
) {
    val focusAmount by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(TvOs.FOCUS_MS),
        label = "setupRowFocus",
    )
    val shape = RoundedCornerShape(scale.dp(ROW_RADIUS))
    // Java's palette: white plate at 15% grows to 95% white on focus, and the
    // body text flips to the same near-black the platform labels use.
    val titleColor = lerp(Color.White, TvInk.focusInk, focusAmount)
    val detailColor = lerp(
        Color.White.copy(alpha = 0.6f),
        TvInk.focusInk.copy(alpha = 0.6f),
        focusAmount,
    )
    val valueColor = detailColor
    val okColor = lerp(LauncherColors.success, LauncherColors.successPressed, focusAmount)
    val on = stringResource(R.string.settings_state_on)
    val off = stringResource(R.string.settings_state_off)
    val selected = stringResource(R.string.settings_selected)
    val announcement = listOfNotNull(row.title, row.detail, row.value).joinToString(", ")

    Box(
        Modifier
            .fillMaxWidth()
            .height(scale.dp(if (row.detail == null) ROW_HEIGHT else ROW_HEIGHT_DETAIL))
            .focusRequester(focusRequester)
            .focusable()
            .clearAndSetSemantics {
                contentDescription = announcement
                when (row.kind) {
                    RowKind.TOGGLE -> {
                        stateDescription = if (row.checked) on else off
                        toggleableState = if (row.checked) ToggleableState.On else ToggleableState.Off
                        role = Role.Switch
                    }

                    RowKind.RADIO -> {
                        stateDescription = if (row.checked) selected else off
                        role = Role.RadioButton
                    }

                    RowKind.ACTION -> role = Role.Button
                }
                row.action?.let { action -> onClick(label = row.title) { action(); true } }
            }
            .graphicsLayer {
                val grow = 1f + (ROW_FOCUS_SCALE - 1f) * focusAmount
                scaleX = grow
                scaleY = grow
            }
            .clip(shape)
            .background(lerp(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.95f), focusAmount))
            .padding(start = scale.dp(ROW_PADDING), end = scale.dp(ROW_PADDING_END)),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                if (row.kind == RowKind.ACTION && row.value != null) {
                    // Java keeps the state on the label's line and gives the
                    // detail the full row width; a status column of its own
                    // would cut the detail short.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = row.title,
                            color = titleColor,
                            fontSize = scale.sp(ROW_FONT),
                            fontWeight = FontWeight.Medium,
                            lineHeight = scale.sp(TvLeading.row),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(scale.dp(TvSpace.xl)))
                        Text(
                            text = if (row.ok) "✓ ${row.value}" else row.value,
                            color = if (row.ok) okColor else valueColor,
                            fontSize = scale.sp(ROW_VALUE_FONT),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                } else {
                    Text(
                        text = row.title,
                        color = titleColor,
                        fontSize = scale.sp(ROW_FONT),
                        fontWeight = FontWeight.Medium,
                        // The platform metrics alone push the detail past the row
                        // edge; the explicit heights keep both lines inside.
                        lineHeight = scale.sp(TvLeading.row),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (row.detail != null) {
                    Text(
                        text = row.detail,
                        color = detailColor,
                        fontSize = scale.sp(ROW_DETAIL_FONT),
                        lineHeight = scale.sp(TvLeading.rowDetail),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            when (row.kind) {
                RowKind.TOGGLE -> {
                    Spacer(Modifier.width(scale.dp(14f)))
                    SwitchMark(on = row.checked, focused = focused, scale = scale)
                }

                RowKind.RADIO -> {
                    Spacer(Modifier.width(scale.dp(14f)))
                    SelectMark(selected = row.checked, focused = focused, scale = scale)
                }

                RowKind.ACTION -> Unit
            }
        }
    }
}

@Composable
private fun SwitchMark(on: Boolean, focused: Boolean, scale: TvScale) {
    // Java's switch: a green track when on, a faint white one when off, and a
    // white knob that always travels 28 points to the right.
    val track = when {
        on -> if (focused) LauncherColors.successPressed else LauncherColors.success
        focused -> TvInk.focusInk.copy(alpha = 0.25f)
        else -> Color.White.copy(alpha = 0.25f)
    }
    val knobOffset by animateFloatAsState(
        targetValue = if (on) 28f else 0f,
        animationSpec = tween(TvOs.FOCUS_MS),
        label = "setupSwitch",
    )
    Box(
        Modifier
            .size(scale.dp(64f), scale.dp(36f))
            .clip(RoundedCornerShape(scale.dp(18f)))
            .background(track),
    ) {
        Box(
            Modifier
                // 4-point inset on every side; 64 − 28 − 2×4 is the travel.
                .offset(x = scale.dp(4f + knobOffset), y = scale.dp(4f))
                .size(scale.dp(28f))
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

@Composable
private fun SelectMark(selected: Boolean, focused: Boolean, scale: TvScale) {
    // Java's radio: a green disc with a white tick when chosen, a faint ring
    // otherwise. Focus only darkens the ring — the choice stays green.
    val ring = when {
        selected -> if (focused) LauncherColors.successPressed else LauncherColors.success
        focused -> TvInk.focusInk.copy(alpha = 0.5f)
        else -> Color.White.copy(alpha = 0.5f)
    }
    Box(
        Modifier
            .size(scale.dp(ROW_MARK))
            .clip(CircleShape)
            .background(if (selected) ring else Color.Transparent)
            .border(width = scale.dp(3f), color = ring, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Image(
                imageVector = SectionGlyph.check,
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(scale.dp(22f)),
            )
        }
    }
}

@Composable
private fun SetupButton(
    text: String,
    primary: Boolean,
    focused: Boolean,
    scale: TvScale,
    focusRequester: FocusRequester,
) {
    val idle = if (primary) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.14f)
    // Java grows a focused button by 8% (its medium focus scale), more than a row.
    val focusAmount by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(TvOs.FOCUS_MS),
        label = "setupButtonFocus",
    )
    Box(
        Modifier
            // Russian labels run longer than the English ones; the pill grows
            // instead of clipping "Открыть Главную" to "Открыть".
            .widthIn(min = scale.dp(BUTTON_MIN_WIDTH))
            .height(scale.dp(BUTTON_HEIGHT))
            .focusRequester(focusRequester)
            .focusable()
            .graphicsLayer {
                val grow = 1f + (BUTTON_FOCUS_SCALE - 1f) * focusAmount
                scaleX = grow
                scaleY = grow
            }
            .clip(RoundedCornerShape(scale.dp(BUTTON_RADIUS)))
            .background(if (focused) Color.White else idle)
            .padding(horizontal = scale.dp(BUTTON_PADDING))
            .clearAndSetSemantics {
                contentDescription = text
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (focused) Color.Black else Color.White,
            fontSize = scale.sp(BUTTON_FONT),
            fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}
