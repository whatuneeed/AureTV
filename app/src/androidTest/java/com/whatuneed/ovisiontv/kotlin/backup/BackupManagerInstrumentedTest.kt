package com.whatuneed.ovisiontv.kotlin.backup

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.ambient.AmbientClockFormat
import com.whatuneed.ovisiontv.kotlin.ambient.AmbientDim
import com.whatuneed.ovisiontv.kotlin.ambient.AmbientMode
import com.whatuneed.ovisiontv.kotlin.ambient.AmbientSession
import com.whatuneed.ovisiontv.kotlin.ambient.AmbientSettingsStore
import com.whatuneed.ovisiontv.kotlin.data.ContentPreferences
import com.whatuneed.ovisiontv.kotlin.data.HomeItem
import com.whatuneed.ovisiontv.kotlin.data.HomeOrderStore
import com.whatuneed.ovisiontv.kotlin.data.KidsStore
import com.whatuneed.ovisiontv.kotlin.data.LauncherPrefs
import com.whatuneed.ovisiontv.kotlin.data.LocalePreference
import com.whatuneed.ovisiontv.kotlin.data.Profile
import com.whatuneed.ovisiontv.kotlin.data.ProfileCatalog
import com.whatuneed.ovisiontv.kotlin.data.ProfileStore
import com.whatuneed.ovisiontv.kotlin.data.SaverPreferences
import com.whatuneed.ovisiontv.kotlin.data.SaverRefresh
import com.whatuneed.ovisiontv.kotlin.data.SaverTheme
import com.whatuneed.ovisiontv.kotlin.data.SetupProgress
import com.whatuneed.ovisiontv.kotlin.data.StartupPreferences
import com.whatuneed.ovisiontv.kotlin.data.TvApp
import com.whatuneed.ovisiontv.kotlin.data.WeatherPreferences
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoverySettings
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryStore
import com.whatuneed.ovisiontv.kotlin.ui.PrefsSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * Exercises the real SharedPreferences-backed path. The JVM codec tests prove
 * the format; this class proves that a local copy can actually replace live
 * data and that the pre-restore rollback point and journal are wired correctly.
 */
