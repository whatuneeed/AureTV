package com.whatuneed.ovisiontv.kotlin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BinaryPlistTest {

    private fun fixture(name: String): ByteArray =
        javaClass.classLoader!!.getResourceAsStream("aerial/$name")!!.readBytes()

    @Test
    fun the_apple_tables_are_read_as_plain_string_maps() {
        val en = BinaryPlist.parseStrings(fixture("strings-en.bplist"))
        val ru = BinaryPlist.parseStrings(fixture("strings-ru.bplist"))

        assertEquals(687, en.size)
        assertEquals(687, ru.size)
        assertEquals("Dubai Skyline", en["DB_D002_C003_NAME"])
        assertTrue(ru.getValue("DB_D002_C003_NAME").startsWith("Вид на"))
        // Non-breaking spaces are part of Apple's copy, not a decoding fault.
        assertEquals('\u00a0', ru.getValue("DB_D002_C003_NAME")[6])
    }

    @Test
    fun broken_input_yields_no_table() {
        assertEquals(emptyMap<String, String>(), BinaryPlist.parseStrings(ByteArray(0)))
        assertEquals(emptyMap<String, String>(), BinaryPlist.parseStrings("not a plist".toByteArray()))
        assertEquals(
            emptyMap<String, String>(),
            BinaryPlist.parseStrings("bplist00".toByteArray() + ByteArray(64)),
        )
        assertNull(BinaryPlist.parse(ByteArray(40)))
    }
}
