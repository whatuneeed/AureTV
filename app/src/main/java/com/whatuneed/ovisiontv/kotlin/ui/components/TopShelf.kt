package com.whatuneed.ovisiontv.kotlin.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.data.Adaptive
import com.whatuneed.ovisiontv.kotlin.data.ArtFailureCache
import com.whatuneed.ovisiontv.kotlin.data.estimatedArtBytes
import com.whatuneed.ovisiontv.kotlin.data.AppArtCache
import com.whatuneed.ovisiontv.kotlin.data.TvApp
import com.whatuneed.ovisiontv.kotlin.data.loadShelfBanner
import com.whatuneed.ovisiontv.kotlin.data.trimmedCapacity
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryItem
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryRail
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryReason
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoverySection
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoverySource
import com.whatuneed.ovisiontv.kotlin.discovery.artUri
import com.whatuneed.ovisiontv.kotlin.ui.ArtEasing
import com.whatuneed.ovisiontv.kotlin.ui.LauncherColors
import com.whatuneed.ovisiontv.kotlin.ui.TvInk
import com.whatuneed.ovisiontv.kotlin.ui.TvLeading
import com.whatuneed.ovisiontv.kotlin.ui.TvOs
import com.whatuneed.ovisiontv.kotlin.ui.TvScale
import com.whatuneed.ovisiontv.kotlin.ui.TvSpace
import com.whatuneed.ovisiontv.kotlin.ui.TvType
import com.whatuneed.ovisiontv.kotlin.ui.bannerIsHero
import com.whatuneed.ovisiontv.kotlin.ui.motionDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

// 12_SYSTEM_TRANSITIONS T8: content hand-off stays under half a second; the
// outgoing poster is kept composed for the whole fade.
private const val ART_CROSSFADE_MS = 350

// The rail is the tvOS "Up Next" row: large landscape cards, one hero behind
// them, and a compact section bar instead of a second line of page dots.
private const val CARD_WIDTH = 330f
private const val CARD_HEIGHT = 186f
private const val CARD_GAP = 24f
private const val CARD_RADIUS = 18f
private const val RAIL_BOTTOM = 56f
private const val RAIL_TITLE_GAP = 12f

/**
 * The selected card is scrolled to the left edge of the rail. The context menu
 * belongs there, next to the card, not in the middle of the screen.
 */
internal fun shelfCardAnchor(): Offset = Offset(
    TvOs.SAFE_AREA + CARD_WIDTH / 2f,
    TvOs.SCREEN_HEIGHT - RAIL_BOTTOM - CARD_HEIGHT / 2f,
)
private const val INFO_BOTTOM = 340f
private const val SECTION_BAR_TOP = 270f
private const val SECTION_BAR_HEIGHT = 68f
private const val SECTION_GAP = 12f

/**
 * tvOS top shelf, extended into rails. The focused app still owns the screen:
 * its poster fills the display and its rows are the first section. The section
 * bar only appears when the profile has more than one non-empty rail. A home
 * with no banner and no published rows gets a neutral welcome, not a black
 * field above the dock.
 */
