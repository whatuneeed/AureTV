package com.whatuneed.ovisiontv.kotlin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchNextTest {

    private fun row(
        title: String = "Title",
        packageName: String = "ru.kino",
        intent: String? = "intent://watch#Intent;package=ru.kino;end",
        engagement: Long = 0L,
        continueRow: Boolean = true,
    ) = WatchNextRow(
        packageName = packageName,
        title = title,
        subtitle = null,
        thumbnailUri = null,
        posterUri = null,
        intentUri = intent,
        progress = null,
        engagement = engagement,
        continueRow = continueRow,
    )

    @Test
    fun progressNeedsBothValues() {
        assertNull(watchNextProgress(0, 100))
        assertNull(watchNextProgress(10, 0))
        assertNull(watchNextProgress(-1, 100))
        assertEquals(0.25f, watchNextProgress(25, 100)!!, 0.001f)
        assertEquals(1f, watchNextProgress(120, 100)!!, 0.001f)
    }

    @Test
    fun onlyFocusedPackageRowsAreKept() {
        val ranked = rankWatchNext(
            listOf(
                row(title = "Kino", packageName = "ru.kino"),
                row(title = "Other", packageName = "ru.other"),
            ),
            packageName = "ru.kino",
        )
        assertEquals(listOf("Kino"), ranked.map { it.title })
    }

    @Test
    fun rowsWithoutTitleOrIntentAreDropped() {
        val ranked = rankWatchNext(
            listOf(
                row(title = " "),
                row(title = "No link", intent = null),
                row(title = "Blank link", intent = " "),
                row(title = "Good"),
            ),
            packageName = "ru.kino",
        )
        assertEquals(listOf("Good"), ranked.map { it.title })
    }

    @Test
    fun newestEngagementComesFirst() {
        val ranked = rankWatchNext(
            listOf(
                row(title = "Old", intent = "intent://old", engagement = 10),
                row(title = "New", intent = "intent://new", engagement = 30),
                row(title = "Mid", intent = "intent://mid", engagement = 20),
            ),
            packageName = "ru.kino",
        )
        assertEquals(listOf("New", "Mid", "Old"), ranked.map { it.title })
    }

    @Test
    fun duplicateIntentIsShownOnce() {
        val ranked = rankWatchNext(
            listOf(
                row(title = "First", engagement = 20),
                row(title = "Same link", engagement = 10),
            ),
            packageName = "ru.kino",
        )
        assertEquals(listOf("First"), ranked.map { it.title })
    }

    @Test
    fun duplicateIntentKeepsNewestEntryRegardlessOfProviderOrder() {
        val ranked = rankWatchNext(
            listOf(
                row(title = "Stale", intent = "intent://same", engagement = 5),
                row(title = "Fresh", intent = "intent://same", engagement = 40),
            ),
            packageName = "ru.kino",
        )
        assertEquals(listOf("Fresh"), ranked.map { it.title })
    }

    @Test
    fun limitIsEightAtMostAndDefaultSix() {
        val rows = (1..12).map { row(title = "T$it", intent = "intent://t$it") }
        assertEquals(6, rankWatchNext(rows, "ru.kino").size)
        assertEquals(8, rankWatchNext(rows, "ru.kino", limit = 8).size)
    }

    @Test
    fun continueRowsCanBeHiddenWithoutHidingNewOnes() {
        val rows = listOf(
            row(title = "Continue", continueRow = true),
            row(title = "New", intent = "intent://new", continueRow = false),
        )
        assertEquals(
            listOf("New"),
            rankWatchNext(rows, "ru.kino", includeContinue = false).map { it.title },
        )
        assertEquals(2, rankWatchNext(rows, "ru.kino", includeContinue = true).size)
    }
}
