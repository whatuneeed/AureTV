package com.whatuneed.ovisiontv.kotlin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeOrderTest {

    private fun app(packageName: String) = TvApp(
        packageName = packageName,
        activityName = "$packageName.Main",
        label = packageName,
        banner = null,
        icon = null,
    )

    private val apps = listOf(app("alpha"), app("beta"), app("gamma"), app("delta"))

    @Test
    fun missingOrderListsEveryAppInInstallOrder() {
        val result = buildHomeOrder(null, apps, "Folder")
        assertEquals(listOf("alpha", "beta", "gamma", "delta"), result.keys())
    }

    @Test
    fun mixedOrderKeepsFoldersAndAppPositions() {
        val saved = """["gamma",{"id":"f1","name":"Папка","apps":["alpha","beta"]},"delta"]"""
        val result = buildHomeOrder(saved, apps, "Folder")
        assertEquals(listOf("gamma", "folder:f1", "delta"), result.keys())
        val folder = result[1] as HomeItem.FolderItem
        assertEquals("Папка", folder.name)
        assertEquals(listOf("alpha", "beta"), folder.apps.map { it.packageName })
    }

    @Test
    fun uninstalledAppsDisappearAndEmptyFolderIsDropped() {
        val saved = """[{"id":"f1","name":"Папка","apps":["gone"]},"beta"]"""
        val result = buildHomeOrder(saved, apps, "Folder")
        assertEquals(listOf("beta", "alpha", "gamma", "delta"), result.keys())
        assertTrue(result.none { it is HomeItem.FolderItem })
    }

    @Test
    fun folderKeepsSurvivingChildren() {
        val saved = """[{"id":"f1","name":"Папка","apps":["alpha","gone"]},"beta"]"""
        val result = buildHomeOrder(saved, apps, "Folder")
        val folder = result.first() as HomeItem.FolderItem
        assertEquals(listOf("alpha"), folder.apps.map { it.packageName })
    }

    @Test
    fun newlyInstalledAppIsAppended() {
        val saved = """["beta"]"""
        val result = buildHomeOrder(saved, apps, "Folder")
        assertEquals(listOf("beta", "alpha", "gamma", "delta"), result.keys())
    }

    @Test
    fun packageInsideFolderIsNotDuplicated() {
        val saved = """[{"id":"f1","name":"Папка","apps":["alpha"]},"alpha","beta"]"""
        val result = buildHomeOrder(saved, apps, "Folder")
        assertEquals(listOf("folder:f1", "beta", "gamma", "delta"), result.keys())
    }

    @Test
    fun brokenJsonFallsBackToInstallOrder() {
        val result = buildHomeOrder("not json", apps, "Folder")
        assertEquals(listOf("alpha", "beta", "gamma", "delta"), result.keys())
    }

    @Test
    fun emptyArrayFallsBackToInstallOrder() {
        val result = buildHomeOrder("[]", apps, "Folder")
        assertEquals(listOf("alpha", "beta", "gamma", "delta"), result.keys())
    }

    @Test
    fun renamedFolderKeepsItsName() {
        val saved = """[{"id":"f1","name":"Кино и сериалы","apps":["alpha","beta"]}]"""
        val result = buildHomeOrder(saved, apps, "Folder")
        val folder = result.first() as HomeItem.FolderItem
        assertEquals("Кино и сериалы", folder.name)
    }

    @Test
    fun blankFolderNameUsesTheGivenName() {
        val saved = """[{"id":"f1","name":"","apps":["alpha","beta"]}]"""
        val result = buildHomeOrder(saved, apps, "Folder")
        val folder = result.first() as HomeItem.FolderItem
        assertEquals("Folder", folder.name)
    }

    @Test
    fun blankFolderNameUsesTheProvidedLocalizedName() {
        val saved = """[{"id":"f1","name":"   ","apps":["alpha","beta"]}]"""
        val result = buildHomeOrder(saved, apps, "Папка")
        val folder = result.first() as HomeItem.FolderItem
        assertEquals("Папка", folder.name)
    }

    @Test
    fun duplicateFolderIdsAreRegenerated() {
        val saved = """[
            {"id":"same","name":"A","apps":["alpha","beta"]},
            {"id":"same","name":"B","apps":["gamma","delta"]}
        ]"""
        val result = buildHomeOrder(saved, apps, "Folder")
        assertEquals(2, result.size)
        assertEquals("A", (result[0] as HomeItem.FolderItem).name)
        assertEquals("B", (result[1] as HomeItem.FolderItem).name)
        assertEquals(2, result.map { it.key }.toSet().size)
    }

    @Test
    fun newAppsAreInsertedBeforeTheHiddenFolder() {
        val saved = """["beta",{"id":"hidden","name":"Ещё приложения","apps":["alpha"]}]"""
        val result = buildHomeOrder(saved, apps, "Папка", "More Apps")
        assertEquals(
            listOf("beta", "gamma", "delta", "folder:hidden"),
            result.keys(),
        )
    }

    @Test
    fun hiddenFolderTakesTheNameOfTheCurrentLanguage() {
        val saved = """[{"id":"hidden","name":"Ещё приложения","apps":["alpha","beta"]}]"""
        val result = buildHomeOrder(saved, apps, "Папка", "More Apps")
        // Newly installed apps fill in before the hidden folder, which stays last.
        val folder = result.last() as HomeItem.FolderItem
        assertEquals("More Apps", folder.name)
    }

    @Test
    fun userNamedFolderKeepsItsNameWhenAHiddenNameIsProvided() {
        val saved = """[{"id":"f1","name":"Кино","apps":["alpha","beta"]}]"""
        val result = buildHomeOrder(saved, apps, "Папка", "More Apps")
        val folder = result.first() as HomeItem.FolderItem
        assertEquals("Кино", folder.name)
    }

    @Test
    fun orderCoveringEveryAppCountsAsComplete() {
        val saved = """["gamma",{"id":"f1","name":"Папка","apps":["alpha","beta"]},"delta"]"""
        assertTrue(savedOrderCovers(saved, apps))
    }

    @Test
    fun orderMissingAnAppIsIncomplete() {
        val saved = """["gamma",{"id":"f1","name":"Папка","apps":["alpha","beta"]}]"""
        assertEquals(false, savedOrderCovers(saved, apps))
    }

    @Test
    fun orderAlsoListingUninstalledAppsStillCoversInstalledOnes() {
        val saved = """["alpha","beta","gamma","delta","gone"]"""
        assertTrue(savedOrderCovers(saved, apps))
    }

    @Test
    fun absentCorruptOrEmptyOrderIsIncomplete() {
        assertEquals(false, savedOrderCovers(null, apps))
        assertEquals(false, savedOrderCovers("not json", apps))
        assertEquals(false, savedOrderCovers("[]", apps))
    }

    private fun List<HomeItem>.keys(): List<String> = map { it.key }
}
