package app.aure.tv.ui.components

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import android.media.session.MediaSession
import android.media.session.PlaybackState
import app.aure.tv.data.LauncherUserAgent

/** The system media card follows the active player, not the slot index. */
internal fun updateSaverPlaybackState(session: MediaSession, players: List<Player>) {
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

/**
 * The saver's decoder pair. The host builds it while Home is idle: a cold
 * pair built inside the saver's first composition delays the first picture.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun createSaverPlayers(context: Context): List<ExoPlayer> =
    List(2) { runCatching { createPlayer(context) }.getOrNull() }.filterNotNull()

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun createPlayer(context: Context): ExoPlayer {
    // Clips stream straight from Apple's CDN, as in the Java launcher: nothing
    // is downloaded to disk and the saver works with a full cache partition.
    val http = DefaultHttpDataSource.Factory()
        .setUserAgent(LauncherUserAgent.value)
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
