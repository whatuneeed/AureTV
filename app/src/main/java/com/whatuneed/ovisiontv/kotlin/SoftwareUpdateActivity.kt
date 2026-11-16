package com.whatuneed.ovisiontv.kotlin

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.whatuneed.ovisiontv.kotlin.data.LocalePreference
import com.whatuneed.ovisiontv.kotlin.input.TvRemoteActivity
import com.whatuneed.ovisiontv.kotlin.ui.OVisionTheme
import com.whatuneed.ovisiontv.kotlin.ui.SoftwareUpdateScreen
import com.whatuneed.ovisiontv.kotlin.ui.TvCanvas

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
                    SoftwareUpdateScreen(onClose = { finish() })
                }
            }
        }
    }
}
