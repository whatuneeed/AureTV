@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.whatuneed.ovisiontv.kotlin.ui.components

import android.view.KeyEvent
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.whatuneed.ovisiontv.kotlin.data.TvApp
import com.whatuneed.ovisiontv.kotlin.ui.OVisionTheme
import com.whatuneed.ovisiontv.kotlin.ui.rememberTvScale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SearchOverlayTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun back_from_results_keeps_the_query_and_a_second_back_closes_search() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val russian = context.resources.configuration.locales[0].language == "ru"
        val label = if (russian) "Фото" else "Alpha"
        val typed = if (russian) "ф" else "a"
        var dismissals = 0
        rule.setContent {
            OVisionTheme {
                SearchOverlay(
                    apps = listOf(
                        TvApp("com.example.photo", "com.example.photo.Main", label, null, null),
                        TvApp("com.example.alpha", "com.example.alpha.Main", "Alpha", null, null),
                    ),
                    scale = rememberTvScale(),
                    reduceMotion = true,
                    onClose = { dismissals++ },
                    onLaunch = { },
                )
            }
        }
        rule.waitForIdle()

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A))
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A))
        rule.waitForIdle()
        rule.onNodeWithText(typed, useUnmergedTree = true).assertExists()

        // Walk up to the first keyboard row; one more Up enters the results.
        repeat(5) {
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
            rule.waitForIdle()
        }

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitForIdle()
        assertEquals(0, dismissals)
        rule.onNodeWithText(typed, useUnmergedTree = true).assertExists()

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitForIdle()
        assertEquals(1, dismissals)
    }
}
