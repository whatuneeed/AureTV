package app.aure.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.aure.tv.R
import app.aure.tv.ambient.AmbientMode
import app.aure.tv.data.AccessibilityStatus
import app.aure.tv.data.LocalePreference
import app.aure.tv.data.Profile
import app.aure.tv.data.ProfileCatalog
import app.aure.tv.data.SaverDelay

@Composable
internal fun setupWelcomePage(onNext: () -> Unit): SetupPage = SetupPage(
    title = stringResource(R.string.setup_welcome_title),
    subtitle = stringResource(R.string.setup_welcome_hint),
    primary = "",
    onPrimary = onNext,
    pressAnywhere = true,
    centered = true,
    subtitleFont = GREETING_HINT_FONT,
    rows = emptyList(),
)

@Composable
internal fun setupPersonalPage(
    language: String,
    profile: Profile,
    systemLanguage: String,
    onChooseLanguage: (String) -> Unit,
    onOpenAvatar: () -> Unit,
    onOpenName: () -> Unit,
    onNext: () -> Unit,
): SetupPage = SetupPage(
    // Language and identity are one decision: both describe the person using
    // this TV and both are edited in the same two launcher surfaces.
    title = stringResource(R.string.setup_personal_title),
    subtitle = stringResource(R.string.setup_personal_hint),
    primary = stringResource(R.string.setup_continue),
    onPrimary = onNext,
    rows = listOf(
        SetupRow(
            title = stringResource(R.string.setup_language_system),
            detail = systemLanguage,
            kind = RowKind.RADIO,
            checked = language == LocalePreference.SYSTEM,
            action = { onChooseLanguage(LocalePreference.SYSTEM) },
        ),
        SetupRow(
            title = stringResource(R.string.settings_language_russian),
            detail = null,
            kind = RowKind.RADIO,
            checked = language == LocalePreference.RUSSIAN,
            action = { onChooseLanguage(LocalePreference.RUSSIAN) },
        ),
        SetupRow(
            title = stringResource(R.string.settings_language_english),
            detail = null,
            kind = RowKind.RADIO,
            checked = language == LocalePreference.ENGLISH,
            action = { onChooseLanguage(LocalePreference.ENGLISH) },
        ),
        SetupRow(
            title = stringResource(R.string.profile_avatar),
            detail = null,
            kind = RowKind.ACTION,
            value = profile.emoji.ifEmpty { ProfileCatalog.initials(profile.name) }
                .ifEmpty { stringResource(R.string.profile_avatar_placeholder) },
            action = onOpenAvatar,
        ),
        SetupRow(
            title = stringResource(R.string.profile_name),
            detail = null,
            kind = RowKind.ACTION,
            value = profile.name,
            action = onOpenName,
        ),
    ),
    initialFocus = when (language) {
        LocalePreference.RUSSIAN -> 1
        LocalePreference.ENGLISH -> 2
        else -> 0
    },
)

@Composable
internal fun setupHomePage(
    recommendations: Boolean,
    continueWatching: Boolean,
    weatherEnabled: Boolean,
    weatherManual: Boolean,
    weatherPlace: String,
    onToggleRecommendations: () -> Unit,
    onToggleContinueWatching: () -> Unit,
    onWeatherAutomatic: () -> Unit,
    onWeatherCity: () -> Unit,
    onWeatherOff: () -> Unit,
    onNext: () -> Unit,
): SetupPage = SetupPage(
    // One content question: what appears above the tiles. The weather radio
    // group follows the two shelf switches.
    title = stringResource(R.string.setup_home_weather_title),
    subtitle = stringResource(R.string.setup_home_weather_hint),
    primary = stringResource(R.string.setup_continue),
    onPrimary = onNext,
    rows = listOf(
        SetupRow(
            title = stringResource(R.string.settings_recommendations),
            detail = stringResource(R.string.setup_recommendations_detail),
            kind = RowKind.TOGGLE,
            checked = recommendations,
            action = onToggleRecommendations,
        ),
        SetupRow(
            title = stringResource(R.string.settings_continue_watching),
            detail = stringResource(R.string.setup_continue_detail),
            kind = RowKind.TOGGLE,
            checked = continueWatching,
            action = onToggleContinueWatching,
        ),
        SetupRow(
            title = stringResource(R.string.setup_weather_auto),
            detail = null,
            kind = RowKind.RADIO,
            // The two shelf switches and the three weather choices are
            // different questions; the hairline keeps them from reading as one
            // five-row list.
            dividerBefore = true,
            checked = weatherEnabled && !weatherManual,
            action = onWeatherAutomatic,
        ),
        SetupRow(
            title = stringResource(R.string.setup_weather_city),
            detail = if (weatherManual) weatherPlace else null,
            kind = RowKind.RADIO,
            checked = weatherEnabled && weatherManual,
            action = onWeatherCity,
        ),
        SetupRow(
            title = stringResource(R.string.setup_weather_off),
            detail = null,
            kind = RowKind.RADIO,
            checked = !weatherEnabled,
            action = onWeatherOff,
        ),
    ),
)

