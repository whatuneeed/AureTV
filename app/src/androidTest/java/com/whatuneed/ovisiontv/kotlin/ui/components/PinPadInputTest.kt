package com.whatuneed.ovisiontv.kotlin.ui.components

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.whatuneed.ovisiontv.kotlin.data.KidsStore
import com.whatuneed.ovisiontv.kotlin.ui.OVisionTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PinPadInputTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val store = KidsStore(InstrumentationRegistry.getInstrumentation().targetContext)
    private val id = "input-test-${UUID.randomUUID()}"

    @After
    fun cleanup() { store.clearProfile(id) }

    @Test
    fun back_cancels_during_pin_verification() {
        runBlocking { store.setPin(id, "1111") }
        var cancelled = 0
        var passed = 0
        rule.setContent {
            OVisionTheme {
                PinPadOverlay(PinPadMode.VERIFY, id, true,
                    onPassed = { passed++ }, onCancel = { cancelled++ })
            }
        }
        repeat(3) {
            rule.onNodeWithContentDescription("1").performSemanticsAction(SemanticsActions.OnClick)
        }
        rule.runOnIdle {
            val now = android.os.SystemClock.uptimeMillis()
            fun send(action: Int, code: Int) {
                rule.activity.dispatchKeyEvent(KeyEvent(now, now, action, code, 0))
            }
            send(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)
            send(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)
            send(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)
            send(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)
            assertEquals("BACK must remain available while hashing", 1, cancelled)
        }
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("pin-pad-ready").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals("cancelled verification must not pass", 0, passed)
    }

    @Test
    fun key_up_during_hashing_allows_the_next_real_ok() {
        runBlocking { store.setPin(id, "1234") }
        val prefs = InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences(KidsStore.FILE, android.content.Context.MODE_PRIVATE)
        rule.setContent {
            OVisionTheme {
                PinPadOverlay(PinPadMode.VERIFY, id, true, onPassed = {}, onCancel = {})
            }
        }
        val time = android.os.SystemClock.uptimeMillis()
        repeat(2) { attempt ->
            repeat(3) {
                rule.onNodeWithContentDescription("1").performSemanticsAction(SemanticsActions.OnClick)
            }
            rule.runOnIdle {
                val at = time + attempt * 50
                rule.activity.dispatchKeyEvent(KeyEvent(at, at, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0))
                rule.activity.dispatchKeyEvent(KeyEvent(at, at, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0))
            }
            rule.waitUntil(10_000) {
                prefs.getInt("fail_$id", 0) == attempt + 1 &&
                    rule.onAllNodesWithTag("pin-pad-ready").fetchSemanticsNodes().isNotEmpty()
            }
        }
        assertEquals(2, prefs.getInt("fail_$id", 0))
    }
}
