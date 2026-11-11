package com.whatuneed.ovisiontv.kotlin

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.whatuneed.ovisiontv.kotlin.data.LocalePreference
import com.whatuneed.ovisiontv.kotlin.input.TvRemoteActivity
import com.whatuneed.ovisiontv.kotlin.ui.OVisionTheme
import com.whatuneed.ovisiontv.kotlin.ui.SetupScreen
import com.whatuneed.ovisiontv.kotlin.ui.TvCanvas

/**
 * First run of the launcher, and the same screens again from Settings.
 *
 * It is a separate activity because Home must not be rebuilt under the wizard:
 * the language row recreates this activity, and finishing simply uncovers
 * whatever started it (Home on first run, Settings on a re-run).
 */
class SetupActivity : TvRemoteActivity() {

    private var appliedLanguage: String = LocalePreference.SYSTEM

    // The system Home picker and App info open outside this activity; the
    // default-Home and permission values are only true again on resume.
    private val resumeTick = mutableIntStateOf(0)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocalePreference.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appliedLanguage = LocalePreference.get(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            OVisionTheme {
                TvCanvas {
                    SetupScreen(
                        resumeTick = resumeTick.intValue,
                        onLanguageChanged = { language ->
                            LocalePreference.set(this, language)
                            recreate()
                        },
                        onFinish = { finish() },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (LocalePreference.get(this) != appliedLanguage) recreate()
        resumeTick.intValue++
    }
}