@Composable
fun TopShelf(
    app: TvApp?,
    rails: List<DiscoveryRail>,
    selectedSection: DiscoverySection?,
    selectedCard: Int,
    focusedCard: Int,
    sectionFocused: Boolean,
    onCardActivate: (DiscoveryItem) -> Unit,
    onSectionActivate: (DiscoverySection) -> Unit,
    scale: TvScale,
    reduceMotion: Boolean = false,
    // Only the selected online card has an official preview URL. Home owns the
    // opt-in, Reduce Motion and modal gates; null means "show the poster".
    heroPreviewUri: String? = null,
    headingTop: Float = 57f,
    // A resume, a closed menu or a closed search can leave Android focus on a
    // node that no longer exists; this tick asks the shelf to restore its card.
    focusTick: Int = 0,
) {
    val rail = rails.firstOrNull { it.section == selectedSection } ?: rails.firstOrNull()
    val selected = rail?.items?.getOrNull(selectedCard) ?: rail?.items?.firstOrNull()
    val arts = rememberRailArts(rail?.items.orEmpty(), selected?.id)
    val bannerArt = app?.let { rememberShelfBanner(it) }
    val density = LocalDensity.current.density
    // A banner is only drawn when it can cover the shelf without being
    // upscaled past BANNER_MAX_UPSCALE. An app can ship a small banner and no
    // rows; treating that banner as a hero leaves a title on a black field.
    // Only a banner that will actually be drawn counts as content.
    val drawableBanner = bannerArt?.takeIf {
        bannerIsHero(it.width, scale.factor * density)
    }

    // An app with no usable banner and no published rows still has a Home: the
    // dock is below, so a neutral welcome state explains how to fill the shelf
    // instead of leaving a black field above it.
    if (drawableBanner == null && rails.isEmpty()) {
        EmptyHome(scale)
        return
    }
    val heroPrefix = "${ArtRole.HERO}:"
    val cardPrefix = "${ArtRole.CARD}:"
    // A provider may publish a card but no separate hero. Using that card's
    // real artwork keeps the shelf from becoming a title on a black field.
    val currentHero = selected?.let {
        arts["$heroPrefix${it.id}"] ?: arts["$cardPrefix${it.id}"]
    }

    Box(Modifier.fillMaxSize()) {
        // Backdrop: the hero when it is large enough to fill the screen, the
        // app banner otherwise. A narrow source is shown as a card, never
        // stretched into a fake 4K backdrop.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(LauncherColors.backgroundTop, LauncherColors.backgroundBottom),
                    ),
                ),
        )
        if (currentHero != null) {
            // The previous hero stays in the map for one transition, so the
            // outgoing poster is composed for the whole fade (T8).
            Crossfade(
                targetState = selected?.id,
                modifier = Modifier.fillMaxSize(),
                animationSpec = tween(ART_CROSSFADE_MS, easing = ArtEasing),
                label = "shelfHero",
            ) { id ->
                val bitmap = id?.let { arts["$heroPrefix$it"] }
                if (bitmap != null) {
                    if (heroIsWide(bitmap)) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            filterQuality = FilterQuality.Medium,
                        )
                    } else {
                        // tvOS fills the shelf with the selected artwork. A
                        // narrow provider poster cannot honestly become a 4K
                        // backdrop, so it sits centred on a dimmed crop of the
                        // same art. The crop supplies the colour field without
                        // stretching the poster; the sharp 760pt card remains
                        // the subject.
                        Box(Modifier.fillMaxSize()) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { alpha = 0.30f },
                                contentScale = ContentScale.Crop,
                                filterQuality = FilterQuality.Low,
                            )
                            Image(
                                bitmap = bitmap,
                                contentDescription = null,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(bottom = scale.dp(80f))
                                    .width(scale.dp(760f))
                                    .aspectRatio(bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1))
                                    .clip(RoundedCornerShape(scale.dp(CARD_RADIUS)))
                                    .shadow(24.dp, RoundedCornerShape(scale.dp(CARD_RADIUS)))
                                    .border(
                                        1.dp,
                                        Color.White.copy(alpha = 0.14f),
                                        RoundedCornerShape(scale.dp(CARD_RADIUS)),
                                    ),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                }
            }
        } else if (rail?.section == DiscoverySection.FROM_APP || rails.isEmpty()) {
            // The dock app's banner is only honest behind its own shelf; a
            // cross-app rail with no art keeps the neutral gradient instead of
            // borrowing the wrong app's branding.
            drawableBanner
                ?.let { banner ->
                    Image(
                        bitmap = banner,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.Low,
                    )
                }
        }

        // tvOS-style motion hero. The player is transparent until the first
        // frame, so the poster above remains the loading and failure state.
        // It is deliberately behind the same scrim, title and dock as a still.
        if (heroPreviewUri != null && !reduceMotion) {
            HeroPreview(
                uri = heroPreviewUri,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Readability veil; the hero is never darkened into mud.
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val width = size.width.roundToInt()
                    val height = size.height.roundToInt()
                    // A smooth veil has no detail to lose: keep the cached
                    // gradient at design size instead of allocating a 4K
                    // ARGB bitmap (33 MB on a 3840×2160 panel).
                    drawImage(
                        image = shelfScrim(
                            width.coerceAtMost(SCRIM_MAX_WIDTH),
                            height.coerceAtMost(SCRIM_MAX_HEIGHT),
                        ),
                        dstSize = IntSize(width, height),
                        filterQuality = FilterQuality.Low,
                    )
                },
        )

        TopShelfHeading(
            app = app,
            selected = selected,
            scale = scale,
            top = headingTop,
        )

        // The browsing Home is a poster, a title and the dock, as on tvOS. The
        // rail and its section bar belong to the focused shelf; drawing them
        // behind the grid was what put cards under the dock. They appear only
        // after Up enters the shelf, when the grid has already slid away.
        val shelfEngaged = focusedCard >= 0 || sectionFocused
        if (shelfEngaged && rails.size > 1 && rail != null) {
            SectionBar(
                rails = rails,
                selected = rail.section,
                focused = sectionFocused,
                appLabel = app?.label,
                onSectionActivate = onSectionActivate,
                scale = scale,
                reduceMotion = reduceMotion,
                focusTick = focusTick,
            )
        }

        if (shelfEngaged && selected != null) {
            RecommendationInfo(selected, scale)
        }

        if (shelfEngaged && rail != null) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(),
            ) {
                ShelfRail(
                    rail = rail,
                    appLabel = app?.label,
                    selectedCard = selectedCard,
                    focusedCard = focusedCard,
                    arts = arts,
                    showSource = rail.section != DiscoverySection.FROM_APP,
                    onCardActivate = onCardActivate,
                    scale = scale,
                    reduceMotion = reduceMotion,
                    focusTick = focusTick,
                )
            }
        }
    }
}

