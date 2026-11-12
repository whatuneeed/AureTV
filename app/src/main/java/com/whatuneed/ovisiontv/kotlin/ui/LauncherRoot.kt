package com.whatuneed.ovisiontv.kotlin.ui

import android.animation.ValueAnimator
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.exoplayer.ExoPlayer
import com.whatuneed.ovisiontv.kotlin.KidsEntryGrant
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.SettingsActivity
import com.whatuneed.ovisiontv.kotlin.data.Adaptive
import com.whatuneed.ovisiontv.kotlin.data.AerialCatalog
import com.whatuneed.ovisiontv.kotlin.data.AerialClipCache
import com.whatuneed.ovisiontv.kotlin.data.AppRepository
import com.whatuneed.ovisiontv.kotlin.data.HomeItem
import com.whatuneed.ovisiontv.kotlin.data.HomeOrderStore
import com.whatuneed.ovisiontv.kotlin.data.AerialRefresh
import com.whatuneed.ovisiontv.kotlin.data.AerialRefreshResult
import com.whatuneed.ovisiontv.kotlin.data.KidsMode
import com.whatuneed.ovisiontv.kotlin.data.KidsStore
import com.whatuneed.ovisiontv.kotlin.data.ProfileStore
import com.whatuneed.ovisiontv.kotlin.data.SaverDelay
import com.whatuneed.ovisiontv.kotlin.data.SaverPlaylist
import com.whatuneed.ovisiontv.kotlin.data.SaverPreferences
import com.whatuneed.ovisiontv.kotlin.data.SaverSchedule
import com.whatuneed.ovisiontv.kotlin.data.SetupProgress
import com.whatuneed.ovisiontv.kotlin.data.SleepControls
import com.whatuneed.ovisiontv.kotlin.data.StartupPreferences
import com.whatuneed.ovisiontv.kotlin.data.TvApp
import com.whatuneed.ovisiontv.kotlin.data.isSettingsTile
import com.whatuneed.ovisiontv.kotlin.data.kidsFilter
import com.whatuneed.ovisiontv.kotlin.data.kidsLeaveNeedsPin
import com.whatuneed.ovisiontv.kotlin.ui.components.AerialPosterStore
import com.whatuneed.ovisiontv.kotlin.ui.components.AerialPreviewStore
import com.whatuneed.ovisiontv.kotlin.ui.components.PinPadMode
import com.whatuneed.ovisiontv.kotlin.ui.components.PinPadOverlay
import com.whatuneed.ovisiontv.kotlin.ui.components.ProfilePicker
import com.whatuneed.ovisiontv.kotlin.ui.components.SaverScreen
import com.whatuneed.ovisiontv.kotlin.ui.components.createSaverPlayers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

// While music holds the saver back the music state is re-read at this rate;
// the playback callback normally ends the wait sooner.
private const val SAVER_MUSIC_CHECK_MS = 15_000L
private const val SAVER_HOME_SCALE_MS = 500
private const val SAVER_HOME_SCALE = 0.7f

// Asked once per launcher process: a restart in the middle of the day is not
// a TV boot, and Settings must not bring the picker back.
private var bootProfilePickerShown = false

/** What the verified PIN unlocks. */
private enum class PinGate { SETTINGS, PROFILE, SYSTEM }

