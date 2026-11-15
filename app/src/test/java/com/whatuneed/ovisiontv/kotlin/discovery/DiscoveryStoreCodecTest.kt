package com.whatuneed.ovisiontv.kotlin.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The store is JSON in SharedPreferences, so the codecs are where a malformed
 * or truncated file must be rejected instead of crashing the launcher.
 */
class DiscoveryStoreCodecTest {

    private fun item(
        id: String = "tv|app.one|intent://one",
        progress: Float? = 0.4f,
    ) = DiscoveryItem(
        id = id,
        source = DiscoverySource.TV_PROVIDER,
        kind = DiscoveryKind.VIDEO,
        packageName = "app.one",
        packageLabel = "App One",
        title = "One",
        subtitle = "Episode 1",
        landscapeUri = "file:///thumb.jpg",
        posterUri = "file:///poster.jpg",
        intentUri = "intent://one#Intent;package=app.one;end",
        activityName = "app.one.Main",
        progress = progress,
        isContinue = true,
        lastEngagementUtc = 1_700_000_000_000L,
        reason = DiscoveryReason.CONTINUE_WATCHING,
    )

    @Test
    fun usage_round_trips_and_ignores_zero_counts() {
        val usage = mapOf("a" to Usage(3, 100L), "b" to Usage(1, 50L))
        assertEquals(usage, decodeUsage(encodeUsage(usage)))
        assertEquals(
            emptyMap<String, Usage>(),
            decodeUsage("""{"a":{"c":0,"t":100},"b":{"c":2,"t":0}}"""),
        )
    }

    @Test
    fun a_broken_usage_blob_is_empty_not_a_crash() {
        assertEquals(emptyMap<String, Usage>(), decodeUsage("{not json"))
        assertEquals(emptyMap<String, Usage>(), decodeUsage(null))
        assertTrue(decodeUsage("[]").isEmpty())
    }

    @Test
    fun an_oversized_item_blob_is_capped_before_it_is_parsed_into_memory() {
        val raw = buildString {
            append('[')
            for (index in 0 until 1_100) {
                if (index > 0) append(',')
                append("""{"id":"id$index","pkg":"a","title":"t$index"}""")
            }
            append(']')
        }

        assertEquals(1_000, decodeItems(raw).size)
    }

    @Test
    fun a_huge_item_blob_is_rejected_without_building_a_list() {
        assertTrue(decodeItems("[" + " ".repeat(600_000)).isEmpty())
    }

    @Test
    fun usage_pruning_keeps_the_newest() {
        val usage = mapOf(
            "old" to Usage(1, 10L),
            "new" to Usage(1, 30L),
            "middle" to Usage(1, 20L),
        )
        assertEquals(listOf("new", "middle"), pruneUsage(usage, 2).keys.toList())
        assertTrue(pruneUsage(usage, 0).isEmpty())
    }

    @Test
    fun oversized_item_fields_are_bounded_before_they_reach_storage() {
        val huge = "x".repeat(20_000)
        val decoded = decodeItems(
            encodeItems(
                listOf(
                    item().copy(
                        title = huge,
                        subtitle = huge,
                        packageLabel = huge,
                        landscapeUri = huge,
                        posterUri = huge,
                        intentUri = huge,
                        activityName = huge,
                    ),
                ),
            ),
        ).single()

        assertEquals(MAX_ITEM_TITLE_CHARS, decoded.title.length)
        assertEquals(MAX_ITEM_SUBTITLE_CHARS, decoded.subtitle!!.length)
        assertEquals(MAX_ITEM_LABEL_CHARS, decoded.packageLabel!!.length)
        assertEquals(MAX_ITEM_ACTIVITY_CHARS, decoded.activityName!!.length)
        assertNull(decoded.landscapeUri)
        assertNull(decoded.posterUri)
        assertNull(decoded.intentUri)
    }

    @Test
    fun oversized_item_identity_is_dropped_instead_of_truncated() {
        val id = "i".repeat(MAX_ITEM_ID_CHARS + 1)
        val raw = """[{"id":"$id","pkg":"app.one","title":"x"}]"""
        assertTrue(decodeItems(raw).isEmpty())
        assertTrue(encodeItems(listOf(item(id = id))).trim() == "[]")
    }

    @Test
    fun an_item_survives_the_round_trip() {
        val original = item()
        val decoded = decodeItems(encodeItems(listOf(original))).single()
        assertEquals(original.copy(score = 0.0), decoded)
    }

    @Test
    fun an_item_without_identity_or_title_is_dropped() {
        val raw = """
            [
              {"id":"","pkg":"a","title":"x"},
              {"id":"one","pkg":"","title":"x"},
              {"id":"two","pkg":"a","title":""},
              {"id":"three","pkg":"a","title":"kept"}
            ]
        """.trimIndent()
        assertEquals(listOf("three"), decodeItems(raw).map { it.id })
    }

    @Test
    fun an_unknown_enum_falls_back_instead_of_throwing() {
        val raw = """[{"id":"one","pkg":"a","title":"x","src":"FROM_MARS","kind":"HOLO"}]"""
        val decoded = decodeItems(raw).single()
        assertEquals(DiscoverySource.TV_PROVIDER, decoded.source)
        assertEquals(DiscoveryKind.UNKNOWN, decoded.kind)
    }

    @Test
    fun an_out_of_range_progress_is_clamped_or_dropped() {
        val high = decodeItems("""[{"id":"one","pkg":"a","title":"x","prog":9.5}]""").single()
        assertEquals(1f, high.progress!!, 0.0001f)
        val broken = decodeItems("""[{"id":"one","pkg":"a","title":"x","prog":"nan"}]""").single()
        assertNull(broken.progress)
    }

    @Test
    fun item_pruning_deduplicates_and_respects_the_limit() {
        val items = listOf(item("one"), item("one"), item("two"), item("three"))
        assertEquals(listOf("one", "two"), pruneItems(items, 2).map { it.id })
        assertTrue(pruneItems(items, 0).isEmpty())
    }

    @Test
    fun shown_round_trips_and_prunes_by_time() {
        val shown = mapOf("CONTINUE|a" to 100L, "FOR_YOU|b" to 300L, "FOR_YOU|c" to 200L)
        assertEquals(shown, decodeShown(encodeShown(shown)))
        assertEquals(
            listOf("FOR_YOU|b", "FOR_YOU|c"),
            pruneShown(shown, 2).keys.toList(),
        )
        assertEquals(emptyMap<String, Long>(), decodeShown("""{"a":0}"""))
    }
}
