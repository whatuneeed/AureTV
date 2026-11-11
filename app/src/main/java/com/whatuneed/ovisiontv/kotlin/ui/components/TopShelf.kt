package com.whatuneed.ovisiontv.kotlin.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.data.Adaptive
import com.whatuneed.ovisiontv.kotlin.data.ShelfCard
import com.whatuneed.ovisiontv.kotlin.data.TvApp
import com.whatuneed.ovisiontv.kotlin.data.loadShelfBanner
import com.whatuneed.ovisiontv.kotlin.ui.ArtEasing
import com.whatuneed.ovisiontv.kotlin.ui.TvOs
import com.whatuneed.ovisiontv.kotlin.ui.TvScale
import com.whatuneed.ovisiontv.kotlin.ui.bannerIsHero
import com.whatuneed.ovisiontv.kotlin.ui.motionDuration
import com.whatuneed.ovisiontv.kotlin.ui.TvLeading
import com.whatuneed.ovisiontv.kotlin.ui.TvType
import com.whatuneed.ovisiontv.kotlin.ui.TvSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import kotlin.math.roundToInt

// 12_SYSTEM_TRANSITIONS T8: content hand-off stays under half a second; the
// outgoing poster is kept composed for the whole fade.
private const val ART_CROSSFADE_MS = 350

// Decoded once per URI at roughly screen width. Hero art is redrawn every
// frame, so it must never be decoded, blurred or scaled on the render thread.
// The profile lowers the cap only on devices that cannot hold the full poster.

private const val DOT_SIZE = 7f
// The current page has to read at a glance over bright artwork, so it grows
// into a short capsule instead of relying on opacity alone (06_TOP_SHELF §7).
private const val DOT_ACTIVE_WIDTH = 20f
private const val DOT_GAP = 14f
// The dock band lifts by DOCK_FOCUS_SHIFT while the first row holds focus, so
// the dots are measured from the raised band edge, with 28 pt of air above it.
private const val DOTS_BOTTOM_GAP = TvOs.SCREEN_HEIGHT -
    (TvOs.DOCK_Y - TvOs.DOCK_FOCUS_SHIFT - 28f)
// 06_TOP_SHELF §7: while the parade owns the screen its page controls sit
// 24 pt above the bottom edge, as measured on tvOS.
private const val DOTS_BOTTOM_CAROUSEL = 24f
// The Java hero put the details at the bottom-left while the carousel had the
// remote; the dock line is where they rest when the tiles are on screen.
private const val INFO_BOTTOM_CAROUSEL = 40f
private const val INFO_BOTTOM_DOCK = TvOs.SCREEN_HEIGHT - 700f

/**
 * tvOS home: the focused app owns the whole screen. Its selected recommendation
 * fills the display, the dots below switch between recommendations, and an app
 * that published nothing keeps its banner or stays bare (06_TOP_SHELF §9).
 *
 * There is no GPU blur here on purpose: art is decoded once at screen size,
 * which is what the Java launcher did with its baked hero frames. A per-frame
 * RenderEffect on a full-screen surface measured ~20 fps on the TV image.
 */
