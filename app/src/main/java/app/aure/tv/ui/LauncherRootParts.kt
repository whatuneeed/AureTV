package app.aure.tv.ui

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aure.tv.R
import app.aure.tv.data.SleepControls

/**
 * The app menu uses the system's own screens: uninstall is always confirmed by
 * the platform, and App Info is the system settings page for the package.
 */
internal fun appInfoIntent(packageName: String): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

internal fun uninstallIntent(packageName: String): Intent =
    Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

internal fun startSystemIntent(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        // Some builds have no handler for a package's settings page.
        // A refused admin screen still owes the OEM accessibility fallback;
        // this runs only after the gate authorized the navigation.
        if (intent.action == DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN) {
            SleepControls.requestPermission(context)
        } else {
            Toast.makeText(context, R.string.settings_system_unavailable, Toast.LENGTH_SHORT).show()
        }
    }
}

internal fun packageChangeFilter(): IntentFilter = IntentFilter().apply {
    addAction(Intent.ACTION_PACKAGE_ADDED)
    addAction(Intent.ACTION_PACKAGE_REMOVED)
    addAction(Intent.ACTION_PACKAGE_CHANGED)
    addAction(Intent.ACTION_PACKAGE_REPLACED)
    addDataScheme("package")
}

/**
 * An update sends PACKAGE_REMOVED with EXTRA_REPLACING just before PACKAGE_ADDED.
 * Reloading on the removal drops the tile for a frame and overwrites the saved
 * focus with its clamped neighbour, so the ADDED that follows does the reload
 * instead. Any other action outside the filter is ignored.
 */
internal fun reloadsHomeFor(action: String?, replacing: Boolean): Boolean = when (action) {
    Intent.ACTION_PACKAGE_REMOVED -> !replacing
    Intent.ACTION_PACKAGE_ADDED,
    Intent.ACTION_PACKAGE_CHANGED,
    Intent.ACTION_PACKAGE_REPLACED,
    -> true

    else -> false
}

/**
 * The first catalogue scan failed. It is deliberately not the same surface as
 * "no apps installed": the user needs a real retry instead of an unplugged TV
 * look, and the retry goes through the same generation-guarded reload path.
 */
@Composable
internal fun HomeLoadError(onRetry: () -> Unit) {
    val scale = rememberTvScale()
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // The button is attached on a later frame on cold start; retry on the
        // frame clock rather than hiding the problem behind a fixed delay.
        repeat(3) {
            if (runCatching { requester.requestFocus() }.isSuccess) return@LaunchedEffect
            withFrameNanos { }
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.home_load_failed),
                color = Color.White,
                fontSize = scale.sp(TvType.title),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(scale.dp(TvSpace.lg)))
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .width(scale.dp(220f))
                    .focusRequester(requester),
            ) {
                Text(text = stringResource(R.string.home_load_retry), fontSize = scale.sp(TvType.body))
            }
        }
    }
}

@Composable
internal fun EmptyState() {
    val scale = rememberTvScale()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.empty_title),
                color = Color.White,
                fontSize = scale.sp(TvType.title),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(scale.dp(TvSpace.lg)))
            Text(
                text = stringResource(R.string.empty_hint),
                color = LauncherColors.textSecondary,
                fontSize = scale.sp(TvType.body),
                textAlign = TextAlign.Center,
            )
        }
    }
}
