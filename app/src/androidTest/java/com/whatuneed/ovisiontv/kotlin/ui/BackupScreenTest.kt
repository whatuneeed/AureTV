@file:OptIn(ExperimentalTestApi::class)

package com.whatuneed.ovisiontv.kotlin.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.whatuneed.ovisiontv.kotlin.BackupActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<BackupActivity>()

    @Test
    fun theCreatePageIsReachableAndKeepsDpadFocusOnTheNextRow() {
        compose.onNodeWithText("Create a backup").assertIsFocused()
        compose.onNodeWithText("Create a backup").performClick()

        compose.onNodeWithText("Profiles and home layout").assertIsFocused()
        compose.onNodeWithText("Profiles and home layout").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        compose.onNodeWithText("Home layout and folders").assertIsFocused()
    }

    @Test
    fun thePasswordFieldOwnsInitialFocusAndRejectsAShortPassword() {
        compose.onNodeWithText("Create a backup").performClick()
        compose.onNodeWithTag("backup_rows")
            .performScrollToNode(hasText("Choose file and export"))
        compose.onNodeWithText("Choose file and export").performClick()

        compose.onNodeWithTag("backup_password").assertIsFocused()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Use at least 8 characters").assertIsDisplayed()
    }

    @Test
    fun theBottomActionsRemainReachableAfterScrolling() {
        compose.onNodeWithText("Create a backup").performClick()

        compose.onNodeWithTag("backup_rows")
            .performScrollToNode(hasText("Choose file and export"))
        compose.onNodeWithText("Choose file and export").assertIsDisplayed()
    }
}
