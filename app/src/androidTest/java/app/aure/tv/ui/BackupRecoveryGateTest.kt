package app.aure.tv.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import app.aure.tv.R
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class BackupRecoveryGateTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun contentStaysHiddenUntilRecoveryCompletes() {
        val ready = MutableStateFlow(false)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        rule.setContent {
            OVisionTheme {
                BackupRecoveryGate(ready = ready) {
                    Text("content-ready")
                }
            }
        }

        rule.onNodeWithText(context.getString(R.string.backup_recovery_wait)).assertExists()
        rule.onNodeWithText("content-ready").assertDoesNotExist()

        ready.value = true
        rule.waitForIdle()

        rule.onNodeWithText("content-ready").assertExists()
        rule.onNodeWithText(context.getString(R.string.backup_recovery_wait)).assertDoesNotExist()
    }
}
