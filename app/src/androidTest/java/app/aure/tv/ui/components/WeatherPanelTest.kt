@file:OptIn(ExperimentalTestApi::class)

package app.aure.tv.ui.components

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.aure.tv.ui.OVisionTheme
import app.aure.tv.ui.TvScale
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The forecast is read-only, but it owns the focus while it is up: OK must
 * close it instead of swallowing the press on a dead target (UX-10).
 */
@RunWith(AndroidJUnit4::class)
class WeatherPanelTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setPanel(onClose: () -> Unit) {
        rule.setContent {
            OVisionTheme {
                WeatherPanel(
                    scale = TvScale(1f),
                    data = null,
                    stale = false,
                    reduceMotion = true,
                    onClose = onClose,
                )
            }
        }
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
    }

    @Test
    fun ok_closes_the_panel() {
        var closed = false
        setPanel(onClose = { closed = true })

        rule.onRoot().performKeyInput {
            keyDown(Key.DirectionCenter)
            keyUp(Key.DirectionCenter)
        }
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()

        assertTrue("OK must close the forecast", closed)
    }
}
