package app.aure.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.aure.tv.R
import app.aure.tv.ambient.AmbientClockFormat
import app.aure.tv.ambient.AmbientDim
import app.aure.tv.ambient.AmbientMode
import app.aure.tv.ambient.AmbientSession

@Composable
internal fun ambientModeLabel(mode: AmbientMode): String = stringResource(
    when (mode) {
        AmbientMode.AERIAL -> R.string.saver_mode_aerial
        AmbientMode.CLOCK -> R.string.saver_mode_clock
        AmbientMode.MINIMAL -> R.string.saver_mode_minimal
        AmbientMode.PHOTOS -> R.string.saver_mode_photos
        AmbientMode.REDDIT -> R.string.saver_mode_reddit
    },
)

@Composable
internal fun ambientClockFormatLabel(format: AmbientClockFormat): String = stringResource(
    when (format) {
        AmbientClockFormat.SYSTEM -> R.string.saver_clock_format_system
        AmbientClockFormat.TWELVE -> R.string.saver_clock_format_12
        AmbientClockFormat.TWENTY_FOUR -> R.string.saver_clock_format_24
    },
)

@Composable
internal fun ambientDimLabel(dim: AmbientDim): String = stringResource(
    when (dim) {
        AmbientDim.NEVER -> R.string.saver_dim_never
        AmbientDim.TEN -> R.string.saver_dim_10
        AmbientDim.TWENTY -> R.string.saver_dim_20
        AmbientDim.THIRTY -> R.string.saver_dim_30
    },
)

@Composable
internal fun ambientSessionLabel(session: AmbientSession): String = stringResource(
    when (session) {
        AmbientSession.NEVER -> R.string.saver_session_never
        AmbientSession.THIRTY -> R.string.saver_session_30
        AmbientSession.SIXTY -> R.string.saver_session_60
        AmbientSession.ONE_TWENTY -> R.string.saver_session_120
    },
)
