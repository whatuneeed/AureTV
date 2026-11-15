package com.whatuneed.ovisiontv.kotlin.ui.components

import android.animation.ValueAnimator
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.whatuneed.ovisiontv.kotlin.MainActivity
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.data.AerialClip
import com.whatuneed.ovisiontv.kotlin.ui.TvInk
import com.whatuneed.ovisiontv.kotlin.ui.LauncherColors
import com.whatuneed.ovisiontv.kotlin.ui.SaverAlphaEasing
import com.whatuneed.ovisiontv.kotlin.ui.SaverExitEasing
import com.whatuneed.ovisiontv.kotlin.ui.SurfaceEasing
import com.whatuneed.ovisiontv.kotlin.ui.rememberTvScale
import com.whatuneed.ovisiontv.kotlin.ui.TvType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val SAVER_SCREEN_TAG = "saver_screen"

/** The system media card follows the active player, not the slot index. */
private fun updateSaverPlaybackState(session: MediaSession, players: List<Player>) {
    val player = players.firstOrNull { it.mediaItemCount > 0 }
    val state = when {
        player == null -> PlaybackState.STATE_NONE
        player.playbackState == Player.STATE_BUFFERING -> PlaybackState.STATE_BUFFERING
        player.isPlaying -> PlaybackState.STATE_PLAYING
        player.playbackState == Player.STATE_READY -> PlaybackState.STATE_PAUSED
        else -> PlaybackState.STATE_NONE
    }
    val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
        PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SEEK_TO or
        PlaybackState.ACTION_STOP
    session.setPlaybackState(
        PlaybackState.Builder()
            .setActions(actions)
            .setState(
                state,
                player?.currentPosition ?: 0L,
                if (player?.isPlaying == true) 1f else 0f,
            )
            .build(),
    )
}

// The Apple masters run about six minutes each; tvOS shows an excerpt, and so
// does the saver. A still keeps the same rhythm when no clip can play.
internal const val SAVER_STILL_MS = 26_000L
internal const val SAVER_CLIP_MS = 30_000L
private const val SAVER_CROSSFADE_MS = 1200
private const val SAVER_STILL_FADE_MS = 600
private const val SAVER_ENTRY_MS = 500
private const val SAVER_ENTRY_ALPHA_MS = 250
private const val SAVER_EXIT_MS = 330
private const val SAVER_REDUCED_MS = 400
private const val SAVER_SCALE = 1.3f
private const val SAVER_ZOOM = 1.06f
private const val SAVER_ZOOM_MS = 26_000
private const val CAPTION_DELAY_MS = 800L
private const val CAPTION_HOLD_MS = 5_500L
private const val CAPTION_FADE_MS = 700
private const val PLAYER_POLL_MS = 200L
private const val CLIP_START_TIMEOUT_MS = 20_000L
private const val CLIP_STALL_TIMEOUT_MS = 12_000L
private const val PREFETCH_TIMEOUT_MS = 20_000L
private const val NEXT_START_TIMEOUT_MS = 8_000L
// Apple's masters open on a short black lead-in; the still must stay visible
// until the clip has faded up to real content.
private const val FIRST_FRAME_LEAD_IN_MS = 1_500L
// A 1080p still normally draws within a frame or two; if the draw callback
// never arrives, the entrance falls back to showing the bitmap anyway.
private const val STILL_DRAW_FALLBACK_MS = 1_000L
private const val FAILED_HOLD_MS = 12_000L
private const val NO_PICTURE_TIMEOUT_MS = 15_000L
private const val POSTER_DELAY_MS = 200L
private const val MAX_CONSECUTIVE_FAILURES = 3
private const val TAG = "SaverPlayer"

/**
 * After a full round of stills, retry the network only after a growing wait.
 * Without it a one-clip offline saver re-asked Apple every 26 seconds forever.
 */
internal fun saverOfflineRetryDelay(rounds: Int): Long {
    val shift = rounds.coerceIn(0, 3)
    return (15_000L shl shift).coerceAtMost(120_000L)
}

// Measured in tvOS 27 (11_SCREENSAVER §2.2). The mesh distortion is not
// reproducible here; the scale and alpha curves are.

