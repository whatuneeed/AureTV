package com.whatuneed.ovisiontv.kotlin.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.data.TvApp
import com.whatuneed.ovisiontv.kotlin.data.searchApps
import com.whatuneed.ovisiontv.kotlin.ui.TvInk
import com.whatuneed.ovisiontv.kotlin.ui.LauncherColors
import com.whatuneed.ovisiontv.kotlin.ui.TvOs
import com.whatuneed.ovisiontv.kotlin.ui.TvScale
import com.whatuneed.ovisiontv.kotlin.ui.motionDuration
import com.whatuneed.ovisiontv.kotlin.ui.TvType
import com.whatuneed.ovisiontv.kotlin.ui.TvSpace

// Own geometry: 09_SEARCH §7 records that tvOS builds Search in TVML at runtime
// and none of its panel values could be measured, so the split below is a
// project decision — keyboard on the left, the results column on the right.
private const val TITLE_X = 90f
private const val TITLE_Y = 58f
private const val FIELD_X = 90f
private const val FIELD_Y = 140f
private const val KEYBOARD_X = 60f
private const val KEYBOARD_Y = 470f
private const val KEYBOARD_WIDTH = 1040f
private const val RESULTS_X = 1140f
private const val RESULTS_Y = 250f
private const val RESULTS_WIDTH = 720f
private const val RESULTS_HEIGHT = 760f
private const val RESULT_ROW_HEIGHT = 96f
private const val RESULT_ROW_GAP = 12f
private const val RESULT_PITCH = RESULT_ROW_HEIGHT + RESULT_ROW_GAP
private const val RESULT_ART_WIDTH = 128f
private const val RESULT_ART_HEIGHT = 72f
private const val VISIBLE_RESULTS = 7

// Lets the UI test keep the results header clear of the input field.
internal const val SEARCH_FIELD_TAG = "search_field"
internal const val SEARCH_OVERLAY_TAG = "search_overlay"

private enum class SearchZone { KEYS, RESULTS }

