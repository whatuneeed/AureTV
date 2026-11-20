package app.aure.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.aure.tv.R
import app.aure.tv.data.SaverTheme

@Composable
internal fun saverThemeLabel(theme: SaverTheme): String = stringResource(
    when (theme) {
        SaverTheme.LANDSCAPES -> R.string.saver_theme_landscapes
        SaverTheme.CITIES -> R.string.saver_theme_cities
        SaverTheme.UNDERWATER -> R.string.saver_theme_underwater
        SaverTheme.EARTH -> R.string.saver_theme_earth
    },
)
