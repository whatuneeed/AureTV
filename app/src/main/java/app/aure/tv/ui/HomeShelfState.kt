package app.aure.tv.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.aure.tv.discovery.DiscoveryItem
import app.aure.tv.discovery.DiscoverySection
import app.aure.tv.discovery.DiscoverySnapshot
import app.aure.tv.discovery.defaultDiscoverySection

/**
 * Shelf and discovery state for Home. The selected rail is kept in a
 * `rememberSaveable` holder passed by the composable, so the class can own the
 * rest without losing process-restore behaviour.
 */
internal class HomeShelfState(
    val sectionName: MutableState<String?>,
) {
    var snapshot by mutableStateOf(DiscoverySnapshot.EMPTY)
    var sectionFocused by mutableStateOf(false)
    var revision by mutableIntStateOf(0)
    var menu by mutableStateOf<DiscoveryItem?>(null)
    var selected by mutableIntStateOf(0)
    var focus by mutableIntStateOf(-1)
    var focusTick by mutableIntStateOf(0)
    val sectionCardMemory = mutableStateMapOf<String, Int>()
    var lastShelfPackage by mutableStateOf<String?>(null)
    var playingTrailer by mutableStateOf<PlayingTrailer?>(null)
    var onlineInfo by mutableStateOf<DiscoveryItem?>(null)
    var onlineInfoTarget by mutableStateOf<DiscoveryItem?>(null)

    val rails get() = snapshot.rails
    val currentSection get() = rails.firstOrNull { it.section.name == sectionName.value }?.section
        ?: defaultDiscoverySection(rails)
    val shelfCards get() = rails.firstOrNull { it.section == currentSection }?.items.orEmpty()

    fun selectSection(section: DiscoverySection) {
        val previous = currentSection
        if (previous != null) sectionCardMemory[previous.name] = selected
        sectionName.value = section.name
        val rail = rails.firstOrNull { it.section == section } ?: return
        selected = (sectionCardMemory[section.name] ?: 0)
            .coerceIn(0, rail.items.lastIndex.coerceAtLeast(0))
        if (focus >= 0) focus = selected
    }

    fun enterShelf() {
        if (rails.isEmpty()) return
        val target = currentSection ?: defaultDiscoverySection(rails) ?: return
        sectionName.value = target.name
        sectionFocused = false
        val rail = rails.firstOrNull { it.section == target } ?: return
        selected = selected.coerceIn(0, rail.items.lastIndex.coerceAtLeast(0))
        focus = selected
    }

}
