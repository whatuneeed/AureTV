package com.whatuneed.ovisiontv.kotlin.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.data.Profile
import com.whatuneed.ovisiontv.kotlin.data.ProfileCatalog
import com.whatuneed.ovisiontv.kotlin.ui.TvFocus
import com.whatuneed.ovisiontv.kotlin.ui.TvInk
import com.whatuneed.ovisiontv.kotlin.ui.LauncherColors
import com.whatuneed.ovisiontv.kotlin.ui.SurfaceEasing
import com.whatuneed.ovisiontv.kotlin.ui.TvOs
import com.whatuneed.ovisiontv.kotlin.ui.TvScale
import com.whatuneed.ovisiontv.kotlin.ui.motionDuration
import com.whatuneed.ovisiontv.kotlin.ui.rememberTvScale
import com.whatuneed.ovisiontv.kotlin.ui.TvType

// "Who's watching?" uses the launcher's own pastel discs instead of Apple's
// portraits: the same profile picture appears in Settings, so there is one
// avatar per profile and nothing to download.
private const val PICKER_TITLE_TOP = 208f
private const val PICKER_TITLE_FONT = TvType.largeTitle
private const val PICKER_TITLE_GAP = 82f
private const val PICKER_DISC = 220f
private const val PICKER_DISC_GAP = 88f
private const val PICKER_EMOJI_FONT = TvType.glyphLarge
private const val PICKER_NAME_FONT = TvType.subhead
private const val PICKER_NAME_GAP = 24f
private const val PICKER_BADGE = 54f
private const val PICKER_BADGE_GLYPH = 30f
private const val PICKER_FOCUS_SCALE = 1.08f

private const val AVATAR_TITLE_TOP = 108f
private const val AVATAR_TITLE_FONT = TvType.title
private const val AVATAR_TITLE_GAP = 52f
private const val AVATAR_CELL = 132f
private const val AVATAR_CELL_GAP = 26f
private const val AVATAR_EMOJI_FONT = TvType.glyph
private const val AVATAR_HEADER_FONT = TvType.body
private const val AVATAR_HEADER_HEIGHT = 38f
private const val AVATAR_ROW_GAP = 18f
private const val AVATAR_GROUP_GAP = 26f
private const val AVATAR_FOCUS_SCALE = 1.1f
private const val AVATAR_TAKEN_ALPHA = 0.24f
private const val AVATAR_CHECK = 32f

private const val PICKER_SLIDE = 26f
/**
 * The Apple TV gate: one row of profile discs over the dimmed Home. The whole
 * page enters as one surface — the launcher never staggers its rows — and the
 * focused disc takes the same inset white ring as the rest of the chrome.
 *
 * Back keeps the current profile; it must never leave the user with no profile.
 */
