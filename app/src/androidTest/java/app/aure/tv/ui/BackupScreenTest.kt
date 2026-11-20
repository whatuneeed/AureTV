@file:OptIn(ExperimentalTestApi::class)

package app.aure.tv.ui

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
import app.aure.tv.BackupActivity
import app.aure.tv.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<BackupActivity>()

    @Test
    fun theCreatePageIsReachableAndKeepsDpadFocusOnTheNextRow() {
        val create = compose.activity.getString(R.string.backup_create)
        compose.onNodeWithText(create).assertIsFocused()
        compose.onNodeWithText(create).performClick()

        val profiles = compose.activity.getString(R.string.backup_category_profiles)
        val home = compose.activity.getString(R.string.backup_category_home)
        compose.onNodeWithText(profiles).assertIsFocused()
        compose.onNodeWithText(profiles).performKeyInput {
            pressKey(Key.DirectionDown)
        }
        compose.onNodeWithText(home).assertIsFocused()
    }

    @Test
    fun thePasswordFieldOwnsInitialFocusAndRejectsAShortPassword() {
        compose.onNodeWithText(compose.activity.getString(R.string.backup_create)).performClick()
        val export = compose.activity.getString(R.string.backup_export_now)
        compose.onNodeWithTag("backup_rows")
            .performScrollToNode(hasText(export))
        compose.onNodeWithText(export).performClick()

        compose.onNodeWithTag("backup_password").assertIsFocused()
        compose.onNodeWithText(compose.activity.getString(R.string.backup_password_continue))
            .performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.backup_password_too_short))
            .assertIsDisplayed()
    }

    @Test
    fun theBottomActionsRemainReachableAfterScrolling() {
        compose.onNodeWithText(compose.activity.getString(R.string.backup_create)).performClick()

        val export = compose.activity.getString(R.string.backup_export_now)
        compose.onNodeWithTag("backup_rows")
            .performScrollToNode(hasText(export))
        compose.onNodeWithText(export).assertIsDisplayed()
    }
}
