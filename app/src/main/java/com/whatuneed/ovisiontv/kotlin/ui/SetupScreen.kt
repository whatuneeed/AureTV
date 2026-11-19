package com.whatuneed.ovisiontv.kotlin.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.ambient.AmbientMode
import com.whatuneed.ovisiontv.kotlin.ambient.AmbientSettingsStore
import com.whatuneed.ovisiontv.kotlin.data.AccessibilityStatus
import com.whatuneed.ovisiontv.kotlin.data.ContentPreferences
import com.whatuneed.ovisiontv.kotlin.data.DeviceFactsReader
import com.whatuneed.ovisiontv.kotlin.data.LocalePreference
import com.whatuneed.ovisiontv.kotlin.data.isPermanentDenial
import com.whatuneed.ovisiontv.kotlin.data.ProfileStore
import com.whatuneed.ovisiontv.kotlin.data.SaverDelay
import com.whatuneed.ovisiontv.kotlin.data.SaverPreferences
import com.whatuneed.ovisiontv.kotlin.data.SetupProgress
import com.whatuneed.ovisiontv.kotlin.data.Wallpaper
import com.whatuneed.ovisiontv.kotlin.data.WeatherPreferences
import com.whatuneed.ovisiontv.kotlin.ui.components.ProfileAvatarPicker
import com.whatuneed.ovisiontv.kotlin.ui.components.RenameKeyboardOverlay
import com.whatuneed.ovisiontv.kotlin.ui.components.SectionGlyph
import com.whatuneed.ovisiontv.kotlin.ui.components.TvKeyRepeat
import com.whatuneed.ovisiontv.kotlin.ui.components.rememberTvKeyRepeat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
internal const val GREETING_HINT_FONT = TvType.subhead
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

private const val PAGE_WELCOME = 0
private const val PAGE_PERSONAL = 1
private const val PAGE_HOME = 2
private const val PAGE_SAVER = 3
private const val PAGE_ACCESS = 4
private const val PAGE_FINISH = 5
private const val PAGE_COUNT = 6

// The three ambient modes that need no extra permission or folder. Photos and
// Reddit stay in Settings: choosing them here would hand the viewer an empty
// or unconfigured surface at the exact moment the saver first starts.
internal val SETUP_AMBIENT_MODES = listOf(
    AmbientMode.AERIAL,
    AmbientMode.CLOCK,
    AmbientMode.MINIMAL,
)

internal enum class RowKind { TOGGLE, RADIO, ACTION }

internal data class SetupRow(
    val title: String,
    val detail: String?,
    val kind: RowKind,
    val checked: Boolean = false,
    val value: String? = null,
    // An ACTION row that is already done: the value turns into the green
    // check state the Java wizard painted on its permission rows.
    val ok: Boolean = false,
    // A hairline between two questions on the same page. The Home & Weather
    // page uses it once; it is not a second heading or another focus stop.
    val dividerBefore: Boolean = false,
    val action: (() -> Unit)? = null,
)

internal data class SetupPage(
    val title: String,
    val subtitle: String,
    val rows: List<SetupRow>,
    val primary: String,
    val onPrimary: () -> Unit,
    // tvOS opens with a buttonless "Hello": OK anywhere continues. Only the
    // welcome page uses it; every other page keeps its explicit buttons.
    val pressAnywhere: Boolean = false,
    // A weight-1 spacer above the pages that carry only a few rows (welcome,
    // access, finish) puts the block in the middle of the screen instead of
    // letting it hang from the top edge.
    val centered: Boolean = false,
    // The greeting's hint is the one subtitle Java sized a step larger.
    val subtitleFont: Float = SUBTITLE_FONT,
    // The row that owns the saved value should receive focus when the page
    // opens. Starting on row zero lets one OK press silently change a language
    // or an ambient mode the viewer already chose.
    val initialFocus: Int = 0,
)

