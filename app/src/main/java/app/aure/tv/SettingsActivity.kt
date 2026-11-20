package app.aure.tv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.aure.tv.backup.BackupRecovery
import app.aure.tv.data.Adaptive
import app.aure.tv.data.LocalePreference
import app.aure.tv.data.ProfileStore
import app.aure.tv.input.TvRemoteActivity
import app.aure.tv.ui.BackupRecoverySplash
import app.aure.tv.ui.LocaleRuntime
import app.aure.tv.ui.OVisionTheme
import app.aure.tv.ui.ProvideAppLocale
import app.aure.tv.ui.SettingsScreen
import app.aure.tv.ui.TvCanvas
import kotlinx.coroutines.launch

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
    private val kidsReady = mutableStateOf(false)
    // Setup or another Settings window may change the launcher language while
    // this activity is paused; its wrapped context is fixed at creation.
    private var appliedLanguage: String = LocalePreference.SYSTEM

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocalePreference.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appliedLanguage = LocalePreference.get(this)
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

        refreshEntry()
        setContent {
            OVisionTheme {
                ProvideAppLocale {
                TvCanvas {
                    if (kidsReady.value) {
                        SettingsScreen(
                            onClose = { finish() },
                            resumeTick = resumeTick.intValue,
                            kidsUnlocked = kidsUnlocked.value,
                        )
                    } else {
                        BackupRecoverySplash()
                    }
                }
                }
            }
        }
    }

    // The entry grant names the current profile, so it must wait for an
    // interrupted restore just like every other preference read.
    private fun refreshEntry() {
        if (BackupRecovery.isReady.value) {
            consumeEntry()
        } else {
            lifecycleScope.launch {
                BackupRecovery.await()
                consumeEntry()
            }
        }
    }

    private fun consumeEntry() {
        kidsUnlocked.value = KidsEntryGrant.consume(ProfileStore(this).current().id)
        kidsReady.value = true
    }

    // singleTop warm start: a grant issued while Settings is already up must
    // still be consumed, or the entry gate would stay locked after the PIN.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        refreshEntry()
    }

    override fun onResume() {
        super.onResume()
        // The device row shows the live profile: re-derive it after system
        // screens, thermal status may have changed while we were away.
        Adaptive.install(this)
        // A language change made outside this window must reach the composition
        // without rebuilding the window (AQ-25).
        val language = LocalePreference.get(this)
        if (language != appliedLanguage) {
            appliedLanguage = language
            LocaleRuntime.notifyChanged()
        }
        resumeTick.intValue++
    }
}