@Composable
internal fun setupSaverPage(
    ambientMode: AmbientMode,
    delay: SaverDelay,
    saverMusic: Boolean,
    onSelectMode: (AmbientMode) -> Unit,
    onCycleDelay: () -> Unit,
    onToggleMusic: () -> Unit,
    onNext: () -> Unit,
): SetupPage = SetupPage(
    // One ambient page instead of "when" plus "themes" (see SetupScreen).
    title = stringResource(R.string.setup_saver_title),
    // Photos and Reddit are configured in Settings, but a re-run must still
    // name the current mode instead of showing three unchecked choices.
    subtitle = if (ambientMode in SETUP_AMBIENT_MODES) {
        stringResource(R.string.setup_saver_hint)
    } else {
        stringResource(R.string.setup_saver_unsupported_hint, ambientModeLabel(ambientMode))
    },
    primary = stringResource(R.string.setup_continue),
    onPrimary = onNext,
    rows = SETUP_AMBIENT_MODES.map { mode ->
        SetupRow(
            title = ambientModeLabel(mode),
            detail = null,
            kind = RowKind.RADIO,
            checked = ambientMode == mode,
            action = { onSelectMode(mode) },
        )
    } + listOf(
        SetupRow(
            title = stringResource(R.string.setup_saver_delay),
            detail = stringResource(R.string.setup_saver_delay_hint),
            kind = RowKind.ACTION,
            value = delayLabel(delay),
            action = onCycleDelay,
        ),
        SetupRow(
            title = stringResource(R.string.saver_during_music),
            detail = stringResource(R.string.setup_saver_music_hint),
            kind = RowKind.TOGGLE,
            checked = saverMusic,
            action = onToggleMusic,
        ),
    ),
    initialFocus = SETUP_AMBIENT_MODES.indexOf(ambientMode).coerceAtLeast(0),
)

@Composable
internal fun setupAccessPage(
    defaultHome: Boolean,
    accessibility: AccessibilityStatus.State,
    listingsGranted: Boolean,
    listingsPermanent: Boolean,
    onOpenHome: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onRequestAccess: () -> Unit,
    onNext: () -> Unit,
): SetupPage = SetupPage(
    title = stringResource(R.string.setup_access_title),
    subtitle = stringResource(R.string.setup_access_hint),
    primary = stringResource(R.string.setup_continue),
    onPrimary = onNext,
    // Three optional rows do not fill the canvas; centering them keeps the
    // page from hanging from the top edge.
    centered = true,
    rows = listOf(
        SetupRow(
            title = stringResource(R.string.setup_home_app),
            detail = stringResource(R.string.setup_home_app_detail),
            kind = RowKind.ACTION,
            ok = defaultHome,
            value = stringResource(
                if (defaultHome) R.string.setup_state_done else R.string.setup_home_setup,
            ),
            action = onOpenHome,
        ),
        SetupRow(
            title = stringResource(R.string.setup_accessibility),
            detail = stringResource(R.string.setup_accessibility_detail),
            kind = RowKind.ACTION,
            ok = accessibility == AccessibilityStatus.State.CONNECTED,
            value = stringResource(
                when (accessibility) {
                    AccessibilityStatus.State.CONNECTED -> R.string.setup_accessibility_on
                    AccessibilityStatus.State.STALE -> R.string.setup_accessibility_stale
                    AccessibilityStatus.State.OFF -> R.string.setup_allow_action
                },
            ),
            action = onOpenAccessibility,
        ),
        SetupRow(
            title = stringResource(R.string.settings_listings_access),
            detail = stringResource(
                if (listingsPermanent) {
                    R.string.setup_access_permanent
                } else {
                    R.string.setup_access_detail
                },
            ),
            kind = RowKind.ACTION,
            ok = listingsGranted,
            value = stringResource(
                when {
                    listingsGranted -> R.string.settings_listings_granted
                    listingsPermanent -> R.string.settings_listings_denied
                    else -> R.string.setup_allow_action
                },
            ),
            action = onRequestAccess,
        ),
    ),
)

@Composable
internal fun setupFinishPage(
    summary: String,
    rerun: Boolean,
    onFinish: () -> Unit,
): SetupPage = SetupPage(
    title = stringResource(R.string.setup_done_title),
    subtitle = summary,
    primary = stringResource(
        if (rerun) R.string.setup_close else R.string.setup_open_home,
    ),
    onPrimary = onFinish,
    centered = true,
    rows = emptyList(),
)