@Composable
fun SearchOverlay(
    apps: List<TvApp>,
    scale: TvScale,
    reduceMotion: Boolean,
    onClose: () -> Unit,
    onLaunch: (TvApp) -> Unit,
) {
    val labels = rememberKeyLabels()
    val russian = LocalConfiguration.current.locales[0].language == "ru"
    val keyboard = remember {
        KeyboardState("", if (russian) KeyboardMode.CYRILLIC else KeyboardMode.LATIN)
    }
    val rows = keyboard.rows(labels)
    val query = keyboard.text
    val results = remember(apps, query) { searchApps(apps, query) }
    var zone by remember { mutableStateOf(SearchZone.KEYS) }
    var resultIndex by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val keyRepeat = rememberTvKeyRepeat()
    val scope = rememberCoroutineScope()
    var entered by remember { mutableStateOf(false) }
    val enterAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        // Search is an entire page, so it fades in as one surface instead of a
        // cascade; the shared entrance keeps it in step with the other panels.
        animationSpec = tween(motionDuration(TvOs.SURFACE_ENTER_MS, reduceMotion)),
        label = "searchEnter",
    )
    LaunchedEffect(Unit) { entered = true }

    // A new query means a new list: the row index may now name another app,
    // so the cursor returns to the best match instead of staying on it.
    LaunchedEffect(query) {
        resultIndex = 0
    }

    LaunchedEffect(results.size) {
        resultIndex = resultIndex.coerceAtMost(results.lastIndex.coerceAtLeast(0))
        if (results.isEmpty()) zone = SearchZone.KEYS
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    fun back() {
        keyRepeat.stopRepeat()
        // The first BACK leaves the results and keeps the query; a second BACK
        // closes Search. Closing straight from the results lost the typed text.
        if (zone == SearchZone.RESULTS) zone = SearchZone.KEYS else onClose()
    }

    BackHandler { back() }

    val windowStart = (resultIndex / VISIBLE_RESULTS) * VISIBLE_RESULTS
    val visibleResults = results.drop(windowStart).take(VISIBLE_RESULTS)

    // The zone cursor lives in the engine; the rows become real focus targets so
    // TalkBack reads the highlighted result instead of silence.
    val resultRequesters = remember(results) { mutableMapOf<Int, FocusRequester>() }
    LaunchedEffect(zone, resultIndex, results) {
        if (zone == SearchZone.RESULTS) resultRequesters[resultIndex]?.requestFocus()
    }

    fun openResults() {
        if (results.isEmpty()) return
        zone = SearchZone.RESULTS
        resultIndex = resultIndex.coerceIn(0, results.lastIndex)
    }

    fun runKey(action: KeyAction) {
        if (action == KeyAction.Done) openResults() else keyboard.apply(action)
    }

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
            .testTag(SEARCH_OVERLAY_TAG)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) {
                    keyRepeat.release(event.key)
                    return@onKeyEvent false
                }
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                // A held key arrives as a stream of KeyDowns that the host may
                // report with repeatCount 0; the engine, not the platform,
                // decides what repeats.
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
                        if (zone == SearchZone.RESULTS) {
                            keyRepeat.stopRepeat()
                            zone = SearchZone.KEYS
                        } else {
                            repeatHorizontally(-1)
                        }
                        true
                    }

                    Key.DirectionRight -> {
                        if (zone == SearchZone.KEYS) repeatHorizontally(1)
                        true
                    }

                    Key.DirectionUp -> {
                        when {
                            zone == SearchZone.RESULTS -> {
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
                        if (zone == SearchZone.RESULTS) {
                            if (resultIndex < results.lastIndex) resultIndex++
                            keyRepeat.startRepeat(scope) {
                                if (resultIndex < results.lastIndex) resultIndex++
                            }
                        } else {
                            repeatVertically(1)
                        }
                        true
                    }

                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        keyRepeat.stopRepeat()
                        if (zone == SearchZone.RESULTS) {
                            results.getOrNull(resultIndex)?.let(onLaunch)
                        } else {
                            runKey(rows[keyboard.row][keyboard.column].action)
                        }
                        true
                    }

                    Key.Back -> {
                        back()
                        true
                    }

                    Key.Backspace, Key.Delete -> {
                        // Text editing keeps the platform's native repeat: every
                        // repeat must delete one more character.
                        keyboard.backspace()
                        true
                    }

                    Key.Search -> true

                    else -> {
                        val code = event.nativeKeyEvent.unicodeChar
                        val char = code.toChar()
                        if (code > 0 && !char.isISOControl()) {
                            zone = SearchZone.KEYS
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
            text = stringResource(R.string.search_title),
            color = LauncherColors.textPrimary,
            fontSize = scale.sp(TvType.title),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .offset(x = scale.dp(TITLE_X), y = scale.dp(TITLE_Y))
                .semantics { heading() },
        )

        TvSearchField(
            query = query,
            placeholder = stringResource(R.string.search_placeholder),
            scale = scale,
            modifier = Modifier
                .testTag(SEARCH_FIELD_TAG)
                .offset(x = scale.dp(FIELD_X), y = scale.dp(FIELD_Y)),
        )

        KeyboardPanel(
            scale = scale,
            width = KEYBOARD_WIDTH,
            modifier = Modifier.offset(x = scale.dp(KEYBOARD_X), y = scale.dp(KEYBOARD_Y)),
        ) {
            val keysTop = (PANEL_HEIGHT - keysHeight(rows)) / 2f
            KeyboardKeys(
                state = keyboard,
                labels = labels,
                scale = scale,
                top = keysTop,
                width = KEYBOARD_WIDTH - CONTENT_PADDING * 2,
                showFocus = zone == SearchZone.KEYS,
                onAction = ::runKey,
            )
        }

        if (results.isNotEmpty()) {
            Text(
                text = stringResource(R.string.search_results),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = scale.sp(TvType.body),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.offset(
                    x = scale.dp(RESULTS_X),
                    y = scale.dp(RESULTS_Y - 52f),
                ),
            )
        }

        Box(
            Modifier
                .offset(x = scale.dp(RESULTS_X), y = scale.dp(RESULTS_Y))
                .size(scale.dp(RESULTS_WIDTH), scale.dp(RESULTS_HEIGHT))
                .clipToBounds(),
        ) {
            // Only the visible page is composed: a clipped row has no place in
            // TalkBack, and the paging window is what keeps the list bounded.
            visibleResults.forEachIndexed { offset, app ->
                val index = windowStart + offset
                val rowRequester = remember(windowStart, offset) { FocusRequester() }
                resultRequesters[index] = rowRequester
                ResultRow(
                    app = app,
                    focused = zone == SearchZone.RESULTS && index == resultIndex,
                    scale = scale,
                    focusRequester = rowRequester,
                    onActivate = { onLaunch(app) },
                    modifier = Modifier.offset(y = scale.dp(offset * RESULT_PITCH)),
                )
            }
        }

        if (results.isEmpty()) {
            Column(
                modifier = Modifier
                    .offset(x = scale.dp(RESULTS_X), y = scale.dp(RESULTS_Y))
                    .size(scale.dp(RESULTS_WIDTH), scale.dp(RESULTS_HEIGHT)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Text(
                    text = stringResource(
                        if (query.isBlank()) R.string.search_hint else R.string.search_no_results,
                    ),
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = scale.sp(TvType.subhead),
                    fontWeight = FontWeight.Medium,
                )
                if (query.isNotBlank()) {
                    Spacer(Modifier.height(scale.dp(TvSpace.md)))
                    Text(
                        text = stringResource(R.string.search_no_results_hint),
                        color = LauncherColors.textSecondary,
                fontSize = scale.sp(TvType.body),
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultRow(
    app: TvApp,
    focused: Boolean,
    scale: TvScale,
    focusRequester: FocusRequester,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvResultRow(
        scale = scale,
        focused = focused,
        focusRequester = focusRequester,
        contentDescription = app.label,
        onClickLabel = app.label,
        onActivate = onActivate,
        modifier = modifier,
    ) { focus ->
        Box(
            Modifier
                .offset(x = scale.dp(12f))
                .size(scale.dp(RESULT_ART_WIDTH), scale.dp(RESULT_ART_HEIGHT))
                .clip(RoundedCornerShape(scale.dp(10f)))
                .background(
                    Brush.verticalGradient(
                        listOf(LauncherColors.cardFallbackTop, LauncherColors.cardFallbackBottom),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            ResultArtwork(app, scale)
        }

        Text(
            text = app.label,
            color = lerp(Color.White.copy(alpha = 0.92f), TvInk.focusInk, focus),
            fontSize = scale.sp(TvType.subhead),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .offset(x = scale.dp(RESULT_ART_WIDTH + 40f))
                .width(scale.dp(RESULTS_WIDTH - RESULT_ART_WIDTH - 72f)),
        )
    }
}

@Composable
private fun ResultArtwork(app: TvApp, scale: TvScale) {
    val banner = rememberArt(app.banner)
    val icon = rememberArt(app.icon)
    when {
        banner != null -> Image(
            bitmap = banner,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.Medium,
        )

        icon != null -> Image(
            bitmap = icon,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(0.55f),
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.Medium,
        )

        else -> Text(
            text = app.label.take(1).uppercase(),
            color = Color.White.copy(alpha = 0.85f),
            fontSize = scale.sp(TvType.subhead),
            fontWeight = FontWeight.SemiBold,
        )
    }
}
