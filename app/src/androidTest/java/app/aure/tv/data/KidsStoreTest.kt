package app.aure.tv.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class KidsStoreTest {
    private val store = KidsStore(InstrumentationRegistry.getInstrumentation().targetContext)
    private val id = "store-test-${UUID.randomUUID()}"

    @After
    fun cleanup() { store.clearProfile(id) }

    @Test
    fun cleared_pin_cannot_pass_an_in_flight_check() = runBlocking {
        store.setPin(id, "1234")
        val pending = async(start = CoroutineStart.UNDISPATCHED) { store.checkPin(id, "1234") }
        store.clearPin(id)
        assertFalse(pending.await())
    }

    @Test
    fun cleared_recovery_cannot_pass_an_in_flight_check() = runBlocking {
        store.setRecovery(id, "hint", "answer")
        val pending = async(start = CoroutineStart.UNDISPATCHED) { store.checkRecovery(id, "answer") }
        store.clearPin(id)
        assertFalse(pending.await())
    }

    @Test
    fun cancelled_recovery_save_does_not_write() = runBlocking {
        val pending = async(start = CoroutineStart.UNDISPATCHED) { store.setRecovery(id, "hint", "answer") }
        pending.cancel()
        pending.join()
        assertFalse(store.hasRecovery(id))
    }

    @Test
    fun clearing_a_profile_does_not_resurrect_an_in_flight_new_pin() = runBlocking {
        val pending = async(start = CoroutineStart.UNDISPATCHED) { store.setPin(id, "1234") }
        store.clearProfile(id)
        pending.join()
        assertTrue(pending.isCancelled)
        assertFalse(store.hasPin(id))
    }

    @Test
    fun concurrent_guesses_share_the_lockout_and_cannot_clear_it() = runBlocking {
        store.setPin(id, "1234")
        val other = KidsStore(InstrumentationRegistry.getInstrumentation().targetContext)
        val attempts = List(6) {
            async(start = CoroutineStart.UNDISPATCHED) { other.verifyPin(id, "0000") }
        }.map { it.await() }
        assertEquals(5, attempts.count { it == KidsStore.Verification.WRONG })
        assertEquals(1, attempts.count { it == KidsStore.Verification.LOCKED })
        assertEquals(KidsStore.Verification.LOCKED, store.verifyPin(id, "1234"))
        assertTrue(store.lockRemaining(id) > 0)
    }
}
