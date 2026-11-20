package app.aure.tv.data

/**
 * Minimal reader for the tar Apple publishes at
 * `sylvan.apple.com/Aerials/resources.tar`. Only the named regular files are
 * extracted; everything else (PAX headers, AppleDouble files, signatures) is
 * skipped without copying it into memory.
 */
internal object AerialTar {
    private const val BLOCK = 512
    private const val NAME_LEN = 100
    private const val SIZE_OFFSET = 124
    private const val SIZE_LEN = 12
    private const val CHECKSUM_OFFSET = 148
    private const val CHECKSUM_LEN = 8
    private const val TYPE_OFFSET = 156

    fun extract(tar: ByteArray, wanted: Set<String>): Map<String, ByteArray> {
        val result = HashMap<String, ByteArray>()
        var offset = 0
        var longName: String? = null
        while (offset + BLOCK <= tar.size) {
            if (tar[offset].toInt() == 0) break
            if (!checksumMatches(tar, offset)) break
            val size = readSize(tar, offset) ?: break
            if (size < 0 || offset + BLOCK + size > tar.size) break
            val type = tar[offset + TYPE_OFFSET].toInt().toChar()
            val dataStart = offset + BLOCK
            val padded = (((size + BLOCK - 1) / BLOCK) * BLOCK).toInt()
            val name = longName ?: readName(tar, offset)
            longName = null

            when (type) {
                '0', '\u0000', '7' -> {
                    if (name in wanted && result[name] == null) {
                        result[name] = tar.copyOfRange(dataStart, dataStart + size.toInt())
                    }
                }
                'L' -> longName = tar.copyOfRange(dataStart, dataStart + size.toInt())
                    .toString(Charsets.US_ASCII)
                    .trimEnd('\u0000')
                    .normalizedName()
            }

            offset = dataStart + padded
        }
        return result
    }

    private fun checksumMatches(tar: ByteArray, offset: Int): Boolean {
        val stored = parseOctal(tar, offset + CHECKSUM_OFFSET, CHECKSUM_LEN) ?: return false
        var sum = 0L
        for (index in 0 until BLOCK) {
            val byte = if (index in CHECKSUM_OFFSET until CHECKSUM_OFFSET + CHECKSUM_LEN) {
                0x20
            } else {
                tar[offset + index].toInt() and 0xff
            }
            sum += byte
        }
        return sum == stored
    }

    private fun readName(tar: ByteArray, offset: Int): String {
        val name = String(tar, offset, NAME_LEN, Charsets.UTF_8).trimEnd('\u0000')
        val prefix = String(tar, offset + 345, 155, Charsets.UTF_8).trimEnd('\u0000')
        val full = if (prefix.isEmpty()) name else "$prefix/$name"
        return full.normalizedName()
    }

    private fun String.normalizedName(): String = trimStart('.', '/')

    private fun readSize(tar: ByteArray, offset: Int): Long? {
        val first = tar[offset + SIZE_OFFSET].toInt() and 0xff
        if (first and 0x80 != 0) {
            // GNU base-256: the high bit marks the encoding, the rest is a
            // big-endian number.
            var value = (first and 0x7f).toLong()
            for (index in 1 until SIZE_LEN) {
                value = (value shl 8) or (tar[offset + SIZE_OFFSET + index].toLong() and 0xff)
            }
            return value
        }
        return parseOctal(tar, offset + SIZE_OFFSET, SIZE_LEN)
    }

    private fun parseOctal(bytes: ByteArray, offset: Int, length: Int): Long? {
        var value = 0L
        var digits = 0
        for (index in 0 until length) {
            val char = bytes[offset + index].toInt()
            when {
                char == 0 || char == 0x20 -> if (digits > 0) return value
                char in 0x30..0x37 -> {
                    value = value * 8 + (char - 0x30)
                    digits++
                }
                else -> return null
            }
        }
        return if (digits > 0) value else null
    }
}
