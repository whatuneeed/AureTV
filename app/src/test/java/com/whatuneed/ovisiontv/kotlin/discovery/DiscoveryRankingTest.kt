package com.whatuneed.ovisiontv.kotlin.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ranking is the contract between "a row exists" and "this row is on the
 * home screen". These tests pin the thresholds and the diversity rules so a
 * later change cannot quietly turn the shelf into an endless feed.
 */
class DiscoveryRankingTest {

    private val now = 1_700_000_000_000L
    private val day = DiscoveryWeights.DAY_MS

    private fun item(
        id: String,
        packageName: String = "app.one",
        source: DiscoverySource = DiscoverySource.TV_PROVIDER,
        progress: Float? = null,
        continueRow: Boolean = false,
        lastUtc: Long? = null,
    ) = DiscoveryItem(
        id = id,
        source = source,
        kind = if (source == DiscoverySource.PACKAGE_MANAGER) {
            DiscoveryKind.APP
        } else {
            DiscoveryKind.VIDEO
        },
        packageName = packageName,
        title = id,
        intentUri = "intent://$id#Intent;package=$packageName;end",
        progress = progress,
        isContinue = continueRow,
        lastEngagementUtc = lastUtc,
    )

    private fun context(
        usage: Map<String, Usage> = emptyMap(),
        favorites: Set<String> = emptySet(),
        shown: Map<String, Long> = emptyMap(),
        currentPackage: String? = null,
    ) = RankingContext(
        nowUtc = now,
        usage = usage,
        favoriteIds = favorites,
        shownIds = shown,
        currentPackage = currentPackage,
        profileId = "p1",
    )

    @Test
    fun recency_halves_every_two_weeks() {
        assertEquals(1.0, recencyFactor(now, now), 0.0001)
        assertEquals(0.5, recencyFactor(now - 14 * day, now), 0.001)
        assertEquals(0.25, recencyFactor(now - 28 * day, now), 0.001)
    }

    @Test
    fun a_missing_timestamp_is_neutral_not_stale() {
        // 0.75 * RECENCY + SOURCE = 0.15 + 0.10 = the For You threshold.
        assertEquals(0.25, scoreItem(item("fresh"), context()), 0.0001)
        assertEquals(0.75, recencyFactor(null, now), 0.0001)
        assertEquals(0.75, recencyFactor(0L, now), 0.0001)
    }

    @Test
    fun affinity_is_logarithmic_and_bounded() {
        assertEquals(0.0, affinityFactor(0, 10), 0.0001)
        assertEquals(0.0, affinityFactor(5, 0), 0.0001)
        assertEquals(1.0, affinityFactor(10, 10), 0.0001)
        assertTrue(affinityFactor(3, 10) < affinityFactor(7, 10))
    }

    @Test
    fun the_shown_penalty_fades_over_a_week() {
        assertEquals(1.0, recentlyShownFactor(now, now), 0.0001)
        assertEquals(0.5, recentlyShownFactor(now - 3 * day - day / 2, now), 0.001)
        assertEquals(0.0, recentlyShownFactor(now - 7 * day, now), 0.0001)
    }

    @Test
    fun a_continue_row_outranks_a_fresh_but_unwatched_row() {
        val continuing = scoreItem(item("c", progress = 0.4f), context())
        val fresh = scoreItem(item("f", lastUtc = now), context())
        assertTrue(continuing > fresh)
    }

    @Test
    fun favorite_and_current_app_are_positive_signals() {
        val plain = item("one")
        assertTrue(
            scoreItem(plain, context(favorites = setOf("one"))) >
                scoreItem(plain, context()),
        )
        assertTrue(
            scoreItem(plain, context(currentPackage = "app.one")) >
                scoreItem(plain, context()),
        )
    }

    @Test
    fun a_shown_card_is_reordered_but_never_removed() {
        val rows = listOf(item("a"), item("b"))
        val ranked = rankSection(
            candidates = rows,
            section = DiscoverySection.FOR_YOU,
            context = context(shown = mapOf("FOR_YOU|a" to now)),
            limit = 2,
        )
        assertEquals(2, ranked.size)
        assertEquals("b", ranked.first().id)
    }

