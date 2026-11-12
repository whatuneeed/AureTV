@file:OptIn(ExperimentalTestApi::class)

package com.whatuneed.ovisiontv.kotlin.ui

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.whatuneed.ovisiontv.kotlin.KidsEntryGrant
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.SettingsActivity
import com.whatuneed.ovisiontv.kotlin.data.AerialCatalog
import com.whatuneed.ovisiontv.kotlin.data.AppRepository
import com.whatuneed.ovisiontv.kotlin.data.KidsMode
import com.whatuneed.ovisiontv.kotlin.data.KidsStore
import com.whatuneed.ovisiontv.kotlin.data.LauncherPrefs
import com.whatuneed.ovisiontv.kotlin.data.ProfileStore
import com.whatuneed.ovisiontv.kotlin.data.SaverDelay
import com.whatuneed.ovisiontv.kotlin.data.SaverPreferences
import com.whatuneed.ovisiontv.kotlin.data.StartupPreferences
import com.whatuneed.ovisiontv.kotlin.ui.components.SAVER_SCREEN_TAG
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The kids gates walk the real LauncherRoot with seeded stores: the grid must
 * hide tiles the allowlist does not name, the launcher's own Settings tile must
 * open the PIN pad instead of Settings, five wrong digits must start the
 * lockout, leaving the profile must ask for the same PIN, and the recovery
 * word must replace the PIN without performing the gated action.
 *
 * The tests drive semantics the way TalkBack does, so no real app is ever
 * launched: the allowed tile is never activated, and the only activity the
 * suite starts is the launcher's own Settings, which is finished before the
 * next test.
 */
