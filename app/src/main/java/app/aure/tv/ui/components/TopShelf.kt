package app.aure.tv.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aure.tv.data.AppArtCache
import app.aure.tv.data.TvApp
import app.aure.tv.data.loadShelfBanner
import app.aure.tv.discovery.DiscoveryItem
import app.aure.tv.discovery.DiscoveryRail
import app.aure.tv.discovery.DiscoverySection
import app.aure.tv.discovery.DiscoverySourceState
import app.aure.tv.ui.ArtEasing
import app.aure.tv.ui.LauncherColors
import app.aure.tv.ui.TvOs
import app.aure.tv.ui.TvScale
import app.aure.tv.ui.TvType
import app.aure.tv.ui.bannerIsHero
import app.aure.tv.ui.motionDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import kotlin.math.roundToInt

// 12_SYSTEM_TRANSITIONS T8: content hand-off stays under half a second; the
// outgoing poster is kept composed for the whole fade.
private const val ART_CROSSFADE_MS = 350

// The rail is the tvOS "Up Next" row: large landscape cards, one hero behind
// them, and a compact section bar instead of a second line of page dots.

/**
 * The selected card is scrolled to the left edge of the rail. The context menu
 * belongs there, next to the card, not in the middle of the screen.
 */
internal fun shelfCardAnchor(): Offset = Offset(
    TvOs.SAFE_AREA + SHELF_CARD_WIDTH / 2f,
    TvOs.SCREEN_HEIGHT - SHELF_RAIL_BOTTOM - SHELF_CARD_HEIGHT / 2f,
)
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
    // The engine knows why provider rows are missing. A silent empty rail made
    // a revoked READ_TV_LISTINGS look like data loss; Home shows this state.
    providerState: DiscoverySourceState = DiscoverySourceState.EMPTY,
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
        EmptyHome(
            scale = scale,
            listingsDenied = providerState == DiscoverySourceState.NO_PERMISSION,
        )
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
                                    .clip(RoundedCornerShape(scale.dp(SHELF_CARD_RADIUS)))
                                    .shadow(24.dp, RoundedCornerShape(scale.dp(SHELF_CARD_RADIUS)))
                                    .border(
                                        1.dp,
                                        Color.White.copy(alpha = 0.14f),
                                        RoundedCornerShape(scale.dp(SHELF_CARD_RADIUS)),
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
            listingsDenied = providerState == DiscoverySourceState.NO_PERMISSION,
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
        if (focused) requesters[selected]?.requestShelfFocusWhenAttached()
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
