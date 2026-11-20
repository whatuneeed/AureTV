package app.aure.tv

import android.content.Context
import android.net.Uri
import android.service.dreams.DreamService
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.aure.tv.ambient.AmbientMode
import app.aure.tv.ambient.AmbientPhotoLibrary
import app.aure.tv.ambient.AmbientPlaylist
import app.aure.tv.ambient.RedditFeedLoader
import app.aure.tv.ambient.RedditSelection
import app.aure.tv.ambient.RedditWallpaper
import app.aure.tv.ambient.AmbientSettingsStore
import app.aure.tv.data.AerialCatalog
import app.aure.tv.data.AerialClip
import app.aure.tv.data.ProfileStore
import app.aure.tv.ui.BackupRecoveryGate
import app.aure.tv.ui.OVisionTheme
import app.aure.tv.ui.TvCanvas
import app.aure.tv.ui.components.AmbientScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * The system screensaver entry. Android TV can select this in Settings →
 * Screen saver, so the aerial loop is no longer limited to Home: it can take
 * over while a third-party app is idle. The Home overlay stays the default
 * route and is unchanged; this service only runs when the system starts a
 * dream.
 */
class AerialDreamService : DreamService() {

    private val dreamLifecycle = DreamLifecycleOwner()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        // A dream is controlled by the system wake keys; the saver itself does
        // not need to own the remote.
        dreamLifecycle.create()
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(dreamLifecycle)
            setViewTreeSavedStateRegistryOwner(dreamLifecycle)
            setContent {
                OVisionTheme {
                    TvCanvas {
                        BackupRecoveryGate {
                            DreamSaver(onFinished = { finish() })
                        }
                    }
                }
            }
        }
        setContentView(view)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        dreamLifecycle.start()
    }

    override fun onDreamingStopped() {
        dreamLifecycle.stop()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        dreamLifecycle.destroy()
        super.onDetachedFromWindow()
    }

    private class DreamLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val savedState = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

        fun create() {
            savedState.performRestore(null)
            registry.currentState = Lifecycle.State.CREATED
        }

        fun start() {
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun stop() {
            registry.currentState = Lifecycle.State.CREATED
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}

@Composable
private fun DreamSaver(onFinished: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { AmbientSettingsStore.load(context) }
    val photosAllowed = remember { !ProfileStore(context).current().kids }
    var photoUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    LaunchedEffect(settings.mode, photosAllowed) {
        photoUris = if (settings.mode == AmbientMode.PHOTOS && photosAllowed) {
            withContext(Dispatchers.IO) { AmbientPhotoLibrary.load(context) }
        } else {
            emptyList()
        }
    }
    var redditItems by remember { mutableStateOf<List<RedditWallpaper>>(emptyList()) }
    LaunchedEffect(
        settings.mode,
        photosAllowed,
        settings.redditSubreddit,
        settings.redditRefresh,
        settings.redditSelection,
        settings.redditSelected,
    ) {
        redditItems = if (settings.mode == AmbientMode.REDDIT && photosAllowed) {
            val feed = withContext(Dispatchers.IO) {
                RedditFeedLoader.load(
                    context,
                    settings.redditSubreddit,
                    settings.redditRefresh,
                )
            }
            if (settings.redditSelection == RedditSelection.SELECTED) {
                feed.items.filter { it.id in settings.redditSelected }
            } else {
                feed.items
            }
        } else {
            emptyList()
        }
    }
    var catalogue by remember(settings.mode) {
        mutableStateOf<List<AerialClip>?>(
            if (settings.mode == AmbientMode.AERIAL) null else emptyList(),
        )
    }
    LaunchedEffect(settings.mode) {
        if (settings.mode != AmbientMode.AERIAL) return@LaunchedEffect
        catalogue = withContext(Dispatchers.IO) { AerialCatalog.load(context) }
    }
    // Clock and Minimal need no catalogue; Aerial waits for the real clips.
    val clips = catalogue ?: return
    val items = remember(clips, settings) {
        AmbientPlaylist.scenes(clips, settings, Random.Default)
    }
    if (settings.mode == AmbientMode.AERIAL && items.isEmpty()) {
        LaunchedEffect(Unit) { onFinished() }
        return
    }
    AmbientScreen(
        mode = settings.mode,
        items = items,
        clockFormat = settings.clockFormat,
        pixelShift = settings.pixelShift,
        dimAfter = settings.dimAfter,
        maxSession = settings.maxSession,
        showWeather = settings.showWeather,
        aerialQuality = settings.aerialQuality,
        photos = photoUris,
        photosAllowed = photosAllowed,
        reddit = redditItems,
        redditAllowed = photosAllowed,
        onCloseRequested = onFinished,
        onDismissed = onFinished,
        publishMediaSession = settings.mode == AmbientMode.AERIAL,
        handleBack = false,
    )
}
