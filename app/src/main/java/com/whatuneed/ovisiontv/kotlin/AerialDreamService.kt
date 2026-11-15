package com.whatuneed.ovisiontv.kotlin

import android.content.Context
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
import com.whatuneed.ovisiontv.kotlin.ambient.AmbientMode
import com.whatuneed.ovisiontv.kotlin.ambient.AmbientPlaylist
import com.whatuneed.ovisiontv.kotlin.ambient.AmbientSettingsStore
import com.whatuneed.ovisiontv.kotlin.data.AerialCatalog
import com.whatuneed.ovisiontv.kotlin.data.AerialClip
import com.whatuneed.ovisiontv.kotlin.ui.OVisionTheme
import com.whatuneed.ovisiontv.kotlin.ui.TvCanvas
import com.whatuneed.ovisiontv.kotlin.ui.components.AmbientScreen
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
                        DreamSaver(onFinished = { finish() })
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
        onCloseRequested = onFinished,
        onDismissed = onFinished,
        publishMediaSession = settings.mode == AmbientMode.AERIAL,
        handleBack = false,
    )
}
