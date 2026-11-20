package app.aure.tv.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateStateStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var store: UpdateStateStore

    @Before
    fun setUp() {
        store = UpdateStateStore(context)
        store.clearAll()
    }

    @After
    fun tearDown() {
        store.clearAll()
        UpdateMetadataCache(context).clear()
    }

    @Test
    fun defaultsAreConservativeAndManualChecksRemainPossible() {
        assertEquals(UpdateChannel.STABLE, store.channel())
        assertEquals(UpdateCheckMode.DAILY, store.mode())
        assertEquals(UpdatePhase.IDLE, store.phase())
        assertTrue(store.isDue(1_000L))
    }

    @Test
    fun cadenceIsThrottledButNotForManualMode() {
        store.setMode(UpdateCheckMode.MANUAL)
        assertTrue(!store.isDue(Long.MAX_VALUE))
        store.setMode(UpdateCheckMode.DAILY)
        store.markAttempt(10_000L)
        assertTrue(!store.isDue(10_000L + 1_000L))
        assertTrue(store.isDue(10_000L + 24L * 60L * 60L * 1000L))
    }

    @Test
    fun availableAndPendingValuesRoundTrip() {
        val release = UpdateRelease(
            versionCode = 81,
            versionName = "0.5.8",
            channel = UpdateChannel.STABLE,
            generatedAt = "2026-11-16T00:00:00Z",
            expiresAt = "2026-12-16T00:00:00Z",
            releaseDate = "2026-11-15T00:00:00Z",
            minAndroidApi = 28,
            minSupportedVersionCode = 60,
            mandatory = false,
            notes = mapOf("en" to listOf("Note")),
            asset = UpdateAsset(
                url = "https://github.com/o/r/releases/download/v/update.apk",
                sha256 = "0".repeat(64),
                sizeBytes = 1L,
            ),
        )
        store.setAvailable(release)
        assertEquals(81, store.availableVersionCode())
        assertEquals("0.5.8", store.summary())
        store.setPending(81, 7)
        assertEquals(81, store.pendingVersionCode())
        assertEquals(7, store.pendingSessionId())
        store.clearPending()
        assertEquals(0, store.pendingVersionCode())
        store.clearAvailable()
        assertNull(store.summary())
    }

    @Test
    fun metadataCacheIsBoundedAndSeparatedByChannel() {
        val cache = UpdateMetadataCache(context)
        cache.clear()
        assertTrue(cache.save(UpdateChannel.STABLE, "stable"))
        assertTrue(cache.save(UpdateChannel.BETA, "beta"))
        assertEquals("stable", cache.load(UpdateChannel.STABLE))
        assertEquals("beta", cache.load(UpdateChannel.BETA))
        cache.clear(UpdateChannel.STABLE)
        assertNull(cache.load(UpdateChannel.STABLE))
        assertEquals("beta", cache.load(UpdateChannel.BETA))
    }
}
