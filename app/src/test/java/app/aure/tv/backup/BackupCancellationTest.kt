package app.aure.tv.backup

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertSame
import org.junit.Test

class BackupCancellationTest {

    @Test
    fun aCancelledBackupOperationIsRethrownUnchanged() {
        val cancellation = CancellationException("screen left")

        try {
            cancellation.rethrowIfCancellation()
            error("cancellation must not be converted into a backup failure")
        } catch (expected: CancellationException) {
            assertSame(cancellation, expected)
        }
    }

    @Test
    fun anOrdinaryBackupFailureIsLeftForTheScreenHandler() {
        val failure = IllegalStateException("disk full")

        failure.rethrowIfCancellation()
    }
}
