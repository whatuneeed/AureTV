package com.whatuneed.ovisiontv.kotlin

import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.whatuneed.ovisiontv.kotlin.backup.BackupRecovery
import com.whatuneed.ovisiontv.kotlin.data.Adaptive
import com.whatuneed.ovisiontv.kotlin.data.LocalePreference
import com.whatuneed.ovisiontv.kotlin.data.SetupProgress
import com.whatuneed.ovisiontv.kotlin.data.StartupPreferences
import com.whatuneed.ovisiontv.kotlin.input.TvRemoteActivity
import com.whatuneed.ovisiontv.kotlin.ui.BackupRecoverySplash
import com.whatuneed.ovisiontv.kotlin.ui.LauncherRoot
import com.whatuneed.ovisiontv.kotlin.ui.MemoryPressure
import com.whatuneed.ovisiontv.kotlin.ui.OVisionTheme
import com.whatuneed.ovisiontv.kotlin.ui.TvCanvas
import kotlinx.coroutines.launch

// SCREEN_ON and onResume race on some TVs. A wake older than this belongs to
// whatever was on screen then, not to the Home resume that arrives now.
private const val WAKE_FRESH_MS = 2_000L

class MainActivity : TvRemoteActivity() {

    private var appliedLanguage: String = LocalePreference.SYSTEM
    // False only while an interrupted restore is rolling back. The setup and
    // boot checks read prefs, so they wait for the recovery gate too.
    private var bootstrapped by mutableStateOf(false)

    // A launch hands the screen to another app; every resume after it plays the
    // reverse zoom (12_SYSTEM_TRANSITIONS T2).
    private val resumeTick = mutableIntStateOf(0)

    // The saver timer runs only while Home is in front; a launched app or the
    // Settings activity pauses this one and must not be covered by the saver.
    private val foreground = mutableStateOf(false)

    // "Who's watching?" after sleep is a Compose overlay, so the screen wake
    // only bumps this counter. A wake belongs to Home only when Home was the
    // surface on screen: another app's wake must not ambush its return later.
    private val wakeTick = mutableIntStateOf(0)
    private var pendingWakeAt = 0L
    private var resumed = false

    // The accessibility service also receives SCREEN_ON; StartupPreferences
    // drops the repeat, so registering here keeps wakes working without it.
    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // A wake that arrives while the recovery gate is up is not ours yet.
            if (!bootstrapped) return
            // SCREEN_ON and onResume can arrive in either order, so a resumed
            // Home takes the wake now and a paused one keeps it for its resume.
            if (resumed) {
                // "Start with the TV" belongs to a Home wake. If another app
                // was in front, waking it must not hand the screen away.
                StartupPreferences.onScreenOn(this@MainActivity)
                wakeTick.intValue++
            } else {
                pendingWakeAt = SystemClock.elapsedRealtime()
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocalePreference.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appliedLanguage = LocalePreference.get(this)
        // Before the first composition touches a cache: the profile decides how
        // much decoded artwork each cache may hold on this device.
        Adaptive.install(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        // tvOS hands an idle screen to its screensaver (11_SCREENSAVER). Until the
        // launcher owns that surface, holding the screen on would keep an OLED panel
        // lit forever, so the TV's own sleep settings stay in charge.
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // Home is the root surface: BACK must not leave it.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = Unit
            },
        )

        // First run asks the four questions that change something real, then
        // never returns here. A re-run is started from Settings instead.
        // Checked on every creation, not only a cold start: after a crash or
        // force-stop the system restores the home task with its saved state,
        // and a savedInstanceState guard skipped the wizard, leaving an
        // unfinished setup behind a working Home. SetupActivity itself resumes
        // the page the user reached, so launching it again is idempotent.
        // A journal makes the gates wait for the rollback, so the setup check
        // never reads a half-restored preference file.
        if (BackupRecovery.isReady.value) {
            bootstrap()
        } else {
            lifecycleScope.launch {
                BackupRecovery.await()
                bootstrap()
            }
        }

        ContextCompat.registerReceiver(
            this,
            screenOnReceiver,
            IntentFilter(Intent.ACTION_SCREEN_ON),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        setContent {
            OVisionTheme {
                TvCanvas {
                    if (bootstrapped) {
                        LauncherRoot(
                            resumeTick = resumeTick.intValue,
                            foreground = foreground.value,
                            wakeTick = wakeTick.intValue,
                        )
                    } else {
                        BackupRecoverySplash()
                    }
                }
            }
        }
    }

    private fun bootstrap() {
        if (bootstrapped) return
        if (!SetupProgress.done(this)) {
            startActivity(Intent(this, SetupActivity::class.java))
        }
        // "Start with the TV" only on an actual boot, not on every Home restart.
        if (StartupPreferences.justBooted()) StartupPreferences.onBoot(this)
        bootstrapped = true
    }

    override fun onResume() {
        super.onResume()
        foreground.value = true
        resumed = true
        // A wake that arrived while the activity was paused is only ours when
        // it is this resume: an old SCREEN_ON from another app is dropped.
        if (pendingWakeAt != 0L) {
            val fresh = SystemClock.elapsedRealtime() - pendingWakeAt <= WAKE_FRESH_MS
            pendingWakeAt = 0L
            if (fresh) wakeTick.intValue++
        }
        // Settings is a separate activity; coming back may carry a new language.
        if (LocalePreference.get(this) != appliedLanguage) recreate()
        resumeTick.intValue++
    }

    override fun onPause() {
        foreground.value = false
        resumed = false
        super.onPause()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        MemoryPressure.trim(level)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        MemoryPressure.trim(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOnReceiver) }
        super.onDestroy()
    }
}