@RunWith(AndroidJUnit4::class)
class KidsGateTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    private val pin = "1234"
    private val kidsName = "Малыш"
    private lateinit var kidsProfileId: String

    private lateinit var savedPrefs: Map<String, Any?>
    private lateinit var savedPrefsP2: Map<String, Any?>
    private lateinit var savedSetup: Map<String, Any?>
    private lateinit var savedStartup: Map<String, Any?>
    private lateinit var savedProfiles: Map<String, Any?>
    private lateinit var savedKids: Map<String, Any?>

    private val apps by lazy {
        runBlocking { AppRepository(context).load(withArtwork = false) }
    }

    /** Two foreign packages the assertions lean on; the grid hides the rest. */
    private val allowedApp by lazy {
        apps.first { it.packageName != context.packageName }
    }

    private val blockedApp by lazy {
        apps.filter { it.packageName != context.packageName }[1]
    }

    private val settingsLabel: String =
        context.getString(R.string.settings_tile_name)

    @Before
    fun seedKidsProfile() {
        savedPrefs = PrefsSnapshot.capture(context)
        savedPrefsP2 = readPrefs("${LauncherPrefs.NAME}_p2")
        savedSetup = PrefsSnapshot.captureSetup(context)
        savedStartup = PrefsSnapshot.captureStartup(context)
        savedProfiles = PrefsSnapshot.captureProfiles(context)
        savedKids = PrefsSnapshot.captureKids(context)

        PrefsSnapshot.markSetupDone(context)
        // No boot picker and no wake-open target: the picker must only appear
        // when a test bumps wakeTick itself.
        context.getSharedPreferences(StartupPreferences.NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        // Five minutes of idle would never pass inside a test, but NEVER makes
        // the point explicit: the saver must not cover a gate mid-test.
        SaverPreferences.setDelay(context, SaverDelay.NEVER)

        val store = ProfileStore(context)
        val kids = store.add(kidsName) ?: error("profile add failed")
        kidsProfileId = kids.id
        store.update(kids.copy(kids = true))
        store.setCurrent(kids.id)
        // The boot picker must not ambush a test; the wake one is welcome.
        store.setAskOnBoot(false)
        store.setAskOnWake(true)

        val kidsStore = KidsStore(context)
        runBlocking { kidsStore.setPin(kids.id, pin) }
        kidsStore.setAllowlist(kids.id, setOf(allowedApp.packageName))
    }

    @After
    fun restoreStores() {
        resumedSettings()?.let { settings ->
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                settings.finish()
            }
        }
        PrefsSnapshot.restore(context, savedPrefs)
        writePrefs("${LauncherPrefs.NAME}_p2", savedPrefsP2)
        PrefsSnapshot.restoreSetup(context, savedSetup)
        PrefsSnapshot.restoreStartup(context, savedStartup)
        PrefsSnapshot.restoreProfiles(context, savedProfiles)
        PrefsSnapshot.restoreKids(context, savedKids)
    }

    private fun readPrefs(name: String): Map<String, Any?> =
        context.getSharedPreferences(name, Context.MODE_PRIVATE).all

    private fun writePrefs(name: String, values: Map<String, Any?>) {
        val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
        for ((key, value) in values) {
            when (value) {
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        editor.commit()
    }

    private fun composeRoot() {
        rule.setContent {
            OVisionTheme {
                LauncherRoot()
            }
        }
    }

    private fun waitForTile(label: String) {
        rule.waitUntil(20_000) {
            rule.onAllNodesWithContentDescription(label)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun click(description: String) {
        rule.onAllNodesWithContentDescription(description)
            .onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
    }

    private fun press(key: Key) {
        rule.onRoot().performKeyInput {
            keyDown(key)
            keyUp(key)
        }
        rule.waitForIdle()
    }

    private fun focusedDescription(): String? =
        rule.onNode(
            SemanticsMatcher("focused") {
                it.config.getOrNull(SemanticsProperties.Focused) == true
            },
        ).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.ContentDescription)
            ?.joinToString()

    /** The pad auto-submits on the fourth digit, one click per digit. */
    private fun typePin(value: String) {
        value.forEach { digit -> click(digit.toString()) }
    }

    private fun waitForFailedPin(expectedFailures: Int) {
        val prefs = context.getSharedPreferences(KidsStore.FILE, Context.MODE_PRIVATE)
        // waitForIdle and a stale error note do not await the background PIN check.
        rule.waitUntil(10_000) {
            prefs.getInt("fail_$kidsProfileId", 0) == expectedFailures &&
                rule.onAllNodesWithTag("pin-pad-ready").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openGate() {
        composeRoot()
        waitForTile(allowedApp.label)
        click(settingsLabel)
        rule.waitUntil(10_000) {
            rule.onAllNodesWithContentDescription(context.getString(R.string.kids_pin_title))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun resumedSettings(): SettingsActivity? {
        var found: SettingsActivity? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            found = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<SettingsActivity>()
                .firstOrNull()
        }
        return found
    }

    private fun closeSettings() {
        val settings = checkNotNull(resumedSettings())
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            settings.finish()
        }
        rule.waitUntil(20_000) { resumedSettings() == null }
    }

    @Test
    fun kids_profile_shows_only_allowed_tiles_and_the_settings_tile() {
        composeRoot()
        waitForTile(allowedApp.label)

        val allowed = rule.onAllNodesWithContentDescription(allowedApp.label)
            .fetchSemanticsNodes()
        assertTrue("the allowed app must stay on the grid", allowed.isNotEmpty())
        assertTrue(
            "the blocked app must be hidden everywhere on Home",
            rule.onAllNodesWithContentDescription(blockedApp.label)
                .fetchSemanticsNodes().isEmpty(),
        )
        // The launcher's own tile is not an app: it stays visible so the child
        // can reach the gate, and the gate decides what happens next.
        waitForTile(settingsLabel)
    }

    @Test
    fun settings_tile_opens_the_pin_gate_and_the_right_pin_opens_settings() {
        openGate()

        // The gate names the surface it protects.
        rule.onNodeWithText(context.getString(R.string.kids_gate_settings))
            .assertExists()

        typePin("0000")
        waitForFailedPin(1)
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText(context.getString(R.string.kids_pin_wrong))
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "a wrong PIN must not open Settings",
            resumedSettings() == null,
        )

        typePin(pin)
        rule.waitUntil(20_000) { resumedSettings() != null }
        closeSettings()
    }

    // UX-01: the Control Center's Android Settings tile is the same door into
    // system Settings, which can clear the launcher. It must ask for the PIN.
    @Test
    fun control_center_system_tile_opens_the_pin_gate() {
        composeRoot()
        waitForTile(allowedApp.label)

        press(Key.DirectionUp)
        press(Key.DirectionCenter)
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
        rule.onNodeWithContentDescription(context.getString(R.string.cc_power))
            .assertIsFocused()

        // Down from the tile row drops into the circles; Right walks them.
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        val systemLabel = context.getString(R.string.cc_system)
        var steps = 0
        while (focusedDescription() != systemLabel && steps < 8) {
            press(Key.DirectionRight)
            steps++
        }
        assertEquals("the Android tile was not reached", systemLabel, focusedDescription())

        press(Key.DirectionCenter)
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithContentDescription(context.getString(R.string.kids_pin_title))
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(context.getString(R.string.kids_gate_system)).assertExists()
    }

    @Test
    fun five_wrong_pins_lock_the_pad_against_the_right_pin_too() {
        openGate()

        repeat(KidsMode.FREE_ATTEMPTS + 1) { attempt ->
            typePin("0000")
            waitForFailedPin(attempt + 1)
        }
        // The countdown ticks, so the match must use the part before the
        // number: the substituted 30 marks where the digits begin.
        val lockPrefix = context.getString(R.string.kids_lock_wait, 30)
            .substringBefore("30")
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText(lockPrefix, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // The lockout eats input as well: the correct PIN must not pass.
        typePin(pin)
        rule.waitForIdle()
        assertTrue(
            "the lockout must hold even for the correct PIN",
            resumedSettings() == null,
        )
    }

    @Test
    fun leaving_the_kids_profile_asks_for_the_pin_and_then_switches() {
        var wake by mutableIntStateOf(0)
        rule.setContent {
            OVisionTheme {
                LauncherRoot(wakeTick = wake)
            }
        }
        waitForTile(allowedApp.label)

        // The wake path is the deterministic way to open "Who's watching?".
        rule.runOnIdle { wake++ }
        val parentName = ProfileStore(context).list().first { !it.kids }.name
        rule.waitUntil(20_000) {
            rule.onAllNodesWithContentDescription(parentName)
                .fetchSemanticsNodes().isNotEmpty()
        }

        click(parentName)
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText(context.getString(R.string.kids_pin_leave))
                .fetchSemanticsNodes().isNotEmpty()
        }

        typePin(pin)
        // The parent layout shows every app again, so the blocked label
        // returning proves the switch happened.
        rule.waitUntil(20_000) {
            rule.onAllNodesWithContentDescription(blockedApp.label)
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "the picker must be gone after the switch",
            rule.onAllNodesWithContentDescription(parentName)
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    // UX-08: BACK on the leave gate must put the cursor back on the disc the
    // picker was left on; otherwise the picker stays up with focus in a pad
    // that no longer exists and the remote appears dead.
    @Test
    fun cancelling_the_leave_gate_returns_focus_to_the_picker_disc() {
        var wake by mutableIntStateOf(0)
        rule.setContent {
            OVisionTheme {
                LauncherRoot(wakeTick = wake)
            }
        }
        waitForTile(allowedApp.label)

        rule.runOnIdle { wake++ }
        val profiles = ProfileStore(context).list()
        val parentName = profiles.first { !it.kids }.name
        rule.waitUntil(20_000) {
            rule.onAllNodesWithContentDescription(parentName)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // The picker opens on the current (kids) disc; walk the cursor to the
        // parent disc the way the remote would, so the cancel has an index to
        // return to.
        val kidsIndex = profiles.indexOfFirst { it.id == kidsProfileId }
        val parentIndex = profiles.indexOfFirst { !it.kids }
        val step = if (parentIndex > kidsIndex) Key.DirectionRight else Key.DirectionLeft
        repeat(kotlin.math.abs(parentIndex - kidsIndex)) { press(step) }
        rule.onNodeWithContentDescription(parentName).assertIsFocused()

        press(Key.DirectionCenter)
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText(context.getString(R.string.kids_pin_leave))
                .fetchSemanticsNodes().isNotEmpty()
        }

        press(Key.Back)
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText(context.getString(R.string.kids_pin_leave))
                .fetchSemanticsNodes().isEmpty()
        }
        rule.onNodeWithContentDescription(parentName).assertIsFocused()
        assertTrue(
            "cancelling the gate must not switch profiles",
            rule.onAllNodesWithContentDescription(blockedApp.label)
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun recovery_word_resets_the_pin_and_closes_the_gate_without_the_action() {
        val store = KidsStore(context)
        runBlocking { store.setRecovery(kidsProfileId, "Кличка питомца", "test") }

        openGate()

        click(context.getString(R.string.kids_forgot))
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText(context.getString(R.string.kids_gate_recover_title))
                .fetchSemanticsNodes().isNotEmpty()
        }

        // The keyboard follows the device language; force the Latin layout
        // when the mode key is offered, then spell the answer.
        val modeLatin = context.getString(R.string.keyboard_mode_latin)
        if (rule.onAllNodesWithContentDescription(modeLatin)
                .fetchSemanticsNodes().isNotEmpty()
        ) {
            click(modeLatin)
        }
        "test".forEach { letter -> click(letter.toString()) }
        // The caret is painted, not spoken: the field's semantics text is the
        // typed value alone (RenameKeyboardTest pins the same rule).
        rule.onNodeWithText("test").assertExists()
        click(context.getString(R.string.keyboard_done))

        // A correct answer opens the new-PIN pad, not the gated action.
        rule.waitUntil(10_000) {
            rule.onAllNodesWithContentDescription(context.getString(R.string.kids_pin_new))
                .fetchSemanticsNodes().isNotEmpty()
        }
        typePin("9999")
        click("OK")

        rule.waitUntil(10_000) {
            rule.onAllNodesWithContentDescription(context.getString(R.string.kids_pin_new))
                .fetchSemanticsNodes().isEmpty() &&
                runBlocking { store.checkPin(kidsProfileId, "9999") }
        }
        assertTrue(
            "recovery must not perform the gated action",
            resumedSettings() == null,
        )
        runBlocking {
            assertTrue("the new PIN must verify", store.checkPin(kidsProfileId, "9999"))
            assertFalse("the old PIN must be gone", store.checkPin(kidsProfileId, pin))
        }
        assertNotNull(store.recoveryHint(kidsProfileId))
    }

    /**
     * The pad is a prompt, not an idle surface. On the old code the timer kept
     * running behind the gate and the saver appeared under the pad. The idle
     * clock is the real [SystemClock], while the test rule freezes coroutine
     * delays until its clock is advanced: the real idle time is accumulated
     * first, then the pending deadline is stepped over in one move.
     */
    @Test
    fun saver_never_starts_under_the_pin_gate() {
        assumeTrue("the saver needs clips", AerialCatalog.load(context).isNotEmpty())
        SaverPreferences.setDelay(context, SaverDelay.TWO)

        openGate()
        // Two minutes is the shortest delay the real settings allow.
        Thread.sleep(125_000)
        rule.mainClock.advanceTimeBy(125_000)
        rule.waitForIdle()

        assertFalse(
            "the saver started behind the PIN pad",
            rule.onAllNodesWithTag(SAVER_SCREEN_TAG).fetchSemanticsNodes().isNotEmpty(),
        )
        rule.onNodeWithText(context.getString(R.string.kids_gate_settings)).assertExists()
    }
}

/**
 * AQ-01: SettingsActivity is exported, so a foreign app can start it with any
 * extra it likes. The entry gate must only trust the launcher's in-process
 * grant, never the intent. The real activity is launched here; the empty
 * compose rule reads its semantics through the global compose root registry.
 */
@RunWith(AndroidJUnit4::class)
class SettingsEntryGrantTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var savedProfiles: Map<String, Any?>
    private lateinit var savedKids: Map<String, Any?>
    private lateinit var kidsProfileId: String

    @Before
    fun seedKidsProfile() {
        KidsEntryGrant.clear()
        savedProfiles = PrefsSnapshot.captureProfiles(context)
        savedKids = PrefsSnapshot.captureKids(context)
        val store = ProfileStore(context)
        val kids = store.add("Малыш") ?: error("profile add failed")
        kidsProfileId = kids.id
        store.update(kids.copy(kids = true))
        store.setCurrent(kids.id)
        runBlocking { KidsStore(context).setPin(kids.id, "1234") }
    }

    @After
    fun restoreStores() {
        KidsEntryGrant.clear()
        PrefsSnapshot.restoreProfiles(context, savedProfiles)
        PrefsSnapshot.restoreKids(context, savedKids)
    }

    private fun settingsIntent(extraKidsOk: Boolean): Intent {
        val intent = Intent(context, SettingsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // The literal extra a foreign app would forge; the constant is gone
        // from the activity, so the test must not depend on it.
        if (extraKidsOk) intent.putExtra("kids_ok", true)
        return intent
    }

    private fun gateVisible(): Boolean =
        rule.onAllNodesWithContentDescription(context.getString(R.string.kids_pin_title))
            .fetchSemanticsNodes().isNotEmpty()

    private fun awaitSettingsRoot() {
        rule.waitUntil(20_000) {
            rule.onAllNodesWithContentDescription(
                context.getString(R.string.settings_profiles),
                substring = true,
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun forged_kids_ok_extra_does_not_unlock_the_entry_gate() {
        ActivityScenario.launch<SettingsActivity>(settingsIntent(extraKidsOk = true)).use {
            rule.waitUntil(20_000) { gateVisible() }
        }
    }

    @Test
    fun issued_grant_unlocks_the_entry_gate_once() {
        KidsEntryGrant.issue(kidsProfileId)
        ActivityScenario.launch<SettingsActivity>(settingsIntent(extraKidsOk = false)).use {
            awaitSettingsRoot()
            assertFalse("the launcher's own PIN pass must not re-open the gate", gateVisible())
        }
        assertFalse("the grant must be consumed by the start", KidsEntryGrant.consume(kidsProfileId))
    }

    @Test
    fun warm_start_with_a_grant_unlocks_the_gate() {
        ActivityScenario.launch<SettingsActivity>(settingsIntent(extraKidsOk = false)).use { scenario ->
            rule.waitUntil(20_000) { gateVisible() }
            // The launcher passes its gate while Settings is already up: the
            // singleTop activity receives the grant through onNewIntent.
            KidsEntryGrant.issue(kidsProfileId)
            scenario.onActivity { activity ->
                activity.startActivity(
                    Intent(activity, SettingsActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                )
            }
            rule.waitUntil(20_000) { !gateVisible() }
            awaitSettingsRoot()
        }
    }
}
