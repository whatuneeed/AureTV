package com.whatuneed.ovisiontv.kotlin.data

/**
 * Reader for the `bplist00` localisation tables inside Apple's aerial
 * resources. Only the shapes those tables use are implemented: dictionaries,
 * arrays, strings, integers, reals, data and booleans. Anything malformed
 * stops the parse with null instead of returning half a table.
 */
internal object BinaryPlist {
    private const val HEADER = "bplist00"
    private const val TRAILER = 32
    private const val MAX_DEPTH = 32
    private const val MAX_OBJECTS = 1_000_000
    private const val MAX_NODES = 100_000

    fun parseStrings(bytes: ByteArray): Map<String, String> {
        val root = parse(bytes) as? Map<*, *> ?: return emptyMap()
        val result = HashMap<String, String>(root.size)
        root.forEach { (key, value) ->
            if (key is String && value is String) result[key] = value
        }
        return result
    }

    fun parse(bytes: ByteArray): Any? {
        if (bytes.size < HEADER.length + TRAILER) return null
        if (String(bytes, 0, HEADER.length, Charsets.US_ASCII) != HEADER) return null

        val trailer = bytes.size - TRAILER
        val offsetSize = bytes[trailer + 6].toInt() and 0xff
        val refSize = bytes[trailer + 7].toInt() and 0xff
        val objectCount = readUInt(bytes, trailer + 8, 8)
        val topObject = readUInt(bytes, trailer + 16, 8)
        val tableOffset = readUInt(bytes, trailer + 24, 8)
        if (offsetSize !in 1..8 || refSize !in 1..8) return null
        if (objectCount <= 0 || objectCount > MAX_OBJECTS) return null
        if (topObject < 0 || topObject >= objectCount) return null
        if (tableOffset < HEADER.length || tableOffset + objectCount * offsetSize > trailer) {
            return null
        }

        fun offsetOf(index: Long): Int? {
            if (index < 0 || index >= objectCount) return null
            val at = tableOffset + index * offsetSize
            val offset = readUInt(bytes, at.toInt(), offsetSize)
            return if (offset in 0 until bytes.size.toLong()) offset.toInt() else null
        }

        val onStack = java.util.BitSet(objectCount.toInt())
        var nodes = 0
        var rejected = false

        fun objectAt(index: Long, depth: Int): Any? {
            if (depth > MAX_DEPTH || index < 0 || index >= objectCount) return null
            if (onStack.get(index.toInt()) || nodes >= MAX_NODES) {
                rejected = true
                return null
            }
            nodes++
            onStack.set(index.toInt())
            try {
                val start = offsetOf(index) ?: return null
                val marker = bytes[start].toInt() and 0xff
                val type = marker ushr 4
                val info = marker and 0x0f
                return when (type) {
                    0x0 -> when (info) {
                        0x0 -> null
                        0x8 -> false
                        0x9 -> true
                        else -> null
                    }
                    0x1 -> {
                        val size = 1 shl info
                        readUInt(bytes, start + 1, size)
                    }
                    0x2 -> {
                        val size = 1 shl info
                        when (size) {
                            4 -> Float.fromBits(readUInt(bytes, start + 1, 4).toInt()).toDouble()
                            8 -> Double.fromBits(readUInt(bytes, start + 1, 8))
                            else -> null
                        }
                    }
                    0x4 -> {
                        val (count, header) = length(bytes, marker, start) ?: return null
                        val from = start + header
                        if (from + count > bytes.size) return null
                        bytes.copyOfRange(from, from + count)
                    }
                    0x5, 0x6 -> {
                        val (count, header) = length(bytes, marker, start) ?: return null
                        val from = start + header
                        val size = if (type == 0x5) count else count * 2
                        if (from + size > bytes.size) return null
                        val charset = if (type == 0x5) Charsets.UTF_8 else Charsets.UTF_16BE
                        String(bytes, from, size, charset)
                    }
                    0xa, 0xc -> {
                        val (count, header) = length(bytes, marker, start) ?: return null
                        val from = start + header
                        if (from + count * refSize > bytes.size) return null
                        List(count) { indexInArray ->
                            val ref = readUInt(bytes, from + indexInArray * refSize, refSize)
                            objectAt(ref, depth + 1)
                        }
                    }
                    0xd -> {
                        val (count, header) = length(bytes, marker, start) ?: return null
                        val keysAt = start + header
                        val valuesAt = keysAt + count * refSize
                        if (valuesAt + count * refSize > bytes.size) return null
                        val map = HashMap<Any?, Any?>(count)
                        for (indexInDict in 0 until count) {
                            val keyRef = readUInt(bytes, keysAt + indexInDict * refSize, refSize)
                            val valueRef = readUInt(bytes, valuesAt + indexInDict * refSize, refSize)
                            map[objectAt(keyRef, depth + 1)] = objectAt(valueRef, depth + 1)
                        }
                        map
                    }
                    else -> null
                }
            } finally {
                // Shared DAG refs are not cycles: the mark must leave with the node.
                onStack.clear(index.toInt())
            }
        }

        val root = objectAt(topObject, 0)
        // A cycle or an exhausted budget leaves null holes; a malformed table must fail as a whole.
        return if (rejected) null else root
    }

    private fun length(bytes: ByteArray, marker: Int, start: Int): Pair<Int, Int>? {
        val info = marker and 0x0f
        if (info != 0x0f) return info to 1
        if (start + 1 >= bytes.size) return null
        val intMarker = bytes[start + 1].toInt() and 0xff
        if (intMarker ushr 4 != 0x1) return null
        val size = 1 shl (intMarker and 0x0f)
        if (start + 2 + size > bytes.size) return null
        val count = readUInt(bytes, start + 2, size)
        if (count < 0 || count > MAX_OBJECTS) return null
        return count.toInt() to (2 + size)
    }

    private fun readUInt(bytes: ByteArray, offset: Int, size: Int): Long {
        var value = 0L
        for (index in 0 until size) {
            if (offset + index >= bytes.size) return -1L
            value = (value shl 8) or (bytes[offset + index].toLong() and 0xff)
        }
        return value
    }
}
