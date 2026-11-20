package app.aure.tv.ui.components

import app.aure.tv.core.network.readAtMost
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shared body read enforces its cap and observes cancellation. */
class BoundedReadTest {

    @Test
    fun aBodyUnderTheLimitIsReturnedWhole() = runBlocking {
        val body = byteArrayOf(1, 2, 3, 4)

        assertArrayEquals(body, ByteArrayInputStream(body).readAtMost(4))
    }

    @Test
    fun aBodyOverTheLimitIsRejected() = runBlocking {
        assertNull(ByteArrayInputStream(ByteArray(5)).readAtMost(4))
    }

    @Test
    fun cancellationIsObservedDuringTheCopyLoop() {
        lateinit var job: Job
        val input = object : InputStream() {
            private var reads = 0

            override fun read(): Int {
                reads++
                if (reads == 2) job.cancel()
                return 1
            }
        }

        val result = runCatching {
            runBlocking {
                job = coroutineContext[Job]!!
                input.readAtMost(1_000_000)
            }
        }

        assertTrue(result.exceptionOrNull() is CancellationException)
    }
}
