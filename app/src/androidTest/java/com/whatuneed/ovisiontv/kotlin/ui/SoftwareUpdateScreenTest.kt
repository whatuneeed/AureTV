@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.whatuneed.ovisiontv.kotlin.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.SoftwareUpdateActivity
import com.whatuneed.ovisiontv.kotlin.update.UpdateStateStore
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
    fun dpadCanReachTheChannelAndAutomaticCheckRows() {
        compose.onNodeWithText(compose.activity.getString(R.string.update_current))
            .assertIsFocused()
        compose.onNodeWithText(compose.activity.getString(R.string.update_current))
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText(compose.activity.getString(R.string.update_channel))
            .assertIsFocused()
        compose.onNodeWithText(compose.activity.getString(R.string.update_channel))
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText(compose.activity.getString(R.string.update_auto))
            .assertIsFocused()
    }
}