@Composable
fun TopShelf(
    app: TvApp?,
    cards: List<ShelfCard>,
    selectedCard: Int,
    focusedCard: Int,
    onCardActivate: (ShelfCard) -> Unit,
    scale: TvScale,
    reduceMotion: Boolean = false,
) {
    val locale = LocalConfiguration.current.locales[0]
    val selected = cards.getOrNull(selectedCard)
    val arts = rememberShelfArts(cards)
    val bannerArt = app?.let { rememberShelfBanner(it) }
    // An app with no banner and no published rows draws nothing at all: even a
    // scrim would read as a band over the home backdrop.
    if (bannerArt == null && cards.isEmpty()) return

    // A loaded poster already covers the screen, so painting the banner under it
    // is a second full-screen fill per frame for nothing. A banner smaller than
    // the screen can hold at 2× is a grid thumbnail, not a hero image: stretched
    // 6–12× it only shows pixels, so the wallpaper stays visible instead.
    val density = LocalDensity.current.density
    val bannerUnderlay = bannerArt
        ?.takeIf { bannerIsHero(it.width, scale.factor * density) }
        ?.takeIf { selected?.artUri == null || arts[selected.artUri] == null }

    Box(Modifier.fillMaxSize()) {
        // The app banner is the fallback backdrop for cards without art.
        bannerUnderlay?.let { banner ->
            Image(
                bitmap = banner,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Low,
            )
        }

        Crossfade(
            targetState = selected?.artUri,
            animationSpec = tween(ART_CROSSFADE_MS, easing = ArtEasing),
            label = "shelfArt",
        ) { uri ->
            // Keyed by URI: a package broadcast rebuilds every TvApp with fresh
            // bitmaps and must not replay the fade on the same poster (T8).
            val art = uri?.let(arts::get)
            if (art != null) {
                Image(
                    bitmap = art,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Medium,
                )
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val width = size.width.roundToInt()
                    val height = size.height.roundToInt()
                    drawImage(
                        image = shelfScrim(width, height),
                        dstSize = IntSize(width, height),
                        filterQuality = FilterQuality.Low,
                    )
                },
        )

        Column(
            // The weather pill owns the top-left corner (top 48, height 76, and
            // it grows from that corner); the title starts below its bottom edge
            // so the focused app's name is never covered.
            Modifier
                .padding(start = scale.dp(TvOs.SAFE_AREA), top = scale.dp(150f))
                .width(scale.dp(750f)),
        ) {
            Text(
                text = app?.label ?: selected?.packageName.orEmpty(),
                color = Color.White,
                fontSize = scale.sp(TvType.title),
                lineHeight = scale.sp(TvLeading.shelfTitle),
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.55f),
                        blurRadius = 30f,
                    ),
                ),
            )
            Spacer(Modifier.height(scale.dp(10f)))
            Text(
                text = stringResource(R.string.shelf_context).uppercase(locale),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = scale.sp(TvType.callout),
                letterSpacing = (23f * scale.factor * 0.08f).sp,
                fontWeight = FontWeight.Medium,
            )
        }

        if (selected != null) {
            RecommendationInfo(
                card = selected,
                focused = focusedCard >= 0,
                reduceMotion = reduceMotion,
                scale = scale,
            )
        }

        if (cards.size > 1) {
            val label = stringResource(R.string.shelf_continue)
            ShelfDots(
                count = cards.size,
                selected = selectedCard.coerceIn(0, cards.lastIndex),
                focused = focusedCard >= 0,
                scale = scale,
                reduceMotion = reduceMotion,
                description = selected?.let { "$label: ${it.title}" } ?: label,
            )
        }
    }
}