/**
 * First run of OVisionTV_Kotlin. Six pages, each one decision: welcome,
 * language + profile, Home + weather, screen saver, optional access, finish.
 * The earlier wizard split language/profile and Home/weather and added a
 * separate themes page; that made the first minute nine screens long without
 * asking a different question. Themes, Photos, Reddit and weather units stay
 * in Settings, where they have previews and warnings.
 *
 * Nothing here grants a permission by itself: the system screens open only
 * after the viewer chooses the row, and the weather needs no permission — the
 * place comes from the internet address. The order keeps the dissected tvOS
 * TVSetup.app welcome (TVOS27_RESEARCH/22) and PHASE 26 resume: the step is
 * saved on every page, a re-run starts at the first page and keeps current
 * values, and an old page map starts over through STEP_VERSION.
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
    var ambientMode by remember(resumeTick) { mutableStateOf(AmbientSettingsStore.mode(context)) }
    var saverMusic by remember { mutableStateOf(SaverPreferences.music(context)) }
    var listingsGranted by remember(resumeTick) {
        mutableStateOf(ContentPreferences.canReadListings(context))
    }
    var defaultHome by remember(resumeTick) { mutableStateOf(false) }
    LaunchedEffect(resumeTick) {
        defaultHome = withContext(Dispatchers.IO) { DeviceFactsReader.isDefaultHome(context) }
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
    var listingsAsked by remember { mutableStateOf(false) }

    val listingsPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // The provider itself reports the final state; a grant that the system
        // still treats as denied must not be shown as allowed.
        listingsGranted = ContentPreferences.canReadListings(context)
        listingsAsked = true
    }

    LaunchedEffect(page) {
        SetupProgress.saveStep(context, page)
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
        val permanent = isPermanentDenial(
            context,
            ContentPreferences.READ_TV_LISTINGS,
            listingsAsked,
        )
        if (listingsGranted || permanent) {
            // A granted permission and a permanent denial both recover only in
            // App Info; asking the system again would do nothing (AQ-30).
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
        PAGE_WELCOME -> setupWelcomePage(onNext = ::next)

        PAGE_PERSONAL -> setupPersonalPage(
            language = language,
            profile = profile,
            systemLanguage = systemLanguage(),
            onChooseLanguage = ::chooseLanguage,
            onOpenAvatar = { profileAvatarOpen = true },
            onOpenName = { profileNameOpen = true },
            onNext = ::next,
        )

        PAGE_HOME -> setupHomePage(
            recommendations = recommendations,
            continueWatching = continueWatching,
            weatherEnabled = weatherEnabled,
            weatherManual = weatherManual,
            weatherPlace = weatherPlace,
            onToggleRecommendations = {
                recommendations = !recommendations
                ContentPreferences.setRecommendations(context, recommendations)
            },
            onToggleContinueWatching = {
                continueWatching = !continueWatching
                ContentPreferences.setContinueWatching(context, continueWatching)
            },
            onWeatherAutomatic = {
                WeatherPreferences.clearPlace(context)
                weatherEnabled = true
                weatherManual = false
                weatherPlace = ""
            },
            onWeatherCity = { citySearchOpen = true },
            onWeatherOff = {
                WeatherPreferences.setEnabled(context, false)
                weatherEnabled = false
            },
            onNext = ::next,
        )

        PAGE_SAVER -> setupSaverPage(
            ambientMode = ambientMode,
            delay = delay,
            saverMusic = saverMusic,
            onSelectMode = { mode ->
                ambientMode = mode
                AmbientSettingsStore.setMode(context, mode)
            },
            onCycleDelay = {
                val next = SaverDelay.entries[
                    (delay.ordinal + 1) % SaverDelay.entries.size
                ]
                delay = next
                SaverPreferences.setDelay(context, next)
            },
            onToggleMusic = {
                saverMusic = !saverMusic
                SaverPreferences.setMusic(context, saverMusic)
            },
            onNext = ::next,
        )

        PAGE_ACCESS -> setupAccessPage(
            defaultHome = defaultHome,
            accessibility = accessibility,
            listingsGranted = listingsGranted,
            listingsPermanent = isPermanentDenial(
                context,
                ContentPreferences.READ_TV_LISTINGS,
                listingsAsked,
            ),
            onOpenHome = { openHomeSettings(context) },
            onOpenAccessibility = { AccessibilityStatus.open(context) },
            onRequestAccess = ::requestAccess,
            onNext = ::next,
        )

        else -> setupFinishPage(
            summary = finishSummary(
                language = language,
                recommendations = recommendations,
                continueWatching = continueWatching,
                weatherEnabled = weatherEnabled,
                weatherManual = weatherManual,
                weatherPlace = weatherPlace,
                delay = delay,
                ambientMode = ambientMode,
                saverMusic = saverMusic,
            ),
            rerun = rerun,
            onFinish = { finish(context, onFinish) },
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
            // A page opens on the value it already holds; an unnamed first row
            // must never be the place where OK flips that value.
            focus = current.initialFocus.coerceIn(0, (rowCount - 1).coerceAtLeast(0))
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
            // The finish page has no rows; Up must not leave the button focus
            // pointing at an index that does not exist (AQ-29).
            if (rowCount == 0) return
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
            current.pressAnywhere -> rootRequester.requestSetupFocusWhenAttached()

            inButtons || rowCount == 0 -> {
                if (showBack && buttonIndex == 0) {
                    backRequester.requestSetupFocusWhenAttached()
                } else {
                    primaryRequester.requestSetupFocusWhenAttached()
                }
            }
            else -> rowRequesters[focus]?.requestSetupFocusWhenAttached()
                ?: rootRequester.requestSetupFocusWhenAttached()
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
    // The aurora stays on every page, including the finish page. Dropping it
    // to plain black made the last screen look like a different product even
    // though the scrim already keeps the summary readable.

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
                    .graphicsLayer { alpha = BACKDROP_DIM },
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
                            if (current.rows[index + 1].dividerBefore) {
                                Spacer(Modifier.height(scale.dp(8f)))
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(scale.dp(1f))
                                        .background(Color.White.copy(alpha = 0.14f)),
                                )
                                Spacer(Modifier.height(scale.dp(8f)))
                            } else {
                                Spacer(Modifier.height(scale.dp(ROW_GAP)))
                            }
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
                            onActivate = { back() },
                        )
                    }
                    SetupButton(
                        text = current.primary,
                        primary = true,
                        focused = inButtons && (!showBack || buttonIndex == 1),
                        scale = scale,
                        focusRequester = primaryRequester,
                        onActivate = { next() },
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

        if (page == PAGE_HOME && citySearchOpen) {
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
private fun openHomeSettings(context: Context) {
    val opened = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess
    if (opened) return
    val fallback = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess
    if (fallback) {
        Toast.makeText(context, R.string.home_settings_hint, Toast.LENGTH_LONG).show()
    }
}

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


/**
 * The city picker of the weather step, as its own full-screen step: the Java
 * wizard swapped its page content for a field and live results, the port
 * reuses the app's Search surfaces — the shared keyboard, a field, and a
 * results column fed by the Open-Meteo geocoder. With an empty query the
 * column shows the biggest cities first, so the usual answer needs no typing.
 */