@Composable
fun ProfilePicker(
    profiles: List<Profile>,
    currentId: String,
    reduceMotion: Boolean,
    onPick: (Profile) -> Unit,
    onDismiss: () -> Unit,
    // A PIN gate can open over the picker and close again; the disc keeps the
    // same index then, so the tick is what brings the focus back explicitly.
    focusTick: Int = 0,
) {
    val scale = rememberTvScale()
    val keyRepeat = rememberTvKeyRepeat()
    val scope = rememberCoroutineScope()
    var focusIndex by remember(profiles) {
        mutableIntStateOf(ProfileCatalog.pickerIndex(profiles, currentId))
    }
    val requesters = remember { mutableMapOf<Int, FocusRequester>() }
    fun requester(index: Int): FocusRequester = requesters.getOrPut(index) { FocusRequester() }

    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val enterAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(motionDuration(TvOs.SURFACE_ENTER_MS, reduceMotion), easing = SurfaceEasing),
        label = "profilePickerAlpha",
    )
    val enterSlide by animateFloatAsState(
        targetValue = if (entered) 0f else PICKER_SLIDE,
        animationSpec = tween(motionDuration(TvOs.SURFACE_ENTER_MS, reduceMotion), easing = SurfaceEasing),
        label = "profilePickerSlide",
    )

    LaunchedEffect(focusIndex, focusTick) { requesters[focusIndex]?.requestFocus() }

    val title = stringResource(R.string.profile_who_watching)

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = enterAlpha }
            .background(
                Brush.verticalGradient(
                    listOf(TvInk.discFadeTop, TvInk.discFadeBottom),
                ),
            )
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) {
                    keyRepeat.release(event.key)
                    return@onKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent true
                // A held button can arrive as repeat KeyDowns or as repeated
                // press/release pairs; both must move once, then repeat slowly.
                if (TvKeyRepeat.isOneShot(event.key) && !keyRepeat.firstPress(event)) {
                    return@onKeyEvent true
                }
                when (event.key) {
                    Key.DirectionLeft -> {
                        if (focusIndex > 0) focusIndex--
                        keyRepeat.startRepeat(scope) {
                            if (focusIndex > 0) focusIndex--
                        }
                        true
                    }

                    Key.DirectionRight -> {
                        if (focusIndex < profiles.lastIndex) focusIndex++
                        keyRepeat.startRepeat(scope) {
                            if (focusIndex < profiles.lastIndex) focusIndex++
                        }
                        true
                    }

                    Key.DirectionUp, Key.DirectionDown -> true

                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        keyRepeat.stopRepeat()
                        profiles.getOrNull(focusIndex)?.let(onPick)
                        true
                    }

                    Key.Back -> {
                        keyRepeat.stopRepeat()
                        onDismiss()
                        true
                    }

                    else -> false
                }
            }
            .focusable(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = enterSlide * density },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                color = LauncherColors.textPrimary,
                fontSize = scale.sp(PICKER_TITLE_FONT),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = scale.dp(PICKER_TITLE_TOP))
                    .clearAndSetSemantics { heading(); contentDescription = title },
            )
            Spacer(Modifier.height(scale.dp(PICKER_TITLE_GAP)))
            Row(
                horizontalArrangement = Arrangement.spacedBy(scale.dp(PICKER_DISC_GAP)),
                verticalAlignment = Alignment.Top,
            ) {
                profiles.forEachIndexed { index, profile ->
                    ProfileDisc(
                        profile = profile,
                        isCurrent = profile.id == currentId,
                        focused = index == focusIndex,
                        scale = scale,
                        focusRequester = requester(index),
                        onPick = { onPick(profile) },
                    )
                }
            }
        }
    }

    // The remote's Back and the screen's Back must both keep the current
    // profile; the picker never removes it.
    BackHandler { onDismiss() }
}

