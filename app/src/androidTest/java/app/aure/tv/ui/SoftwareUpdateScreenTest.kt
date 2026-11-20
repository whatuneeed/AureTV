@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package app.aure.tv.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.aure.tv.R
import app.aure.tv.SoftwareUpdateActivity
import app.aure.tv.data.ProfileStore
import app.aure.tv.update.UpdateStateStore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SoftwareUpdateScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<SoftwareUpdateActivity>()

    @Test
    fun thePageShowsTheInstalledVersionAndDoesNotPretendAConfiguredSourceExists() {
        UpdateStateStore(compose.activity).clearAll()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()

        compose.onNodeWithText(compose.activity.getString(R.string.update_title))
            .assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.update_current))
            .assertIsFocused()
        compose.onNodeWithText(compose.activity.getString(R.string.update_source_missing))
            .assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.update_source_missing_hint))
            .assertIsDisplayed()
    }

    @Test
    fun kidsModeHidesEveryUpdateAction() {
        val store = ProfileStore(compose.activity)
        val before = store.current()
        try {
            store.update(before.copy(kids = true))
            store.setCurrent(before.id)
            compose.activityRule.scenario.recreate()
            compose.waitForIdle()

            compose.onNodeWithText(compose.activity.getString(R.string.update_kids_blocked))
                .assertIsDisplayed()
            compose.onNodeWithText(compose.activity.getString(R.string.update_channel))
                .assertDoesNotExist()
            compose.onNodeWithText(compose.activity.getString(R.string.update_install))
                .assertDoesNotExist()
        } finally {
            store.update(before)
            store.setCurrent(before.id)
        }
    }

    @Test
    fun anUnconfiguredSourceDoesNotOfferDeadChannelOrAutomaticCheckActions() {
        UpdateStateStore(compose.activity).clearAll()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()

        val current = compose.onNodeWithText(compose.activity.getString(R.string.update_current))
        current.assertIsFocused()
        compose.onNodeWithText(compose.activity.getString(R.string.update_channel))
            .assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.update_auto))
            .assertDoesNotExist()

        // The only focus owner on this page is the read-only version row; a
        // stray Down must not move focus onto an invisible/disabled action.
        current.performKeyInput { pressKey(Key.DirectionDown) }
        current.assertIsFocused()
    }
}
