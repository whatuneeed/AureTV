package app.aure.tv.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.aure.tv.ui.PrefsSnapshot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The language choice is stored as a plain preference because the per-app locale
 * API starts at 33 and minSdk is 28. A bad stored value must never reach
 * `Locale.forLanguageTag`, and "system" must leave the context untouched.
 */
@RunWith(AndroidJUnit4::class)
class LocalePreferenceTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var savedPrefs: Map<String, Any?>

    @Before
    fun clearLanguage() {
        savedPrefs = PrefsSnapshot.capture(context)
        context.getSharedPreferences(LauncherPrefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @After
    fun restoreLanguage() {
        PrefsSnapshot.restore(context, savedPrefs)
    }

    @Test
    fun unset_and_unknown_values_mean_system() {
        assertEquals(LocalePreference.SYSTEM, LocalePreference.get(context))

        // set() stores whatever it is given; get() is the gate that normalizes.
        LocalePreference.set(context, "de")
        assertEquals(LocalePreference.SYSTEM, LocalePreference.get(context))
    }

    @Test
    fun stored_language_is_read_back() {
        LocalePreference.set(context, LocalePreference.RUSSIAN)
        assertEquals(LocalePreference.RUSSIAN, LocalePreference.get(context))

        LocalePreference.set(context, LocalePreference.ENGLISH)
        assertEquals(LocalePreference.ENGLISH, LocalePreference.get(context))
    }

    @Test
    fun wrap_applies_the_chosen_language_only_when_not_system() {
        val systemLanguage = context.resources.configuration.locales[0].language
        val untouched = LocalePreference.wrap(context)
        assertEquals(systemLanguage, untouched.resources.configuration.locales[0].language)

        LocalePreference.set(context, LocalePreference.ENGLISH)
        val english = LocalePreference.wrap(context)
        assertEquals("en", english.resources.configuration.locales[0].language)

        LocalePreference.set(context, LocalePreference.RUSSIAN)
        val russian = LocalePreference.wrap(context)
        assertEquals("ru", russian.resources.configuration.locales[0].language)
    }
}
