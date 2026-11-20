package app.aure.tv.ui

import app.aure.tv.R
import app.aure.tv.backup.BackupStage
import org.junit.Assert.assertEquals
import org.junit.Test

/** Backup failures are localized by stage, not forwarded as English causes. */
class BackupErrorTextTest {

    @Test
    fun damagedFilesUseTheFormatMessage() {
        listOf(
            BackupStage.FORMAT,
            BackupStage.MANIFEST,
            BackupStage.SCHEMA,
            BackupStage.MIGRATION,
        ).forEach {
            assertEquals(R.string.backup_error_format, backupErrorRes(it))
        }
    }

    @Test
    fun storageFailuresUseTheIoMessage() {
        assertEquals(R.string.backup_error_io, backupErrorRes(BackupStage.IO))
    }

    @Test
    fun integrityFailuresHaveTheirOwnMessage() {
        assertEquals(R.string.backup_error_integrity, backupErrorRes(BackupStage.INTEGRITY))
    }

    @Test
    fun wrongPasswordsKeepThePasswordMessage() {
        assertEquals(R.string.backup_password_wrong, backupErrorRes(BackupStage.CRYPTO))
    }

    @Test
    fun restoreFailuresUseTheRestoreMessage() {
        assertEquals(R.string.backup_error_restore, backupErrorRes(BackupStage.RESTORE))
    }
}
