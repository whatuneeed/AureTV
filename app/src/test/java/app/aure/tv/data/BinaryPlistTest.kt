package app.aure.tv.data

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

    @Test
    fun an_array_with_two_self_references_is_rejected() {
        // The cycle starts at depth 16 so the old tree expansion stays near 2^17 nodes, not 2^33.
        val chain = Array(16) { i -> byteArrayOf(0xA1.toByte(), (i + 1).toByte()) }
        val cycle = byteArrayOf(0xA2.toByte(), 16, 16)
        assertNull(BinaryPlist.parse(bplist(*(chain + cycle))))
    }

    @Test
    fun an_array_with_a_single_self_reference_is_rejected() {
        assertNull(BinaryPlist.parse(bplist(byteArrayOf(0xA1.toByte(), 0))))
    }

    @Test
    fun a_shared_dag_over_the_work_budget_is_rejected() {
        // 18 doubling levels expand to 2^19-1 nodes, past the 100k budget, without any cycle.
        val chain = Array(18) { i -> byteArrayOf(0xA2.toByte(), (i + 1).toByte(), (i + 1).toByte()) }
        val leaf = byteArrayOf(0x51, 'x'.code.toByte())
        assertNull(BinaryPlist.parse(bplist(*(chain + leaf))))
    }

    @Test
    fun a_shared_reference_without_a_cycle_keeps_its_value() {
        val root = byteArrayOf(0xA2.toByte(), 1, 1)
        val shared = byteArrayOf(0x51, 's'.code.toByte())
        assertEquals(listOf("s", "s"), BinaryPlist.parse(bplist(root, shared)))
    }

    @Test
    fun a_healthy_nested_plist_still_parses() {
        val dict = byteArrayOf(0xD1.toByte(), 1, 2)
        val key = byteArrayOf(0x54) + "name".toByteArray(Charsets.US_ASCII)
        val array = byteArrayOf(0xA2.toByte(), 3, 4)
        val int = byteArrayOf(0x10, 42)
        val string = byteArrayOf(0x52) + "ok".toByteArray(Charsets.US_ASCII)
        assertEquals(mapOf("name" to listOf(42L, "ok")), BinaryPlist.parse(bplist(dict, key, array, int, string)))
    }

    /** Minimal bplist00 with offsetSize/refSize 1; payloads must stay under 256 bytes. */
    private fun bplist(vararg objects: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write("bplist00".toByteArray(Charsets.US_ASCII))
        val offsets = objects.map { obj -> out.size().also { out.write(obj) } }
        val table = out.size()
        offsets.forEach { out.write(it) }
        out.write(ByteArray(6))
        out.write(1)
        out.write(1)
        out.write(be8(objects.size.toLong()))
        out.write(be8(0))
        out.write(be8(table.toLong()))
        return out.toByteArray()
    }

    private fun be8(value: Long): ByteArray =
        ByteArray(8) { i -> ((value ushr (8 * (7 - i))) and 0xff).toByte() }
}
