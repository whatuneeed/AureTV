package com.whatuneed.ovisiontv.kotlin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.whatuneed.ovisiontv.kotlin.data.Adaptive
import com.whatuneed.ovisiontv.kotlin.data.LocalePreference
import com.whatuneed.ovisiontv.kotlin.data.ProfileStore
import com.whatuneed.ovisiontv.kotlin.input.TvRemoteActivity
import com.whatuneed.ovisiontv.kotlin.ui.OVisionTheme
import com.whatuneed.ovisiontv.kotlin.ui.SettingsScreen
import com.whatuneed.ovisiontv.kotlin.ui.TvCanvas

// One-shot in-process token for the launcher's PIN pass: the activity is
// exported, so an intent extra any app can send must never unlock the gate.
internal object KidsEntryGrant {
    @Volatile
    private var profileId: String? = null

    fun issue(id: String) {
        profileId = id
    }

    fun consume(id: String): Boolean {
        val granted = profileId == id
        profileId = null
        return granted
    }

    fun clear() {
        profileId = null
    }
}

class SettingsActivity : TvRemoteActivity() {

    // System pages open outside this activity; the Accessibility switch and
    // the default Home are only true again on resume.
    private val resumeTick = mutableIntStateOf(0)
    private val kidsUnlocked = mutableStateOf(false)

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

        kidsUnlocked.value = KidsEntryGrant.consume(ProfileStore(this).current().id)
        setContent {
            OVisionTheme {
                TvCanvas {
                    SettingsScreen(
                        onClose = { finish() },
                        resumeTick = resumeTick.intValue,
                        kidsUnlocked = kidsUnlocked.value,
                    )
                }
            }
        }
    }

    // singleTop warm start: a grant issued while Settings is already up must
    // still be consumed, or the entry gate would stay locked after the PIN.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        kidsUnlocked.value = KidsEntryGrant.consume(ProfileStore(this).current().id)
    }

    override fun onResume() {
        super.onResume()
        // The device row shows the live profile: re-derive it after system
        // screens, thermal status may have changed while we were away.
        Adaptive.install(this)
        resumeTick.intValue++
    }
}