@Composable
fun LauncherRoot(resumeTick: Int = 0, foreground: Boolean = true, wakeTick: Int = 0) {
    val context = LocalContext.current
    // The repository only reads the package manager and resources, and it
    // outlives a single activity instance; the application context is the
    // safe owner (PHASE 33, memory review).
    val repository = remember(context) { AppRepository(context.applicationContext) }
    val profileStore = remember(context) { ProfileStore(context) }
    var profiles by remember { mutableStateOf(profileStore.list()) }
    var profile by remember { mutableStateOf(profileStore.current()) }
    // Each profile owns its order, focus and shelf. p1 keeps the original file
    // so the layout written before profiles existed stays where it was.
    val orderStore = remember(context, profile.id) {
        HomeOrderStore(context, ProfileStore.homePrefsName(profile.id))
    }
    var apps by remember(orderStore) { mutableStateOf<List<HomeItem>?>(null) }
    var savedOrder by remember(orderStore) { mutableStateOf<String?>(null) }
    var pickerVisible by remember { mutableStateOf(false) }

    // The kids gate lives above every surface: a pending PIN either opens
    // Settings or completes a profile switch out of the kids profile.
    var pinGate by remember { mutableStateOf<PinGate?>(null) }
    var pendingProfileId by remember { mutableStateOf<String?>(null) }
    // The Control Center's system tiles wait here while the kids pad is up.
    var pendingSystemIntent by remember { mutableStateOf<Intent?>(null) }
    // The gate can fall back to the recovery word instead of the PIN.
    var gateRecover by remember { mutableStateOf(false) }

    // The allowlist is re-read on every resume: Settings may have edited it
    // while Home was paused, and the filter must not keep a stale copy.
    val kidsAllowlist = remember(resumeTick, profile.id) {
        KidsStore(context).allowlist(profile.id)
    }
    val kidsActive = profile.kids

    var saverVisible by remember { mutableStateOf(false) }
    var saverClosing by remember { mutableStateOf(false) }
    var focusTick by remember { mutableIntStateOf(0) }
    // The picker needs its own tick: a PIN gate closing over it must bring the
    // disc back, and bumping the shared tick would also wake the Home grid.
    var pickerFocusTick by remember { mutableIntStateOf(0) }
    val lastActivity = remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    // The saver timer sleeps until its deadline; a tick marks input, the music
    // callback and the return from another activity, which recompute the wait.
    var activityTick by remember { mutableIntStateOf(0) }
    var audioTick by remember { mutableIntStateOf(0) }
    val markActivity = {
        lastActivity.longValue = SystemClock.elapsedRealtime()
        activityTick++
    }

    // Settings runs in its own activity, so resumeTick is also the signal to
    // re-read the saver options it may have changed.
    val saverDelay = remember(resumeTick) { SaverPreferences.delay(context) }
    val saverShowDuringMusic = remember(resumeTick) { SaverPreferences.music(context) }
    var catalogTick by remember { mutableIntStateOf(0) }
    val aerialClips = remember(context, catalogTick) { AerialCatalog.load(context) }
    val saverItems = remember(resumeTick, aerialClips) {
        SaverPlaylist.build(
            aerialClips,
            SaverPreferences.themes(context),
            SaverPreferences.hidden(context),
            Random.Default,
        )
    }
    val audioManager = remember(context) { context.getSystemService(AudioManager::class.java) }
    // tvOS has a separate Reduce Motion path for the entrance: no scale.
    val reduceMotion = remember { !ValueAnimator.areAnimatorsEnabled() }
    val homeScale by animateFloatAsState(
        targetValue = if (saverVisible && !reduceMotion) SAVER_HOME_SCALE else 1f,
        animationSpec = tween(SAVER_HOME_SCALE_MS, easing = SurfaceEasing),
        label = "homeScaleUnderSaver",
    )
    // After the entrance the saver is opaque, but Compose still rasterizes the
    // grid under it on every video frame. An alpha-0 layer stops that without
    // dropping Home's state, so returning from the saver stays instant.
    var homeBehindSaver by remember { mutableStateOf(false) }
    LaunchedEffect(saverVisible) {
        if (!saverVisible) {
            homeBehindSaver = false
        } else {
            delay(SAVER_HOME_SCALE_MS.toLong() + 100)
            homeBehindSaver = true
        }
    }

    // The saver belongs to the foreground home screen: a launched app or the
    // Settings activity must never be covered by it.
    LaunchedEffect(foreground) {
        if (!foreground) {
            saverVisible = false
            saverClosing = false
        }
    }

    // Download Frequency is checked on resume, not on a background schedule:
    // the launcher only exists while it is the home screen.
    LaunchedEffect(foreground, context) {
        if (!foreground || !AerialRefresh.isDue(context)) return@LaunchedEffect
        // Only a saved catalogue replaces the playlist; a failed download must
        // not restart the saver on the same clips.
        if (AerialRefresh.update(context) == AerialRefreshResult.UPDATED) catalogTick++
    }

    // Older builds cached clips on disk; the saver streams now, so the old
    // directory has to go rather than take half a gigabyte of TV storage.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { AerialClipCache.cleanup(context) }
    }

    // Warm the first clip's still while Home is idle: the saver then opens on
    // a real picture instead of a dark frame while the decoder starts.
    // Once the clip has played, its own full-resolution frame is preferred.
    // The bitmap is kept to draw one invisible pixel (see the bottom of this
    // composable): that draw uploads the GPU texture now, so the saver's first
    // frame is the picture itself and never the background waiting for it.
    var warmStill by remember { mutableStateOf<Bitmap?>(null) }
    val trimTick = MemoryPressure.trimTick.intValue
    LaunchedEffect(trimTick) {
        if (trimTick > 0) warmStill = null
    }
    LaunchedEffect(foreground, saverItems, saverDelay) {
        warmStill = null
        if (!foreground || saverDelay == SaverDelay.NEVER) return@LaunchedEffect
        val clip = saverItems.firstOrNull() ?: return@LaunchedEffect
        val beforeLoad = MemoryPressure.trimTick.intValue
        val frame = withContext(Dispatchers.IO) {
            AerialPosterStore.load(context, clip.id)
                ?: AerialPreviewStore.load(context, clip.previewUrl)
        }
        // A trim must not immediately allocate the warm frame it just dropped.
        if (MemoryPressure.trimTick.intValue == beforeLoad) warmStill = frame
    }

    // The saver's decoder pair is built while Home is idle: constructing two
    // players inside the saver's first composition delays the first picture.
    // Behind another activity they are the largest idle allocation, so they are
    // released and rebuilt on return; SaverScreen can also build its own pair.
    var saverPlayers by remember { mutableStateOf<List<ExoPlayer>?>(null) }
    LaunchedEffect(foreground, saverVisible, saverDelay) {
        if (!foreground) {
            if (!saverVisible) {
                saverPlayers?.forEach(ExoPlayer::release)
                saverPlayers = null
            }
            return@LaunchedEffect
        }
        if (saverDelay == SaverDelay.NEVER || saverPlayers != null) return@LaunchedEffect
        // Let Home finish its first frame before the decoders take the thread.
        delay(2_000)
        saverPlayers = createSaverPlayers(context)
    }
    DisposableEffect(Unit) {
        onDispose { saverPlayers?.forEach(ExoPlayer::release) }
    }

    // Music can start or stop in another app, so the timer learns it by event
    // rather than by reading the global music state every second.
    DisposableEffect(audioManager) {
        val callback = object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
                audioTick++
            }
        }
        audioManager?.registerAudioPlaybackCallback(callback, null)
        onDispose { audioManager?.unregisterAudioPlaybackCallback(callback) }
    }

    // A hot panel lowers the profile while Home is on screen: caches shrink in
    // place and the draw-time budgets (shadow layers, art sampling) follow on
    // their next draw. The cap only ever tightens, never raises back.
    DisposableEffect(context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@DisposableEffect onDispose {}
        val power = context.getSystemService(PowerManager::class.java)
            ?: return@DisposableEffect onDispose {}
        val listener = PowerManager.OnThermalStatusChangedListener { status ->
            Adaptive.onThermalStatus(status)
            MemoryPressure.trimToProfile()
        }
        power.addThermalStatusListener(listener)
        onDispose { power.removeThermalStatusListener(listener) }
    }

    // Returning from Settings or an app counts as activity: the idle clock
    // restarts, exactly as the old every-second poll used to do on relaunch.
    LaunchedEffect(resumeTick, foreground) {
        if (foreground) markActivity()
    }

    // The timer sleeps until the idle deadline instead of waking every second;
    // input, music and Settings all restart it through the effect keys.
    LaunchedEffect(
        foreground,
        saverVisible,
        saverDelay,
        saverShowDuringMusic,
        resumeTick,
        saverItems,
        pickerVisible,
        pinGate,
        activityTick,
        audioTick,
    ) {
        // A gate is a prompt, not an idle surface: the saver must never open
        // behind the PIN pad.
        if (
            !foreground || saverVisible || pickerVisible || pinGate != null ||
            saverDelay == SaverDelay.NEVER
        ) {
            return@LaunchedEffect
        }
        // Nothing to show is better than an empty overlay.
        if (saverItems.isEmpty()) return@LaunchedEffect
        while (true) {
            val musicPlaying = audioManager?.isMusicActive == true
            val idleMs = SystemClock.elapsedRealtime() - lastActivity.longValue
            if (SaverSchedule.isDue(saverDelay, idleMs, musicPlaying, saverShowDuringMusic)) {
                saverClosing = false
                saverVisible = true
                break
            }
            val waitMs = if (musicPlaying && !saverShowDuringMusic) {
                // No deadline while music holds the saver back: the callback
                // ends the wait, the slow check is a safety net.
                SAVER_MUSIC_CHECK_MS
            } else {
                (saverDelay.minutes * 60_000L - idleMs).coerceAtLeast(1L)
            }
            delay(waitMs)
        }
    }

    // Both ends of the gate restart the idle clock: a pad left open for
    // minutes must not hand an overdue timer to the screen behind it, and a
    // saver already playing is dismissed rather than left covered.
    LaunchedEffect(pinGate) {
        if (pinGate != null) {
            saverVisible = false
            saverClosing = false
        }
        markActivity()
    }

    val scope = rememberCoroutineScope()
    // Package broadcasts and the return from Settings can overlap; a stale
    // catalog must not overwrite the newer one when it finishes last.
    var reloadJob by remember { mutableStateOf<Job?>(null) }
    fun reload() {
        reloadJob?.cancel()
        reloadJob = scope.launch {
            apps = orderStore.applyAndRememberOrder(repository.load())
            savedOrder = orderStore.savedOrder()
        }
    }

    LaunchedEffect(orderStore) {
        reload()
    }

    DisposableEffect(context, repository) {
        val filter = packageChangeFilter()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val replacing = intent?.getBooleanExtra(Intent.EXTRA_REPLACING, false) == true
                if (!reloadsHomeFor(intent?.action, replacing)) return
                reload()
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    // Settings edits the saved layout in its own activity. Rebuilding the grid
    // only when that saved value changed keeps a launch and return untouched.
    // The same return may have switched, renamed or removed a profile, so the
    // current profile is re-read first: it owns which layout is on screen.
    LaunchedEffect(resumeTick) {
        val fresh = profileStore.list()
        if (fresh != profiles) profiles = fresh
        val current = profileStore.current()
        // A new profile rebuilds the order store and reloads through its own
        // effect; the old store must not be read for the new layout.
        if (current.id != profile.id) {
            profile = current
            return@LaunchedEffect
        }
        if (current != profile) profile = current
        if (apps == null || orderStore.savedOrder() == savedOrder) return@LaunchedEffect
        reload()
    }

    // "Who's watching?" on an actual TV start, once per process. An app that
    // opens on boot makes the picker a pointless gate, so it is skipped then.
    LaunchedEffect(Unit) {
        if (bootProfilePickerShown) return@LaunchedEffect
        bootProfilePickerShown = true
        if (!SetupProgress.done(context)) return@LaunchedEffect
        if (!StartupPreferences.justBooted()) return@LaunchedEffect
        if (StartupPreferences.target(context).isNotEmpty()) return@LaunchedEffect
        if (profiles.size < 2 || !profileStore.askOnBoot()) return@LaunchedEffect
        delay(700)
        pickerVisible = true
    }

    // The screen came back on. MainActivity only bumps wakeTick for a screen
    // that woke with Home in front, so the picker cannot ambush a return from
    // an app later.
    var handledWake by remember { mutableIntStateOf(wakeTick) }
    LaunchedEffect(wakeTick, foreground) {
        if (wakeTick == handledWake) return@LaunchedEffect
        if (!foreground) return@LaunchedEffect
        handledWake = wakeTick
        val fresh = profileStore.list()
        if (fresh != profiles) profiles = fresh
        if (fresh.size < 2 || !profileStore.askOnWake()) return@LaunchedEffect
        if (
            StartupPreferences.opensOnWake(
                StartupPreferences.target(context),
                StartupPreferences.onWake(context),
            )
        ) {
            return@LaunchedEffect
        }
        pickerVisible = true
    }

    val loaded = apps
    // One filter feeds the grid, the dock, folders and search: the kids
    // profile never sees a tile it may not open, so there is no second
    // "unavailable" path to maintain.
    val visible = loaded?.let { list ->
        if (kidsActive) kidsFilter(list, kidsAllowlist, context.packageName) else list
    }

    // Every way into the launcher's own Settings runs through one gate: the
    // kids profile reaches Settings only through its PIN. Without a PIN there
    // is nothing to check — a misconfigured profile must not trap the child.
    fun openSettingsGated() {
        if (kidsActive && KidsStore(context).hasPin(profile.id)) {
            pinGate = PinGate.SETTINGS
        } else {
            runCatching {
                context.startActivity(
                    Intent(context, SettingsActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    // The Control Center's Wi-Fi, sound, display, Android settings,
    // accessibility and controller tiles all leave for the system Settings
    // app. That app can uninstall the launcher and clear its data, so a kids
    // profile reaches it only after the same PIN as the launcher's Settings.
    fun openSystemGated(intent: Intent) {
        if (kidsActive && KidsStore(context).hasPin(profile.id)) {
            pendingSystemIntent = intent
            pinGate = PinGate.SYSTEM
        } else {
            startSystemIntent(context, intent)
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(LauncherColors.backgroundTop, LauncherColors.backgroundBottom),
                ),
            ),
    ) {
        // The tvOS show transaction pushes the home layer to 0.7 while the
        // saver window grows in; the background gradient stays put.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = homeScale
                    scaleY = homeScale
                    alpha = if (homeBehindSaver) 0f else 1f
                },
        ) {
            when {
                visible == null -> Unit
                visible.isEmpty() -> EmptyState()
                else -> key(profile.id) {
                    HomeScreen(
                        initialItems = visible,
                        resumeTick = resumeTick,
                        reduceMotion = reduceMotion,
                        prefsName = ProfileStore.homePrefsName(profile.id),
                        kidsMode = kidsActive,
                        onLaunch = { app ->
                            // The launcher's own Settings tile reaches onLaunch
                            // from search and folders, which bypass the grid's
                            // zoom path; it is a gated surface, not an app.
                            if (kidsActive && app.isSettingsTile(context.packageName)) {
                                openSettingsGated()
                                false
                            } else if (kidsActive && !KidsMode.allowed(context, app.packageName)) {
                                // Defence in depth: the filter already hid the tile,
                                // but a launch can still be asked for from search
                                // state captured before the filter applied.
                                false
                            } else {
                                launchApp(context, app)
                            }
                        },
                        onOrderChanged = {
                            orderStore.saveOrder(it)
                            savedOrder = orderStore.savedOrder()
                        },
                        onAppInfo = { startSystemIntent(context, appInfoIntent(it.packageName)) },
                        onUninstall = { startSystemIntent(context, uninstallIntent(it.packageName)) },
                        focusTick = focusTick,
                        onUserActivity = { markActivity() },
                        // The Control Center can start the saver on demand, not
                        // only after the idle delay.
                        onStartSaver = {
                            markActivity()
                            saverClosing = false
                            saverVisible = true
                        },
                        onOpenSettings = {
                            openSettingsGated()
                        },
                        onOpenSystem = { intent -> openSystemGated(intent) },
                    )
                }
            }
        }

        // Drawn over the grid so the idle screen keeps the launcher's look and
        // the saver leaves its focus surface in one place.
        if (saverVisible) {
            SaverScreen(
                items = saverItems,
                closing = saverClosing,
                players = saverPlayers,
                onCloseRequested = { saverClosing = true },
                onDismissed = {
                    saverVisible = false
                    saverClosing = false
                    markActivity()
                    focusTick++
                },
            )
        }

        // "Who's watching?" sits above the grid and the saver: while it is up
        // the launcher is a gate, not a home screen. Back keeps whatever was
        // current, so the picker can be dismissed without switching anything.
        if (pickerVisible) {
            ProfilePicker(
                profiles = profiles,
                currentId = profile.id,
                reduceMotion = reduceMotion,
                focusTick = pickerFocusTick,
                onPick = { chosen ->
                    // Leaving the kids profile is gated by its PIN; switching
                    // into kids or between kids profiles is not.
                    if (kidsLeaveNeedsPin(profile, chosen, KidsStore(context).hasPin(profile.id))) {
                        pendingProfileId = chosen.id
                        pinGate = PinGate.PROFILE
                    } else {
                        if (chosen.id != profile.id) {
                            profileStore.setCurrent(chosen.id)
                            profile = profileStore.current()
                        }
                        pickerVisible = false
                        markActivity()
                        focusTick++
                    }
                },
                onDismiss = {
                    pickerVisible = false
                    markActivity()
                    focusTick++
                },
            )
        }

        // The PIN gate covers the whole launcher, the picker included: while it
        // is up nothing behind it can be reached.
        pinGate?.let { gate ->
            PinPadOverlay(
                mode = if (gateRecover) PinPadMode.RECOVER else PinPadMode.VERIFY,
                profileId = profile.id,
                reduceMotion = reduceMotion,
                onPassed = {
                    pinGate = null
                    gateRecover = false
                    when (gate) {
                        PinGate.SETTINGS -> {
                            // The grant lives in this process: an external
                            // intent cannot forge the PIN pass.
                            KidsEntryGrant.issue(profile.id)
                            runCatching {
                                context.startActivity(
                                    Intent(context, SettingsActivity::class.java)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }.onFailure { KidsEntryGrant.clear() }
                        }

                        PinGate.PROFILE -> {
                            pendingProfileId?.let { id -> profileStore.setCurrent(id) }
                            pendingProfileId = null
                            profile = profileStore.current()
                            pickerVisible = false
                            markActivity()
                            focusTick++
                        }

                        PinGate.SYSTEM -> {
                            pendingSystemIntent?.let { intent -> startSystemIntent(context, intent) }
                            pendingSystemIntent = null
                        }
                    }
                },
                // Recovery replaces the PIN and closes the gate without the
                // gated action (Java leaves the child on the kids profile).
                onRecovered = {
                    pinGate = null
                    gateRecover = false
                    pendingProfileId = null
                    pendingSystemIntent = null
                    markActivity()
                    if (pickerVisible) pickerFocusTick++ else focusTick++
                },
                onCancel = {
                    pinGate = null
                    gateRecover = false
                    pendingProfileId = null
                    pendingSystemIntent = null
                    markActivity()
                    if (pickerVisible) pickerFocusTick++ else focusTick++
                },
                // Java's pad offers the recovery word when it is configured;
                // without it the button would lead to a dead end.
                onForgot = if (!gateRecover && KidsStore(context).hasRecovery(profile.id)) {
                    { gateRecover = true }
                } else {
                    null
                },
                // The Settings gate names its surface; the recovering pad must
                // show the stored hint instead.
                subtitle = when {
                    gateRecover -> null
                    gate == PinGate.SETTINGS -> stringResource(R.string.kids_gate_settings)
                    gate == PinGate.SYSTEM -> stringResource(R.string.kids_gate_system)
                    else -> null
                },
            )
        }

        // One invisible pixel of the saver's opening still. Drawing it here
        // uploads the bitmap to the GPU while Home is idle; the saver then
        // finds the texture ready and its entrance starts on the picture.
        warmStill?.let { frame ->
            Image(
                bitmap = frame.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(1.dp)
                    .graphicsLayer { alpha = 0.01f },
            )
        }
    }
}

/**
 * The app menu uses the system's own screens: uninstall is always confirmed by
 * the platform, and App Info is the system settings page for the package.
 */
internal fun appInfoIntent(packageName: String): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

internal fun uninstallIntent(packageName: String): Intent =
    Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private fun startSystemIntent(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        // Some builds have no handler for a package's settings page.
        // A refused admin screen still owes the OEM accessibility fallback;
        // this runs only after the gate authorized the navigation.
        if (intent.action == DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN) {
            SleepControls.requestPermission(context)
        }
    }
}

internal fun packageChangeFilter(): IntentFilter = IntentFilter().apply {
    addAction(Intent.ACTION_PACKAGE_ADDED)
    addAction(Intent.ACTION_PACKAGE_REMOVED)
    addAction(Intent.ACTION_PACKAGE_CHANGED)
    addAction(Intent.ACTION_PACKAGE_REPLACED)
    addDataScheme("package")
}

/**
 * An update sends PACKAGE_REMOVED with EXTRA_REPLACING just before PACKAGE_ADDED.
 * Reloading on the removal drops the tile for a frame and overwrites the saved
 * focus with its clamped neighbour, so the ADDED that follows does the reload
 * instead. Any other action outside the filter is ignored.
 */
internal fun reloadsHomeFor(action: String?, replacing: Boolean): Boolean = when (action) {
    Intent.ACTION_PACKAGE_REMOVED -> !replacing
    Intent.ACTION_PACKAGE_ADDED,
    Intent.ACTION_PACKAGE_CHANGED,
    Intent.ACTION_PACKAGE_REPLACED,
    -> true

    else -> false
}

@Composable
private fun EmptyState() {
    val scale = rememberTvScale()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.empty_title),
                color = Color.White,
                fontSize = scale.sp(TvType.title),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(scale.dp(TvSpace.lg)))
            Text(
                text = stringResource(R.string.empty_hint),
                color = LauncherColors.textSecondary,
                fontSize = scale.sp(TvType.body),
                textAlign = TextAlign.Center,
            )
        }
    }
}