@Composable
internal fun delayLabel(delay: SaverDelay): String = when (delay) {
    SaverDelay.TWO -> stringResource(R.string.saver_delay_2)
    SaverDelay.FIVE -> stringResource(R.string.saver_delay_5)
    SaverDelay.TEN -> stringResource(R.string.saver_delay_10)
    SaverDelay.TWENTY -> stringResource(R.string.saver_delay_20)
    SaverDelay.NEVER -> stringResource(R.string.saver_never)
}

@Composable
internal fun ambientModeLabel(mode: AmbientMode): String = when (mode) {
    AmbientMode.AERIAL -> stringResource(R.string.saver_mode_aerial)
    AmbientMode.CLOCK -> stringResource(R.string.saver_mode_clock)
    AmbientMode.MINIMAL -> stringResource(R.string.saver_mode_minimal)
    AmbientMode.PHOTOS -> stringResource(R.string.saver_mode_photos)
    AmbientMode.REDDIT -> stringResource(R.string.saver_mode_reddit)
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
    ambientMode: AmbientMode,
    saverMusic: Boolean,
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
            ambientModeLabel(ambientMode),
            delayLabel(delay),
            if (saverMusic) on else off,
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
    val reduceMotion = LocalReduceMotion.current
    val focusAmount by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(motionDuration(TvOs.FOCUS_MS, reduceMotion)),
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

                RowKind.ACTION -> {
                    // The value on the left is not enough: an action row must look
                    // like a place the remote can enter, not like a read-only stat.
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
    val reduceMotion = LocalReduceMotion.current
    val knobOffset by animateFloatAsState(
        targetValue = if (on) 28f else 0f,
        animationSpec = tween(motionDuration(TvOs.FOCUS_MS, reduceMotion)),
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
                .offset {
                    IntOffset(
                        x = scale.dp(4f + knobOffset).roundToPx(),
                        y = scale.dp(4f).roundToPx(),
                    )
                }
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
    onActivate: () -> Unit,
) {
    val idle = if (primary) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.14f)
    // Java grows a focused button by 8% (its medium focus scale), more than a row.
    val reduceMotion = LocalReduceMotion.current
    val focusAmount by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(motionDuration(TvOs.FOCUS_MS, reduceMotion)),
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
                // TalkBack must run the same action as OK; the key handler is
                // not reachable from a semantic click (AQ-23).
                onClick(label = text) { onActivate(); true }
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
