package app.aure.tv

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.aure.tv.data.LocalePreference
import app.aure.tv.input.TvRemoteActivity
import app.aure.tv.ui.BackupRecoveryGate
import app.aure.tv.ui.OVisionTheme
import app.aure.tv.ui.SoftwareUpdateScreen
import app.aure.tv.ui.TvCanvas

/** A sub-screen of Settings; no intent data, not exported. */
class SoftwareUpdateActivity : TvRemoteActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocalePreference.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            OVisionTheme {
                TvCanvas {
                    BackupRecoveryGate {
                        SoftwareUpdateScreen(onClose = { finish() })
                    }
                }
            }
        }
    }
}
