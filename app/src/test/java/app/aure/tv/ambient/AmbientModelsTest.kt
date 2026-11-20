package app.aure.tv.ambient

import app.aure.tv.data.SaverDelay
import app.aure.tv.data.SaverRefresh
import app.aure.tv.data.SaverTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientModelsTest {

    private fun settings(
        mode: AmbientMode = AmbientMode.AERIAL,
        delay: SaverDelay = SaverDelay.FIVE,
        duringMusic: Boolean = true,
        themes: Set<SaverTheme> = SaverTheme.entries.toSet(),
        hidden: Set<String> = emptySet(),
    ) = AmbientSettings(
        mode = mode,
        delay = delay,
        duringMusic = duringMusic,
        themes = themes,
        hidden = hidden,
        refresh = SaverRefresh.WEEKLY,
    )

    @Test
    fun an_unknown_stored_mode_falls_back_to_aerial() {
        assertEquals(AmbientMode.AERIAL, AmbientMode.fromKey(null))
        assertEquals(AmbientMode.AERIAL, AmbientMode.fromKey(""))
        assertEquals(AmbientMode.AERIAL, AmbientMode.fromKey("future-mode"))
        assertEquals(AmbientMode.CLOCK, AmbientMode.fromKey("clock"))
        assertEquals(AmbientMode.MINIMAL, AmbientMode.fromKey("minimal"))
        assertEquals(AmbientMode.PHOTOS, AmbientMode.fromKey("photos"))
        assertEquals(AmbientMode.REDDIT, AmbientMode.fromKey("reddit"))
    }

    @Test
    fun the_stored_keys_are_stable() {
        assertEquals("aerial", AmbientMode.AERIAL.key)
        assertEquals("clock", AmbientMode.CLOCK.key)
        assertEquals("minimal", AmbientMode.MINIMAL.key)
        assertEquals("photos", AmbientMode.PHOTOS.key)
        assertEquals("reddit", AmbientMode.REDDIT.key)
    }

    @Test
    fun the_scheduler_uses_the_ambient_delay_and_music_option() {
        val two = settings(delay = SaverDelay.TWO, duringMusic = false)
        assertFalse(AmbientScheduler.isDue(two, 119_999L, musicPlaying = false))
        assertTrue(AmbientScheduler.isDue(two, 120_000L, musicPlaying = false))
        assertFalse(AmbientScheduler.isDue(two, 120_000L, musicPlaying = true))
    }

    @Test
    fun never_stays_off_even_with_music_and_a_long_idle() {
        val never = settings(delay = SaverDelay.NEVER)
        assertFalse(AmbientScheduler.isDue(never, 86_400_000L, musicPlaying = false))
        assertFalse(AmbientScheduler.isDue(never, 86_400_000L, musicPlaying = true))
    }

    @Test
    fun an_unknown_stored_clock_format_falls_back_to_system() {
        assertEquals(AmbientClockFormat.SYSTEM, AmbientClockFormat.fromKey(null))
        assertEquals(AmbientClockFormat.SYSTEM, AmbientClockFormat.fromKey(""))
        assertEquals(AmbientClockFormat.SYSTEM, AmbientClockFormat.fromKey("future"))
        assertEquals(AmbientClockFormat.TWELVE, AmbientClockFormat.fromKey("twelve"))
        assertEquals(AmbientClockFormat.TWENTY_FOUR, AmbientClockFormat.fromKey("twenty_four"))
    }

    @Test
    fun the_clock_format_follows_or_overrides_the_system() {
        assertTrue(AmbientClockFormat.SYSTEM.use24Hour(systemIs24Hour = true))
        assertFalse(AmbientClockFormat.SYSTEM.use24Hour(systemIs24Hour = false))
        assertFalse(AmbientClockFormat.TWELVE.use24Hour(systemIs24Hour = true))
        assertTrue(AmbientClockFormat.TWENTY_FOUR.use24Hour(systemIs24Hour = false))
    }

    @Test
    fun the_clock_format_keys_are_stable_for_backups() {
        assertEquals("system", AmbientClockFormat.SYSTEM.key)
        assertEquals("twelve", AmbientClockFormat.TWELVE.key)
        assertEquals("twenty_four", AmbientClockFormat.TWENTY_FOUR.key)
    }

    @Test
    fun protection_keys_are_stable_and_fall_back_to_off() {
        assertEquals(AmbientDim.NEVER, AmbientDim.fromKey(null))
        assertEquals(AmbientDim.NEVER, AmbientDim.fromKey("future"))
        assertEquals(AmbientDim.TEN, AmbientDim.fromKey("ten"))
        assertEquals(AmbientDim.TWENTY, AmbientDim.fromKey("twenty"))
        assertEquals(AmbientDim.THIRTY, AmbientDim.fromKey("thirty"))
        assertEquals(AmbientSession.NEVER, AmbientSession.fromKey(null))
        assertEquals(AmbientSession.NEVER, AmbientSession.fromKey("future"))
        assertEquals(AmbientSession.THIRTY, AmbientSession.fromKey("thirty"))
        assertEquals(AmbientSession.SIXTY, AmbientSession.fromKey("sixty"))
        assertEquals(AmbientSession.ONE_TWENTY, AmbientSession.fromKey("one_twenty"))
        assertEquals("one_twenty", AmbientSession.ONE_TWENTY.key)
    }

    @Test
    fun only_aerial_needs_clips() {
        assertTrue(AmbientScheduler.needsClips(AmbientMode.AERIAL))
        assertFalse(AmbientScheduler.needsClips(AmbientMode.CLOCK))
        assertFalse(AmbientScheduler.needsClips(AmbientMode.MINIMAL))
        assertFalse(AmbientScheduler.needsClips(AmbientMode.PHOTOS))
        assertFalse(AmbientScheduler.needsClips(AmbientMode.REDDIT))
    }
}
