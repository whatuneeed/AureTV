package com.whatuneed.ovisiontv.kotlin.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SaverSettingsTest {

    @Test
    fun the_default_set_has_every_aerial_theme() {
        assertEquals(SaverTheme.entries.toSet(), SaverSettings.DEFAULT_THEMES)
    }

    @Test
    fun a_theme_toggles_off_and_back_on() {
        val off = SaverSettings.toggleTheme(SaverSettings.DEFAULT_THEMES, SaverTheme.UNDERWATER)
        assertEquals(SaverSettings.DEFAULT_THEMES - SaverTheme.UNDERWATER, off)

        val back = SaverSettings.toggleTheme(off, SaverTheme.UNDERWATER)
        assertEquals(SaverSettings.DEFAULT_THEMES, back)
    }

    @Test
    fun the_last_theme_cannot_be_turned_off() {
        val single = setOf(SaverTheme.EARTH)
        assertEquals(single, SaverSettings.toggleTheme(single, SaverTheme.EARTH))
    }

    @Test
    fun themes_round_trip_through_the_stored_form() {
        val themes = setOf(SaverTheme.CITIES, SaverTheme.UNDERWATER)
        assertEquals(themes, SaverSettings.decodeThemes(SaverSettings.encodeThemes(themes)))
    }

    @Test
    fun an_unknown_stored_key_is_skipped_and_an_empty_value_means_the_defaults() {
        assertEquals(SaverSettings.DEFAULT_THEMES, SaverSettings.decodeThemes(""))
        assertEquals(setOf(SaverTheme.CITIES), SaverSettings.decodeThemes("cities,bogus"))
    }

    @Test
    fun the_old_space_key_now_selects_underwater() {
        assertEquals(setOf(SaverTheme.UNDERWATER), SaverSettings.decodeThemes("space"))
    }

    @Test
    fun the_old_four_theme_selection_keeps_all_four_themes() {
        assertEquals(
            SaverSettings.DEFAULT_THEMES,
            SaverSettings.decodeThemes("cities,landscapes,earth,space"),
        )
        assertEquals(
            SaverSettings.DEFAULT_THEMES,
            SaverSettings.decodeThemes("landscapes,cities,space,earth"),
        )
    }

    @Test
    fun the_legacy_key_and_the_new_one_do_not_duplicate_a_theme() {
        assertEquals(
            setOf(SaverTheme.UNDERWATER),
            SaverSettings.decodeThemes("underwater,space"),
        )
    }

    @Test
    fun a_clip_hides_and_comes_back_when_others_remain_visible() {
        val hidden = SaverSettings.toggleHidden(emptySet(), "clip-a", visible = 2)
        assertEquals(setOf("clip-a"), hidden)
        assertEquals(emptySet<String>(), SaverSettings.toggleHidden(hidden, "clip-a", visible = 1))
    }

    @Test
    fun the_last_visible_clip_cannot_be_hidden() {
        assertEquals(
            setOf("clip-a"),
            SaverSettings.toggleHidden(setOf("clip-a"), "clip-b", visible = 1),
        )
        assertEquals(
            setOf("clip-a"),
            SaverSettings.toggleHidden(setOf("clip-a"), "clip-b", visible = 0),
        )
    }

    @Test
    fun a_whole_category_hides_and_comes_back_at_once() {
        val ids = setOf("clip-a", "clip-b", "clip-c")
        val hidden = SaverSettings.toggleGroup(emptySet(), ids, visible = 5)
        assertEquals(ids, hidden)
        assertEquals(emptySet<String>(), SaverSettings.toggleGroup(hidden, ids, visible = 2))
    }

    @Test
    fun a_partly_hidden_category_is_completed_before_it_comes_back() {
        val hidden = SaverSettings.toggleGroup(setOf("clip-a"), setOf("clip-a", "clip-b"), visible = 3)
        assertEquals(setOf("clip-a", "clip-b"), hidden)
    }

    @Test
    fun the_last_aerial_cannot_be_hidden_with_its_whole_category() {
        val ids = setOf("clip-a", "clip-b")
        // Every clip of the group is already hidden: the switch brings them back.
        assertEquals(emptySet<String>(), SaverSettings.toggleGroup(ids, ids, visible = 2))
        // Hiding the group would leave nothing to play, so the set stays.
        assertEquals(
            emptySet<String>(),
            SaverSettings.toggleGroup(emptySet(), ids, visible = 2),
        )
        assertEquals(
            setOf("clip-a"),
            SaverSettings.toggleGroup(setOf("clip-a"), ids, visible = 1),
        )
        assertEquals(
            emptySet<String>(),
            SaverSettings.toggleGroup(emptySet(), emptySet(), visible = 4),
        )
    }

    @Test
    fun hidden_ids_round_trip_and_decode_sorts_duplicates_out() {
        val hidden = setOf("b", "a")
        assertEquals("a,b", SaverSettings.encodeHidden(hidden))
        assertEquals(hidden, SaverSettings.decodeHidden("b,a,b"))
        assertEquals(emptySet<String>(), SaverSettings.decodeHidden(null))
        assertEquals(emptySet<String>(), SaverSettings.decodeHidden(""))
    }

    @Test
    fun delays_map_from_minutes_and_unknown_values_fall_back_to_five() {
        assertEquals(SaverDelay.TEN, SaverDelay.fromMinutes(10))
        assertEquals(SaverDelay.NEVER, SaverDelay.fromMinutes(-1))
        assertEquals(SaverDelay.FIVE, SaverDelay.fromMinutes(7))
    }
}