@Composable
private fun ProfileDisc(
    profile: Profile,
    isCurrent: Boolean,
    focused: Boolean,
    scale: TvScale,
    focusRequester: FocusRequester,
    onPick: () -> Unit,
) {
    val focusAmount by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(TvOs.FOCUS_MS),
        label = "profileDiscFocus",
    )
    val current = stringResource(R.string.profile_current)
    val ringAlpha = TvFocus.discAlpha(focusAmount)
    val grow = 1f + (PICKER_FOCUS_SCALE - 1f) * focusAmount
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(scale.dp(PICKER_DISC))
                .focusRequester(focusRequester)
                .focusable()
                .clearAndSetSemantics {
                    contentDescription = profile.name
                    if (isCurrent) stateDescription = current
                    role = Role.Button
                    onClick(label = profile.name) {
                        onPick()
                        true
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = grow
                        scaleY = grow
                    }
                    .clip(CircleShape)
                    .background(Color(ProfileCatalog.color(profile.color).background))
                    .border(
                        width = scale.dp(TvFocus.PROFILE_STROKE),
                        color = Color.White.copy(alpha = ringAlpha),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = profile.emoji.ifEmpty { ProfileCatalog.initials(profile.name) }
                        .ifEmpty { stringResource(R.string.profile_avatar_placeholder) },
                    color = Color(ProfileCatalog.color(profile.color).text),
                    fontSize = scale.sp(PICKER_EMOJI_FONT),
                    textAlign = TextAlign.Center,
                )
            }
            if (isCurrent) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(scale.dp(PICKER_BADGE))
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(
                            width = scale.dp(3f),
                            color = TvInk.disc,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.foundation.Image(
                        imageVector = SectionGlyph.check,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(TvInk.disc),
                        modifier = Modifier.size(scale.dp(PICKER_BADGE_GLYPH)),
                    )
                }
            }
        }
        Spacer(Modifier.height(scale.dp(PICKER_NAME_GAP)))
        Text(
            text = profile.name,
            color = if (isCurrent) LauncherColors.textPrimary else LauncherColors.textSecondary,
            fontSize = scale.sp(PICKER_NAME_FONT),
            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The avatar chooser of Settings. Six discs per row in the catalogue's three
 * groups; a picture already used by another profile stays visible but dimmed
 * and cannot take focus, because two profiles never share a disc.
 */
@Composable
fun ProfileAvatarPicker(
    profiles: List<Profile>,
    editingId: String,
    reduceMotion: Boolean,
    onChoose: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val names = remember(context) { context.resources.getStringArray(R.array.profile_emoji_names) }
    val free = remember(profiles, editingId) {
        ProfileCatalog.freeEmojis(profiles, editingId).toHashSet()
    }
    val editing = profiles.firstOrNull { it.id == editingId }
    var focusIndex by remember(profiles, editingId) {
        mutableIntStateOf(ProfileCatalog.avatarStartIndex(profiles, editingId))
    }
    val requesters = remember { mutableMapOf<Int, FocusRequester>() }
    fun requester(index: Int): FocusRequester = requesters.getOrPut(index) { FocusRequester() }

    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val enterAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(motionDuration(TvOs.SURFACE_ENTER_MS, reduceMotion), easing = SurfaceEasing),
        label = "avatarPickerAlpha",
    )
    val enterSlide by animateFloatAsState(
        targetValue = if (entered) 0f else PICKER_SLIDE,
        animationSpec = tween(motionDuration(TvOs.SURFACE_ENTER_MS, reduceMotion), easing = SurfaceEasing),
        label = "avatarPickerSlide",
    )

    LaunchedEffect(focusIndex) { requesters[focusIndex]?.requestFocus() }

    val scale = rememberTvScale()
    val keyRepeat = rememberTvKeyRepeat()
    val scope = rememberCoroutineScope()
    val takenNote = stringResource(R.string.profile_avatar_taken)
    val title = stringResource(R.string.profile_avatar_choose)
    val rows = remember { ProfileCatalog.EMOJIS.chunked(ProfileCatalog.AVATAR_COLUMNS) }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = enterAlpha }
            .background(
                Brush.verticalGradient(
                    listOf(LauncherColors.backgroundTop, LauncherColors.backgroundBottom),
                ),
            )
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) {
                    keyRepeat.release(event.key)
                    return@onKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent true
                if (TvKeyRepeat.isOneShot(event.key) && !keyRepeat.firstPress(event)) {
                    return@onKeyEvent true
                }
                fun move(dx: Int, dy: Int) {
                    focusIndex = ProfileCatalog.avatarMove(
                        focusIndex, dx, dy, profiles, editingId,
                    )
                }
                when (event.key) {
                    Key.DirectionLeft -> {
                        move(-1, 0)
                        keyRepeat.startRepeat(scope) { move(-1, 0) }
                        true
                    }

                    Key.DirectionRight -> {
                        move(1, 0)
                        keyRepeat.startRepeat(scope) { move(1, 0) }
                        true
                    }

                    Key.DirectionUp -> {
                        move(0, -1)
                        keyRepeat.startRepeat(scope) { move(0, -1) }
                        true
                    }

                    Key.DirectionDown -> {
                        move(0, 1)
                        keyRepeat.startRepeat(scope) { move(0, 1) }
                        true
                    }

                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        keyRepeat.stopRepeat()
                        val emoji = ProfileCatalog.EMOJIS.getOrNull(focusIndex)
                        if (emoji != null && emoji in free) onChoose(emoji)
                        true
                    }

                    Key.Back -> {
                        keyRepeat.stopRepeat()
                        onDismiss()
                        true
                    }

                    else -> false
                }
            }
            .focusable(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = enterSlide * density },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                color = LauncherColors.textPrimary,
                fontSize = scale.sp(AVATAR_TITLE_FONT),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = scale.dp(AVATAR_TITLE_TOP))
                    .clearAndSetSemantics { heading(); contentDescription = title },
            )
            Spacer(Modifier.height(scale.dp(AVATAR_TITLE_GAP)))
            Column(horizontalAlignment = Alignment.Start) {
                var previousGroup = -1
                rows.forEachIndexed { rowIndex, row ->
                    val group = ProfileCatalog.groupOf(row.first())
                    if (group != previousGroup) {
                        if (previousGroup >= 0) Spacer(Modifier.height(scale.dp(AVATAR_GROUP_GAP)))
                        Text(
                            text = stringResource(
                                when (group) {
                                    0 -> R.string.profile_avatar_movies
                                    1 -> R.string.profile_avatar_animals
                                    else -> R.string.profile_avatar_emoji
                                },
                            ),
                            color = LauncherColors.textSecondary,
                            fontSize = scale.sp(AVATAR_HEADER_FONT),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.height(scale.dp(AVATAR_HEADER_HEIGHT)),
                        )
                        previousGroup = group
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(scale.dp(AVATAR_CELL_GAP))) {
                        for (column in row.indices) {
                            val index = rowIndex * ProfileCatalog.AVATAR_COLUMNS + column
                            val emoji = row[column]
                            val available = emoji in free
                            AvatarDisc(
                                emoji = emoji,
                                name = names.getOrElse(index) { "" },
                                selected = editing?.emoji == emoji,
                                focused = available && index == focusIndex,
                                dimmed = !available,
                                takenNote = if (available) null else takenNote,
                                scale = scale,
                                focusRequester = requester(index),
                                onPick = { if (available) onChoose(emoji) },
                            )
                        }
                    }
                    if (rowIndex != rows.lastIndex) {
                        Spacer(Modifier.height(scale.dp(AVATAR_ROW_GAP)))
                    }
                }
            }
        }
    }

    BackHandler { onDismiss() }
}

