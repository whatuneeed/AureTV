@file:OptIn(ExperimentalTestApi::class)

package com.whatuneed.ovisiontv.kotlin.ui

import android.content.Context
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.data.AccessibilityStatus
import com.whatuneed.ovisiontv.kotlin.data.ContentPreferences
import com.whatuneed.ovisiontv.kotlin.data.DeviceFactsReader
import com.whatuneed.ovisiontv.kotlin.data.LocalePreference
import com.whatuneed.ovisiontv.kotlin.data.ProfileCatalog
import com.whatuneed.ovisiontv.kotlin.data.ProfileStore
import com.whatuneed.ovisiontv.kotlin.data.SetupProgress
import com.whatuneed.ovisiontv.kotlin.data.WeatherPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the wizard with the remote, not with clicks: every page is reached by
 * OK (the welcome page answers OK anywhere, as the tvOS TVSetup "Hello" does),
 * Back is the only way to the previous page, and the finish page is the only
 * place that marks setup done (PHASE 26). Page order follows the dissected
 * TVSetup.app with the Java wizard's profile question after language and the
 * weather page after Home: welcome, language, profile, home, weather, saver,
 * saver themes, access, finish — nine pages, so a step here is a page index.
 */
@RunWith(AndroidJUnit4::class)
class SetupScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var savedPrefs: Map<String, Any?>
    private lateinit var savedSetup: Map<String, Any?>
    private lateinit var savedProfiles: Map<String, Any?>

    @Before
    fun resetSetup() {
        savedPrefs = PrefsSnapshot.capture(context)
        savedSetup = PrefsSnapshot.captureSetup(context)
        savedProfiles = PrefsSnapshot.captureProfiles(context)
        PrefsSnapshot.clearSetup(context)
        LocalePreference.set(context, LocalePreference.SYSTEM)
    }

    @After
    fun restoreSetup() {
        PrefsSnapshot.restore(context, savedPrefs)
        PrefsSnapshot.restoreSetup(context, savedSetup)
        PrefsSnapshot.restoreProfiles(context, savedProfiles)
    }

    private fun setSetup(
        startStep: Int? = null,
        onLanguageChanged: (String) -> Unit = {},
        onFinish: () -> Unit = {},
    ) {
        if (startStep != null) SetupProgress.saveStep(context, startStep)
        rule.setContent {
            OVisionTheme {
                SetupScreen(
                    resumeTick = 0,
                    onLanguageChanged = onLanguageChanged,
                    onFinish = onFinish,
                )
            }
        }
        rule.waitForIdle()
    }

    private fun title(id: Int): String = context.getString(id)

    private fun hasDescription(text: String): Boolean =
        rule.onAllNodesWithContentDescription(text, substring = true)
            .fetchSemanticsNodes().isNotEmpty()

    private fun press(key: Key) {
        rule.onRoot().performKeyInput {
            keyDown(key)
            keyUp(key)
        }
        rule.waitForIdle()
    }

    private fun press(key: Key, times: Int) = repeat(times) { press(key) }

    /**
     * The profile page edits whatever the store holds, and the store is the
     * real file the viewer has: a test must not depend on how many profiles an
     * earlier run left behind. An empty file is repaired into the launcher's
     * single default profile, so the grid opens on the first, free picture.
     */
    private fun seedSingleProfile() {
        context.getSharedPreferences(ProfileStore.FILE, Context.MODE_PRIVATE)
            .edit().clear().commit()
        ProfileStore(context).list()
    }

    @Test
    fun firstRunOpensTheWelcomePage() {
        setSetup()

        // The greeting cycle starts on the localized word; its semantics carry
        // the greeting as a description so the tour never re-announces itself.
        assertTrue(hasDescription(title(R.string.setup_hello)))
        rule.onNodeWithText(title(R.string.setup_welcome_hint)).assertExists()
    }

    @Test
    fun okOnTheWelcomePageMovesToTheLanguagePage() {
        setSetup()

        // Arrows must not scroll anywhere on the buttonless page; only OK moves.
        press(Key.DirectionDown)
        press(Key.DirectionUp)
        press(Key.DirectionCenter)

        rule.onNodeWithText(title(R.string.setup_language_title)).assertExists()
        // The system row also names the current system language, so its whole
        // announcement is matched by substring, not as one fixed string.
        assertTrue(hasDescription(title(R.string.setup_language_system)))
        assertTrue(hasDescription(title(R.string.settings_language_russian)))
    }

    @Test
    fun okOnTheLastRowLeavesForTheNextPage() {
        setSetup(startStep = 1)

        // Three language rows, then the buttons under them.
        press(Key.DirectionDown, 3)
        press(Key.DirectionCenter)

        // A first run creates the profile, so the page names the create action.
        rule.onNodeWithText(title(R.string.setup_profile_create)).assertExists()
        assertTrue(hasDescription(title(R.string.profile_avatar)))
    }

    @Test
    fun theAvatarRowOpensTheGridAndBackReturnsToTheRows() {
        seedSingleProfile()
        setSetup(startStep = 2)

        assertTrue(hasDescription(title(R.string.profile_avatar)))
        press(Key.DirectionCenter)

        // The picker is a full screen whose heading carries its own name as a
        // description, so it is found the way TalkBack reads it.
        assertTrue(hasDescription(title(R.string.profile_avatar_choose)))
        press(Key.Back)

        rule.onNodeWithText(title(R.string.setup_profile_create)).assertExists()
        assertTrue(hasDescription(title(R.string.profile_avatar)))
    }

    @Test
    fun pickingAnAvatarWritesTheProfile() {
        seedSingleProfile()
        setSetup(startStep = 2)

        press(Key.DirectionCenter)
        // The grid opens on the profile's own disc (index 0), so one step right
        // lands on the next free picture; OK must write the store Home reads.
        press(Key.DirectionRight)
        press(Key.DirectionCenter)

        val stored = ProfileStore(context).current()
        assertEquals(ProfileCatalog.EMOJIS[1], stored.emoji)
    }

    @Test
    fun theNameRowOpensTheKeyboardAndBackReturns() {
        seedSingleProfile()
        setSetup(startStep = 2)

        // Two rows: avatar, then name. The keyboard is the launcher's own
        // full screen, so it names itself while the row behind is cleared.
        press(Key.DirectionDown)
        press(Key.DirectionCenter)

        // The overlay's title is the only "Name" on screen: the edited row
        // behind it carries the word as a description, not as text.
        rule.onNodeWithText(title(R.string.profile_name)).assertExists()
        press(Key.Back)

        rule.onNodeWithText(title(R.string.setup_profile_create)).assertExists()
    }

    @Test
    fun theProfileRowsFollowTheWizardSteps() {
        // The first-run profile page carries exactly the two editors the Java
        // wizard showed: the picture, then the name. Down from the last row
        // reaches the buttons, where OK continues.
        seedSingleProfile()
        setSetup(startStep = 2)

        assertTrue(hasDescription(title(R.string.profile_avatar)))
        press(Key.DirectionDown)
        assertTrue(hasDescription(title(R.string.profile_name)))
        press(Key.DirectionDown)
        press(Key.DirectionCenter)

        rule.onNodeWithText(title(R.string.setup_home_title)).assertExists()
    }

    @Test
    fun aReRunNamesTheExistingProfile() {
        SetupProgress.markDone(context)

        // A finished run starts at the first page, so the profile page is
        // reached the way the viewer reaches it: OK, then past the language.
        setSetup()
        press(Key.DirectionCenter)
        press(Key.DirectionDown, 3)
        press(Key.DirectionCenter)

        rule.onNodeWithText(title(R.string.setup_profile_title)).assertExists()
    }

    @Test
    fun backReturnsToThePreviousPage() {
        setSetup(startStep = 3)

        rule.onNodeWithText(title(R.string.setup_home_title)).assertExists()
        press(Key.Back)

        rule.onNodeWithText(title(R.string.setup_profile_create)).assertExists()
    }

    @Test
    fun backOnTheFirstPageOfFirstRunStays() {
        var finished = false
        setSetup(onFinish = { finished = true })

        press(Key.Back)

        assertFalse(finished)
        assertTrue(hasDescription(title(R.string.setup_hello)))
    }

    @Test
    fun anInterruptedRunResumesOnItsSavedPage() {
        SetupProgress.saveStep(context, 7)

        setSetup()

        rule.onNodeWithText(title(R.string.setup_access_title)).assertExists()
    }

    @Test
    fun anInterruptedRunFromAnOldPageMapStartsOver() {
        // A wizard updated mid-run keeps its own step version; a step saved by
        // the previous page order must not open the wrong question.
        context.getSharedPreferences(SetupProgress.NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(SetupProgress.KEY_STEP, 3)
            .putInt(SetupProgress.KEY_STEP_VER, SetupProgress.STEP_VERSION - 1)
            .apply()

        setSetup()

        assertTrue(hasDescription(title(R.string.setup_hello)))
    }

    @Test
    fun reRunStartsAtTheFirstPageAndBackCloses() {
        SetupProgress.markDone(context)
        var finished = false
        setSetup(onFinish = { finished = true })

        assertTrue(hasDescription(title(R.string.setup_hello)))
        press(Key.Back)

        assertTrue(finished)
    }

    @Test
    fun finishPageClosesTheWizardAndMarksSetupDone() {
        var finished = false
        setSetup(startStep = 8, onFinish = { finished = true })

        rule.onNodeWithText(title(R.string.setup_done_title)).assertExists()
        press(Key.DirectionCenter)

        assertTrue(finished)
        assertTrue(SetupProgress.done(context))
    }

    @Test
    fun okOnAToggleWritesTheRealPreference() {
        ContentPreferences.setRecommendations(context, true)
        setSetup(startStep = 3)

        press(Key.DirectionCenter)

        assertFalse(ContentPreferences.recommendations(context))
    }

    @Test
    fun theWeatherRowsWriteTheRealPreference() {
        // Weather defaults off; an earlier test or a previous run must not leak
        // an enabled value into this assertion.
        context.getSharedPreferences(WeatherPreferences.NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        setSetup(startStep = 4)

        rule.onNodeWithText(title(R.string.setup_weather_title)).assertExists()
        // The first row is the internet-address answer; it starts unselected.
        assertFalse(WeatherPreferences.enabled(context))
        // Two rows down is "No Weather": choosing it keeps the weather off.
        press(Key.DirectionDown, 2)
        press(Key.DirectionCenter)

        assertFalse(WeatherPreferences.enabled(context))
        // Back up to the first row turns the weather on explicitly, without a city.
        press(Key.DirectionUp, 2)
        press(Key.DirectionCenter)

        assertTrue(WeatherPreferences.enabled(context))
        assertFalse(WeatherPreferences.manual(context))
    }

    @Test
    fun theCitySearchOpensAndBackReturnsToTheRows() {
        setSetup(startStep = 4)

        press(Key.DirectionDown)
        press(Key.DirectionCenter)

        rule.onNodeWithText(title(R.string.setup_city_title)).assertExists()
        press(Key.Back)

        rule.onNodeWithText(title(R.string.setup_weather_title)).assertExists()
    }

    @Test
    fun pickingAPresetWritesThePlace() {
        setSetup(startStep = 4)

        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        // From the keyboard's first row, Up jumps to the results column, where
        // the presets wait; OK picks the first one.
        press(Key.DirectionUp)
        press(Key.DirectionCenter)

        assertTrue(WeatherPreferences.enabled(context))
        assertTrue(WeatherPreferences.manual(context))
        assertEquals(title(R.string.city_moscow), WeatherPreferences.place(context))
        assertEquals(55.7558, WeatherPreferences.latitude(context), 0.001)
        assertEquals(37.6173, WeatherPreferences.longitude(context), 0.001)
    }

    @Test
    fun theAccessRowsShowTheirLiveState() {
        setSetup(startStep = 7)

        // Each row announces either its action or, once the value is real,
        // the done state — and which of the two follows the actual system,
        // not a fixed assumption about the device.
        assertEquals(
            DeviceFactsReader.isDefaultHome(context),
            hasDescription(title(R.string.setup_state_done)),
        )
        assertEquals(
            ContentPreferences.canReadListings(context),
            hasDescription(title(R.string.settings_listings_granted)),
        )
        // The third switch is independent of the other two: "enabled but
        // stopped" is not "done", and the row must offer the restart instead.
        assertEquals(
            AccessibilityStatus.state(context) == AccessibilityStatus.State.CONNECTED,
            hasDescription(title(R.string.setup_accessibility_on)),
        )
    }

    @Test
    fun choosingALanguageReportsItOnce() {
        val chosen = mutableListOf<String>()
        setSetup(onLanguageChanged = { chosen += it })

        press(Key.DirectionCenter)
        press(Key.DirectionDown, 2)
        press(Key.DirectionCenter)

        assertEquals(listOf(LocalePreference.ENGLISH), chosen)
    }
}