/**
 * Full-screen saver surface. Any remote key dismisses it (11_SCREENSAVER: the
 * saver is left by a single press), and keeping the window awake is scoped to
 * this overlay instead of changing the TV's own display settings.
 *
 * The saver only ever shows real aerial frames: the clip's still while a
 * decoder starts, then the clip itself. There is no invented artwork to stand
 * in for content.
 *
 * [closing] is hoisted so the host can reverse the entrance before the overlay
 * is removed; the keys still belong to the saver until [onDismissed] fires.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun SaverScreen(
    items: List<AerialClip>,
    closing: Boolean = false,
    // The preview is opened by holding OK. Until that press is released, all
    // of its events belong to the opener, not to the key that leaves.
    swallowOpeningPress: Boolean = false,
    players: List<ExoPlayer>? = null,
    onCloseRequested: () -> Unit = {},
    onDismissed: () -> Unit = {},
    noPictureTimeoutMs: Long = NO_PICTURE_TIMEOUT_MS,
    // Home and the DreamService expose the saver to system media controls;
    // the Settings preview stays out of the system's Now Playing.
    publishMediaSession: Boolean = false,
    // A DreamService has no OnBackPressedDispatcherOwner; the system owns the
    // wake keys there, so only the Home overlay installs the BackHandler.
    handleBack: Boolean = true,
) {
    val plan = items
    val context = LocalContext.current
    val language = LocalConfiguration.current.locales[0]?.language.orEmpty()
    val hasClips = plan.isNotEmpty()
    // Two players take turns: one plays, the other has the next excerpt
    // buffered, so the clip's still is only needed before the first frame and
    // when the network really fails. The host normally builds them while Home
    // is idle: creating two decoders inside the saver's first composition
    // stalls the entrance long enough to show its background before the
    // picture. Tests and other callers keep the self-made pair.
    val ownedPlayers = remember(hasClips, context, players) {
        if (!hasClips || players != null) {
            emptyList()
        } else {
            createSaverPlayers(context)
        }
    }
    val planPlayers = players ?: ownedPlayers

    var item by remember(plan) { mutableStateOf(plan.firstOrNull()) }

    // The saver is real playback, so the system may show its title and offer
    // play/pause. The session is released with the overlay; it never replaces
    // another app's session while the saver is not visible.
    val mediaSession = remember(context) { MediaSession(context, "OVisionTVSaver") }
    DisposableEffect(mediaSession, publishMediaSession) {
        mediaSession.setFlags(
            MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS,
        )
        mediaSession.setCallback(
            object : MediaSession.Callback() {
                override fun onPlay() {
                    planPlayers.firstOrNull { it.mediaItemCount > 0 }?.play()
                    updateSaverPlaybackState(mediaSession, planPlayers)
                }

                override fun onPause() {
                    planPlayers.forEach { it.pause() }
                    updateSaverPlaybackState(mediaSession, planPlayers)
                }

                override fun onStop() {
                    planPlayers.forEach { it.pause() }
                    updateSaverPlaybackState(mediaSession, planPlayers)
                }

                override fun onSeekTo(pos: Long) {
                    planPlayers.firstOrNull { it.mediaItemCount > 0 }?.seekTo(pos)
                }
            },
        )
        mediaSession.setSessionActivity(
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        if (publishMediaSession) mediaSession.isActive = true
        onDispose {
            mediaSession.isActive = false
            mediaSession.release()
        }
    }
    LaunchedEffect(item, language, publishMediaSession) {
        if (!publishMediaSession) return@LaunchedEffect
        val clip = item ?: return@LaunchedEffect
        mediaSession.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, clip.name(language))
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "OVisionTV")
                .putString(MediaMetadata.METADATA_KEY_ALBUM, "Aerial")
                .build(),
        )
        updateSaverPlaybackState(mediaSession, planPlayers)
    }
    LaunchedEffect(mediaSession, planPlayers, publishMediaSession) {
        if (!publishMediaSession) return@LaunchedEffect
        while (true) {
            updateSaverPlaybackState(mediaSession, planPlayers)
            delay(1_000)
        }
    }

    var slot0Visible by remember { mutableStateOf(false) }
    var slot1Visible by remember { mutableStateOf(false) }
    val slot0Alpha by animateFloatAsState(
        targetValue = if (slot0Visible) 1f else 0f,
        animationSpec = tween(SAVER_CROSSFADE_MS),
        label = "saverSlot0Alpha",
    )
    val slot1Alpha by animateFloatAsState(
        targetValue = if (slot1Visible) 1f else 0f,
        animationSpec = tween(SAVER_CROSSFADE_MS),
        label = "saverSlot1Alpha",
    )

    fun setSlotVisible(slot: Int, visible: Boolean) {
        if (slot == 0) slot0Visible = visible else slot1Visible = visible
    }

    // The clip's real frame stays under the players so every non-video moment
    // (loading, a failed change) shows the aerial itself, never a placeholder.
    val still = rememberSaverStill(item)
    // Having the bitmap is not the same as having it on screen: the first
    // draw of a 1080p still uploads a texture and can land a few frames after
    // composition. The overlay must not fade in on its dark background while
    // that happens, so visibility waits for the frame that actually drew it.
    var stillDrawn by remember(still) { mutableStateOf(false) }
    val videoVisible = slot0Visible || slot1Visible
    var stillHidden by remember { mutableStateOf(false) }
    LaunchedEffect(videoVisible) {
        if (videoVisible) {
            // Hide only once the crossfade has finished, so no dark frame can
            // slip between the still and the clip.
            delay(SAVER_CROSSFADE_MS.toLong())
            stillHidden = true
        } else {
            stillHidden = false
        }
    }
    val stillAlpha by animateFloatAsState(
        targetValue = if (stillHidden) 0f else 1f,
        animationSpec = tween(SAVER_STILL_FADE_MS),
        label = "saverStillAlpha",
    )
    // Once the clip is up the still is invisible: keeping a 1080p bitmap and an
    // endless zoom composed behind the video costs a full-screen draw per tick.
    val stillOnScreen by remember { derivedStateOf { stillAlpha > 0.01f } }
    // The entrance fade is shorter than a decoder start, so the surface waits
    // for a real picture: the clip's still or its first rendered frame. tvOS
    // never shows an empty saver, and a black flash reads as a crash. The
    // video only counts once it is nearly opaque: revealing the overlay while
    // the slot is still fading in from zero would show the same dark surface.
    val videoRevealed = remember {
        derivedStateOf { slot0Alpha >= 0.9f || slot1Alpha >= 0.9f }
    }
    // A drawn still proves the saver has something real on screen; a still
    // that exists but has not drawn yet keeps the watchdog quiet without
    // letting the dark background fade in. If the draw callback never comes,
    // the fallback reveals the still anyway instead of holding Home forever.
    var stillFallback by remember(still) { mutableStateOf(false) }
    LaunchedEffect(still) {
        if (still == null) return@LaunchedEffect
        delay(STILL_DRAW_FALLBACK_MS)
        if (!stillDrawn) stillFallback = true
    }
    val hasPicture = still != null || videoRevealed.value
    val revealPicture = stillDrawn || stillFallback || videoRevealed.value
    // A new item may momentarily have no still while its frame loads; dropping
    // alpha to zero and back would pulse the whole overlay once per clip.
    var everHadPicture by remember { mutableStateOf(false) }
    LaunchedEffect(revealPicture) { if (revealPicture) everHadPicture = true }
    // An overlay with no picture owns the remote while it shows a dark
    // surface, and Home is already dimmed behind it. Never keep that up:
    // leave the saver to the host instead. The bound sits above a decoder
    // start plus the slot crossfade, so a slow first frame still gets shown.
    LaunchedEffect(hasPicture, closing) {
        if (hasPicture || closing) return@LaunchedEffect
        delay(noPictureTimeoutMs)
        if (!closing) onCloseRequested()
    }

    // A frame is only shown once the decoder has actually rendered it: a
    // started position would hide the still behind a black surface.
    val renderedFirstFrame = remember(planPlayers) { BooleanArray(planPlayers.size) }
    val playerViews = remember(planPlayers) { arrayOfNulls<PlayerView>(planPlayers.size) }

    // The slot alpha changes every frame during the crossfade. Reading it from
    // AndroidView's update would recompose this whole screen at 60 fps for
    // 1.2 s per clip, so the views are driven from a snapshot flow instead.
    LaunchedEffect(planPlayers) {
        snapshotFlow {
            Array(planPlayers.size) { slot -> if (slot == 0) slot0Alpha else slot1Alpha }
        }.collect { alphas ->
            planPlayers.indices.forEach { slot ->
                val playerView = playerViews[slot] ?: return@forEach
                val alpha = alphas.getOrElse(slot) { 0f }
                // Do not hide the surface behind INVISIBLE: a TextureView only
                // consumes decoded frames while it is drawn, and ExoPlayer
                // reports the first frame only after the surface took it. A
                // hidden slot would keep decoding (position advances, buffer
                // grows) yet never report a frame, and the saver stayed on the
                // still. One percent of black over the still is invisible.
                playerView.alpha = alpha.coerceAtLeast(0.01f)
                playerView.visibility = View.VISIBLE
            }
        }
    }

    LaunchedEffect(planPlayers, plan) {
        fun stop(slot: Int) {
            val player = planPlayers[slot]
            player.playWhenReady = false
            player.stop()
            player.clearMediaItems()
            setSlotVisible(slot, false)
        }

        fun prepare(slot: Int, url: String, play: Boolean) {
            val player = planPlayers[slot]
            player.stop()
            player.clearMediaItems()
            renderedFirstFrame[slot] = false
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.playWhenReady = play
        }

        // The still of the next session is the frame the clip itself opens on,
        // so the picture dissolves into the same image.
        suspend fun capturePoster(slot: Int, clip: AerialClip) {
            delay(POSTER_DELAY_MS)
            val texture = playerViews[slot]?.videoSurfaceView as? TextureView ?: return
            if (!texture.isAvailable) return
            // A 1080p readback allocates ~8 MB; the saver must not drop frames
            // for it right after revealing the clip.
            val frame = withContext(Dispatchers.IO) {
                runCatching { texture.bitmap }.getOrNull()
            } ?: return
            AerialPosterStore.save(context, clip.id, frame)
        }

        // The first clip starts at the head: a mid-file seek needs a second
        // network round trip and can outlast the start watchdog on slow
        // decoders, leaving the saver on the still.
        suspend fun startFromHead(slot: Int, clip: AerialClip): Long {
            prepare(slot, clip.videoUrl, play = true)
            val player = planPlayers[slot]
            val born = SystemClock.elapsedRealtime()
            while (true) {
                delay(PLAYER_POLL_MS)
                val failed = player.playerError != null ||
                    player.playbackState == Player.STATE_ENDED
                val timedOut = SystemClock.elapsedRealtime() - born >= CLIP_START_TIMEOUT_MS
                if (failed || (timedOut && !renderedFirstFrame[slot])) {
                    // Media is HTTPS-only; a blocked TLS port falls back to the
                    // stills-only path rather than silently downgrading to HTTP.
                    Log.w(TAG, "saver clip failed to start", player.playerError)
                    return -1L
                }
                if (renderedFirstFrame[slot]) {
                    launch { capturePoster(slot, clip) }
                    // Every clip starts at its head here, and the head is
                    // black. Crossfading in on that first frame would paint
                    // the overlay black over the still; let the picture come
                    // up first, then reveal the slot.
                    delay(FIRST_FRAME_LEAD_IN_MS)
                    setSlotVisible(slot, true)
                    return SystemClock.elapsedRealtime()
                }
                if (timedOut) {
                    Log.w(
                        TAG,
                        "saver clip did not render within $CLIP_START_TIMEOUT_MS ms" +
                            " state=${player.playbackState} pos=${player.currentPosition}" +
                            " buffered=${player.bufferedPosition}",
                    )
                    return -1L
                }
            }
        }

        // Both clips start at their head: a mid-file seek needs a second
        // network round trip and can outlast the start watchdog on slow
        // decoders, leaving the saver on the still. This is also how the Java
        // launcher plays its clips.
        suspend fun prefetch(slot: Int, clip: AerialClip): Boolean {
            prepare(slot, clip.videoUrl, play = false)
            val player = planPlayers[slot]
            val born = SystemClock.elapsedRealtime()
            while (true) {
                delay(PLAYER_POLL_MS)
                if (player.playerError != null ||
                    player.playbackState == Player.STATE_ENDED
                ) {
                    Log.w(TAG, "saver prefetch failed", player.playerError)
                    return false
                }
                if (player.playbackState == Player.STATE_READY) return true
                if (SystemClock.elapsedRealtime() - born >= PREFETCH_TIMEOUT_MS) {
                    return false
                }
            }
        }

        suspend fun awaitStart(slot: Int): Boolean {
            val player = planPlayers[slot]
            // The flag belongs to prefetch: true already means this clip has a
            // frame on the surface. Clearing it here would wait for a callback
            // that ExoPlayer only sends once per seek or stream change.
            player.playWhenReady = true
            val born = SystemClock.elapsedRealtime()
            while (true) {
                delay(PLAYER_POLL_MS)
                if (player.playerError != null ||
                    player.playbackState == Player.STATE_ENDED
                ) {
                    return false
                }
                if (renderedFirstFrame[slot]) break
                if (SystemClock.elapsedRealtime() - born >= NEXT_START_TIMEOUT_MS) {
                    return false
                }
            }
            // Apple's masters open on a short black lead-in. Revealing the
            // slot on that first frame would fade the still into black.
            delay(FIRST_FRAME_LEAD_IN_MS)
            return true
        }

        fun nextIndex(index: Int): Int = if (plan.size <= 1) 0 else (index + 1) % plan.size

        if (planPlayers.size < 2) {
            // No decoder surfaces: real stills in clip order are the only
            // honest fallback, and the saver stays dismissable.
            var index = 0
            while (plan.isNotEmpty()) {
                item = plan[index]
                delay(SAVER_STILL_MS)
                index = nextIndex(index)
            }
        } else {
            var index = 0
            var active = 0
            var failures = 0
            var stillsOnly = false
            var stillSteps = 0
            var offlineRounds = 0
            // The crossfade starts the next clip itself; restarting it at the
            // loop top would blank a visible slot and jump back to the head.
            var activeItem: AerialClip? = null
            var activeStartedAt = 0L
            while (true) {
                if (stillsOnly) {
                    activeItem = null
                    item = plan[index]
                    delay(SAVER_STILL_MS)
                    index = nextIndex(index)
                    if (++stillSteps >= plan.size) {
                        // A full round of stills: retry the clips in case the
                        // network has come back.
                        stillSteps = 0
                        failures = 0
                        stillsOnly = false
                        delay(saverOfflineRetryDelay(offlineRounds))
                        offlineRounds++
                    }
                    continue
                }

                val clip = plan[index]
                item = clip
                val startedAt = if (activeItem === clip && activeStartedAt > 0L) {
                    activeStartedAt
                } else {
                    startFromHead(active, clip).also { started ->
                        if (started >= 0L) {
                            activeItem = clip
                            activeStartedAt = started
                        }
                    }
                }
                if (startedAt < 0L) {
                    activeItem = null
                    stop(active)
                    failures++
                    if (failures >= MAX_CONSECUTIVE_FAILURES) {
                        stillSteps = 0
                        stillsOnly = true
                        continue
                    }
                    delay(FAILED_HOLD_MS)
                    index = nextIndex(index)
                    continue
                }
                failures = 0
                offlineRounds = 0

                val next = nextIndex(index)
                val nextClip = plan[next]
                val nextSlot = 1 - active
                var nextReady = false
                val prefetchJob = launch {
                    nextReady = prefetch(nextSlot, nextClip)
                }

                var failedNow = false
                var bufferingSince = 0L
                while (true) {
                    delay(PLAYER_POLL_MS)
                    val player = planPlayers[active]
                    if (player.playerError != null) {
                        failedNow = true
                        break
                    }
                    val state = player.playbackState
                    if (state == Player.STATE_ENDED) break
                    val now = SystemClock.elapsedRealtime()
                    if (state == Player.STATE_READY) {
                        bufferingSince = 0L
                    } else if (bufferingSince == 0L) {
                        bufferingSince = now
                    }
                    if (bufferingSince != 0L &&
                        now - bufferingSince >= CLIP_STALL_TIMEOUT_MS
                    ) {
                        failedNow = true
                        break
                    }
                    if (now - startedAt >= SAVER_CLIP_MS - SAVER_CROSSFADE_MS) break
                }

                if (failedNow) {
                    prefetchJob.cancel()
                    stop(active)
                    stop(nextSlot)
                    activeItem = null
                    failures++
                    if (failures >= MAX_CONSECUTIVE_FAILURES) {
                        stillSteps = 0
                        stillsOnly = true
                        continue
                    }
                    delay(FAILED_HOLD_MS)
                    index = next
                    continue
                }

                prefetchJob.join()
                if (nextReady && awaitStart(nextSlot)) {
                    setSlotVisible(nextSlot, true)
                    setSlotVisible(active, false)
                    item = nextClip
                    activeItem = nextClip
                    activeStartedAt = SystemClock.elapsedRealtime()
                    delay(SAVER_CROSSFADE_MS.toLong())
                    stop(active)
                    active = nextSlot
                    index = next
                    continue
                }
                prefetchJob.cancel()
                stop(active)
                stop(nextSlot)
                activeItem = null
                failures++
                if (failures >= MAX_CONSECUTIVE_FAILURES) {
                    stillSteps = 0
                    stillsOnly = true
                    continue
                }
                delay(FAILED_HOLD_MS)
                index = next
            }
        }
    }

    DisposableEffect(planPlayers, ownedPlayers) {
        val listeners = planPlayers.mapIndexed { slot, player ->
            object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    renderedFirstFrame[slot] = true
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Log.w(TAG, "saver slot $slot error", error)
                }
            }.also(player::addListener)
        }
        onDispose {
            planPlayers.forEachIndexed { slot, player -> player.removeListener(listeners[slot]) }
            // Host-owned players outlive the overlay, so they have to be
            // stopped here: a dismissed saver must not keep decoding a clip.
            planPlayers.forEach { player ->
                player.playWhenReady = false
                player.stop()
                player.clearMediaItems()
            }
            ownedPlayers.forEach(ExoPlayer::release)
        }
    }

    // tvOS has a separate Reduce Motion path: alpha only, no scale.
    val reduceMotion = remember { !ValueAnimator.areAnimatorsEnabled() }
    var entered by remember { mutableStateOf(false) }
    var openingPressReleased by remember { mutableStateOf(!swallowOpeningPress) }
    LaunchedEffect(Unit) { entered = true }

    val screenAlpha by animateFloatAsState(
        targetValue = if (entered && everHadPicture && !closing) 1f else 0f,
        animationSpec = tween(
            durationMillis = when {
                reduceMotion -> SAVER_REDUCED_MS
                closing -> SAVER_EXIT_MS
                else -> SAVER_ENTRY_ALPHA_MS
            },
            easing = if (closing) SaverExitEasing else SaverAlphaEasing,
        ),
        label = "saverScreenAlpha",
    )
    val screenScale by animateFloatAsState(
        // The scale follows the picture, not the first composition: on a cold
        // start the entrance must not have already finished while invisible.
        targetValue = if (reduceMotion) {
            1f
        } else if (!everHadPicture || closing) {
            SAVER_SCALE
        } else {
            1f
        },
        animationSpec = tween(
            durationMillis = if (closing) SAVER_EXIT_MS else SAVER_ENTRY_MS,
            easing = if (closing) SaverExitEasing else SurfaceEasing,
        ),
        label = "saverScreenScale",
    )

    LaunchedEffect(closing) {
        if (closing) {
            delay((if (reduceMotion) SAVER_REDUCED_MS else SAVER_EXIT_MS).toLong())
            onDismissed()
        }
    }

    // A slow push keeps a still frame from looking frozen. Reduce Motion
    // keeps it still, and the transition only exists while the still does.
    val zoom = if (stillOnScreen && !reduceMotion) {
        rememberInfiniteTransition(label = "saverZoom").animateFloat(
            initialValue = 1f,
            targetValue = SAVER_ZOOM,
            animationSpec = infiniteRepeatable(
                animation = tween(SAVER_ZOOM_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "saverZoomValue",
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    val focusRequester = remember { FocusRequester() }
    // The remote must belong to the saver from its first frame: a delayed
    // request leaves the home grid listening while the overlay fades in.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    if (handleBack) {
        BackHandler { if (!closing) onCloseRequested() }
    }

    var captionVisible by remember { mutableStateOf(false) }
    LaunchedEffect(item) {
        captionVisible = false
        delay(CAPTION_DELAY_MS)
        captionVisible = true
        delay(CAPTION_HOLD_MS)
        captionVisible = false
    }
    val captionAlpha by animateFloatAsState(
        targetValue = if (captionVisible) 1f else 0f,
        animationSpec = tween(CAPTION_FADE_MS),
        label = "saverCaptionAlpha",
    )

    val scale = rememberTvScale()
    val current = item
    val title = current?.name(language).orEmpty()
    val description = if (current != null) {
        stringResource(R.string.saver_screen_description, title)
    } else {
        ""
    }

    Box(
        Modifier
            .fillMaxSize()
            // The fade has to own the background: with background() before
            // graphicsLayer the solid surface paints at full opacity from the
            // first composition, and the entrance shows a dark saver before
            // the still fades in. The layer is the outer wrapper here.
            .graphicsLayer {
                alpha = screenAlpha
                scaleX = screenScale
                scaleY = screenScale
            }
            .background(TvInk.saver)
            .testTag(SAVER_SCREEN_TAG)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                // The OS may repeat a held key with repeatCount 0 (the emulator
                // forwards host keys that way), so the opener is recognised by
                // its release rather than by a repeat counter.
                when (event.type) {
                    KeyEventType.KeyDown -> if (openingPressReleased && !closing) {
                        onCloseRequested()
                    }

                    KeyEventType.KeyUp -> openingPressReleased = true

                    else -> Unit
                }
                true
            }
            .focusable()
            .clearAndSetSemantics { contentDescription = description },
    ) {
        if (stillOnScreen) {
            still?.let { frame ->
                Image(
                    bitmap = frame.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()
                            if (!stillDrawn) stillDrawn = true
                        }
                        .graphicsLayer {
                            alpha = stillAlpha
                            scaleX = zoom.value
                            scaleY = zoom.value
                        },
                )
            }
        }

        planPlayers.forEachIndexed { slot, player ->
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    (LayoutInflater.from(ctx)
                        .inflate(R.layout.saver_player, FrameLayout(ctx), false) as PlayerView)
                        .apply {
                            this.player = player
                            playerViews[slot] = this
                            useController = false
                            setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                            isFocusable = false
                            isFocusableInTouchMode = false
                            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                            // Starts hidden: the slot alpha arrives from the
                            // snapshot flow above, and a default-visible
                            // surface would cover the still with black first.
                            alpha = 0f
                            visibility = View.INVISIBLE
                        }
                },
                update = { playerView ->
                    playerView.player = player
                },
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.6f to Color.Transparent,
                        1f to TvInk.saver.copy(alpha = 0.62f),
                    ),
                ),
        )
        if (current != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = scale.dp(72f), bottom = scale.dp(64f))
                    .graphicsLayer { alpha = captionAlpha },
            ) {
                Text(
                    text = title,
                    color = LauncherColors.textPrimary,
                    fontSize = scale.sp(TvType.subhead),
                    fontWeight = FontWeight.SemiBold,
                    style = TextStyle(
                        shadow = Shadow(TvInk.shadow, Offset(0f, 2f), 12f),
                    ),
                )
                Text(
                    text = saverThemeLabel(current.theme),
                    color = LauncherColors.textSecondary,
                    fontSize = scale.sp(TvType.callout),
                    style = TextStyle(
                        shadow = Shadow(TvInk.shadow, Offset(0f, 2f), 10f),
                    ),
                )
            }
        }
    }
}

/**
 * The saver's decoder pair. The host builds it while Home is idle: a cold
 * pair built inside the saver's first composition delays the first picture.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun createSaverPlayers(context: Context): List<ExoPlayer> =
    List(2) { runCatching { createPlayer(context) }.getOrNull() }.filterNotNull()

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun createPlayer(context: Context): ExoPlayer {
    // Clips stream straight from Apple's CDN, as in the Java launcher: nothing
    // is downloaded to disk and the saver works with a full cache partition.
    val http = DefaultHttpDataSource.Factory()
        .setUserAgent(Util.getUserAgent(context, "OVisionTV"))
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(15_000)
    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(http))
        .build()
        .apply {
            // The saver never takes over sound or audio focus, even when the
            // TV is playing music in the background.
            volume = 0f
            setAudioAttributes(AudioAttributes.DEFAULT, false)
        }
}