@Composable
private fun AvatarDisc(
    emoji: String,
    name: String,
    selected: Boolean,
    focused: Boolean,
    dimmed: Boolean,
    takenNote: String?,
    scale: TvScale,
    focusRequester: FocusRequester,
    onPick: () -> Unit,
) {
    val focusAmount by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(TvOs.FOCUS_MS),
        label = "avatarDiscFocus",
    )
    val grow = 1f + (AVATAR_FOCUS_SCALE - 1f) * focusAmount
    val selectedState = stringResource(R.string.profile_current)
    Box(
        Modifier
            .size(scale.dp(AVATAR_CELL))
            .focusRequester(focusRequester)
            .focusable()
            .clearAndSetSemantics {
                contentDescription = takenNote?.let { "$name, $it" } ?: name
                if (selected) stateDescription = selectedState
                if (!dimmed) {
                    role = Role.Button
                    onClick(label = name) {
                        onPick()
                        true
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val color = ProfileCatalog.color(ProfileCatalog.colorFor(emoji))
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = grow
                    scaleY = grow
                    alpha = if (dimmed) AVATAR_TAKEN_ALPHA else 1f
                }
                .clip(CircleShape)
                .background(Color(color.background))
                .border(
                    width = scale.dp(TvFocus.AVATAR_STROKE),
                    color = Color.White.copy(alpha = TvFocus.discAlpha(focusAmount)),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = emoji,
                color = Color(color.text),
                fontSize = scale.sp(AVATAR_EMOJI_FONT),
                textAlign = TextAlign.Center,
            )
        }
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = scale.dp(2f), y = scale.dp(-2f))
                    .size(scale.dp(AVATAR_CHECK))
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Image(
                    imageVector = SectionGlyph.check,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(TvInk.disc),
                    modifier = Modifier.size(scale.dp(20f)),
                )
            }
        }
    }
}
