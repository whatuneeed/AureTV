package app.aure.tv.ui

import androidx.compose.runtime.mutableStateOf
import app.aure.tv.discovery.DiscoveryItem
import app.aure.tv.discovery.DiscoveryKind
import app.aure.tv.discovery.DiscoveryRail
import app.aure.tv.discovery.DiscoverySection
import app.aure.tv.discovery.DiscoverySnapshot
import app.aure.tv.discovery.DiscoverySource
import app.aure.tv.discovery.DiscoverySourceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The shelf selection is small but stateful: switching rails must remember the
 * card per rail, clamp after a rail shrinks, and never leave the cursor on a
 * card that no longer exists.
 */
class HomeShelfStateTest {

    private fun item(id: String) = DiscoveryItem(
        id = id,
        source = DiscoverySource.PACKAGE_MANAGER,
        kind = DiscoveryKind.VIDEO,
        packageName = "com.example.player",
        title = id,
    )

    private fun rail(section: DiscoverySection, count: Int) = DiscoveryRail(
        section = section,
        items = (0 until count).map { item("${section.name}-$it") },
    )

    private fun state(
        vararg rails: DiscoveryRail,
        selected: String? = null,
    ) = HomeShelfState(mutableStateOf(selected)).apply {
        snapshot = DiscoverySnapshot(
            profileId = "p1",
            rails = rails.toList(),
            generatedAtUtc = 0L,
            providerState = DiscoverySourceState.OK,
        )
    }

    @Test
    fun switching_sections_keeps_the_card_for_each_rail() {
        val shelf = state(rail(DiscoverySection.FROM_APP, 2), rail(DiscoverySection.FOR_YOU, 4))

        shelf.selectSection(DiscoverySection.FROM_APP)
        shelf.selected = 1
        shelf.selectSection(DiscoverySection.FOR_YOU)
        shelf.selected = 3
        shelf.selectSection(DiscoverySection.FROM_APP)

        assertEquals(DiscoverySection.FROM_APP, shelf.sectionName.value?.let(DiscoverySection::valueOf))
        assertEquals(1, shelf.selected)
    }

    @Test
    fun switching_to_a_shorter_rail_clamps_the_remembered_card() {
        val shelf = state(rail(DiscoverySection.FROM_APP, 5), rail(DiscoverySection.RECENT, 1))

        shelf.selectSection(DiscoverySection.FROM_APP)
        shelf.selected = 4
        shelf.selectSection(DiscoverySection.RECENT)

        assertEquals(0, shelf.selected)
    }

    @Test
    fun enter_shelf_uses_the_default_rail_and_moves_the_cursor_to_its_card() {
        val shelf = state(rail(DiscoverySection.FOR_YOU, 3), rail(DiscoverySection.RECENT, 2))

        shelf.enterShelf()

        assertEquals(DiscoverySection.FOR_YOU, shelf.sectionName.value?.let(DiscoverySection::valueOf))
        assertEquals(0, shelf.selected)
        assertEquals(0, shelf.focus)
        assertFalse(shelf.sectionFocused)
    }

    @Test
    fun enter_shelf_does_nothing_without_rails() {
        val shelf = state()

        shelf.enterShelf()

        assertNull(shelf.sectionName.value)
        assertEquals(-1, shelf.focus)
        assertEquals(0, shelf.selected)
    }
}
