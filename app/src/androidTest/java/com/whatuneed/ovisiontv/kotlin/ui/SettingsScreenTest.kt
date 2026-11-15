@file:OptIn(ExperimentalTestApi::class)

package com.whatuneed.ovisiontv.kotlin.ui

import android.content.Context
import com.whatuneed.ovisiontv.kotlin.ambient.AmbientPhotoStore
import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.whatuneed.ovisiontv.kotlin.BuildConfig
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.ambient.AmbientClockFormat
import com.whatuneed.ovisiontv.kotlin.ambient.AmbientDim
import com.whatuneed.ovisiontv.kotlin.ambient.AmbientMode
import com.whatuneed.ovisiontv.kotlin.ambient.AmbientSession
import com.whatuneed.ovisiontv.kotlin.ambient.AmbientSettingsStore
import com.whatuneed.ovisiontv.kotlin.data.AerialClip
import com.whatuneed.ovisiontv.kotlin.data.AerialRefreshResult
import com.whatuneed.ovisiontv.kotlin.data.AccessibilityStatus
import com.whatuneed.ovisiontv.kotlin.data.ContentPreferences
import com.whatuneed.ovisiontv.kotlin.data.DeviceFactsReader
import com.whatuneed.ovisiontv.kotlin.data.HIDDEN_FOLDER_ID
import com.whatuneed.ovisiontv.kotlin.data.HomeItem
import com.whatuneed.ovisiontv.kotlin.data.HomeOrderStore
import com.whatuneed.ovisiontv.kotlin.data.KidsStore
import com.whatuneed.ovisiontv.kotlin.data.LauncherPrefs
import com.whatuneed.ovisiontv.kotlin.data.LocalePreference
import com.whatuneed.ovisiontv.kotlin.data.PermissionSummary
import com.whatuneed.ovisiontv.kotlin.data.ProfileCatalog
import com.whatuneed.ovisiontv.kotlin.data.ProfileStore
import com.whatuneed.ovisiontv.kotlin.data.SaverDelay
import com.whatuneed.ovisiontv.kotlin.data.SaverPreferences
import com.whatuneed.ovisiontv.kotlin.data.SaverRefresh
import com.whatuneed.ovisiontv.kotlin.data.SaverTheme
import com.whatuneed.ovisiontv.kotlin.data.SleepControls
import com.whatuneed.ovisiontv.kotlin.data.StartupPreferences
import com.whatuneed.ovisiontv.kotlin.data.TvApp
import com.whatuneed.ovisiontv.kotlin.data.WeatherPreferences
import com.whatuneed.ovisiontv.kotlin.ui.components.AMBIENT_PREVIEW_TAG
import com.whatuneed.ovisiontv.kotlin.ui.components.SAVER_PREVIEW_TAG
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// The real preview opens ExoPlayer decoders; tests swap it for this surface.
private const val SAVER_PREVIEW_STUB = "saver_preview_stub"

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var savedPrefs: Map<String, Any?>
    private lateinit var savedStartup: Map<String, Any?>
    private lateinit var savedProfiles: Map<String, Any?>
    private lateinit var savedWeather: Map<String, Any?>

    /** Four clips with locale-independent names: one per theme. */
    private val testClips = listOf(
        clip("aerial-1", SaverTheme.LANDSCAPES, "Aerial 1", 0),
        clip("aerial-2", SaverTheme.CITIES, "Aerial 2", 1),
        clip("aerial-3", SaverTheme.UNDERWATER, "Aerial 3", 2),
        clip("aerial-4", SaverTheme.EARTH, "Aerial 4", 3),
    )

    @Before
    fun resetLanguage() {
        savedPrefs = PrefsSnapshot.capture(context)
        savedStartup = PrefsSnapshot.captureStartup(context)
        context.getSharedPreferences(LauncherPrefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.getSharedPreferences(StartupPreferences.NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        savedProfiles = PrefsSnapshot.captureProfiles(context)
        context.getSharedPreferences(ProfileStore.FILE, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        savedWeather = PrefsSnapshot.captureWeather(context)
        context.getSharedPreferences(WeatherPreferences.NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @After
    fun restoreLanguage() {
        PrefsSnapshot.restore(context, savedPrefs)
        PrefsSnapshot.restoreStartup(context, savedStartup)
        PrefsSnapshot.restoreProfiles(context, savedProfiles)
        PrefsSnapshot.restoreWeather(context, savedWeather)
    }

    private fun setSettings(
        apps: List<TvApp> = listOf(app("one"), app("two"), app("three")),
        clips: List<AerialClip> = testClips,
        onClose: () -> Unit = {},
        refreshCatalog: (suspend () -> AerialRefreshResult)? = null,
        previewSurface: (@Composable (AerialClip) -> Unit)? = null,
    ) {
        rule.setContent {
            OVisionTheme {
                SettingsScreen(
                    onClose = onClose,
                    apps = apps,
                    clips = clips,
                    refreshCatalog = refreshCatalog,
                    previewSurface = previewSurface,
                )
            }
        }
        // The screen requests focus after 60 ms and draws no animation before
        // that, so the test clock has to move to the first frame.
        rule.mainClock.advanceTimeBy(100)
        rule.waitForIdle()
    }

    private fun press(key: Key) {
        rule.onRoot().performKeyInput {
            keyDown(key)
            keyUp(key)
        }
        rule.waitForIdle()
    }

    private fun string(id: Int): String = context.getString(id)

    private fun app(packageName: String) = TvApp(
        packageName = packageName,
        activityName = "$packageName.Main",
        label = packageName,
        banner = null,
        icon = null,
    )

    private fun clip(id: String, theme: SaverTheme, name: String, order: Int) = AerialClip(
        id = id,
        nameEn = name,
        nameRu = name,
        theme = theme,
        videoUrl = "https://example.com/$id.mp4",
        previewUrl = "",
        order = order,
    )

    private fun hiddenCount(count: Int): String =
        context.resources.getQuantityString(R.plurals.settings_hidden_count, count, count)

    private fun click(description: String) {
        rule.onNodeWithContentDescription(description)
            .performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
    }

    private fun clickRow(title: String) {
        rule.onNodeWithContentDescription(title, substring = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
    }

    private fun row(title: String, value: String) = title + ", " + value

    private fun openInterface() {
        waitForRow(string(R.string.settings_interface))
        clickRow(string(R.string.settings_interface))
    }

    private fun openDevice() {
        waitForRow(string(R.string.settings_device))
        clickRow(string(R.string.settings_device))
    }

    // The hidden-apps count is read from storage after the first composition,
    // so the root row is addressed by its title instead of its live value.
    private fun openHome() {
        waitForRow(string(R.string.settings_home_screen))
        clickRow(string(R.string.settings_home_screen))
    }

    private fun openHidden() {
        openHome()
        waitForRow(string(R.string.settings_hidden_apps))
        clickRow(string(R.string.settings_hidden_apps))
    }

    private fun openWeather() {
        waitForRow(string(R.string.weather_title))
        clickRow(string(R.string.weather_title))
    }

    private fun openSaver() {
        openInterface()
        waitForRow(string(R.string.settings_saver))
        clickRow(string(R.string.settings_saver))
    }

    private fun openAboutGroup() {
        waitForRow(string(R.string.settings_about_system))
        clickRow(string(R.string.settings_about_system))
    }

    private fun openSystemInfo() {
        openAboutGroup()
        waitForRow(string(R.string.settings_system_info))
        clickRow(string(R.string.settings_system_info))
    }

    private fun openLegal() {
        openAboutGroup()
        waitForRow(string(R.string.settings_legal))
        clickRow(string(R.string.settings_legal))
    }

    private fun openProfiles() {
        waitForRow(string(R.string.settings_profiles))
        clickRow(string(R.string.settings_profiles))
    }

    // The profile name editor is the folder keyboard: [Shift, Space, Backspace,
    // mode, mode, Clear, Done] on the row under the letters.
    private fun keyboardKey(index: Int) {
        repeat(3) { press(Key.DirectionDown) }
        repeat(10) { press(Key.DirectionLeft) }
        repeat(index) { press(Key.DirectionRight) }
        press(Key.DirectionCenter)
    }

    private fun typeOneOnTheKeyboard() {
        keyboardKey(3)
        // The mode switch lands on the first digit, so one press types "1".
        press(Key.DirectionCenter)
    }

    private fun waitForRow(title: String) {
        rule.waitUntil(5_000) {
            rule.onAllNodesWithContentDescription(title, substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    /** Focus is requested after the page transition settles, so poll for it. */
    private fun waitForFocus(description: String) {
        rule.waitUntil(5_000) {
            rule.onAllNodesWithContentDescription(description, substring = true)
                .fetchSemanticsNodes()
                .any { it.config.getOrNull(SemanticsProperties.Focused) == true }
        }
    }

    @Test
    fun root_shows_six_sections_without_group_headers() {
        setSettings()

        rule.onNodeWithText(string(R.string.settings_title))
            .assertIsDisplayed()
            .assert(isHeading())

        // Values come from storage and from the device, so the rows are
        // addressed by their title: the list is complete on every frame.
        val sections = listOf(
            R.string.settings_interface,
            R.string.settings_home_screen,
            R.string.settings_profiles,
            R.string.weather_title,
            R.string.settings_device,
            R.string.settings_about_system,
        )
        sections.forEach { id ->
            rule.onNodeWithContentDescription(string(id), substring = true)
                .assertExists()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        }

        // The old grouping headers ("Main", "Device", "Information") added a
        // level without meaning. Rows clear their own semantics down to one
        // description, so a header would be the only node exposing the title
        // as plain text: none may exist.
        listOf(
            string(R.string.settings_interface),
            string(R.string.settings_home_screen),
        ).forEach { title ->
            rule.onAllNodes(hasText(title)).assertCountEquals(0)
        }

        // Every section fits one screen; the remote reaches the last row.
        repeat(sections.lastIndex) { press(Key.DirectionDown) }
        waitForFocus(string(R.string.settings_about_system))

        // These rows must not borrow a value from another page: the count on
        // Home read as "0 apps", and the Android version belongs to Device.
        // Both carry their own value now, so match on the title only.
        rule.onNodeWithContentDescription(string(R.string.settings_home_screen), substring = true)
            .assertExists()
        rule.onNodeWithContentDescription(string(R.string.settings_device), substring = true)
            .assertExists()
    }

    @Test
    fun weather_clear_requires_confirmation_and_removes_saved_data() {
        context.getSharedPreferences(WeatherPreferences.NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(WeatherPreferences.K_ENABLED, true)
            .putBoolean(WeatherPreferences.K_MANUAL, true)
            .putString(WeatherPreferences.K_PLACE, "Moscow")
            .putString(WeatherPreferences.K_CACHE, "{\"temp\":1}")
            .putLong(WeatherPreferences.K_CACHE_AT, 123L)
            .commit()
        setSettings()

        openWeather()
        waitForRow(string(R.string.weather_clear_data))
        clickRow(string(R.string.weather_clear_data))
        waitForRow(string(R.string.weather_clear_confirm))
        // Opening the confirmation must not delete anything yet.
        assertTrue(WeatherPreferences.enabled(context))
        assertTrue(WeatherPreferences.place(context).isNotEmpty())

        // Cancel is focused by default; Back leaves without a write.
        press(Key.Back)
        waitForRow(string(R.string.weather_clear_data))
        assertTrue(WeatherPreferences.enabled(context))

        clickRow(string(R.string.weather_clear_data))
        waitForRow(string(R.string.weather_clear_confirm))
        press(Key.DirectionUp)
        press(Key.DirectionCenter)

        assertFalse(WeatherPreferences.enabled(context))
        assertEquals("", WeatherPreferences.place(context))
        assertNull(WeatherPreferences.cachedJson(context))
        waitForRow(string(R.string.weather_clear_data))
    }

    @Test
    fun down_then_center_opens_about_and_back_returns_to_root() {
        setSettings()

        // Appearance, Home, Profiles, Weather, Device, About.
        repeat(5) { press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        rule.waitForIdle()

        rule.onNodeWithText(string(R.string.settings_about_system))
            .assertIsDisplayed()
            .assert(isHeading())
        rule.onNodeWithContentDescription(
            string(R.string.settings_updates) + ", " + BuildConfig.VERSION_NAME,
        ).assertIsDisplayed()

        press(Key.Back)
        rule.waitForIdle()

        rule.onNodeWithText(string(R.string.settings_title)).assertIsDisplayed()
        // Back must land on the row that opened About, not on the first row.
        waitForFocus(string(R.string.settings_about_system))
        rule.onNodeWithContentDescription(
            string(R.string.settings_about_system),
            substring = true,
        ).assertIsFocused()
    }

    @Test
    fun interface_page_leads_to_the_language_list() {
        setSettings()
        openInterface()

        rule.onNodeWithText(string(R.string.settings_interface))
            .assertIsDisplayed()
            .assert(isHeading())

        click(row(string(R.string.settings_language), string(R.string.settings_language_system)))

        rule.onNodeWithContentDescription(string(R.string.settings_language_system))
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    string(R.string.settings_selected),
                ),
            )
            // One-of-many choices are radio buttons, and the checked one is
            // selected: TalkBack announces both without inferring from colour.
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertIsSelected()
        rule.onNodeWithContentDescription(string(R.string.settings_language_english))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
    }

    @Test
    fun profiles_page_opens_the_person_and_hides_the_delete_until_there_are_two() {
        setSettings()
        openProfiles()

        rule.onNodeWithText(string(R.string.settings_profiles))
            .assertIsDisplayed()
            .assert(isHeading())
        rule.onNodeWithText(string(R.string.profile_hint)).assertIsDisplayed()
        // Both picker triggers default to on, as in the Java launcher.
        rule.onNodeWithContentDescription(row(string(R.string.profile_ask_boot), string(R.string.saver_on)))
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        rule.onNodeWithContentDescription(row(string(R.string.profile_ask_wake), string(R.string.saver_on)))
            .assertIsDisplayed()

        val me = ProfileStore(context).list().single()
        clickRow(me.name)

        // The page is named after the person it edits.
        rule.onNodeWithText(me.name).assertIsDisplayed().assert(isHeading())
        rule.onNodeWithContentDescription(row(string(R.string.profile_switch), string(R.string.profile_current)))
            .assertIsDisplayed()
            .assertIsSelected()
        rule.onNodeWithContentDescription(row(string(R.string.profile_name), me.name))
            .assertIsDisplayed()
        rule.onNodeWithContentDescription(row(string(R.string.profile_avatar), me.emoji))
            .assertIsDisplayed()
        // The only profile cannot be deleted, so it offers no destructive row.
        rule.onNodeWithContentDescription(string(R.string.profile_delete), substring = true)
            .assertDoesNotExist()
    }

    @Test
    fun adding_a_profile_appends_it_to_the_list() {
        setSettings()
        openProfiles()

        clickRow(string(R.string.profile_add))
        // The add editor reuses the keyboard surface, not a stock dialog, and
        // its hint must name the profile: the folder wording leaked here.
        rule.onNodeWithText(string(R.string.profile_add)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.profile_name_hint)).assertIsDisplayed()
        typeOneOnTheKeyboard()
        keyboardKey(6)

        val added = ProfileStore(context).list().last()
        assertEquals("1", added.name)
        assertEquals(2, ProfileStore(context).list().size)
        waitForRow(added.name)
        rule.onNodeWithContentDescription(row(added.name, added.emoji)).assertIsDisplayed()
    }

    @Test
    fun switching_and_deleting_a_profile_walks_its_own_page() {
        val store = ProfileStore(context)
        val guest = requireNotNull(store.add("Гость"))
        setSettings()
        openProfiles()

        waitForRow(guest.name)
        clickRow(guest.name)
        rule.onNodeWithText(guest.name).assertIsDisplayed().assert(isHeading())

        clickRow(string(R.string.profile_switch))
        rule.waitForIdle()
        assertEquals(guest.id, ProfileStore(context).current().id)
        rule.onNodeWithContentDescription(row(string(R.string.profile_switch), string(R.string.profile_current)))
            .assertIsSelected()

        // Delete asks first; Cancel keeps the person and the page.
        clickRow(string(R.string.profile_delete))
        rule.onNodeWithText(string(R.string.profile_delete_title))
            .assertIsDisplayed()
            .assert(isHeading())
        rule.onNodeWithText(string(R.string.profile_delete_hint)).assertIsDisplayed()
        clickRow(string(R.string.settings_cancel))
        rule.onNodeWithText(guest.name).assertIsDisplayed().assert(isHeading())

        // Confirming removes the profile and lands on the list in one step.
        clickRow(string(R.string.profile_delete))
        clickRow(string(R.string.profile_delete_confirm))
        rule.waitForIdle()

        assertEquals(1, ProfileStore(context).list().size)
        assertEquals(ProfileCatalog.DEFAULT_ID, ProfileStore(context).current().id)
        rule.onNodeWithText(string(R.string.settings_profiles))
            .assertIsDisplayed()
            .assert(isHeading())
        rule.onAllNodesWithContentDescription(guest.name, substring = true).assertCountEquals(0)
    }

    /** The pad auto-submits on the fourth digit, one click per digit. */
    private fun typePin(value: String) {
        value.forEach { digit ->
            rule.onAllNodesWithContentDescription(digit.toString())
                .onFirst()
                .performSemanticsAction(SemanticsActions.OnClick)
        }
        rule.waitForIdle()
    }

    // AQ-02: A -> kids B -> A. The leave gate opens under B, so it must verify
    // B's PIN; the entry snapshot (A) is a different person's lock.
    @Test
    fun leave_gate_checks_the_current_kids_profile_not_the_entry_snapshot() {
        val savedKids = PrefsSnapshot.captureKids(context)
        try {
            val store = ProfileStore(context)
            val kidsStore = KidsStore(context)
            val adult = store.current()
            val kids = requireNotNull(store.add("Малыш"))
            store.update(kids.copy(kids = true))
            runBlocking {
                kidsStore.setPin(adult.id, "1111")
                kidsStore.setPin(kids.id, "2222")
            }
            setSettings()
            openProfiles()

            // Entering the kids profile is not gated.
            waitForRow(kids.name)
            click(row(kids.name, kids.emoji))
            clickRow(string(R.string.profile_switch))
            rule.waitForIdle()
            assertEquals(kids.id, ProfileStore(context).current().id)

            // Leaving it back to the adult profile raises the leave gate.
            press(Key.Back)
            waitForRow(adult.name)
            click(row(adult.name, adult.emoji))
            clickRow(string(R.string.profile_switch))
            rule.waitUntil(5_000) {
                rule.onAllNodesWithContentDescription(string(R.string.kids_pin_title))
                    .fetchSemanticsNodes().isNotEmpty()
            }

            // The adult's own PIN must not pass the child's gate.
            typePin("1111")
            val kidsPrefs = context.getSharedPreferences(KidsStore.FILE, Context.MODE_PRIVATE)
            rule.waitUntil(10_000) {
                ProfileStore(context).current().id == adult.id ||
                    (kidsPrefs.getInt("fail_${kids.id}", 0) == 1 &&
                        rule.onAllNodesWithTag("pin-pad-ready").fetchSemanticsNodes().isNotEmpty())
            }
            assertEquals(
                "the leave gate must check the kids profile's PIN, not the entry snapshot's",
                kids.id,
                ProfileStore(context).current().id,
            )

            typePin("2222")
            rule.waitUntil(10_000) { ProfileStore(context).current().id == adult.id }
        } finally {
            PrefsSnapshot.restoreKids(context, savedKids)
        }
    }

    @Test
    fun about_opens_the_license_text_and_back_returns_to_about() {
        setSettings()
        openSystemInfo()

        rule.onNodeWithContentDescription(
            row(string(R.string.settings_developer), string(R.string.settings_developer_value)),
        ).assertIsDisplayed()
        rule.onNodeWithContentDescription(
            row(
                string(R.string.settings_build_channel),
                string(
                    if (BuildConfig.DEBUG) {
                        R.string.settings_build_debug
                    } else {
                        R.string.settings_build_stable
                    },
                ),
            ),
        ).assertIsDisplayed()

        // Back to the About section, then into the only legal entry. The
        // Licences row must be reached with the remote: a synthetic click
        // leaves the focus index on the first row, and the point of the last
        // assertion is that Back returns to the row that opened the document.
        press(Key.Back)
        rule.waitForIdle()
        waitForRow(string(R.string.settings_legal))
        clickRow(string(R.string.settings_legal))
        waitForRow(string(R.string.settings_terms))
        repeat(3) { press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        rule.waitForIdle()

        rule.onNodeWithText(string(R.string.settings_licenses))
            .assertIsDisplayed()
            .assert(isHeading())
        // Every paragraph is its own accessibility stop; an exact match fails
        // against the former single 11 KB TextView.
        rule.onNodeWithText("OVisionTV Launcher").assertIsDisplayed()
        rule.onNodeWithText("TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION")
            .assertIsDisplayed()
        rule.onNodeWithText("1. Definitions.").assertExists()

        press(Key.Back)
        rule.waitForIdle()

        rule.onNodeWithText(string(R.string.settings_legal))
            .assertIsDisplayed()
            .assert(isHeading())
        waitForFocus(string(R.string.settings_licenses))
        rule.onNodeWithContentDescription(string(R.string.settings_licenses), substring = true)
            .assertIsFocused()
    }

    @Test
    fun the_license_page_scrolls_with_the_remote() {
        setSettings()
        openLegal()
        waitForRow(string(R.string.settings_licenses))
        clickRow(string(R.string.settings_licenses))
        rule.waitForIdle()

        fun scrollValue(): Float = rule.onNode(hasScrollAction())
            .fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
            .value()

        assertEquals(0f, scrollValue())
        repeat(4) { press(Key.DirectionDown) }
        rule.waitForIdle()

        assertTrue("The licenses text did not scroll down", scrollValue() > 0f)
    }

    @Test
    fun an_empty_catalog_still_reaches_the_hidden_list() {
        setSettings(apps = emptyList())

        rule.onNodeWithText(string(R.string.settings_title)).assertIsDisplayed()
        openHidden()

        rule.onNodeWithText(string(R.string.settings_hidden_empty)).assertIsDisplayed()
    }

    @Test
    fun back_at_root_closes_the_screen() {
        var closed = false
        setSettings(onClose = { closed = true })

        press(Key.Back)
        rule.waitForIdle()

        assertTrue(closed)
    }

    @Test
    fun talkback_click_opens_the_about_page() {
        setSettings()

        // TalkBack double-tap runs the semantics OnClick action; a touch click
        // would never reach this key-driven screen.
        rule.onNodeWithContentDescription(
            string(R.string.settings_about_system) + ", " + BuildConfig.VERSION_NAME,
        ).performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()

        rule.onNodeWithText(string(R.string.settings_about_system))
            .assertIsDisplayed()
            .assert(isHeading())
    }

    @Test
    fun the_saver_page_shows_the_preview_and_the_tv_os_rows() {
        setSettings()

        openSaver()

        rule.onNodeWithText(string(R.string.settings_saver))
            .assertIsDisplayed()
            .assert(isHeading())
        // The preview stands in for TSKPreviewViewController beside the options.
        rule.onNodeWithTag(SAVER_PREVIEW_TAG).assertIsDisplayed()

        rule.onNodeWithContentDescription(
            row(string(R.string.saver_choose), string(R.string.saver_aerials)),
        ).assertIsDisplayed()
        rule.onNodeWithContentDescription(
            row(string(R.string.saver_start_after), string(R.string.saver_delay_5)),
        ).assertIsDisplayed()
        rule.onNodeWithContentDescription(
            row(string(R.string.saver_during_music), string(R.string.saver_on)),
        ).assertIsDisplayed()
        rule.onNodeWithContentDescription(
            row(string(R.string.saver_themes), string(R.string.saver_all_themes)),
        ).assertIsDisplayed()
        rule.onNodeWithContentDescription(
            row(string(R.string.saver_refresh), string(R.string.saver_refresh_weekly)),
        ).assertIsDisplayed()
    }

    @Test
    fun the_ambient_mode_page_switches_between_aerial_and_clock() {
        setSettings()
        openSaver()
        click(row(string(R.string.saver_mode), string(R.string.saver_mode_aerial)))
        rule.onNodeWithText(string(R.string.saver_mode)).assertIsDisplayed()

        click(string(R.string.saver_mode_clock))
        assertEquals(AmbientMode.CLOCK, AmbientSettingsStore.mode(context))

        press(Key.Back)
        rule.waitForIdle()
        rule.onNodeWithContentDescription(
            row(string(R.string.saver_mode), string(R.string.saver_mode_clock)),
        ).assertIsDisplayed()
        rule.onNodeWithTag(AMBIENT_PREVIEW_TAG).assertIsDisplayed()
    }

    @Test
    fun the_photos_mode_names_its_folder_and_never_pretends_it_is_chosen() {
        setSettings()
        openSaver()
        click(row(string(R.string.saver_mode), string(R.string.saver_mode_aerial)))
        click(string(R.string.saver_mode_photos))

        assertEquals(AmbientMode.PHOTOS, AmbientSettingsStore.mode(context))
        press(Key.Back)
        rule.waitForIdle()

        rule.onNodeWithContentDescription(
            row(string(R.string.saver_mode), string(R.string.saver_mode_photos)),
        ).assertIsDisplayed()
        val sourceTitle = if (
            AmbientPhotoStore.canPickFolder(
                InstrumentationRegistry.getInstrumentation().targetContext,
            )
        ) {
            R.string.saver_photos_folder
        } else {
            R.string.saver_photos_selected_title
        }
        rule.onNodeWithContentDescription(
            string(sourceTitle),
            substring = true,
        ).assertIsDisplayed()
        rule.onAllNodesWithContentDescription(
            string(R.string.saver_photos_forget),
            substring = true,
        ).assertCountEquals(0)
    }

    @Test
    fun the_clock_page_sets_a_12_24_format_and_pixel_shift() {
        setSettings()
        openSaver()
        click(row(string(R.string.saver_mode), string(R.string.saver_mode_aerial)))
        click(string(R.string.saver_mode_clock))
        press(Key.Back)

        click(
            row(
                string(R.string.saver_clock_format),
                string(R.string.saver_clock_format_system),
            ),
        )
        click(string(R.string.saver_clock_format_24))
        assertEquals(AmbientClockFormat.TWENTY_FOUR, AmbientSettingsStore.clockFormat(context))

        press(Key.Back)
        rule.waitForIdle()
        click(
            row(
                string(R.string.saver_pixel_shift),
                string(R.string.saver_on),
            ),
        )
        assertFalse(AmbientSettingsStore.pixelShift(context))
    }

    @Test
    fun the_clock_page_sets_dim_and_max_session() {
        setSettings()
        openSaver()
        click(row(string(R.string.saver_mode), string(R.string.saver_mode_aerial)))
        click(string(R.string.saver_mode_clock))
        press(Key.Back)

        // Clock rows: Mode, Start After, Music, Clock Format, Pixel Shift,
        // Dim After, Max Session, System screen saver, Reset.
        repeat(5) { press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        click(string(R.string.saver_dim_20))
        assertEquals(AmbientDim.TWENTY, AmbientSettingsStore.dimAfter(context))

        press(Key.Back)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        click(string(R.string.saver_session_60))
        assertEquals(AmbientSession.SIXTY, AmbientSettingsStore.maxSession(context))
    }

    @Test
    fun the_clock_page_offers_weather_when_the_weather_feature_is_on() {
        WeatherPreferences.setEnabled(context, true)
        setSettings()
        openSaver()
        click(row(string(R.string.saver_mode), string(R.string.saver_mode_aerial)))
        click(string(R.string.saver_mode_clock))
        press(Key.Back)

        click(
            row(
                string(R.string.saver_show_weather),
                string(R.string.saver_off),
            ),
        )
        assertTrue(AmbientSettingsStore.showWeather(context))
    }

    @Test
    fun the_clock_page_points_to_weather_settings_when_weather_is_off() {
        setSettings()
        openSaver()
        click(row(string(R.string.saver_mode), string(R.string.saver_mode_aerial)))
        click(string(R.string.saver_mode_clock))
        press(Key.Back)

        rule.onNodeWithContentDescription(
            row(
                string(R.string.saver_show_weather),
                string(R.string.saver_weather_off),
            ),
        ).assertIsDisplayed()
    }

    @Test
    fun the_saver_chooser_toggles_clips_and_keeps_the_last_one() {
        setSettings()
        openSaver()

        click(row(string(R.string.saver_choose), string(R.string.saver_aerials)))
        rule.onNodeWithText(string(R.string.saver_choose))
            .assertIsDisplayed()
            .assert(isHeading())

        // Four catalogue clips, each with its own still tile.
        for (index in 1..4) {
            rule.onNodeWithContentDescription("Aerial $index")
                .assertIsDisplayed()
                .assert(
                    SemanticsMatcher.expectValue(
                        SemanticsProperties.StateDescription,
                        string(R.string.saver_on),
                    ),
                )
        }

        click("Aerial 2")
        assertEquals(setOf("aerial-2"), SaverPreferences.hidden(context))
        rule.onNodeWithContentDescription("Aerial 2")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    string(R.string.saver_off),
                ),
            )

        click("Aerial 1")
        click("Aerial 3")
        assertEquals(
            setOf("aerial-1", "aerial-2", "aerial-3"),
            SaverPreferences.hidden(context),
        )

        // The fourth tap would leave nothing to play, so it must be a no-op.
        click("Aerial 4")
        rule.onNodeWithText(string(R.string.settings_last_clip_hint)).assertIsDisplayed()
        // The count and the refusal note share one column, and that column must
        // end above the grid; the note once overlapped the count.
        val note = rule.onNodeWithText(string(R.string.settings_last_clip_hint))
            .getUnclippedBoundsInRoot()
        val firstTile = rule.onNodeWithContentDescription("Aerial 1")
            .getUnclippedBoundsInRoot()
        assertTrue("note bottom ${note.bottom} overlaps tile top ${firstTile.top}", note.bottom <= firstTile.top)
        assertEquals(
            setOf("aerial-1", "aerial-2", "aerial-3"),
            SaverPreferences.hidden(context),
        )

        press(Key.Back)
        rule.waitForIdle()
        rule.onNodeWithText(string(R.string.settings_saver)).assertIsDisplayed()
    }

    @Test
    fun the_category_list_filters_the_grid_and_switches_a_whole_theme() {
        setSettings()
        openSaver()

        click(row(string(R.string.saver_choose), string(R.string.saver_aerials)))

        // Tiles and category rows are real focus targets: TalkBack reads them.
        rule.onNodeWithContentDescription("Aerial 1").assert(isFocusable())
        rule.onNodeWithContentDescription(string(R.string.saver_filter_all), substring = true)
            .assert(isFocusable())
            .assertIsDisplayed()

        // Every enabled theme has its own row; "All" starts selected.
        listOf(
            R.string.saver_theme_landscapes,
            R.string.saver_theme_cities,
            R.string.saver_theme_underwater,
            R.string.saver_theme_earth,
        ).forEach { title ->
            rule.onNodeWithContentDescription(string(title), substring = true)
                .assertIsDisplayed()
        }

        // Moving the focus onto a category is the filter change; the grid
        // follows without a separate OK press.
        press(Key.DirectionUp)
        waitForFocus(string(R.string.saver_filter_all))
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        waitForFocus(string(R.string.saver_theme_cities))
        rule.onNodeWithContentDescription("Aerial 2").assertIsDisplayed()
        rule.onNodeWithContentDescription("Aerial 1").assertDoesNotExist()
        rule.onNodeWithContentDescription("Aerial 4").assertDoesNotExist()

        // One press hides every clip of the category.
        press(Key.DirectionCenter)
        assertEquals(setOf("aerial-2"), SaverPreferences.hidden(context))
        rule.onNodeWithContentDescription("Aerial 2").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                string(R.string.saver_off),
            ),
        )

        // The same press now brings the whole category back.
        press(Key.DirectionCenter)
        assertEquals(emptySet<String>(), SaverPreferences.hidden(context))
        rule.onNodeWithContentDescription("Aerial 2").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                string(R.string.saver_on),
            ),
        )

        press(Key.DirectionUp)
        press(Key.DirectionUp)
        waitForFocus(string(R.string.saver_filter_all))
        rule.onNodeWithContentDescription("Aerial 1").assertIsDisplayed()
        rule.onNodeWithContentDescription("Aerial 4").assertIsDisplayed()
    }

    @Test
    fun back_from_the_category_rail_returns_to_the_grid_before_the_page() {
        setSettings()
        openSaver()
        click(row(string(R.string.saver_choose), string(R.string.saver_aerials)))

        // Up enters the category rail; BACK is a layer inside the page, not a
        // request to leave the whole chooser.
        press(Key.DirectionUp)
        waitForFocus(string(R.string.saver_filter_all))
        press(Key.Back)
        rule.waitForIdle()

        rule.onNodeWithContentDescription(string(R.string.saver_filter_all), substring = true)
            .assertIsDisplayed()
        rule.onNodeWithContentDescription("Aerial 1").assertIsDisplayed()
    }

    @Test
    fun a_whole_category_cannot_leave_the_saver_without_clips() {
        // Only one theme is enabled, so hiding it would hide every aerial.
        SaverPreferences.toggleTheme(context, SaverTheme.CITIES)
        SaverPreferences.toggleTheme(context, SaverTheme.UNDERWATER)
        SaverPreferences.toggleTheme(context, SaverTheme.EARTH)
        setSettings()
        openSaver()

        click(row(string(R.string.saver_choose), string(R.string.saver_aerials)))
        press(Key.DirectionUp)
        waitForFocus(string(R.string.saver_filter_all))
        press(Key.DirectionDown)
        waitForFocus(string(R.string.saver_theme_landscapes))
        press(Key.DirectionCenter)

        assertEquals(emptySet<String>(), SaverPreferences.hidden(context))
        rule.onNodeWithText(string(R.string.settings_last_clip_hint)).assertIsDisplayed()
    }

    @Test
    fun holding_ok_on_a_clip_previews_the_saver_without_toggling_it() {
        val previewed = mutableListOf<String>()
        setSettings(
            previewSurface = { clip ->
                Box(Modifier.fillMaxSize().testTag(SAVER_PREVIEW_STUB))
                LaunchedEffect(clip) { previewed += clip.id }
            },
        )
        openSaver()
        click(row(string(R.string.saver_choose), string(R.string.saver_aerials)))

        // A normal press still toggles the clip.
        press(Key.DirectionCenter)
        assertEquals(setOf("aerial-1"), SaverPreferences.hidden(context))
        press(Key.DirectionCenter)
        assertEquals(emptySet<String>(), SaverPreferences.hidden(context))
        assertTrue(previewed.isEmpty())

        // Holding OK opens the saver over the chooser and must not count as a
        // toggle when it is released.
        rule.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        rule.mainClock.advanceTimeBy(700)
        rule.waitForIdle()
        rule.onNodeWithTag(SAVER_PREVIEW_STUB).assertIsDisplayed()
        assertEquals(listOf("aerial-1"), previewed)
        rule.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        rule.waitForIdle()
        assertEquals(emptySet<String>(), SaverPreferences.hidden(context))
    }

    @Test
    fun a_chosen_start_delay_is_stored_and_read_back_on_reopen() {
        val visible = mutableStateOf(true)
        rule.setContent {
            OVisionTheme {
                if (visible.value) {
                    SettingsScreen(
                        onClose = {},
                        apps = listOf(app("one")),
                        clips = testClips,
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(100)
        rule.waitForIdle()

        openSaver()
        click(row(string(R.string.saver_start_after), string(R.string.saver_delay_5)))
        click(string(R.string.saver_delay_10))
        assertEquals(SaverDelay.TEN, SaverPreferences.delay(context))

        press(Key.Back)
        rule.waitForIdle()
        rule.onNodeWithContentDescription(
            row(string(R.string.saver_start_after), string(R.string.saver_delay_10)),
        ).assertIsDisplayed()

        // The screen is disposed and composed again: the choice must come from
        // storage, not from the remember slot of the previous composition.
        rule.runOnIdle { visible.value = false }
        rule.waitForIdle()
        rule.runOnIdle { visible.value = true }
        rule.mainClock.advanceTimeBy(100)
        rule.waitForIdle()

        openSaver()
        rule.onNodeWithContentDescription(
            row(string(R.string.saver_start_after), string(R.string.saver_delay_10)),
        ).assertIsDisplayed()
    }

    @Test
    fun the_music_toggle_updates_its_value_and_is_stored() {
        setSettings()
        openSaver()

        rule.onNodeWithContentDescription(
            row(string(R.string.saver_during_music), string(R.string.saver_on)),
        )
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ToggleableState,
                    ToggleableState.On,
                ),
            )
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    string(R.string.settings_state_on),
                ),
            )

        click(row(string(R.string.saver_during_music), string(R.string.saver_on)))

        assertFalse(SaverPreferences.music(context))
        rule.onNodeWithContentDescription(
            row(string(R.string.saver_during_music), string(R.string.saver_off)),
        )
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ToggleableState,
                    ToggleableState.Off,
                ),
            )
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    string(R.string.settings_state_off),
                ),
            )
    }

    @Test
    fun the_last_aerial_theme_cannot_be_turned_off() {
        setSettings()
        openSaver()
        click(row(string(R.string.saver_themes), string(R.string.saver_all_themes)))

        click(string(R.string.saver_theme_cities))
        click(string(R.string.saver_theme_landscapes))
        click(string(R.string.saver_theme_earth))
        assertEquals(setOf(SaverTheme.UNDERWATER), SaverPreferences.themes(context))

        // The fourth tap would leave no source to play, so it must be a no-op.
        click(string(R.string.saver_theme_underwater))
        rule.onNodeWithText(string(R.string.saver_themes_hint)).assertIsDisplayed()
        rule.onNodeWithContentDescription(string(R.string.saver_theme_underwater))
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    string(R.string.settings_selected),
                ),
            )
        assertEquals(setOf(SaverTheme.UNDERWATER), SaverPreferences.themes(context))

        press(Key.Back)
        rule.waitForIdle()
        rule.onNodeWithContentDescription(
            row(
                string(R.string.saver_themes),
                context.getString(R.string.saver_themes_count, 1, SaverTheme.entries.size),
            ),
        ).assertIsDisplayed()
    }

    @Test
    fun the_refresh_frequency_is_stored_and_announced() {
        setSettings()
        openSaver()
        click(row(string(R.string.saver_refresh), string(R.string.saver_refresh_weekly)))
        click(string(R.string.saver_refresh_monthly))

        rule.onNodeWithText(string(R.string.saver_refresh_hint)).assertIsDisplayed()
        assertEquals(SaverRefresh.MONTHLY, SaverPreferences.refresh(context))
        rule.onNodeWithContentDescription(string(R.string.saver_refresh_monthly))
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    string(R.string.settings_selected),
                ),
            )

        press(Key.Back)
        rule.waitForIdle()
        rule.onNodeWithContentDescription(
            row(string(R.string.saver_refresh), string(R.string.saver_refresh_monthly)),
        ).assertIsDisplayed()
    }

    @Test
    fun the_delay_page_opens_on_the_stored_choice() {
        SaverPreferences.setDelay(context, SaverDelay.TEN)
        setSettings()
        openSaver()
        click(row(string(R.string.saver_start_after), string(R.string.saver_delay_10)))

        // OK without moving re-confirms the stored value. Opening the page on
        // its first row would silently switch 10 minutes to 2.
        press(Key.DirectionCenter)
        rule.waitForIdle()
        assertEquals(SaverDelay.TEN, SaverPreferences.delay(context))
    }

    @Test
    fun the_refresh_page_opens_on_the_stored_choice() {
        SaverPreferences.setRefresh(context, SaverRefresh.MONTHLY)
        setSettings()
        openSaver()
        click(row(string(R.string.saver_refresh), string(R.string.saver_refresh_monthly)))

        press(Key.DirectionCenter)
        rule.waitForIdle()
        assertEquals(SaverRefresh.MONTHLY, SaverPreferences.refresh(context))
    }

    @Test
    fun the_language_page_opens_on_the_stored_choice() {
        LocalePreference.set(context, LocalePreference.RUSSIAN)
        rule.setContent {
            OVisionTheme {
                SettingsScreen(
                    onClose = {},
                    apps = listOf(app("one")),
                    clips = testClips,
                )
            }
        }
        rule.mainClock.advanceTimeBy(100)
        rule.waitForIdle()

        // General holds the language row; OK again opens the language list.
        press(Key.DirectionCenter)
        rule.waitForIdle()
        press(Key.DirectionCenter)
        rule.waitForIdle()

        // OK on the stored item confirms Russian instead of overwriting it
        // with the first row of the list.
        press(Key.DirectionCenter)
        rule.waitForIdle()
        assertEquals(LocalePreference.RUSSIAN, LocalePreference.get(context))
    }

    private fun saveHiddenOrder(vararg hidden: String) {
        HomeOrderStore(context).saveOrder(
            listOf(HomeItem.AppItem(app("one"))) +
                HomeItem.FolderItem(
                    HIDDEN_FOLDER_ID,
                    "More Apps",
                    hidden.map(::app),
                ),
        )
    }

    private fun hiddenRow(packageName: String) =
        rule.onNodeWithContentDescription(
            packageName + ", " + string(R.string.menu_show_on_home),
        )

    @Test
    fun hidden_apps_page_restores_an_app_to_the_saved_order() {
        saveHiddenOrder("two", "three")
        setSettings()

        openHidden()

        hiddenRow("three").assertIsDisplayed()
        hiddenRow("three").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()

        hiddenRow("three").assertDoesNotExist()
        hiddenRow("two").assertIsDisplayed()

        // The restored app lands right after the hidden folder, as it does from
        // the folder panel; only the saved order matters for Home.
        val saved = HomeOrderStore(context)
            .applyOrder(listOf(app("one"), app("two"), app("three")))
        assertEquals(listOf("one", "folder:hidden", "three"), saved.map { it.key })

        press(Key.Back)
        rule.waitForIdle()
        rule.onNodeWithContentDescription(
            row(string(R.string.settings_hidden_apps), hiddenCount(1)),
        ).assertIsDisplayed()
    }

    @Test
    fun a_hidden_app_row_names_the_restore_action() {
        saveHiddenOrder("two")
        setSettings()

        openHidden()

        // TalkBack reads "double-tap to <label>"; the app name alone would name
        // the row, not what the tap does.
        val label = hiddenRow("two")
            .fetchSemanticsNode()
            .config[SemanticsActions.OnClick]
            .label
        assertEquals(string(R.string.menu_show_on_home), label)
    }

    @Test
    fun hidden_apps_page_explains_when_nothing_is_hidden() {
        setSettings()

        openHidden()

        rule.onNodeWithText(string(R.string.settings_hidden_empty))
            .assertIsDisplayed()
            .assert(isFocusable())
        rule.onNodeWithText(string(R.string.settings_hidden_empty_hint))
            .assertIsDisplayed()
            .assert(isFocusable())
    }

    @Test
    fun a_package_change_rebuilds_an_open_hidden_list() {
        saveHiddenOrder("two")
        // The screen cannot send a package broadcast from a test (protected
        // action), so the tick that the receiver raises is driven directly.
        val refresh = mutableIntStateOf(0)
        rule.setContent {
            OVisionTheme {
                SettingsScreen(
                    onClose = {},
                    apps = listOf(app("one"), app("two")),
                    refreshKey = refresh.intValue,
                    clips = testClips,
                )
            }
        }
        rule.mainClock.advanceTimeBy(100)
        rule.waitForIdle()

        openHidden()
        hiddenRow("two").assertIsDisplayed()

        // The row must follow the saved order without leaving the page.
        HomeOrderStore(context).clearOrder()
        rule.runOnIdle { refresh.intValue++ }
        rule.waitForIdle()

        hiddenRow("two").assertDoesNotExist()
        rule.onNodeWithText(string(R.string.settings_hidden_empty)).assertIsDisplayed()
    }

    @Test
    fun long_hidden_list_scrolls_to_the_last_row() {
        val parked = (1..20).map { "h$it" }
        saveHiddenOrder(*parked.toTypedArray())
        setSettings(apps = listOf(app("one")) + parked.map(::app))

        openHidden()

        // Eleven rows fit above the fold; the rest must scroll instead of
        // sitting below the screen where the D-pad can never show them.
        repeat(parked.lastIndex) { press(Key.DirectionDown) }
        rule.waitForIdle()

        hiddenRow("h20").assertIsDisplayed()
        hiddenRow("h1").assertIsNotDisplayed()
    }

    @Test
    fun reset_asks_first_and_cancel_keeps_the_saved_order() {
        HomeOrderStore(context).saveOrder(
            listOf(
                HomeItem.AppItem(app("one")),
                HomeItem.FolderItem("f", "Folder", listOf(app("two"))),
                HomeItem.AppItem(app("three")),
            ),
        )
        setSettings()

        openHome()
        // The page now opens with the content toggles and the access row above
        // the layout rows, so the reset page is addressed by its title.
        clickRow(string(R.string.settings_reset_layout))

        rule.onNodeWithText(string(R.string.settings_reset_title))
            .assertIsDisplayed()
            .assert(isHeading())
        rule.onNodeWithText(string(R.string.settings_reset_hint)).assertIsDisplayed()

        // Focus starts on Cancel: OK alone must not destroy the layout.
        press(Key.DirectionCenter)
        rule.waitForIdle()

        rule.onNodeWithText(string(R.string.settings_home_screen)).assertIsDisplayed()
        assertTrue(HomeOrderStore(context).savedOrder() != null)
    }

    @Test
    fun confirming_the_reset_clears_the_saved_layout() {
        HomeOrderStore(context).saveOrder(
            listOf(
                HomeItem.AppItem(app("one")),
                HomeItem.FolderItem("f", "Folder", listOf(app("two"))),
                HomeItem.AppItem(app("three")),
            ),
        )
        setSettings()

        openHome()
        clickRow(string(R.string.settings_reset_layout))

        press(Key.DirectionUp)
        press(Key.DirectionCenter)
        rule.waitForIdle()

        // An absent saved order is the factory layout: every app, no folders.
        assertNull(HomeOrderStore(context).savedOrder())
        assertEquals(
            listOf("one", "two", "three"),
            HomeOrderStore(context)
                .applyOrder(listOf(app("one"), app("two"), app("three")))
                .map { it.key },
        )
        rule.onNodeWithContentDescription(
            row(string(R.string.settings_hidden_apps), hiddenCount(0)),
        ).assertIsDisplayed()
    }

    @Test
    fun reopening_the_home_page_restores_the_row_it_was_left_on() {
        setSettings()

        openHome()
        // Move to "Reset layout": the last row after the Recommendations entry
        // and phone-only apps, listings access and hidden apps.
        repeat(4) { press(Key.DirectionDown) }
        waitForFocus(string(R.string.settings_reset_layout))
        press(Key.Back)
        rule.waitForIdle()

        // Back returns to the root row that opened the page.
        rule.onNodeWithText(string(R.string.settings_title)).assertIsDisplayed()

        // Re-entering the page must restore the second row: OK opens Reset,
        // not the hidden apps list that sits on the first row.
        openHome()
        press(Key.DirectionCenter)
        rule.waitForIdle()
        rule.onNodeWithText(string(R.string.settings_reset_title)).assertIsDisplayed()
    }

    @Test
    fun network_page_reports_the_live_connection() {
        setSettings()
        openDevice()

        clickRow(string(R.string.settings_network))

        rule.onNodeWithText(string(R.string.settings_network))
            .assertIsDisplayed()
            .assert(isHeading())
        waitForRow(string(R.string.settings_connection))
        rule.onNodeWithContentDescription(string(R.string.settings_ip_address), substring = true)
            .assertIsDisplayed()
        rule.onNodeWithText(string(R.string.settings_network_hint)).assertIsDisplayed()
    }

    @Test
    fun device_page_lists_facts_read_from_the_tv() {
        setSettings()
        openDevice()

        clickRow(string(R.string.settings_about_device))

        rule.onNodeWithText(string(R.string.settings_about_device))
            .assertIsDisplayed()
            .assert(isHeading())
        waitForRow(string(R.string.settings_device_model))
        rule.onNodeWithContentDescription(string(R.string.settings_device_display), substring = true)
            .assertIsDisplayed()
        rule.onNodeWithContentDescription(
            string(R.string.settings_device_android),
            substring = true,
        ).assertIsDisplayed()
    }

    @Test
    fun storage_page_counts_and_clears_the_caches() {
        setSettings()
        openDevice()

        clickRow(string(R.string.settings_storage))

        rule.onNodeWithText(string(R.string.settings_storage))
            .assertIsDisplayed()
            .assert(isHeading())
        waitForRow(string(R.string.settings_storage_total))
        rule.onNodeWithText(string(R.string.settings_storage_hint)).assertIsDisplayed()

        // Clearing is destructive: the row opens a question, Cancel holds focus,
        // and only the explicit Clear row performs the wipe.
        clickRow(string(R.string.settings_storage_clear))
        rule.onNodeWithText(string(R.string.settings_cache_clear_title))
            .assertIsDisplayed()
            .assert(isHeading())
        rule.onNodeWithText(string(R.string.settings_cache_clear_hint)).assertIsDisplayed()
        waitForFocus(string(R.string.settings_cancel))

        press(Key.DirectionUp)
        press(Key.DirectionCenter)
        waitForRow(row(string(R.string.settings_storage_clear), "0"))
    }

    @Test
    fun system_page_shows_the_airplay_receiver_state() {
        setSettings()
        openDevice()

        clickRow(string(R.string.settings_system))

        rule.onNodeWithText(string(R.string.settings_system))
            .assertIsDisplayed()
            .assert(isHeading())
        waitForRow(string(R.string.settings_system_home))
        rule.onNodeWithContentDescription(
            string(R.string.settings_system_open),
        )
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        rule.onNodeWithContentDescription(
            string(R.string.settings_system_airplay),
            substring = true,
        ).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.settings_system_hint)).assertIsDisplayed()
    }

    @Test
    fun system_page_offers_the_home_button_and_startup_rows() {
        setSettings()
        openDevice()

        clickRow(string(R.string.settings_system))

        waitForRow(string(R.string.settings_home_button))
        rule.onNodeWithContentDescription(string(R.string.settings_home_button), substring = true)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        waitForRow(row(string(R.string.settings_start_open), string(R.string.startup_home)))
        // The hint explains that the switch is what actually takes the button.
        rule.onNodeWithText(string(R.string.settings_system_hint)).assertIsDisplayed()
    }

    @Test
    fun home_button_row_turns_the_override_on_without_opening_settings() {
        // On a device where this launcher is already Home, the row is "Not
        // needed" by design and has nothing to toggle.
        assumeFalse(DeviceFactsReader.isDefaultHome(context))
        StartupPreferences.setHomeOverride(context, false)
        setSettings()
        openDevice()

        clickRow(string(R.string.settings_system))
        waitForRow(
            row(string(R.string.settings_home_button), string(R.string.settings_home_button_off)),
        )

        // The first press only stores the preference; the system switch is a
        // separate, second press, so nothing may leave this screen here.
        clickRow(string(R.string.settings_home_button))
        assertTrue(StartupPreferences.homeOverride(context))

        val expected = when (AccessibilityStatus.state(context)) {
            AccessibilityStatus.State.CONNECTED -> R.string.settings_home_button_on
            AccessibilityStatus.State.STALE -> R.string.settings_home_button_stale
            AccessibilityStatus.State.OFF -> R.string.settings_home_button_allow
        }
        waitForRow(row(string(R.string.settings_home_button), string(expected)))
    }

    @Test
    fun permissions_root_row_counts_only_the_grants_that_can_be_given() {
        setSettings()

        val missing = PermissionSummary.missing(
            accessibilityConnected = AccessibilityStatus.state(context) == AccessibilityStatus.State.CONNECTED,
            accessibilityAvailable = AccessibilityStatus.available(context),
            listingsGranted = ContentPreferences.canReadListings(context),
            powerAllowed = SleepControls.hasAdmin(context),
            powerSupported = SleepControls.adminSupported(context),
            defaultHome = DeviceFactsReader.isDefaultHome(context),
            homeOverride = StartupPreferences.homeOverride(context),
            locationGranted = DeviceFactsReader.hasWifiNamePermission(context),
        )
        val summary = if (missing == 0) {
            string(R.string.settings_perms_all_allowed)
        } else {
            context.getString(R.string.settings_perms_remaining, missing)
        }

        // The count is read from the device after the first composition, so
        // polling for the computed row also proves it never sticks on a stale 0.
        openDevice()
        waitForRow(row(string(R.string.settings_permissions), summary))
    }

    @Test
    fun permissions_page_lists_every_real_grant_with_its_live_state() {
        setSettings()
        openDevice()

        clickRow(string(R.string.settings_permissions))

        rule.onNodeWithText(string(R.string.settings_permissions))
            .assertIsDisplayed()
            .assert(isHeading())

        // A lost switch needs a restart, not a new grant, exactly as Java's
        // Perms page says; the row must not fall back to "Allow".
        val accessibilityValue = when (AccessibilityStatus.state(context)) {
            AccessibilityStatus.State.CONNECTED -> R.string.settings_perms_allowed
            AccessibilityStatus.State.STALE -> R.string.settings_home_button_stale
            AccessibilityStatus.State.OFF -> R.string.settings_perms_allow
        }
        waitForRow(row(string(R.string.settings_perms_home_button), string(accessibilityValue)))

        waitForRow(
            row(
                string(R.string.settings_perms_listings),
                string(
                    if (ContentPreferences.canReadListings(context)) {
                        R.string.settings_perms_allowed
                    } else {
                        R.string.settings_perms_allow
                    },
                ),
            ),
        )

        // Power never promises a tap the TV cannot honour.
        val powerValue = when {
            SleepControls.hasAdmin(context) -> R.string.settings_perms_allowed
            SleepControls.adminSupported(context) -> R.string.settings_perms_allow
            else -> R.string.settings_perms_unavailable
        }
        waitForRow(row(string(R.string.settings_perms_power), string(powerValue)))

        // The Home button route is enough: a row must not ask for the system
        // HOME after the root counter has already stopped counting it.
        val homeReady = PermissionSummary.homeReady(
            defaultHome = DeviceFactsReader.isDefaultHome(context),
            homeOverride = StartupPreferences.homeOverride(context),
            accessibilityConnected = AccessibilityStatus.state(context) == AccessibilityStatus.State.CONNECTED,
        )
        waitForRow(
            row(
                string(R.string.settings_perms_home),
                string(
                    if (homeReady) {
                        R.string.settings_yes
                    } else {
                        R.string.settings_perms_setup
                    },
                ),
            ),
        )

        waitForRow(
            row(
                string(R.string.settings_perms_wifi),
                string(
                    if (DeviceFactsReader.hasWifiNamePermission(context)) {
                        R.string.settings_perms_allowed
                    } else {
                        R.string.settings_perms_allow
                    },
                ),
            ),
        )
    }

    @Test
    fun startup_choice_and_wake_are_stored_and_summarized() {
        setSettings(apps = listOf(app("one"), app("two")))
        openDevice()

        clickRow(string(R.string.settings_system))
        waitForRow(row(string(R.string.settings_start_open), string(R.string.startup_home)))
        clickRow(string(R.string.settings_start_open))

        rule.onNodeWithText(string(R.string.settings_start_title))
            .assertIsDisplayed()
            .assert(isHeading())
        rule.onNodeWithContentDescription(string(R.string.settings_start_home))
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    string(R.string.settings_selected),
                ),
            )

        clickRow("one")
        assertEquals("app:one", StartupPreferences.target(context))
        waitForRow(row(string(R.string.settings_start_open), "one"))

        waitForRow(
            row(string(R.string.settings_start_when), string(R.string.settings_start_when_boot)),
        )
        clickRow(string(R.string.settings_start_when))
        rule.onNodeWithText(string(R.string.settings_start_when_title))
            .assertIsDisplayed()
            .assert(isHeading())
        clickRow(string(R.string.settings_start_when_wake))
        rule.onNodeWithContentDescription(string(R.string.settings_start_when_wake))
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    string(R.string.settings_selected),
                ),
            )
        assertTrue(StartupPreferences.onWake(context))

        press(Key.Back)
        waitForRow(
            row(string(R.string.settings_start_when), string(R.string.settings_start_when_wake)),
        )
    }

    @Test
    fun updates_page_shows_the_catalogue_and_nothing_fake() {
        setSettings()
        openAboutGroup()

        clickRow(string(R.string.settings_updates))

        rule.onNodeWithText(string(R.string.settings_updates))
            .assertIsDisplayed()
            .assert(isHeading())
        rule.onNodeWithContentDescription(
            row(
                string(R.string.settings_aerial_catalog),
                context.resources.getQuantityString(
                    R.plurals.settings_clips_count,
                    testClips.size,
                    testClips.size,
                ),
            ),
        ).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.settings_updates_hint)).assertIsDisplayed()

        // The fake "Update Channel: Manual" row and the version duplicate are
        // gone; the catalogue figure is a read-only fact, so the refresh
        // action is the only control on the page.
        rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertCountEquals(1)
        rule.onNodeWithContentDescription(string(R.string.settings_aerial_update))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))

        // The hint sits in the fixed band above the list; its bottom must stay
        // clear of the first row.
        val hint = rule.onNodeWithText(string(R.string.settings_updates_hint))
            .getUnclippedBoundsInRoot()
        val catalog = rule.onNodeWithContentDescription(
            string(R.string.settings_aerial_catalog),
            substring = true,
        ).getUnclippedBoundsInRoot()
        assertTrue("hint bottom ${hint.bottom} overlaps row top ${catalog.top}", hint.bottom <= catalog.top)
    }

    @Test
    fun catalogue_refresh_reports_current_failed_and_updated_separately() {
        val results = ArrayDeque(
            listOf(
                AerialRefreshResult.UNCHANGED,
                AerialRefreshResult.FAILED,
                AerialRefreshResult.UPDATED,
            ),
        )
        setSettings(refreshCatalog = { results.removeFirst() })

        openAboutGroup()
        clickRow(string(R.string.settings_updates))
        // The page opens on the catalogue row; the refresh action is next.
        press(Key.DirectionDown)

        press(Key.DirectionCenter)
        waitForRow(
            row(string(R.string.settings_aerial_update), string(R.string.settings_catalog_unchanged)),
        )

        // A network error must never be presented as "Already current".
        press(Key.DirectionCenter)
        waitForRow(
            row(string(R.string.settings_aerial_update), string(R.string.settings_catalog_failed)),
        )

        press(Key.DirectionCenter)
        waitForRow(
            row(string(R.string.settings_aerial_update), string(R.string.settings_catalog_updated)),
        )
        assertTrue(results.isEmpty())
    }

    @Test
    fun resetting_the_saver_returns_every_option_to_defaults() {
        SaverPreferences.setDelay(context, SaverDelay.TWENTY)
        SaverPreferences.setMusic(context, false)
        SaverPreferences.setRefresh(context, SaverRefresh.NEVER)
        SaverPreferences.toggleTheme(context, SaverTheme.CITIES)
        SaverPreferences.toggleHidden(context, "aerial-1", visible = testClips.size)

        setSettings()
        openSaver()

        // The reset row is last, after Mode, Choose, Start After, Music, Themes,
        // Frequency and the system screen-saver row.
        repeat(7) { press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        rule.onNodeWithText(string(R.string.saver_reset_title))
            .assertIsDisplayed()
            .assert(isHeading())
        rule.onNodeWithText(string(R.string.saver_reset_hint)).assertIsDisplayed()
        waitForFocus(string(R.string.settings_cancel))

        press(Key.DirectionUp)
        press(Key.DirectionCenter)

        assertEquals(SaverDelay.FIVE, SaverPreferences.delay(context))
        assertTrue(SaverPreferences.music(context))
        assertEquals(SaverRefresh.WEEKLY, SaverPreferences.refresh(context))
        assertEquals(SaverTheme.entries.toSet(), SaverPreferences.themes(context))
        assertTrue(SaverPreferences.hidden(context).isEmpty())
        // The confirmation leaves the saver page with the restored summaries.
        waitForRow(row(string(R.string.saver_start_after), string(R.string.saver_delay_5)))
    }

    @Test
    fun legal_page_opens_the_terms_document() {
        setSettings()
        openLegal()

        rule.onNodeWithText(string(R.string.settings_legal))
            .assertIsDisplayed()
            .assert(isHeading())
        listOf(
            string(R.string.settings_terms),
            string(R.string.settings_privacy),
            string(R.string.settings_aerial_sources),
            string(R.string.settings_licenses),
        ).forEach { title ->
            rule.onNodeWithContentDescription(title).assertIsDisplayed()
        }

        click(string(R.string.settings_terms))
        // The document body repeats the term in its own paragraphs, so only
        // the heading proves the page opened.
        rule.onNode(hasText(string(R.string.settings_terms)) and isHeading())
            .assertIsDisplayed()
        // Every paragraph is its own focus target: TalkBack reads the text of
        // the focused node instead of an empty scroll view.
        rule.waitUntil(5_000) {
            rule.onAllNodes(hasText(string(R.string.settings_terms)) and isFocusable())
                .fetchSemanticsNodes()
                .any { it.config.getOrNull(SemanticsProperties.Focused) == true }
        }
        press(Key.DirectionDown)
        rule.waitUntil(5_000) {
            rule.onAllNodes(hasText("OVisionTV Launcher", substring = true) and isFocusable())
                .fetchSemanticsNodes()
                .any { it.config.getOrNull(SemanticsProperties.Focused) == true }
        }

        press(Key.Back)
        rule.waitForIdle()
        rule.onNodeWithText(string(R.string.settings_legal)).assertIsDisplayed()
    }

    @Test
    fun a_held_arrow_moves_the_chooser_one_step() {
        setSettings()
        openSaver()
        click(row(string(R.string.saver_choose), string(R.string.saver_aerials)))

        // A held key arrives as repeated ACTION_DOWNs, and a host keyboard may
        // report them with repeatCount 0; the burst must act once.
        holdKey(AndroidKeyEvent.KEYCODE_DPAD_RIGHT, repeats = 6)
        rule.waitForIdle()

        waitForFocus("Aerial 2")
        rule.onNodeWithContentDescription("Aerial 3").assertIsNotFocused()
    }

    @Test
    fun repeated_ok_down_still_opens_the_saver_preview() {
        val previewed = mutableListOf<String>()
        setSettings(
            previewSurface = { clip ->
                Box(Modifier.fillMaxSize().testTag(SAVER_PREVIEW_STUB))
                LaunchedEffect(clip) { previewed += clip.id }
            },
        )
        openSaver()
        click(row(string(R.string.saver_choose), string(R.string.saver_aerials)))

        // The hold arrives with an auto-repeat; each repeat used to restart the
        // 600 ms timer, so the preview never opened.
        val start = SystemClock.uptimeMillis()
        dispatchRawKey(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_DPAD_CENTER, eventTime = start)
        for (step in 1..8) {
            dispatchRawKey(
                AndroidKeyEvent.ACTION_DOWN,
                AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                eventTime = start + step * 40L,
            )
        }
        rule.mainClock.advanceTimeBy(700)
        rule.waitForIdle()
        rule.onNodeWithTag(SAVER_PREVIEW_STUB).assertIsDisplayed()
        assertEquals(listOf("aerial-1"), previewed)

        // The release that ends the hold closes nothing and toggles nothing.
        dispatchRawKey(
            AndroidKeyEvent.ACTION_UP,
            AndroidKeyEvent.KEYCODE_DPAD_CENTER,
            eventTime = start + 400L,
        )
        rule.mainClock.advanceTimeBy(50)
        rule.waitForIdle()
        rule.onNodeWithTag(SAVER_PREVIEW_STUB).assertIsDisplayed()
        assertEquals(emptySet<String>(), SaverPreferences.hidden(context))
    }

    @Test
    fun moving_along_one_row_does_not_scroll_the_grid() {
        // Two full rows fit the grid area: neither moving to the second row nor
        // moving inside it may pull the list up and hide the first row.
        val clips = (1..6).map { clip("aerial-$it", SaverTheme.LANDSCAPES, "Aerial $it", it - 1) }
        setSettings(clips = clips)
        openSaver()
        click(row(string(R.string.saver_choose), string(R.string.saver_aerials)))

        press(Key.DirectionDown)
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Aerial 1").assertIsDisplayed()
        val secondRowTop = rule.onNodeWithContentDescription("Aerial 4")
            .getUnclippedBoundsInRoot().top

        press(Key.DirectionRight)
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Aerial 1").assertIsDisplayed()
        assertEquals(
            secondRowTop,
            rule.onNodeWithContentDescription("Aerial 4").getUnclippedBoundsInRoot().top,
        )
    }
}