@RunWith(AndroidJUnit4::class)
class BackupManagerInstrumentedTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    private val names = listOf(
        LauncherPrefs.NAME,
        ProfileStore.homePrefsName("p2"),
        ProfileStore.FILE,
        KidsStore.FILE,
        WeatherPreferences.NAME,
        StartupPreferences.NAME,
        SetupProgress.NAME,
    )

    private lateinit var savedPrefs: Map<String, Map<String, Any?>>
    private var savedBackupDir: File? = null

    @Before
    fun isolateAndSeed() {
        savedPrefs = names.associateWith { PrefsSnapshot.capturePrefs(context, it) }
        names.forEach { PrefsSnapshot.clearPrefs(context, it) }

        // The backup directory is real app data too. Move it aside for the
        // duration of the test, then put it back even if an assertion fails.
        val backupDir = File(context.filesDir, "backups")
        if (backupDir.exists()) {
            val aside = File(context.cacheDir, "backup-test-${UUID.randomUUID()}")
            check(backupDir.renameTo(aside)) { "could not move the existing backup directory" }
            savedBackupDir = aside
        }

        seed()
    }

    @After
    fun restoreUserState() {
        names.forEach { PrefsSnapshot.restorePrefs(context, it, savedPrefs.getValue(it)) }
        val backupDir = File(context.filesDir, "backups")
        if (backupDir.exists()) backupDir.deleteRecursively()
        savedBackupDir?.let { aside ->
            if (aside.exists()) check(aside.renameTo(backupDir))
        }
        savedBackupDir = null
    }

    @Test
    fun localBackupRestoresEveryCategoryAndKeepsAPinnedRollbackPoint() = runBlocking {
        val manager = BackupManager(context)
        val created = manager.createLocal(BackupSelection(), BackupTrigger.MANUAL)
        assertEquals(BackupContract.FORMAT_VERSION, created.formatVersion)
        assertTrue(manager.list().any { it.info.backupId == created.info.backupId })

        mutateEverything()

        val preview = manager.previewLocal(created, null)
        assertEquals(BackupCategory.entries.toSet(), preview.categories)
        manager.restore(preview, BackupSelection())

        assertEquals("p1", ProfileStore(context).current().id)
        assertEquals("Owner", ProfileStore(context).list().first { it.id == "p1" }.name)
        assertEquals("ru", LocalePreference.get(context))
        assertTrue(ContentPreferences.recommendations(context))
        assertFalse(ContentPreferences.continueWatching(context))
        assertEquals(20, SaverPreferences.delay(context).minutes)
        assertFalse(SaverPreferences.music(context))
        assertEquals(SaverRefresh.MONTHLY, SaverPreferences.refresh(context))
        assertEquals(
            setOf(SaverTheme.LANDSCAPES, SaverTheme.EARTH),
            SaverPreferences.themes(context),
        )
        assertEquals(setOf("clip-1"), SaverPreferences.hidden(context))
        assertEquals(AmbientMode.AERIAL, AmbientSettingsStore.mode(context))
        assertEquals(AmbientClockFormat.SYSTEM, AmbientSettingsStore.clockFormat(context))
        assertTrue(AmbientSettingsStore.pixelShift(context))
        assertEquals(AmbientDim.NEVER, AmbientSettingsStore.dimAfter(context))
        assertEquals(AmbientSession.NEVER, AmbientSettingsStore.maxSession(context))
        assertFalse(AmbientSettingsStore.showWeather(context))

        assertTrue(WeatherPreferences.enabled(context))
        assertTrue(WeatherPreferences.manual(context))
        assertEquals("Samara", WeatherPreferences.place(context))
        assertEquals("f", WeatherPreferences.units(context))
        assertEquals("met", WeatherPreferences.source(context))
        assertEquals(30, WeatherPreferences.refreshMinutes(context))
        assertNull(WeatherPreferences.cachedJson(context))

        assertEquals("app:com.example.one", StartupPreferences.target(context))
        assertEquals("One", StartupPreferences.label(context))
        assertTrue(StartupPreferences.onWake(context))
        assertEquals(setOf("com.example.two"), KidsStore(context).allowlist("p2"))
        assertEquals(
            2,
            DiscoveryStore(context, LauncherPrefs.NAME).usage()["com.example.one"]?.count,
        )
        assertEquals(
            "com.example.two",
            (HomeOrderStore(context, ProfileStore.homePrefsName("p2"))
                .applyOrder(listOf(app("com.example.two")))
                .single() as HomeItem.AppItem).app.packageName,
        )

        val home = HomeOrderStore(context).applyOrder(
            listOf(app("com.example.one"), app("com.example.two")),
        )
        assertEquals("com.example.one", (home[0] as HomeItem.AppItem).app.packageName)
        val folder = home[1] as HomeItem.FolderItem
        assertEquals("f1", folder.id)
        assertEquals(listOf("com.example.two", "com.example.missing"), folder.apps.map { it.packageName })
        assertFalse(folder.apps.last().available)

        val entries = manager.list()
        assertTrue(entries.any { it.info.trigger == BackupTrigger.PRE_RESTORE && it.info.pinned })
        assertFalse(File(context.filesDir, "backups/journal.json").exists())
        assertTrue(SetupProgress.done(context))
    }

    @Test
    fun aKidsProfileWithoutAPinIsRestoredAsNonKids() = runBlocking {
        val profiles = JSONArray()
            .put(profile("p1", "Owner", ProfileCatalog.EMOJIS[0], 0, kids = false))
            .put(profile("p2", "Child", ProfileCatalog.EMOJIS[6], 1, kids = true))
        context.getSharedPreferences(ProfileStore.FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(ProfileStore.KEY_LIST, profiles.toString())
            .commit()

        val manager = BackupManager(context)
        val created = manager.createLocal(BackupSelection(), BackupTrigger.MANUAL)
        mutateEverything()
        manager.restore(manager.previewLocal(created, null), BackupSelection())

        val restored = ProfileStore(context).list().first { it.id == "p2" }
        assertFalse(restored.kids)
        assertFalse(KidsStore(context).hasPin("p2"))
    }

    @Test
    fun theAlarmReceiverCreatesAnAutomaticCopy() = runBlocking {
        context.sendBroadcast(Intent(context, BackupAlarmReceiver::class.java))
        val manager = BackupManager(context)
        repeat(50) {
            if (manager.list().any { it.info.trigger == BackupTrigger.AUTO }) return@runBlocking
            delay(200)
        }
        throw AssertionError("the alarm receiver did not create an automatic copy")
    }

    @Test
    fun anEncryptedExportRoundTripsThroughTheContentResolver() = runBlocking {
        val manager = BackupManager(context)
        val password = "correct horse battery".toCharArray()
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "ovisiontv-test-${UUID.randomUUID()}.ovbk")
            put(MediaStore.Downloads.MIME_TYPE, "application/zip")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri: Uri = context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            values,
        ) ?: throw AssertionError("could not create a Downloads entry")
        try {
            manager.export(uri, BackupSelection(), password)
            val pending = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            context.contentResolver.update(uri, pending, null, null)

            val preview = manager.previewUri(uri, password)
            assertTrue(preview.encrypted)
            assertEquals(BackupCategory.entries.toSet(), preview.categories)

            try {
                manager.previewUri(uri, "wrong password".toCharArray())
                throw AssertionError("wrong password must not open an encrypted export")
            } catch (expected: BackupException) {
                assertEquals(BackupStage.CRYPTO, expected.stage)
            }
        } finally {
            context.contentResolver.delete(uri, null, null)
        }
    }

    @Test
    fun anInterruptedRestoreIsRolledBackFromTheJournal() = runBlocking {
        val manager = BackupManager(context)
        val rollbackPoint = manager.createLocal(BackupSelection(), BackupTrigger.PRE_RESTORE)
        mutateEverything()

        RestoreJournal(context).write(
            RestoreJournalState(
                state = "applying",
                backupId = "interrupted-backup",
                preRestoreId = rollbackPoint.info.backupId,
                startedAt = "2026-11-15T00:00:00Z",
            ),
        )

        assertTrue(manager.recoverIfNeeded())
        assertFalse(File(context.filesDir, "backups/journal.json").exists())
        assertEquals("ru", LocalePreference.get(context))
        assertEquals("p1", ProfileStore(context).current().id)
        assertTrue(WeatherPreferences.manual(context))
        assertEquals("Samara", WeatherPreferences.place(context))
        assertTrue(StartupPreferences.onWake(context))
    }

    private fun seed() {
        val profiles = JSONArray()
            .put(profile("p1", "Owner", ProfileCatalog.EMOJIS[0], 0, kids = false))
            .put(profile("p2", "Child", ProfileCatalog.EMOJIS[6], 1, kids = false))
        context.getSharedPreferences(ProfileStore.FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(ProfileStore.KEY_LIST, profiles.toString())
            .putString(ProfileStore.KEY_CURRENT, "p1")
            .putInt(ProfileStore.KEY_NEXT_ID, 3)
            .putBoolean(ProfileStore.KEY_ASK_ON_BOOT, true)
            .putBoolean(ProfileStore.KEY_ASK_ON_WAKE, false)
            .commit()

        context.getSharedPreferences(LauncherPrefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(
                LauncherPrefs.KEY_HOME_ORDER,
                """["com.example.one",{"id":"f1","name":"Kino","apps":["com.example.two","com.example.missing"]}]""",
            )
            .putString(LocalePreference.KEY, "ru")
            .putBoolean(LauncherPrefs.KEY_SHOW_PHONE_APPS, false)
            .putBoolean(ContentPreferences.KEY_RECOMMENDATIONS, true)
            .putBoolean(ContentPreferences.KEY_CONTINUE_WATCHING, false)
            .putInt(SaverPreferences.KEY_DELAY_MINUTES, 20)
            .putBoolean(SaverPreferences.KEY_MUSIC, false)
            .putString(SaverPreferences.KEY_REFRESH, "MONTHLY")
            .putString(SaverPreferences.KEY_THEMES, "landscapes,earth")
            .putString(SaverPreferences.KEY_HIDDEN_CLIPS, "clip-1")
            .commit()

        context.getSharedPreferences(ProfileStore.homePrefsName("p2"), Context.MODE_PRIVATE)
            .edit()
            .putString(LauncherPrefs.KEY_HOME_ORDER, """["com.example.two"]""")
            .commit()

        DiscoverySettings(context, LauncherPrefs.NAME).setPersonalization(true)
        DiscoveryStore(context, LauncherPrefs.NAME).recordLaunch("com.example.one")
        DiscoveryStore(context, LauncherPrefs.NAME).recordLaunch("com.example.one")
        KidsStore(context).setAllowlist("p2", setOf("com.example.two"))

        context.getSharedPreferences(WeatherPreferences.NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(WeatherPreferences.K_ENABLED, true)
            .putBoolean(WeatherPreferences.K_MANUAL, true)
            .putString(WeatherPreferences.K_PLACE, "Samara")
            .putLong(WeatherPreferences.K_LAT, 53.2.toRawBits())
            .putLong(WeatherPreferences.K_LON, 50.1.toRawBits())
            .putString(WeatherPreferences.K_UNITS, "f")
            .putString(WeatherPreferences.K_SOURCE, "met")
            .putInt(WeatherPreferences.K_REFRESH, 30)
            .putString(WeatherPreferences.K_CACHE, """{"temp":1}""")
            .putLong(WeatherPreferences.K_CACHE_AT, 123L)
            .putString(WeatherPreferences.K_CACHE_KEY, "f|met|53.2,50.1")
            .putInt(WeatherPreferences.K_GENERATION, 4)
            .commit()

        context.getSharedPreferences(StartupPreferences.NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(StartupPreferences.KEY_TARGET, "app:com.example.one")
            .putString(StartupPreferences.KEY_LABEL, "One")
            .putBoolean(StartupPreferences.KEY_ON_WAKE, true)
            .commit()
    }

    private fun mutateEverything() {
        context.getSharedPreferences(LauncherPrefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(LauncherPrefs.KEY_HOME_ORDER, """["com.example.two"]""")
            .putString(LocalePreference.KEY, "en")
            .putBoolean(ContentPreferences.KEY_RECOMMENDATIONS, false)
            .putBoolean(ContentPreferences.KEY_CONTINUE_WATCHING, true)
            .putInt(SaverPreferences.KEY_DELAY_MINUTES, 2)
            .putBoolean(SaverPreferences.KEY_MUSIC, true)
            .putString(SaverPreferences.KEY_REFRESH, "DAILY")
            .putString(SaverPreferences.KEY_THEMES, "cities")
            .putString(SaverPreferences.KEY_HIDDEN_CLIPS, "")
            .commit()
        context.getSharedPreferences(ProfileStore.homePrefsName("p2"), Context.MODE_PRIVATE)
            .edit()
            .putString(LauncherPrefs.KEY_HOME_ORDER, """["com.example.missing"]""")
            .commit()
        context.getSharedPreferences(ProfileStore.FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(
                ProfileStore.KEY_LIST,
                JSONArray()
                    .put(profile("p1", "Changed", ProfileCatalog.EMOJIS[0], 0, kids = false))
                    .put(profile("p2", "Child", ProfileCatalog.EMOJIS[6], 1, kids = false))
                    .toString(),
            )
            .putString(ProfileStore.KEY_CURRENT, "p2")
            .commit()
        KidsStore(context).setAllowlist("p2", setOf("com.example.other"))
        WeatherPreferences.clearData(context)
        context.getSharedPreferences(StartupPreferences.NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(StartupPreferences.KEY_TARGET, "")
            .putString(StartupPreferences.KEY_LABEL, "")
            .putBoolean(StartupPreferences.KEY_ON_WAKE, false)
            .commit()
        DiscoveryStore(context, LauncherPrefs.NAME).clearAll()
        AmbientSettingsStore.setMode(context, AmbientMode.CLOCK)
        AmbientSettingsStore.setClockFormat(context, AmbientClockFormat.TWENTY_FOUR)
        AmbientSettingsStore.setPixelShift(context, false)
        AmbientSettingsStore.setDimAfter(context, AmbientDim.TWENTY)
        AmbientSettingsStore.setMaxSession(context, AmbientSession.SIXTY)
        AmbientSettingsStore.setShowWeather(context, true)
    }

    private fun profile(
        id: String,
        name: String,
        emoji: String,
        color: Int,
        kids: Boolean,
    ): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("emoji", emoji)
        .put("color", color)
        .put("kids", kids)

    private fun app(packageName: String): TvApp = TvApp(
        packageName = packageName,
        activityName = "$packageName.MainActivity",
        label = packageName,
        banner = null,
        icon = null,
    )
}
