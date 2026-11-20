package app.aure.tv.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.aure.tv.R
import app.aure.tv.data.LocalePreference
import java.util.Locale
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A language change recomposes; it must not tear down the activity. */
@RunWith(AndroidJUnit4::class)
class AppLocaleTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var savedPrefs: Map<String, Any?>

    @Before
    fun setRussian() {
        savedPrefs = PrefsSnapshot.capture(context)
        LocalePreference.set(context, LocalePreference.RUSSIAN)
        LocaleRuntime.notifyChanged()
    }

    @After
    fun restore() {
        PrefsSnapshot.restore(context, savedPrefs)
        LocaleRuntime.notifyChanged()
    }

    @Test
    fun switchingLanguageUpdatesBothStringResourceAndContextStrings() {
        rule.setContent {
            OVisionTheme {
                ProvideAppLocale {
                    val direct = LocalContext.current.getString(R.string.settings_language)
                    Text(stringResource(R.string.settings_language) + "|" + direct)
                }
            }
        }
        rule.waitForIdle()

        val russian = localized(R.string.settings_language, LocalePreference.RUSSIAN)
        rule.onNodeWithText("$russian|$russian").assertExists()

        LocalePreference.set(context, LocalePreference.ENGLISH)
        LocaleRuntime.notifyChanged()
        rule.waitForIdle()

        val english = localized(R.string.settings_language, LocalePreference.ENGLISH)
        rule.onNodeWithText("$english|$english").assertExists()
    }

    private fun localized(id: Int, language: String): String {
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }
        return context.createConfigurationContext(configuration).resources.getString(id)
    }
}
