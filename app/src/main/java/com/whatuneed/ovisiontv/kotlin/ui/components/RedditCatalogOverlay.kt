package com.whatuneed.ovisiontv.kotlin.ui.components

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.ambient.RedditImageStore
import com.whatuneed.ovisiontv.kotlin.ambient.RedditWallpaper
import com.whatuneed.ovisiontv.kotlin.ui.LauncherColors
import com.whatuneed.ovisiontv.kotlin.ui.TvRadius
import com.whatuneed.ovisiontv.kotlin.ui.TvScale
import com.whatuneed.ovisiontv.kotlin.ui.TvType
import com.whatuneed.ovisiontv.kotlin.ui.rememberTvScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val REDDIT_CATALOG_TAG = "reddit_catalog"
internal const val REDDIT_TILE_TAG = "reddit_tile"

private const val COLUMNS = 3
private const val TILE_WIDTH = 420f
private const val TILE_HEIGHT = 236f
private const val TILE_LABEL_HEIGHT = 70f
private const val TILE_GAP_X = 24f
private const val TILE_GAP_Y = 24f
private const val CATALOG_LEFT = 120f
private const val CATALOG_TOP = 170f
private const val CATALOG_BOTTOM = 78f

/**
 * Full-screen picker for individual Reddit images. It is deliberately separate
 * from the Settings row list: the grid has its own focus cursor and Back returns
 * to the exact Ambient page without changing the page cursor.
 */
@Composable
internal fun RedditCatalogOverlay(
    items: List<RedditWallpaper>,
    selected: Set<String>,
    loading: Boolean,
    onToggle: (RedditWallpaper) -> Unit,
    onClose: () -> Unit,
) {
    val scale = rememberTvScale()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var focusIndex by remember { mutableIntStateOf(0) }
    val rootRequester = remember { FocusRequester() }

    val last = items.lastIndex
    LaunchedEffect(items.size) {
        if (focusIndex > last) focusIndex = last.coerceAtLeast(0)
    }
    LaunchedEffect(Unit) {
        runCatching { rootRequester.requestFocus() }
    }
    LaunchedEffect(focusIndex, items.size) {
        if (items.isEmpty()) return@LaunchedEffect
        // The root owns the focus; the cursor is the source of truth for the
        // highlighted tile, exactly like the launcher's own grids. Scroll only
        // when the cursor moves to a row that is out of view.
        listState.animateScrollToItem(focusIndex / COLUMNS)
    }

    BackHandler(onBack = onClose)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0D))
            .testTag(REDDIT_CATALOG_TAG)
            .focusRequester(rootRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                    onClose()
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown || items.isEmpty()) {
                    return@onPreviewKeyEvent false
                }
                when (event.key) {
                    Key.DirectionLeft -> {
                        if (focusIndex % COLUMNS > 0) focusIndex--
                        true
                    }

                    Key.DirectionRight -> {
                        if (focusIndex % COLUMNS < COLUMNS - 1 &&
                            focusIndex < last
                        ) {
                            focusIndex++
                        }
                        true
                    }

                    Key.DirectionUp -> {
                        if (focusIndex >= COLUMNS) focusIndex -= COLUMNS
                        true
                    }

                    Key.DirectionDown -> {
                        if (focusIndex + COLUMNS <= last) focusIndex += COLUMNS
                        true
                    }

                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        items.getOrNull(focusIndex)?.let(onToggle)
                        true
                    }

                    else -> false
                }
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(
                    start = scale.dp(CATALOG_LEFT),
                    end = scale.dp(CATALOG_LEFT),
                    top = scale.dp(CATALOG_TOP),
                    bottom = scale.dp(CATALOG_BOTTOM),
                ),
        ) {
            Text(
                text = stringResource(R.string.saver_reddit_catalog_title),
                color = Color.White,
                fontSize = scale.sp(TvType.title),
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(scale.dp(10f)))
            Text(
                text = stringResource(R.string.saver_reddit_catalog_hint),
                color = LauncherColors.textSecondary,
                fontSize = scale.sp(TvType.body),
            )
            Spacer(Modifier.height(scale.dp(16f)))
            when {
                loading && items.isEmpty() -> CatalogMessage(stringResource(R.string.saver_reddit_loading), scale)
                items.isEmpty() -> CatalogMessage(stringResource(R.string.saver_reddit_empty), scale)
                else -> {
                    Text(
                        text = pluralStringResource(
                            R.plurals.saver_reddit_images,
                            items.size,
                            items.size,
                        ),
                        color = LauncherColors.textSecondary,
                        fontSize = scale.sp(TvType.caption),
                    )
                    Spacer(Modifier.height(scale.dp(12f)))
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = scale.dp(24f)),
                        verticalArrangement = Arrangement.spacedBy(scale.dp(TILE_GAP_Y)),
                    ) {
                        val rows = (items.size + COLUMNS - 1) / COLUMNS
                        items(rows) { row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(scale.dp(TILE_GAP_X)),
                            ) {
                                repeat(COLUMNS) { column ->
                                    val index = row * COLUMNS + column
                                    val item = items.getOrNull(index)
                                    if (item == null) {
                                        Spacer(Modifier.width(scale.dp(TILE_WIDTH)))
                                    } else {
                                        RedditTile(
                                            context = context,
                                            item = item,
                                            selected = item.id in selected,
                                            focused = index == focusIndex,
                                            scale = scale,
                                            onClick = { onToggle(item) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogMessage(message: String, scale: TvScale) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(top = scale.dp(40f)),
        contentAlignment = Alignment.TopStart,
    ) {
        Text(
            text = message,
            color = LauncherColors.textSecondary,
            fontSize = scale.sp(TvType.body),
        )
    }
}

@Composable
private fun RedditTile(
    context: android.content.Context,
    item: RedditWallpaper,
    selected: Boolean,
    focused: Boolean,
    scale: TvScale,
    onClick: () -> Unit,
) {
    var bitmap by remember(item.previewUrl) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(item.previewUrl) {
        if (bitmap == null) {
            bitmap = withContext(Dispatchers.IO) {
                RedditImageStore.loadThumbnail(context, item.previewUrl)
            }
        }
    }
    Column(
        Modifier
            .width(scale.dp(TILE_WIDTH))
            .height(scale.dp(TILE_HEIGHT + TILE_LABEL_HEIGHT))
            .clip(RoundedCornerShape(scale.dp(TvRadius.row)))
            .background(Color.White.copy(alpha = if (focused) 0.98f else 0.08f))
            .then(
                if (focused) {
                    Modifier.border(
                        width = scale.dp(3f),
                        color = Color.White,
                        shape = RoundedCornerShape(scale.dp(TvRadius.row)),
                    )
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .testTag(REDDIT_TILE_TAG)
            .padding(scale.dp(8f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(scale.dp(TILE_HEIGHT))
                .clip(RoundedCornerShape(scale.dp(TvRadius.field)))
                .background(Color(0xFF1C1C21)),
        ) {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(scale.dp(10f))
                        .size(scale.dp(30f))
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.94f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✓",
                        color = Color.Black,
                        fontSize = scale.sp(20f),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.height(scale.dp(8f)))
        Text(
            text = item.title,
            color = if (focused) Color.Black else Color.White,
            fontSize = scale.sp(TvType.caption),
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(scale.dp(42f)),
        )
        Text(
            text = if (item.author.isBlank()) {
                "r/${item.community}"
            } else {
                "r/${item.community} · u/${item.author}"
            },
            color = if (focused) Color.Black.copy(alpha = 0.65f) else LauncherColors.textSecondary,
            fontSize = scale.sp(TvType.caption - 2f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
