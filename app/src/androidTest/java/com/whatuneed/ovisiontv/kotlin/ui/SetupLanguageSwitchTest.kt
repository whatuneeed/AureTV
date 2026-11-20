@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.whatuneed.ovisiontv.kotlin.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.SetupActivity
import com.whatuneed.ovisiontv.kotlin.data.LocalePreference
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

/** The wizard must relabel in place, not recreate its window. */
@RunWith(AndroidJUnit4::class)
class SetupLanguageSwitchTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    private val prefs = object : TestRule {
        override fun apply(base: Statement, description: org.junit.runner.Description) =
            object : Statement() {
                override fun evaluate() {
                    val saved = PrefsSnapshot.capture(context)
                    val setup = PrefsSnapshot.captureSetup(context)
                    PrefsSnapshot.clearSetup(context)
                    LocalePreference.set(context, LocalePreference.SYSTEM)
                    try {
                        base.evaluate()
                    } finally {
                        PrefsSnapshot.restore(context, saved)
                        PrefsSnapshot.restoreSetup(context, setup)
                        LocaleRuntime.notifyChanged()
                    }
                }
            }
    }

    private val compose = createAndroidComposeRule<SetupActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(prefs).around(compose)

    @Test
    fun choosingRussianRelabelsTheSameActivity() {
        compose.waitForIdle()
        val activity = compose.activity

        press(Key.DirectionCenter) // welcome -> language + profile
        press(Key.DirectionDown)   // system -> Russian
        press(Key.DirectionCenter)

        assertEquals(LocalePreference.RUSSIAN, LocalePreference.get(context))
        assertSame(activity, compose.activity)
        compose.onNodeWithText(localized(R.string.setup_personal_title, "ru"))
            .assertExists()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput {
            keyDown(key)
            keyUp(key)
        }
        compose.waitForIdle()
    }

    private fun localized(id: Int, language: String): String {
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }
        return context.createConfigurationContext(configuration).resources.getString(id)
    }
}
