package app.aure.tv.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaverScheduleTest {

    @Test
    fun the_saver_starts_exactly_at_the_chosen_delay() {
        assertFalse(SaverSchedule.isDue(SaverDelay.TWO, 119_999L, false, true))
        assertTrue(SaverSchedule.isDue(SaverDelay.TWO, 120_000L, false, true))
    }

    @Test
    fun never_keeps_the_saver_off_even_after_a_day() {
        assertFalse(SaverSchedule.isDue(SaverDelay.NEVER, 86_400_000L, false, true))
    }

    @Test
    fun music_holds_the_saver_back_when_it_is_not_allowed_during_playback() {
        assertFalse(SaverSchedule.isDue(SaverDelay.TEN, 600_000L, true, false))
    }

    @Test
    fun music_does_not_block_the_saver_when_playback_is_allowed() {
        assertTrue(SaverSchedule.isDue(SaverDelay.TEN, 600_000L, true, true))
    }

    @Test
    fun silence_does_not_depend_on_the_music_preference() {
        assertTrue(SaverSchedule.isDue(SaverDelay.FIVE, 300_000L, false, false))
    }
}
