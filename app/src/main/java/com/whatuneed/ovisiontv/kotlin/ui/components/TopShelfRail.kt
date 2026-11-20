package com.whatuneed.ovisiontv.kotlin.ui.components

import com.whatuneed.ovisiontv.kotlin.core.network.imageRedirectIsTrusted
import com.whatuneed.ovisiontv.kotlin.core.network.readAtMost
import com.whatuneed.ovisiontv.kotlin.data.resolveRedirectUrl
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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.whatuneed.ovisiontv.kotlin.data.trimmedCapacity
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryItem
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryRail
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoverySource
import com.whatuneed.ovisiontv.kotlin.discovery.artUri
import com.whatuneed.ovisiontv.kotlin.ui.LauncherColors
import com.whatuneed.ovisiontv.kotlin.ui.TvInk
import com.whatuneed.ovisiontv.kotlin.ui.TvOs
import com.whatuneed.ovisiontv.kotlin.ui.TvScale
import com.whatuneed.ovisiontv.kotlin.ui.TvSpace
import com.whatuneed.ovisiontv.kotlin.ui.TvType
import com.whatuneed.ovisiontv.kotlin.ui.motionDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

internal const val SHELF_CARD_WIDTH = 330f
internal const val SHELF_CARD_HEIGHT = 186f
internal const val SHELF_CARD_GAP = 24f
internal const val SHELF_PRELOAD_CARDS = 8
internal const val SHELF_CARD_RADIUS = 18f
internal const val SHELF_RAIL_BOTTOM = 56f
internal const val SHELF_RAIL_TITLE_GAP = 12f
internal const val SHELF_ART_TIMEOUT_MS = 10_000
internal const val SHELF_ART_MAX_BYTES = 8 * 1024 * 1024
private const val SHELF_ART_MAX_REDIRECTS = 3

