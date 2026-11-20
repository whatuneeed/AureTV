package app.aure.tv.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import app.aure.tv.data.LocalePreference
import java.util.Locale

/**
 * A language change must not recreate the activity. Recreating tears down the
 * window surface, so the wizard flashed black between choosing a language and
 * drawing the same page again.
 *
 * The localized context wraps the activity instead of changing the process
 * resources: `context.getString` and Compose `stringResource` see the chosen
 * language, while the activity and its window stay alive.
 */
internal object LocaleRuntime {
    var version by mutableIntStateOf(0)
        private set

    fun notifyChanged() {
        version++
    }
}

private class LocalizedContext(
    base: Context,
    private val localized: Resources,
) : ContextWrapper(base) {
    override fun getResources(): Resources = localized
}

@Composable
internal fun ProvideAppLocale(content: @Composable () -> Unit) {
    val tick = LocaleRuntime.version
    val context = LocalContext.current
    val language = LocalePreference.get(context)
    val currentConfiguration = LocalConfiguration.current
    val configuration = remember(language, tick) {
        Configuration(currentConfiguration).apply {
            if (language == LocalePreference.SYSTEM) {
                // The application context is not wrapped by LocalePreference,
                // so it still carries the device locale.
                setLocales(context.applicationContext.resources.configuration.locales)
            } else {
                setLocales(LocaleList(Locale.forLanguageTag(language)))
            }
        }
    }
    val localizedContext = remember(language) {
        val resources = context.createConfigurationContext(configuration).resources
        LocalizedContext(context, resources)
    }
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides configuration,
    ) {
        content()
    }
}
