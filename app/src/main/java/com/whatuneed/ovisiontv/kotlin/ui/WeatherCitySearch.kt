package com.whatuneed.ovisiontv.kotlin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.whatuneed.ovisiontv.kotlin.core.designsystem.SectionGlyph
import com.whatuneed.ovisiontv.kotlin.ui.components.TvKeyRepeat
import kotlin.math.abs
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.data.CityPlace
import com.whatuneed.ovisiontv.kotlin.data.CitySearch
import com.whatuneed.ovisiontv.kotlin.data.LocalePreference
import com.whatuneed.ovisiontv.kotlin.data.WeatherPreferences
import com.whatuneed.ovisiontv.kotlin.ui.components.CONTENT_PADDING
import com.whatuneed.ovisiontv.kotlin.ui.components.KeyAction
import com.whatuneed.ovisiontv.kotlin.ui.components.KeyboardKeys
import com.whatuneed.ovisiontv.kotlin.ui.components.KeyboardMode
import com.whatuneed.ovisiontv.kotlin.ui.components.KeyboardPanel
import com.whatuneed.ovisiontv.kotlin.ui.components.KeyboardState
import com.whatuneed.ovisiontv.kotlin.ui.components.PANEL_HEIGHT
import com.whatuneed.ovisiontv.kotlin.ui.components.TvResultRow
import com.whatuneed.ovisiontv.kotlin.ui.components.TvSearchField
import com.whatuneed.ovisiontv.kotlin.ui.components.keysHeight
import com.whatuneed.ovisiontv.kotlin.ui.components.rememberKeyLabels
import com.whatuneed.ovisiontv.kotlin.ui.components.rememberTvKeyRepeat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

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

private enum class CityZone { KEYS, RESULTS }

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
    // The query the results belong to; a stale list must not be selectable
    // while the next debounce/network answer is still pending (AQ-24).
    var foundQuery by remember { mutableStateOf<String?>(null) }
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
            foundQuery = null
            return@LaunchedEffect
        }
        delay(CITY_DEBOUNCE_MS)
        searching = true
        val result = withContext(Dispatchers.IO) { CitySearch.search(q, geoLanguage) }
        found = result
        foundQuery = q
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
    } else if (foundQuery == trimmed) {
        found.orEmpty()
    } else {
        emptyList()
    }
    val note: String? = when {
        trimmed.length < CITY_MIN_QUERY -> null
        searching -> stringResource(R.string.setup_city_searching)
        foundQuery != trimmed -> null
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
        if (zone == CityZone.RESULTS) rowRequesters[resultIndex]?.requestSetupFocusWhenAttached()
    }

    LaunchedEffect(Unit) { focusRequester.requestSetupFocusWhenAttached() }

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

// The wizard changes pages by swapping the whole row set. The requester is
// attached in the next layout pass, so retry on the frame clock instead of
// leaving the root focused after a cold start or a page transition.
internal suspend fun FocusRequester.requestSetupFocusWhenAttached(): Boolean {
    repeat(3) {
        if (runCatching { requestFocus() }.isSuccess) return true
        withFrameNanos { }
    }
    return false
}