    @Test
    fun a_rail_shows_at_most_two_cards_per_app() {
        val sameApp = (1..5).map { item("a$it", lastUtc = now - it * day) }
        val others = (1..3).map { item("o$it", packageName = "app.$it", lastUtc = now) }
        val ranked = rankSection(
            candidates = sameApp + others,
            section = DiscoverySection.FOR_YOU,
            context = context(),
            limit = 5,
        )
        assertEquals(5, ranked.size)
        assertTrue(ranked.count { it.packageName == "app.one" } <= 2)
    }

    @Test
    fun a_short_rail_fills_past_the_cap_instead_of_leaving_a_gap() {
        val sameApp = (1..3).map { item("a$it", lastUtc = now - it * day) }
        val ranked = rankSection(
            candidates = sameApp,
            section = DiscoverySection.FOR_YOU,
            context = context(),
            limit = 6,
        )
        assertEquals(3, ranked.size)
    }

    @Test
    fun two_cards_from_one_app_are_not_adjacent_when_an_alternative_exists() {
        val rows = (1..4).map { item("a$it", lastUtc = now) } +
            (1..4).map { item("b$it", packageName = "app.two", lastUtc = now) }
        val ranked = rankSection(
            candidates = rows,
            section = DiscoverySection.FOR_YOU,
            context = context(),
            limit = 6,
            maxPerPackage = 4,
        )
        ranked.zipWithNext().forEach { (left, right) ->
            assertNotEquals(left.packageName, right.packageName)
        }
    }

    @Test
    fun a_low_score_is_left_out() {
        val launcher = item(
            id = "app|app.one",
            source = DiscoverySource.PACKAGE_MANAGER,
        )
        assertTrue(scoreItem(launcher, context()) < DiscoveryWeights.MIN_SCORE)
        assertTrue(
            rankSection(
                candidates = listOf(launcher),
                section = DiscoverySection.FOR_YOU,
                context = context(),
                limit = 8,
            ).isEmpty(),
        )
    }

    @Test
    fun one_unfamiliar_card_gets_the_exploration_slot() {
        val familiar = (1..4).map { item("f$it", packageName = "app.f$it", lastUtc = now) }
        val unfamiliar = item("new", packageName = "app.new", lastUtc = now)
        val ranked = rankSection(
            candidates = familiar + unfamiliar,
            section = DiscoverySection.FOR_YOU,
            // A favorite reaches the exploration score without pretending the
            // package is familiar; the reason is still the exploration one.
            context = context(favorites = setOf("new")),
            limit = 4,
        )
        assertTrue(ranked.any { it.reason == DiscoveryReason.DIVERSE_PICK })
        assertEquals("app.new", ranked.single { it.reason == DiscoveryReason.DIVERSE_PICK }.packageName)
        assertTrue(ranked.size <= 4)
    }

    @Test
    fun a_forced_reason_wins_for_the_favorites_rail() {
        val ranked = rankSection(
            candidates = listOf(item("one", progress = 0.5f)),
            section = DiscoverySection.FAVORITES,
            context = context(),
            limit = 8,
            minScore = 0.0,
            forcedReason = DiscoveryReason.FAVORITE,
        )
        assertEquals(DiscoveryReason.FAVORITE, ranked.single().reason)
    }

    @Test
    fun duplicate_ids_keep_the_fresher_row() {
        val old = item("one", lastUtc = now - 5 * day)
        val fresh = item("one", lastUtc = now)
        val ranked = rankSection(
            candidates = listOf(old, fresh),
            section = DiscoverySection.FOR_YOU,
            context = context(),
            limit = 8,
        )
        assertEquals(1, ranked.size)
        assertEquals(now, ranked.single().lastEngagementUtc)
    }

    @Test
    fun the_tie_break_is_stable_within_a_day_and_moves_between_days() {
        val dayStart = now / day * day
        val morning = dailyTieBreak("p1", dayStart, "card")
        val evening = dailyTieBreak("p1", dayStart + 23 * 3_600_000L, "card")
        assertEquals(morning, evening)
        assertNotEquals(morning, dailyTieBreak("p1", dayStart + day, "card"))
    }

    @Test
    fun only_a_package_quiet_for_a_month_counts_as_unfamiliar() {
        val recent = mapOf("app.one" to Usage(3, now - 2 * day))
        val quiet = mapOf("app.one" to Usage(3, now - 40 * day))
        assertFalse(isUnfamiliar("app.one", context(usage = recent)))
        assertTrue(isUnfamiliar("app.one", context(usage = quiet)))
        assertTrue(isUnfamiliar("app.unknown", context()))
    }
}
