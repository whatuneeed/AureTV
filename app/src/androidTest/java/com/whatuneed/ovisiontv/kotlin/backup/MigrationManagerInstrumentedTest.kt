package com.whatuneed.ovisiontv.kotlin.backup

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.whatuneed.ovisiontv.kotlin.BuildConfig
import com.whatuneed.ovisiontv.kotlin.ui.PrefsSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MigrationManagerInstrumentedTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext
    private val state = context.getSharedPreferences(
        MigrationManager.STATE_PREFS,
        Context.MODE_PRIVATE,
    )
    private lateinit var savedState: Map<String, Any?>
    private var savedBackupDir: File? = null

    @Before
    fun isolate() {
        savedState = PrefsSnapshot.capturePrefs(context, MigrationManager.STATE_PREFS)
        state.edit().clear().commit()
        val backupDir = File(context.filesDir, "backups")
        if (backupDir.exists()) {
            val aside = File(context.cacheDir, "migration-test-${UUID.randomUUID()}")
            check(backupDir.renameTo(aside))
            savedBackupDir = aside
        }
    }

    @After
    fun restore() {
        PrefsSnapshot.restorePrefs(context, MigrationManager.STATE_PREFS, savedState)
        val backupDir = File(context.filesDir, "backups")
        if (backupDir.exists()) backupDir.deleteRecursively()
        savedBackupDir?.let { if (it.exists()) check(it.renameTo(backupDir)) }
        savedBackupDir = null
    }

    @Test
    fun aFreshInstallRecordsTheSchemaAndLeavesAPreMigrationCopy() = runBlocking {
        val manager = MigrationManager(context)
        val first = manager.ensureCurrent()

        assertEquals(BackupContract.DATA_SCHEMA, first.schema)
        assertNotNull(first.preMigration)
        assertEquals(BackupContract.DATA_SCHEMA, manager.schema())
        assertEquals(BuildConfig.VERSION_CODE, manager.lastVersionCode())
        assertTrue(
            BackupManager(context).list().any {
                it.info.trigger == BackupTrigger.PRE_MIGRATION && it.info.pinned
            },
        )

        val second = manager.ensureCurrent()
        assertNull(second.preMigration)
        assertNull(second.preUpdate)
    }

    @Test
    fun anAppVersionChangeLeavesAPreUpdateCopy() = runBlocking {
        state.edit()
            .putInt(MigrationManager.KEY_SCHEMA, BackupContract.DATA_SCHEMA)
            .putInt(MigrationManager.KEY_LAST_VERSION, BuildConfig.VERSION_CODE - 1)
            .commit()

        val result = MigrationManager(context).ensureCurrent()

        assertNotNull(result.preUpdate)
        assertNull(result.preMigration)
        assertEquals(BuildConfig.VERSION_CODE, MigrationManager(context).lastVersionCode())
        assertTrue(
            BackupManager(context).list().any {
                it.info.trigger == BackupTrigger.PRE_UPDATE && it.info.pinned
            },
        )
    }
}