@Composable
internal fun ShelfRail(
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
            requester?.requestShelfFocusWhenAttached()
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
            requester?.requestShelfFocusWhenAttached()
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = scale.dp(SHELF_RAIL_BOTTOM)),
    ) {
        Text(
            text = sectionLabel(rail.section, appLabel),
            color = Color.White.copy(alpha = 0.6f),
            fontSize = scale.sp(TvType.subhead),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = scale.dp(TvOs.SAFE_AREA)),
        )
        Spacer(Modifier.height(scale.dp(SHELF_RAIL_TITLE_GAP)))
        LazyRow(
            state = state,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = scale.dp(TvOs.SAFE_AREA)),
            horizontalArrangement = Arrangement.spacedBy(scale.dp(SHELF_CARD_GAP)),
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
internal fun RailCard(
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
    val context = LocalContext.current
    var lazyArt by remember(item.id) { mutableStateOf<ImageBitmap?>(null) }
    // A 24-card online rail cannot be preloaded without evicting the hero or
    // holding old bitmaps for cards that are far off screen. Cards outside the
    // preload window load their own art when LazyRow composes them, and release
    // it when they leave composition; the shared LRU still avoids re-decoding.
    LaunchedEffect(item.id, art) {
        if (art != null) {
            lazyArt = null
            return@LaunchedEffect
        }
        val target = Adaptive.budgets.discoveryCardPx
        val cacheKey = "${item.source}:${item.artUri}|$target|${ArtRole.CARD}"
        if (shelfArtFailures.isFailed(cacheKey)) return@LaunchedEffect
        val cached = shelfArtCache.get(cacheKey)
        val loaded = cached ?: withContext(Dispatchers.IO) {
            loadArt(context, item, target)
        }
        if (loaded != null) {
            if (cached == null) shelfArtCache.put(cacheKey, loaded)
            lazyArt = loaded
            shelfArtFailures.clear(cacheKey)
        } else if (cached == null) {
            shelfArtFailures.markFailed(cacheKey)
        }
    }
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
        if (focused) focusRequester.requestShelfFocusWhenAttached()
    }
    Box(
        Modifier
            .width(scale.dp(SHELF_CARD_WIDTH))
            .height(scale.dp(SHELF_CARD_HEIGHT))
            .focusRequester(focusRequester)
            .focusable()
            .graphicsLayer {
                scaleX = grow
                scaleY = grow
            }
            .shadow(
                elevation = if (focused) 22.dp else 8.dp,
                shape = RoundedCornerShape(scale.dp(SHELF_CARD_RADIUS)),
                clip = false,
            )
            .clip(RoundedCornerShape(scale.dp(SHELF_CARD_RADIUS)))
            .background(TvInk.panel)
            .then(
                when {
                    focused -> Modifier.border(
                        3.dp,
                        Color.White,
                        RoundedCornerShape(scale.dp(SHELF_CARD_RADIUS)),
                    )
                    // The hero above is this card; keep a quiet marker even
                    // while the section bar owns the remote.
                    selected -> Modifier.border(
                        2.dp,
                        Color.White.copy(alpha = 0.55f),
                        RoundedCornerShape(scale.dp(SHELF_CARD_RADIUS)),
                    )
                    else -> Modifier.border(
                        1.dp,
                        Color.White.copy(alpha = 0.10f),
                        RoundedCornerShape(scale.dp(SHELF_CARD_RADIUS)),
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
        val shownArt = art ?: lazyArt
        if (shownArt != null) {
            Image(
                bitmap = shownArt,
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

        val badge = when {
            // Every JustWatch card is an in-launcher information card now: its
            // preview may decorate the hero, but there is no installed app to
            // open, so OK must not promise a trailer destination.
            item.source == DiscoverySource.ONLINE -> stringResource(R.string.online_info_about)
            // A provider row only gets this badge when an exact JustWatch title
            // supplied a real official preview. OK then plays it and a second
            // OK opens the film in the app that published the row.
            !item.trailerUri.isNullOrBlank() -> stringResource(R.string.discovery_trailer_badge)
            else -> null
        }
        badge?.let { label ->
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(scale.dp(TvSpace.sm))
                    .clip(RoundedCornerShape(scale.dp(9f)))
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(horizontal = scale.dp(8f), vertical = scale.dp(3f)),
            ) {
                Text(
                    text = label,
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = scale.sp(15f),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

internal val shelfArtCache =
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
internal val shelfArtEpoch = mutableIntStateOf(0)

// A dead poster host must not be retried on every recomposition. Failures are
// negative-cached briefly; the TTL keeps a temporary offline state recoverable.
internal val shelfArtFailures = ArtFailureCache()

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
internal fun rememberRailArts(
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
        // Hero first: the focused card is what the viewer is looking at, and
        // waiting for 24 card downloads before it was a visible black frame.
        items.firstOrNull { it.id == selectedId }?.let { item ->
            loadInto(arts, context, item, ArtRole.HERO)
        }
        items.take(SHELF_PRELOAD_CARDS).forEach { item ->
            loadInto(arts, context, item, ArtRole.CARD)
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

internal fun heroKey(item: DiscoveryItem): String = "${ArtRole.HERO}:${item.id}"
internal fun cardKey(item: DiscoveryItem): String = "${ArtRole.CARD}:${item.id}"

/**
 * TV providers publish posters on hosts that may hang. The old path opened the
 * URL twice with no connect/read timeout and no size cap, so one dead host held
 * a Dispatchers.IO thread for minutes and could allocate an unbounded buffer
 * (audit A-04). Now the bytes are read once, bounded, and decoded from memory.
 */

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
            readHttpArt(value)
        } else {
            context.contentResolver.openInputStream(uri)?.use { it.readAtMost(SHELF_ART_MAX_BYTES) }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }
}

private suspend fun readHttpArt(value: String): ByteArray? {
    var current = value
    repeat(SHELF_ART_MAX_REDIRECTS + 1) { attempt ->
        currentCoroutineContext().ensureActive()
        val connection = URL(current).openConnection() as HttpURLConnection
        connection.connectTimeout = SHELF_ART_TIMEOUT_MS
        connection.readTimeout = SHELF_ART_TIMEOUT_MS
        connection.instanceFollowRedirects = false
        try {
            val code = connection.responseCode
            if (code in 300..399) {
                if (attempt >= SHELF_ART_MAX_REDIRECTS) return null
                val location = connection.getHeaderField("Location") ?: return null
                val next = resolveRedirectUrl(current, location) ?: return null
                if (!imageRedirectIsTrusted(current, next)) return null
                current = next
                return@repeat
            }
            if (code != HttpURLConnection.HTTP_OK) return null
            if (connection.contentLengthLong > SHELF_ART_MAX_BYTES) return null
            return connection.inputStream.use { it.readAtMost(SHELF_ART_MAX_BYTES) }
        } finally {
            connection.disconnect()
        }
    }
    return null
}

/**
 * The focused app is decoded once at screen size, so an app that ships a larger
 * banner is not shown from the thumbnail; a smaller source keeps the thumbnail
 * instead of being upscaled into a bigger bitmap.
 */
// Same layout-attach retry as Home: a requester created by this composition
// is not attached until the next layout pass.
internal suspend fun FocusRequester.requestShelfFocusWhenAttached(): Boolean {
    repeat(3) {
        if (runCatching { requestFocus() }.isSuccess) return true
        withFrameNanos { }
    }
    return false
}