/** Title, episode and resume point of the poster that is on screen. */
@Composable
private fun RecommendationInfo(
    card: ShelfCard,
    focused: Boolean,
    reduceMotion: Boolean,
    scale: TvScale,
) {
    // tvOS parade: the details sit at the bottom-left while the carousel owns
    // the screen, and rise to the dock line when the tiles come back.
    val bottom by animateDpAsState(
        targetValue = scale.dp(if (focused) INFO_BOTTOM_CAROUSEL else INFO_BOTTOM_DOCK),
        animationSpec = tween(motionDuration(TvOs.SCROLL_DURATION_MS, reduceMotion)),
        label = "shelfInfoBottom",
    )
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = scale.dp(TvOs.SAFE_AREA), bottom = bottom)
                .width(scale.dp(900f)),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = card.title,
                modifier = Modifier.testTag("shelfInfoTitle"),
                color = Color.White,
                fontSize = scale.sp(TvType.subhead),
                lineHeight = scale.sp(TvLeading.infoTitle),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 24f),
                ),
            )
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
private fun ShelfDots(
    count: Int,
    selected: Int,
    focused: Boolean,
    scale: TvScale,
    reduceMotion: Boolean,
    description: String,
) {
    // 06_TOP_SHELF §7: the parade's page controls drop to a 24 pt bottom inset
    // with the details; above the dock they keep their band-relative spot.
    val bottom by animateDpAsState(
        targetValue = scale.dp(if (focused) DOTS_BOTTOM_CAROUSEL else DOTS_BOTTOM_GAP),
        animationSpec = tween(motionDuration(TvOs.SCROLL_DURATION_MS, reduceMotion)),
        label = "shelfDotsBottom",
    )
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottom)
                .semantics {
                    // A page indicator, as in tvOS: it reports the current card
                    // to TalkBack but is not a control. The carousel itself
                    // takes the arrows and OK launches the poster.
                    contentDescription = description
                },
            horizontalArrangement = Arrangement.spacedBy(
                space = scale.dp(DOT_GAP),
                alignment = Alignment.CenterHorizontally,
            ),
            verticalAlignment = Alignment.Bottom,
        ) {
            repeat(count) { index ->
                val active = index == selected
                val alpha by animateFloatAsState(
                    targetValue = when {
                        active -> 1f
                        focused -> 0.42f
                        else -> 0.32f
                    },
                    animationSpec = tween(TvOs.FOCUS_MS),
                    label = "shelfDot",
                )
                val width by animateDpAsState(
                    targetValue = scale.dp(if (active) DOT_ACTIVE_WIDTH else DOT_SIZE),
                    animationSpec = tween(TvOs.FOCUS_MS),
                    label = "shelfDotWidth",
                )
                Box(
                    Modifier
                        .size(width = width, height = scale.dp(DOT_SIZE))
                        .shadow(
                            elevation = 4.dp,
                            shape = CircleShape,
                            clip = false,
                            ambientColor = Color.Black,
                            spotColor = Color.Black,
                        )
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = alpha))
                        .testTag("shelfDot"),
                )
            }
        }
    }
}

// Sized for the twelve posters the carousel keeps on screen (see below): an
// eight-entry budget evicted art that was still being drawn and every step
// through a longer row re-decoded from disk. Only the LOW profile shrinks it.
private val shelfArtCache =
    object : LruCache<String, ImageBitmap>(Adaptive.budgets.shelfArtEntries) {}

/** Called from MemoryPressure; the provider still holds every poster. */
internal fun trimShelfArtCache(percent: Int) {
    if (percent >= 100) shelfArtCache.evictAll()
    else shelfArtCache.trimToSize(shelfArtCache.maxSize() * (100 - percent) / 100)
}

/** Follows a profile downgrade: the entry budget may only shrink. */
internal fun trimShelfArtCacheTo(entries: Int) {
    if (entries < shelfArtCache.maxSize()) shelfArtCache.trimToSize(entries)
}

// P-GPU-2: the scrim used to be three full-screen gradient rects per frame.
// Its stops never move, so it is baked once into one half-resolution bitmap and
// drawn as a single image; upscaling a smooth gradient is invisible.
private const val SCRIM_SCALE = 0.5f
private const val SCRIM_TRANSPARENT = 0x00000000
private const val SCRIM_LEFT = 0xD9101012.toInt()
private const val SCRIM_LEFT_MID = 0x73101012.toInt()
private const val SCRIM_CORNER = 0x660E0E11.toInt()
private const val SCRIM_BOTTOM_MID = 0xB3101012.toInt()
private const val SCRIM_BOTTOM_EDGE = 0xFF101012.toInt()

private var scrimImage: ImageBitmap? = null
private var scrimWidth = 0
private var scrimHeight = 0

/** Baked once per surface size; safe to drop under pressure, it rebuilds on draw. */
internal fun dropShelfScrim() {
    scrimImage = null
    scrimWidth = 0
    scrimHeight = 0
}