/** Neutral first screen when no app publishes a banner or a recommendation. */
@Composable
private fun EmptyHome(scale: TvScale) {
    Column(
        Modifier
            .padding(start = scale.dp(TvOs.SAFE_AREA), top = scale.dp(300f))
            .width(scale.dp(1000f)),
    ) {
        Text(
            text = stringResource(R.string.home_empty_title),
            color = Color.White,
            fontSize = scale.sp(TvType.largeTitle),
            lineHeight = scale.sp(64f),
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(scale.dp(18f)))
        Text(
            text = stringResource(R.string.home_empty_body),
            color = Color.White.copy(alpha = 0.62f),
            fontSize = scale.sp(TvType.subhead),
            lineHeight = scale.sp(42f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TopShelfHeading(
    app: TvApp?,
    selected: DiscoveryItem?,
    scale: TvScale,
    top: Float,
) {
    // tvOS captions the shelf with the selected content, not with the app
    // category. The old "APPLICATION" line was product metadata leaking into
    // the most prominent corner of Home.
    val title = selected?.title?.takeIf { it.isNotBlank() }
        ?: app?.label
        ?: selected?.packageLabel
        ?: stringResource(R.string.shelf_context)
    Column(
        Modifier
            .padding(start = scale.dp(TvOs.SAFE_AREA), top = scale.dp(top))
            .width(scale.dp(900f)),
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = scale.sp(TvType.hero),
            lineHeight = scale.sp(78f),
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                shadow = Shadow(color = Color.Black.copy(alpha = 0.55f), blurRadius = 30f),
            ),
        )
    }
}

@Composable
private fun SectionBar(
    rails: List<DiscoveryRail>,
    selected: DiscoverySection,
    focused: Boolean,
    appLabel: String?,
    onSectionActivate: (DiscoverySection) -> Unit,
    scale: TvScale,
    reduceMotion: Boolean,
    focusTick: Int = 0,
) {
    val requesters = remember(rails) { mutableMapOf<DiscoverySection, FocusRequester>() }
    // The pill is a real focus target: the custom cursor used to paint only,
    // so TalkBack stayed on the grid while the bar visibly owned the remote.
    LaunchedEffect(focused, selected, focusTick) {
        if (focused) requesters[selected]?.requestWhenAttached()
    }
    Row(
        modifier = Modifier
            .padding(
                start = scale.dp(TvOs.SAFE_AREA),
                top = scale.dp(SECTION_BAR_TOP),
            )
            .height(scale.dp(SECTION_BAR_HEIGHT)),
        horizontalArrangement = Arrangement.spacedBy(scale.dp(SECTION_GAP)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        rails.forEach { rail ->
            val active = rail.section == selected
            val label = sectionLabel(rail.section, appLabel)
            val alpha by animateFloatAsState(
                targetValue = if (active) 1f else 0.52f,
                animationSpec = tween(motionDuration(TvOs.FOCUS_MS, reduceMotion)),
                label = "sectionTab",
            )
            val requester = requesters.getOrPut(rail.section) { FocusRequester() }
            Column(
                modifier = Modifier
                    .focusRequester(requester)
                    .focusable()
                    .clip(RoundedCornerShape(scale.dp(10f)))
                    .background(
                        if (active && focused) Color.White.copy(alpha = 0.10f) else Color.Transparent,
                    )
                    .padding(
                        horizontal = scale.dp(12f),
                        vertical = scale.dp(8f),
                    )
                    .semantics {
                        contentDescription = label
                        role = Role.Tab
                        onClick(label = label) {
                            onSectionActivate(rail.section)
                            true
                        }
                    }
                    .testTag("shelfSection"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // tvOS names the active page with plain type and a short rule;
                // a white iOS pill in the middle of the poster was the loudest
                // non-tvOS element left on Home.
                Text(
                    text = label,
                    color = Color.White.copy(alpha = alpha),
                    fontSize = scale.sp(TvType.subhead),
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                )
                if (active) {
                    Spacer(Modifier.height(scale.dp(5f)))
                    Box(
                        Modifier
                            .width(scale.dp(34f))
                            .height(scale.dp(2f))
                            .background(Color.White.copy(alpha = 0.9f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun sectionLabel(section: DiscoverySection, appLabel: String?): String = when (section) {
    DiscoverySection.CONTINUE -> stringResource(R.string.discovery_section_continue)
    DiscoverySection.ONLINE -> stringResource(R.string.discovery_section_online)
    DiscoverySection.FOR_YOU -> stringResource(R.string.discovery_section_for_you)
    DiscoverySection.FROM_APP -> if (appLabel.isNullOrBlank()) {
        stringResource(R.string.discovery_section_from_current)
    } else {
        stringResource(R.string.discovery_section_from_app, appLabel)
    }
    DiscoverySection.RECENT -> stringResource(R.string.discovery_section_recent)
    DiscoverySection.FAVORITES -> stringResource(R.string.discovery_section_favorites)
}

@Composable
private fun ShelfRail(
    rail: DiscoveryRail,
    appLabel: String?,
    selectedCard: Int,
    focusedCard: Int,
    arts: Map<String, ImageBitmap>,
    showSource: Boolean,
    onCardActivate: (DiscoveryItem) -> Unit,
    scale: TvScale,
    reduceMotion: Boolean,
    focusTick: Int = 0,
) {
    val state = rememberLazyListState()
    val requesters = remember(rail.items) { mutableMapOf<String, FocusRequester>() }
    LaunchedEffect(rail.section, rail.items, selectedCard, focusedCard, focusTick) {
        val target = if (focusedCard >= 0) focusedCard else selectedCard
        if (rail.items.isNotEmpty()) {
            val requester = if (focusedCard >= 0) {
                requesters[rail.items[focusedCard.coerceIn(0, rail.items.lastIndex)].id]
            } else {
                null
            }
            // A card outside the lazy window has not been composed yet, so the
            // first request can fail. Reveal it, then ask again once it exists.
            requester?.requestWhenAttached()
            // Reveal the card by the smallest amount. Aligning every step to
            // the left edge made the rail jump under the cursor.
            val info = state.layoutInfo
            val visible = info.visibleItemsInfo.firstOrNull { it.index == target }
            if (visible == null) {
                state.animateScrollToItem(target.coerceIn(0, rail.items.lastIndex))
            } else {
                val delta = when {
                    visible.offset < info.viewportStartOffset ->
                        visible.offset - info.viewportStartOffset
                    visible.offset + visible.size > info.viewportEndOffset ->
                        visible.offset + visible.size - info.viewportEndOffset
                    else -> 0
                }
                if (delta != 0) {
                    state.animateScrollBy(
                        delta.toFloat(),
                        tween(motionDuration(TvOs.SCROLL_DURATION_MS, reduceMotion)),
                    )
                }
            }
            requester?.requestWhenAttached()
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = scale.dp(RAIL_BOTTOM)),
    ) {
        Text(
            text = sectionLabel(rail.section, appLabel),
            color = Color.White.copy(alpha = 0.6f),
            fontSize = scale.sp(TvType.subhead),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = scale.dp(TvOs.SAFE_AREA)),
        )
        Spacer(Modifier.height(scale.dp(RAIL_TITLE_GAP)))
        LazyRow(
            state = state,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = scale.dp(TvOs.SAFE_AREA)),
            horizontalArrangement = Arrangement.spacedBy(scale.dp(CARD_GAP)),
        ) {
            itemsIndexed(rail.items, key = { _, item -> item.id }) { index, item ->
                val focused = index == focusedCard
                val requester = remember(item.id) { FocusRequester() }
                requesters[item.id] = requester
                RailCard(
                    item = item,
                    art = arts[cardKey(item)],
                    focused = focused,
                    selected = index == selectedCard,
                    showSource = showSource,
                    onActivate = { onCardActivate(item) },
                    scale = scale,
                    reduceMotion = reduceMotion,
                    focusRequester = requester,
                )
            }
        }
    }
}

@Composable
private fun RailCard(
    item: DiscoveryItem,
    art: ImageBitmap?,
    focused: Boolean,
    selected: Boolean,
    showSource: Boolean,
    onActivate: () -> Unit,
    scale: TvScale,
    reduceMotion: Boolean,
    focusRequester: FocusRequester,
) {
    val grow by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = tween(motionDuration(TvOs.FOCUS_MS, reduceMotion)),
        label = "railCard",
    )
    // A lazy row composes its first cards during the first measure, after the
    // parent's LaunchedEffect has already run. The card therefore owns the
    // focus request as well: an initially focused shelf (or a card revealed by
    // a scroll) must take focus once it actually exists.
    LaunchedEffect(focused) {
        if (focused) focusRequester.requestWhenAttached()
    }
    Box(
        Modifier
            .width(scale.dp(CARD_WIDTH))
            .height(scale.dp(CARD_HEIGHT))
            .focusRequester(focusRequester)
            .focusable()
            .graphicsLayer {
                scaleX = grow
                scaleY = grow
            }
            .shadow(
                elevation = if (focused) 22.dp else 8.dp,
                shape = RoundedCornerShape(scale.dp(CARD_RADIUS)),
                clip = false,
            )
            .clip(RoundedCornerShape(scale.dp(CARD_RADIUS)))
            .background(TvInk.panel)
            .then(
                when {
                    focused -> Modifier.border(
                        3.dp,
                        Color.White,
                        RoundedCornerShape(scale.dp(CARD_RADIUS)),
                    )
                    // The hero above is this card; keep a quiet marker even
                    // while the section bar owns the remote.
                    selected -> Modifier.border(
                        2.dp,
                        Color.White.copy(alpha = 0.55f),
                        RoundedCornerShape(scale.dp(CARD_RADIUS)),
                    )
                    else -> Modifier.border(
                        1.dp,
                        Color.White.copy(alpha = 0.10f),
                        RoundedCornerShape(scale.dp(CARD_RADIUS)),
                    )
                },
            )
            .semantics {
                contentDescription = item.title
                role = Role.Button
                onClick(label = item.title) {
                    onActivate()
                    true
                }
            }
            .testTag("shelfCard"),
    ) {
        if (art != null) {
            Image(
                bitmap = art,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Low,
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                LauncherColors.cardFallbackTop,
                                LauncherColors.cardFallbackBottom,
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = item.title,
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = scale.sp(TvType.callout),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = scale.dp(18f)),
                )
            }
        }

        val progress = item.progress
        if (progress != null && progress > 0f) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(scale.dp(4f))
                    .background(Color.White.copy(alpha = 0.24f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(scale.dp(4f))
                        .background(Color.White.copy(alpha = 0.92f)),
                )
            }
        }

        val source = item.packageLabel
        if (showSource && !source.isNullOrBlank()) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(scale.dp(TvSpace.sm))
                    .clip(RoundedCornerShape(scale.dp(9f)))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = scale.dp(8f), vertical = scale.dp(3f)),
            ) {
                Text(
                    text = source,
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = scale.sp(15f),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RecommendationInfo(card: DiscoveryItem, scale: TvScale) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = scale.dp(TvOs.SAFE_AREA),
                    bottom = scale.dp(INFO_BOTTOM),
                )
                .width(scale.dp(900f)),
            verticalArrangement = Arrangement.Bottom,
        ) {
            card.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                Spacer(Modifier.height(scale.dp(6f)))
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = scale.sp(TvType.callout),
                    lineHeight = scale.sp(TvLeading.infoSubtitle),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(scale.dp(8f)))
            Text(
                text = reasonLabel(card.reason),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = scale.sp(TvType.caption),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val progress = card.progress
            if (progress != null && progress > 0f) {
                Spacer(Modifier.height(scale.dp(TvSpace.md)))
                Box(
                    Modifier
                        .width(scale.dp(360f))
                        .height(scale.dp(4f))
                        .background(Color.White.copy(alpha = 0.28f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(scale.dp(4f))
                            .background(Color.White.copy(alpha = 0.9f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun reasonLabel(reason: DiscoveryReason): String = when (reason) {
    DiscoveryReason.CONTINUE_WATCHING -> stringResource(R.string.discovery_reason_continue)
    DiscoveryReason.RECENTLY_LAUNCHED -> stringResource(R.string.discovery_reason_recent)
    DiscoveryReason.FAVORITE -> stringResource(R.string.discovery_reason_favorite)
    DiscoveryReason.NEW_FROM_APP -> stringResource(R.string.discovery_reason_new)
    DiscoveryReason.OFTEN_USED -> stringResource(R.string.discovery_reason_often)
    DiscoveryReason.FROM_CURRENT_APP -> stringResource(R.string.discovery_reason_from_app)
    DiscoveryReason.TRENDING -> stringResource(R.string.discovery_reason_trending)
    DiscoveryReason.DIVERSE_PICK -> stringResource(R.string.discovery_reason_diverse)
}

// ---------------------------------------------------------------------------
// Artwork. Two decode sizes and two cache keys per item: the rail card is not
// allowed to pin the full-screen hero (and the hero is not downsampled to a
// card). Everything is bounds-first and never upscaled.
// ---------------------------------------------------------------------------

internal enum class ArtRole { CARD, HERO }

private val shelfArtCache =
    object : LruCache<String, ImageBitmap>(Adaptive.budgets.discoveryArtBytes) {
        // The old cache was bounded by entry count: twelve 2560px heroes could
        // sit at ~176 MB while the budget read "12". Bytes make the cap honest.
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            estimatedArtBytes(value.width, value.height)
    }

/**
 * Bumped by every trim. The shelf keeps decoded art in a Compose map; that map
 * was a second owner of the same bitmaps, so an eviction freed nothing
 * (audit A-02). A bump makes it drop its entries.
 */
private val shelfArtEpoch = mutableIntStateOf(0)

// A dead poster host must not be retried on every recomposition. Failures are
// negative-cached briefly; the TTL keeps a temporary offline state recoverable.
private val shelfArtFailures = ArtFailureCache()

internal fun trimShelfArtCache(percent: Int) {
    if (percent >= 100) {
        val max = shelfArtCache.maxSize()
        shelfArtCache.evictAll()
        shelfArtCache.resize(trimmedCapacity(max, 100))
    } else {
        val target = trimmedCapacity(shelfArtCache.maxSize(), percent)
        shelfArtCache.resize(target)
        shelfArtCache.trimToSize(target)
    }
    shelfArtEpoch.intValue++
}

internal fun trimShelfArtCacheTo(bytes: Int) {
    val target = bytes.coerceAtLeast(1)
    if (target < shelfArtCache.maxSize()) {
        shelfArtCache.resize(target)
        shelfArtCache.trimToSize(target)
        shelfArtEpoch.intValue++
    }
}

/**
 * Preloads the rail and the selected hero. The provider still owns every
 * poster; this is a decoded working set, not a second catalogue.
 */
@Composable
private fun rememberRailArts(
    items: List<DiscoveryItem>,
    selectedId: String?,
): Map<String, ImageBitmap> {
    val context = LocalContext.current
    val arts = remember { mutableStateMapOf<String, ImageBitmap>() }
    val epoch = shelfArtEpoch.intValue
    var loadedEpoch by remember { mutableIntStateOf(epoch) }
    LaunchedEffect(items, selectedId, epoch) {
        if (loadedEpoch != epoch) {
            arts.clear()
            loadedEpoch = epoch
        }
        // Drop cards that left the rail and the hero of a previous selection.
        // Without this, browsing twenty cards would fill the map with old
        // heroes and an arbitrary trim could evict a card still on screen.
        val cards = items.mapTo(HashSet()) { cardKey(it) }
        val hero = selectedId?.let { "${ArtRole.HERO}:$it" }
        val heroKeys = arts.keys.toList().filter { it.startsWith("${ArtRole.HERO}:") }
        // Keep the current hero and the one before it so Crossfade can compose
        // the outgoing poster; anything older is safe to drop.
        val previousHero = heroKeys.lastOrNull { it != hero }
        arts.keys.toList().forEach { key ->
            if ((key.startsWith("${ArtRole.CARD}:") && key !in cards) ||
                (key.startsWith("${ArtRole.HERO}:") && key != hero && key != previousHero)
            ) {
                arts.remove(key)
            }
        }
        items.forEach { item ->
            loadInto(arts, context, item, ArtRole.CARD)
        }
        items.firstOrNull { it.id == selectedId }?.let { item ->
            loadInto(arts, context, item, ArtRole.HERO)
        }
        // Keep the rail plus the hero; the LRU would evict anyway, but the map
        // must not outlive the cache entry it points at.
        val keep = Adaptive.budgets.discoveryArtEntries
        if (arts.size > keep) {
            val overflow = arts.size - keep
            arts.keys.take(overflow).forEach(arts::remove)
        }
    }
    return arts
}

private suspend fun loadInto(
    arts: MutableMap<String, ImageBitmap>,
    context: Context,
    item: DiscoveryItem,
    role: ArtRole,
) {
    val key = if (role == ArtRole.HERO) heroKey(item) else cardKey(item)
    if (arts[key] != null) return
    val target = when (role) {
        ArtRole.HERO -> Adaptive.budgets.discoveryHeroPx
        ArtRole.CARD -> Adaptive.budgets.discoveryCardPx
    }
    val cacheKey = "${item.source}:${item.artUri}|$target|$role"
    if (shelfArtFailures.isFailed(cacheKey)) return
    val cached = shelfArtCache.get(cacheKey)
    val loaded = cached ?: withContext(Dispatchers.IO) { loadArt(context, item, target) }
    if (loaded != null) {
        if (cached == null) shelfArtCache.put(cacheKey, loaded)
        arts[key] = loaded
        shelfArtFailures.clear(cacheKey)
    } else if (cached == null) {
        shelfArtFailures.markFailed(cacheKey)
    }
}

private fun heroKey(item: DiscoveryItem): String = "${ArtRole.HERO}:${item.id}"
private fun cardKey(item: DiscoveryItem): String = "${ArtRole.CARD}:${item.id}"

/**
 * TV providers publish posters on hosts that may hang. The old path opened the
 * URL twice with no connect/read timeout and no size cap, so one dead host held
 * a Dispatchers.IO thread for minutes and could allocate an unbounded buffer
 * (audit A-04). Now the bytes are read once, bounded, and decoded from memory.
 */
private const val ART_TIMEOUT_MS = 10_000
private const val ART_MAX_BYTES = 8 * 1024 * 1024

private suspend fun loadArt(context: Context, item: DiscoveryItem, targetPx: Int): ImageBitmap? {
    if (item.source == DiscoverySource.PACKAGE_MANAGER) {
        val banner = item.landscapeUri?.let { key ->
            AppArtCache.get(key) ?: AppArtCache.load(context, key)
        }
        if (banner != null) return banner
        return item.posterUri?.let { key ->
            AppArtCache.get(key) ?: AppArtCache.load(context, key)
        }
    }
    val candidates = listOfNotNull(item.landscapeUri, item.posterUri).distinct()
    for (value in candidates) {
        decodeArt(context, value, targetPx)?.let { return it }
    }
    return null
}

private suspend fun decodeArt(
    context: Context,
    value: String,
    targetPx: Int,
): ImageBitmap? {
    return try {
        val uri = Uri.parse(value)
        val bytes = readArtBytes(context, uri, value) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        val limit = targetPx.coerceAtLeast(1)
        while (bounds.outWidth / sample > limit || bounds.outHeight / sample > limit) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inScaled = false
            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }
}

private suspend fun readArtBytes(
    context: Context,
    uri: Uri,
    value: String,
): ByteArray? {
    return try {
        currentCoroutineContext().ensureActive()
        if (uri.scheme == "http" || uri.scheme == "https") {
            val connection = URL(value).openConnection() as HttpURLConnection
            connection.connectTimeout = ART_TIMEOUT_MS
            connection.readTimeout = ART_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
                if (connection.contentLengthLong > ART_MAX_BYTES) return null
                connection.inputStream.use { it.readAtMost(ART_MAX_BYTES) }
            } finally {
                connection.disconnect()
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { it.readAtMost(ART_MAX_BYTES) }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }
}

/** Reads a stream into one buffer, refusing anything over [limit] bytes. */
private suspend fun java.io.InputStream.readAtMost(limit: Int): ByteArray? {
    val out = java.io.ByteArrayOutputStream(COPY_CHUNK)
    val buffer = ByteArray(COPY_CHUNK)
    var total = 0
    while (true) {
        currentCoroutineContext().ensureActive()
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > limit) return null
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}

private const val COPY_CHUNK = 64 * 1024

/**
 * The focused app is decoded once at screen size, so an app that ships a larger
 * banner is not shown from the thumbnail; a smaller source keeps the thumbnail
 * instead of being upscaled into a bigger bitmap.
 */
@Composable
private fun rememberShelfBanner(app: TvApp): ImageBitmap? {
    if (app.banner == null) return null
    val fallback = rememberArt(app.banner)
    val context = LocalContext.current
    var wide by remember(app.packageName, app.banner) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(app.packageName, app.banner) {
        val thumbnail = fallback ?: withContext(Dispatchers.IO) {
            AppArtCache.load(context, app.banner)
        }
        wide = loadShelfBanner(
            context,
            app.packageName,
            TvOs.SCREEN_WIDTH.roundToInt(),
            TvOs.SCREEN_HEIGHT.roundToInt(),
        )?.takeIf { thumbnail == null || it.width > thumbnail.width }
    }
    // A smaller source keeps the grid thumbnail instead of being upscaled.
    return wide?.takeIf { fallback == null || it.width > fallback.width } ?: fallback
}

/**
 * A hero must be landscape and cover at least half of the physical screen
 * before it fills it. A tall poster that happens to be high-resolution would
 * otherwise be cropped into a fake backdrop; it is shown as a card instead.
 */
private fun heroIsWide(art: ImageBitmap): Boolean =
    // A landscape source is the normal top-shelf shape on tvOS. The previous
    // half-screen rule rejected a perfectly usable 16:9 provider still and
    // dropped it into a floating card; that card, not the resolution, was the
    // visible difference from tvOS. Portrait art still keeps its card.
    art.width >= art.height * 1.3f

private const val SCRIM_MAX_WIDTH = 960
private const val SCRIM_MAX_HEIGHT = 540

private var scrimImage: ImageBitmap? = null
private var scrimWidth = 0
private var scrimHeight = 0

internal fun dropShelfScrim() {
    scrimImage = null
}

/** One vertical veil, rebuilt only when the surface size changes. */
private fun shelfScrim(width: Int, height: Int): ImageBitmap {
    val cached = scrimImage
    if (cached != null && scrimWidth == width && scrimHeight == height) return cached
    val bitmap = android.graphics.Bitmap.createBitmap(
        width.coerceAtLeast(1),
        height.coerceAtLeast(1),
        android.graphics.Bitmap.Config.ARGB_8888,
    )
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint()
    paint.shader = android.graphics.LinearGradient(
        0f,
        0f,
        0f,
        height.toFloat(),
        intArrayOf(
            android.graphics.Color.argb(90, 0, 0, 0),
            android.graphics.Color.argb(20, 0, 0, 0),
            android.graphics.Color.argb(170, 0, 0, 0),
        ),
        floatArrayOf(0f, 0.45f, 1f),
        android.graphics.Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    val image = bitmap.asImageBitmap()
    scrimImage = image
    scrimWidth = width
    scrimHeight = height
    return image
}

// Same layout-attach retry as Home: a requester created by this composition
// is not attached until the next layout pass.
private suspend fun FocusRequester.requestWhenAttached(): Boolean {
    repeat(3) {
        if (runCatching { requestFocus() }.isSuccess) return true
        withFrameNanos { }
    }
    return false
}
