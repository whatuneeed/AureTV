package app.aure.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilesTest {

    private val me = Profile("p1", "Я", ProfileCatalog.EMOJIS[0], 3)
    private val guest = Profile("p2", "Гость", ProfileCatalog.EMOJIS[6], 6)

    @Test
    fun emptyStoreStartsWithOneDefaultProfile() {
        val parsed = parseProfiles(null, "Я")
        assertTrue(parsed.repaired)
        assertEquals(1, parsed.profiles.size)
        assertEquals("p1", parsed.profiles[0].id)
        assertEquals("Я", parsed.profiles[0].name)
        assertEquals(ProfileCatalog.EMOJIS[0], parsed.profiles[0].emoji)
    }

    @Test
    fun savedListRoundTrips() {
        val saved = serializeProfiles(listOf(me, guest))
        val parsed = parseProfiles(saved, "Я")
        assertFalse(parsed.repaired)
        assertEquals(listOf(me, guest), parsed.profiles)
    }

    @Test
    fun brokenJsonFallsBackToTheDefaultProfile() {
        val parsed = parseProfiles("{not json", "Me")
        assertTrue(parsed.repaired)
        assertEquals(listOf("p1"), parsed.profiles.map { it.id })
        assertEquals("Me", parsed.profiles[0].name)
    }

    @Test
    fun oneBadEntryDoesNotDropTheList() {
        val raw = """[{"id":"p1","name":"Я","emoji":"🍿","color":3},{"name":"no id"}]"""
        val parsed = parseProfiles(raw, "Me")
        assertEquals(1, parsed.profiles.size)
        assertTrue(parsed.repaired)
    }

    @Test
    fun duplicateEmojiIsReplacedWithAFreeOne() {
        val raw = serializeProfiles(
            listOf(
                me.copy(emoji = ProfileCatalog.EMOJIS[6]),
                guest.copy(emoji = ProfileCatalog.EMOJIS[6]),
            ),
        )
        val parsed = parseProfiles(raw, "Я")
        assertTrue(parsed.repaired)
        assertEquals(2, parsed.profiles.map { it.emoji }.toSet().size)
        assertEquals(ProfileCatalog.EMOJIS[6], parsed.profiles[0].emoji)
        assertNotEquals(ProfileCatalog.EMOJIS[6], parsed.profiles[1].emoji)
    }

    @Test
    fun outOfRangeColourIsRebuiltFromTheEmoji() {
        val parsed = parseProfiles(serializeProfiles(listOf(me.copy(color = 42))), "Я")
        assertTrue(parsed.repaired)
        assertEquals(ProfileCatalog.colorFor(me.emoji), parsed.profiles[0].color)
    }

    @Test
    fun placeholderNameFollowsTheInterfaceLanguage() {
        val raw = serializeProfiles(listOf(me.copy(name = "Me")))
        assertEquals("Я", parseProfiles(raw, "Я").profiles[0].name)
        assertEquals("Me", parseProfiles(raw, "Me").profiles[0].name)
        assertEquals("Мама", parseProfiles(serializeProfiles(listOf(me.copy(name = "Мама"))), "Me")
            .profiles[0].name)
    }

    @Test
    fun blankNameRepairKeepsTheKidsFlag() {
        val raw = """[{"id":"p2","name":"   ","emoji":"🍿","color":3,"kids":true}]"""
        val parsed = parseProfiles(raw, "Я")
        assertTrue(parsed.repaired)
        assertEquals("Я", parsed.profiles[0].name)
        assertTrue(parsed.profiles[0].kids)
    }

    @Test
    fun repairedKidsFlagSurvivesTheSavedRepair() {
        val raw = """[{"id":"p2","name":"","emoji":"🍿","color":3,"kids":true}]"""
        val repaired = parseProfiles(raw, "Me").profiles
        val reread = parseProfiles(serializeProfiles(repaired), "Me")
        assertFalse(reread.repaired)
        assertTrue(reread.profiles[0].kids)
    }

    @Test
    fun blankNameRepairLeavesKidsFalseWhenFalseOrAbsent() {
        val falseFlag = """[{"id":"p2","name":" ","emoji":"🍿","color":3,"kids":false}]"""
        val legacy = """[{"id":"p2","name":" ","emoji":"🍿","color":3}]"""
        assertFalse(parseProfiles(falseFlag, "Me").profiles[0].kids)
        assertFalse(parseProfiles(legacy, "Me").profiles[0].kids)
    }

    @Test
    fun an_oversized_profile_list_is_capped_and_marked_repaired() {
        val many = (1..40).map { Profile("p$it", "Name $it", ProfileCatalog.EMOJIS[0], 3) }
        val parsed = parseProfiles(serializeProfiles(many), "Me")

        assertEquals(32, parsed.profiles.size)
        assertTrue(parsed.repaired)
    }

    @Test
    fun an_oversized_raw_blob_falls_back_without_parsing_it() {
        val parsed = parseProfiles("[" + " ".repeat(300_000), "Me")

        assertEquals(listOf("p1"), parsed.profiles.map { it.id })
        assertTrue(parsed.repaired)
    }

    @Test
    fun nextIdSkipsExistingNumbers() {
        assertEquals("p3", ProfileCatalog.nextId(listOf(me, guest)))
        assertEquals("p1", ProfileCatalog.nextId(emptyList()))
        assertEquals("p4", ProfileCatalog.nextId(listOf(me, guest, Profile("p3", "X", "", 0))))
    }

    @Test
    fun nextIdHonoursThePersistedFloorAfterADelete() {
        // p2 was deleted; the floor keeps its number from being reused.
        assertEquals("p3", ProfileCatalog.nextId(listOf(me), floor = 3))
        assertEquals("p5", ProfileCatalog.nextId(listOf(me, guest), floor = 5))
        assertEquals("p3", ProfileCatalog.nextId(listOf(me, guest), floor = 0))
    }

    @Test
    fun freeEmojisExcludeOtherProfilesButNotTheEditedOne() {
        val all = ProfileCatalog.freeEmojis(listOf(me, guest))
        assertFalse(ProfileCatalog.EMOJIS[0] in all)
        assertFalse(ProfileCatalog.EMOJIS[6] in all)
        assertEquals(ProfileCatalog.EMOJIS.size - 2, all.size)
        assertTrue(ProfileCatalog.EMOJIS[0] in ProfileCatalog.freeEmojis(listOf(me, guest), "p1"))
    }

    @Test
    fun atMostThreeProfiles() {
        assertTrue(ProfileCatalog.canAdd(listOf(me, guest)))
        assertFalse(ProfileCatalog.canAdd(listOf(me, guest, Profile("p3", "X", "", 0))))
    }

    @Test
    fun initialsTakeTwoLettersAndSurviveEmoji() {
        assertEquals("МК", ProfileCatalog.initials("Мама Кот"))
        assertEquals("A", ProfileCatalog.initials("alice"))
        assertEquals("", ProfileCatalog.initials("   "))
    }

    @Test
    fun firstProfileKeepsTheOriginalLayoutFile() {
        assertEquals(LauncherPrefs.NAME, ProfileStore.homePrefsName("p1"))
        assertEquals("${LauncherPrefs.NAME}_p2", ProfileStore.homePrefsName("p2"))
    }

    @Test
    fun pickerOpensOnTheCurrentProfile() {
        assertEquals(0, ProfileCatalog.pickerIndex(listOf(me, guest), "p1"))
        assertEquals(1, ProfileCatalog.pickerIndex(listOf(me, guest), "p2"))
        assertEquals(0, ProfileCatalog.pickerIndex(listOf(me, guest), "gone"))
        assertEquals(0, ProfileCatalog.pickerIndex(emptyList(), "p1"))
    }

    @Test
    fun avatarStartsOnTheEditedPictureWhileFree() {
        assertEquals(6, ProfileCatalog.avatarStartIndex(listOf(me, guest), "p2"))
        // Another profile owns a picture only by its own id; the edited one
        // keeps its own disc in focus.
        val renamed = listOf(me.copy(emoji = ProfileCatalog.EMOJIS[12]), guest)
        assertEquals(12, ProfileCatalog.avatarStartIndex(renamed, "p1"))
    }

    @Test
    fun avatarStartsOnTheFirstFreePictureForANewProfile() {
        val profiles = listOf(
            me.copy(emoji = ProfileCatalog.EMOJIS[0]),
            guest.copy(emoji = ProfileCatalog.EMOJIS[1]),
        )
        assertEquals(2, ProfileCatalog.avatarStartIndex(profiles, "p3"))
    }

    @Test
    fun avatarStartIndexStaysInRangeWhenEveryPictureIsTaken() {
        val all = ProfileCatalog.EMOJIS.mapIndexed { index, emoji ->
            Profile(id = "p$index", name = "P$index", emoji = emoji, color = 0)
        }
        assertTrue(ProfileCatalog.freeEmojis(all).isEmpty())
        assertEquals(0, ProfileCatalog.avatarStartIndex(all, "new"))
    }

    @Test
    fun avatarMoveSkipsPicturesOwnedByOthers() {
        // p2 owns 🐶 at index 6; p1 owns 🍿 at index 0.
        assertEquals(7, ProfileCatalog.avatarMove(7, -1, 0, listOf(me, guest), "p1"))
        assertEquals(7, ProfileCatalog.avatarMove(6, 1, 0, listOf(me, guest), "p1"))
        // Up from the animals row: p1 may take its own popcorn, p2 may not.
        assertEquals(0, ProfileCatalog.avatarMove(6, 0, -1, listOf(me, guest), "p1"))
        assertEquals(6, ProfileCatalog.avatarMove(6, 0, -1, listOf(me, guest), "p2"))
    }

    @Test
    fun avatarMoveKeepsTheCursorAtTheEdges() {
        assertEquals(5, ProfileCatalog.avatarMove(5, 1, 0, listOf(me, guest), "p2"))
        assertEquals(0, ProfileCatalog.avatarMove(0, -1, 0, listOf(me, guest), "p2"))
        assertEquals(17, ProfileCatalog.avatarMove(17, 0, 1, listOf(me, guest), "p2"))
        assertEquals(0, ProfileCatalog.avatarMove(0, 0, -1, listOf(me, guest), "p2"))
    }
}
