package app.aure.tv.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.random.Random

class AmbientPhotoPlaylistTest {
    private val photos = listOf("a.jpg", "b.jpg", "c.jpg")

    @Test
    fun a_shuffle_keeps_every_photo_once() {
        val ordered = AmbientPhotoPlaylist.ordered(photos, Random(7))
        assertEquals(photos.toSet(), ordered.toSet())
        assertEquals(photos.size, ordered.size)
    }

    @Test
    fun one_photo_is_returned_as_the_same_list() {
        val single = listOf(photos.first())
        assertSame(single, AmbientPhotoPlaylist.ordered(single))
    }

    @Test
    fun the_same_seed_makes_the_same_order() {
        assertEquals(
            AmbientPhotoPlaylist.ordered(photos, Random(11)),
            AmbientPhotoPlaylist.ordered(photos, Random(11)),
        )
    }
}