private fun shelfScrim(width: Int, height: Int): ImageBitmap {
    scrimImage?.let { if (scrimWidth == width && scrimHeight == height) return it }
    val bakedWidth = (width * SCRIM_SCALE).roundToInt().coerceAtLeast(1)
    val bakedHeight = (height * SCRIM_SCALE).roundToInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(bakedWidth, bakedHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint()
    val w = bakedWidth.toFloat()
    val h = bakedHeight.toFloat()

    // Left scrim: the app title and shelf copy sit on bright artwork.
    paint.shader = LinearGradient(
        0f, 0f, w, 0f,
        intArrayOf(SCRIM_LEFT, SCRIM_LEFT_MID, SCRIM_TRANSPARENT),
        floatArrayOf(0f, 0.42f, 0.78f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, w, h, paint)
    // Corner vignette that keeps the top-left poster edge from glowing.
    paint.shader = RadialGradient(
        0f, 0f, w * 0.5f,
        intArrayOf(SCRIM_CORNER, SCRIM_TRANSPARENT),
        floatArrayOf(0f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, w, h, paint)
    // Bottom darkening into the home background so the dock gap is seamless.
    paint.shader = LinearGradient(
        0f, 0f, 0f, h,
        intArrayOf(SCRIM_TRANSPARENT, SCRIM_BOTTOM_MID, SCRIM_BOTTOM_EDGE),
        floatArrayOf(0.48f, 0.82f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, w, h, paint)

    return bitmap.asImageBitmap().also {
        scrimImage = it
        scrimWidth = width
        scrimHeight = height
    }
}

/**
 * Preloads every distinct poster for the app while its tiles are still on
 * screen, so stepping through the carousel never waits on a decode.
 */
@Composable
private fun rememberShelfArts(cards: List<ShelfCard>): Map<String, ImageBitmap> {
    val context = LocalContext.current
    val arts = remember { mutableStateMapOf<String, ImageBitmap>() }
    LaunchedEffect(cards) {
        val current = cards.mapNotNull { it.artUri }.distinct()
        for (uri in current) {
            if (arts[uri] != null) continue
            val cached = shelfArtCache.get(uri)
            val loaded = cached ?: withContext(Dispatchers.IO) { loadShelfArt(context, uri) }
            if (loaded != null) {
                if (cached == null) shelfArtCache.put(uri, loaded)
                arts[uri] = loaded
            }
        }
        // Keep the posters in focus plus the one still fading out.
        val keep = Adaptive.budgets.shelfArtEntries
        if (arts.size > keep) {
            arts.keys.filterNot { it in current }.take(arts.size - keep).forEach(arts::remove)
        }
    }
    return arts
}

/**
 * The shelf banner covers the screen only when it can do so within
 * [bannerIsHero]'s 2× limit, while [TvApp.banner] is a 400×240 grid thumbnail.
 * The focused app is therefore decoded once at screen size, so an app that
 * ships a larger banner is not shown from the thumbnail; a smaller source keeps
 * the thumbnail instead of being upscaled into a bigger bitmap.
 */
@Composable
private fun rememberShelfBanner(app: TvApp): ImageBitmap? {
    val fallback = app.banner ?: return null
    val context = LocalContext.current
    var banner by remember(app.packageName, app.banner) { mutableStateOf(fallback) }
    LaunchedEffect(app.packageName, app.banner) {
        val wide = loadShelfBanner(
            context,
            app.packageName,
            TvOs.SCREEN_WIDTH.roundToInt(),
            TvOs.SCREEN_HEIGHT.roundToInt(),
        )
        if (wide != null && wide.width > fallback.width) banner = wide
    }
    return banner
}

private fun loadShelfArt(context: Context, value: String): ImageBitmap? = runCatching {
    val uri = Uri.parse(value)
    openArtStream(context, uri, value)?.use { stream ->
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(stream, null, bounds)
        var sample = 1
        val maxPx = Adaptive.budgets.shelfArtMaxPx
        while (bounds.outWidth / sample > maxPx || bounds.outHeight / sample > maxPx) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
        }
        openArtStream(context, uri, value)?.use { again ->
            BitmapFactory.decodeStream(again, null, options)?.asImageBitmap()
        }
    }
}.getOrNull()

private fun openArtStream(
    context: Context,
    uri: Uri,
    value: String,
): java.io.InputStream? = runCatching {
    if (uri.scheme == "http" || uri.scheme == "https") {
        URL(value).openStream()
    } else {
        context.contentResolver.openInputStream(uri)
    }
}.getOrNull()
