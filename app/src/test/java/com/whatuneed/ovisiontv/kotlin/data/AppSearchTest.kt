package com.whatuneed.ovisiontv.kotlin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSearchTest {

    private fun app(packageName: String, label: String) = TvApp(
        packageName = packageName,
        activityName = "$packageName.Main",
        label = label,
        banner = null,
        icon = null,
    )

    private val apps = listOf(
        app("ru.kino", "КиноПоиск"),
        app("ru.piknik", "Пикник"),
        app("ru.kino.live", "Кино Live"),
        app("ru.moe", "Моё Кино"),
        app("ru.aikido", "Аикидо"),
        app("en.tv", "TV Guide"),
        app("ru.yolka", "Ёлка ТВ"),
    )

    private fun labels(query: String) = searchApps(apps, query).map { it.label }

    @Test
    fun blankQueryHasNoResults() {
        assertTrue(searchApps(apps, "").isEmpty())
        assertTrue(searchApps(apps, "   ").isEmpty())
    }

    @Test
    fun matchIsCaseInsensitive() {
        assertEquals(listOf("Кино Live", "КиноПоиск", "Моё Кино"), labels("КИНО"))
        assertEquals(listOf("TV Guide"), labels("tv"))
    }

    @Test
    fun yoMatchesYe() {
        assertEquals(listOf("Ёлка ТВ"), labels("елка"))
        assertEquals(listOf("Ёлка ТВ"), labels("ЁЛКА"))
    }

    @Test
    fun prefixRanksAboveWordAndWordAboveSubstring() {
        assertEquals(
            listOf("Кино Live", "КиноПоиск", "Моё Кино", "Аикидо"),
            labels("ки"),
        )
    }

    @Test
    fun exactNameWins() {
        assertEquals(listOf("Пикник"), labels("пикник"))
    }

    @Test
    fun queryWithExtraSpacesStillMatches() {
        assertEquals(listOf("TV Guide"), labels("  tv   guide "))
    }

    @Test
    fun noMatchReturnsEmpty() {
        assertTrue(searchApps(apps, "нет такого").isEmpty())
    }

    @Test
    fun wordOrderDoesNotMatter() {
        val names = listOf(
            app("com.google.youtube.kids", "YouTube Kids"),
            app("com.google.youtube", "YouTube"),
        )
        assertEquals(
            listOf("YouTube Kids"),
            searchApps(names, "kids youtube").map { it.label },
        )
    }

    @Test
    fun wordsMayBeSplitByNbsp() {
        val names = listOf(app("com.ivi", "Иви\u00A0ТВ"), app("com.other", "Другое"))
        assertEquals(listOf("Иви\u00A0ТВ"), searchApps(names, "иви тв").map { it.label })
    }

    @Test
    fun hyphensMatchASpaceAndTheOtherWayAround() {
        val names = listOf(app("com.wifi", "Wi-Fi"), app("com.other", "Другое"))
        assertEquals(listOf("Wi-Fi"), searchApps(names, "wi fi").map { it.label })
        assertEquals(listOf("Wi-Fi"), searchApps(names, "wi-fi").map { it.label })
    }

    @Test
    fun dashInsideTheQueryStillMatchesASpaceInTheLabel() {
        val names = listOf(app("com.kids", "Kids TV"), app("com.other", "Другое"))
        assertEquals(listOf("Kids TV"), searchApps(names, "kids\u2013tv").map { it.label })
    }

    @Test
    fun duplicatePackageIsListedOnce() {
        val doubled = apps + app("ru.kino", "КиноПоиск")
        assertEquals(1, searchApps(doubled, "кино").count { it.packageName == "ru.kino" })
    }

    @Test
    fun homeAppsFlattenFoldersAndHiddenApps() {
        val items = listOf(
            HomeItem.AppItem(app("a", "Первый")),
            HomeItem.FolderItem(
                id = "f",
                name = "Папка",
                apps = listOf(app("b", "Второй"), app("c", "Третий")),
            ),
            HomeItem.FolderItem(
                id = HIDDEN_FOLDER_ID,
                name = "Ещё",
                apps = listOf(app("d", "Четвёртый")),
            ),
        )
        assertEquals(listOf("a", "b", "c", "d"), homeApps(items).map { it.packageName })
    }

    @Test
    fun homeAppsDropsDuplicates() {
        val same = app("a", "Первый")
        val items = listOf(
            HomeItem.AppItem(same),
            HomeItem.FolderItem("f", "Папка", listOf(same)),
        )
        assertEquals(1, homeApps(items).size)
    }
}
