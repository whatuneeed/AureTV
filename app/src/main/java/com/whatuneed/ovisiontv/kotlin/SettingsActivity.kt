package com.whatuneed.ovisiontv.kotlin

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.whatuneed.ovisiontv.kotlin.data.Adaptive
import com.whatuneed.ovisiontv.kotlin.data.LocalePreference
import com.whatuneed.ovisiontv.kotlin.input.TvRemoteActivity
import com.whatuneed.ovisiontv.kotlin.ui.OVisionTheme
import com.whatuneed.ovisiontv.kotlin.ui.SettingsScreen
import com.whatuneed.ovisiontv.kotlin.ui.TvCanvas

class SettingsActivity : TvRemoteActivity() {

    companion object {
        // Set by the launcher's kids PIN gate: the child passed the check and
        // may see Settings. A direct start (system deep link, recents) without
        // the extra re-opens the gate inside SettingsScreen.
        const val EXTRA_KIDS_OK = "kids_ok"
    }

    // System pages open outside this activity; the Accessibility switch and
    // the default Home are only true again on resume.
    private val resumeTick = mutableIntStateOf(0)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocalePreference.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Settings is reachable in its own task (deep link, recents); its
        // preview caches must see the same budgets as Home.
        Adaptive.install(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Same rule as Home: an idle TV must reach the screen saver and sleep.
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            OVisionTheme {
                TvCanvas {
                    SettingsScreen(
                        onClose = { finish() },
                        resumeTick = resumeTick.intValue,
                        kidsUnlocked = intent.getBooleanExtra(EXTRA_KIDS_OK, false),
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The device row shows the live profile: re-derive it after system
        // screens, thermal status may have changed while we were away.
        Adaptive.install(this)
        resumeTick.intValue++
    }
}
