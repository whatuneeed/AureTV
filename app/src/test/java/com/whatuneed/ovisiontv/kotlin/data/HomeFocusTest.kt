package com.whatuneed.ovisiontv.kotlin.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeFocusTest {

    private fun app(packageName: String) = TvApp(
        packageName = packageName,
        activityName = "$packageName.Main",
        label = packageName,
        banner = null,
        icon = null,
    )

    private val items = listOf(
        HomeItem.AppItem(app("alpha")),
        HomeItem.FolderItem("f1", "Папка", listOf(app("beta"), app("gamma"))),
        HomeItem.AppItem(app("delta")),
    )

    @Test
    fun appFocusKeyIsItsPackage() {
        assertEquals("alpha", homeFocusKey(items[0]))
    }

    @Test
    fun folderFocusKeyNamesTheFolder() {
        assertEquals("folder:f1", homeFocusKey(items[1]))
        assertEquals(1, indexOfHomeFocus(items, "folder:f1"))
    }

    @Test
    fun savedAppInsideFolderRestoresTheFolder() {
        assertEquals(1, indexOfHomeFocus(items, "beta"))
        assertEquals(1, indexOfHomeFocus(items, "gamma"))
    }

    @Test
    fun savedFolderIdStillWinsOverItsChildren() {
        assertEquals(1, indexOfHomeFocus(items, "folder:f1"))
    }

    @Test
    fun missingOrEmptyValueHasNoIndex() {
        assertEquals(-1, indexOfHomeFocus(items, null))
        assertEquals(-1, indexOfHomeFocus(items, ""))
        assertEquals(-1, indexOfHomeFocus(items, "folder:gone"))
        assertEquals(-1, indexOfHomeFocus(items, "gone"))
    }

    @Test
    fun plainAppFocusRestoresItsCell() {
        assertEquals(2, indexOfHomeFocus(items, "delta"))
    }
}
