package app.aure.tv.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AerialTarTest {

    private fun entry(
        name: String,
        content: ByteArray,
        type: Char = '0',
    ): ByteArray {
        val header = ByteArray(512)
        name.toByteArray().copyInto(header, 0, 0, minOf(name.length, 99))
        "0000644\u0000".toByteArray().copyInto(header, 100)
        "0000000\u0000".toByteArray().copyInto(header, 108)
        "0000000\u0000".toByteArray().copyInto(header, 116)
        "%011o\u0000".format(content.size).toByteArray().copyInto(header, 124)
        "%011o\u0000".format(0).toByteArray().copyInto(header, 136)
        header[148] = ' '.code.toByte()
        header[149] = ' '.code.toByte()
        header[150] = ' '.code.toByte()
        header[151] = ' '.code.toByte()
        header[152] = ' '.code.toByte()
        header[153] = ' '.code.toByte()
        header[154] = ' '.code.toByte()
        header[155] = ' '.code.toByte()
        header[156] = type.code.toByte()
        "ustar\u0000".toByteArray().copyInto(header, 257)
        "00".toByteArray().copyInto(header, 263)

        val checksum = header.sumOf { it.toInt() and 0xff }
        "%06o\u0000 ".format(checksum).toByteArray().copyInto(header, 148)

        val padding = (512 - content.size % 512) % 512
        return header + content + ByteArray(padding)
    }

    @Test
    fun only_the_wanted_regular_files_are_extracted() {
        val tar = entry("./entries.json", "{\"a\":1}".toByteArray()) +
            entry("./._entries.json", byteArrayOf(0, 1, 2)) +
            entry("./TVIdleScreenStrings.bundle/en.lproj/Localizable.nocache.strings", "en".toByteArray()) +
            entry("./TVIdleScreenStrings.bundle/ru.lproj/Localizable.nocache.strings", "ru".toByteArray()) +
            ByteArray(1024)

        val files = AerialTar.extract(
            tar,
            setOf(
                "entries.json",
                "TVIdleScreenStrings.bundle/en.lproj/Localizable.nocache.strings",
                "TVIdleScreenStrings.bundle/ru.lproj/Localizable.nocache.strings",
            ),
        )

        assertEquals(3, files.size)
        assertArrayEquals("{\"a\":1}".toByteArray(), files["entries.json"])
        assertArrayEquals("en".toByteArray(), files["TVIdleScreenStrings.bundle/en.lproj/Localizable.nocache.strings"])
        assertArrayEquals("ru".toByteArray(), files["TVIdleScreenStrings.bundle/ru.lproj/Localizable.nocache.strings"])
    }

    @Test
    fun pax_and_directory_entries_do_not_shift_the_files_after_them() {
        val pax = entry("PaxHeader/entries.json", "path=./entries.json\n".toByteArray(), 'x')
        val directory = entry("./TVIdleScreenStrings.bundle/", ByteArray(0), '5')
        val tar = pax + directory + entry("./entries.json", "ok".toByteArray()) +
            ByteArray(1024)

        val files = AerialTar.extract(tar, setOf("entries.json"))

        assertArrayEquals("ok".toByteArray(), files["entries.json"])
    }

    @Test
    fun a_truncated_file_is_not_returned() {
        val tar = entry("./entries.json", "0123456789".toByteArray())
        val truncated = tar.copyOfRange(0, 512 + 4)

        assertEquals(emptyMap<String, ByteArray>(), AerialTar.extract(truncated, setOf("entries.json")))
    }

    @Test
    fun garbage_is_refused_instead_of_guessed() {
        assertEquals(
            emptyMap<String, ByteArray>(),
            AerialTar.extract(ByteArray(2048) { 7 }, setOf("entries.json")),
        )
    }
}
