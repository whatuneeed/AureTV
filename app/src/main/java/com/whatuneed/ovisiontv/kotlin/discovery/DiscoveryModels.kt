package com.whatuneed.ovisiontv.kotlin.discovery

/**
 * One discovery rail. Rails are the only grouping the engine produces: a
 * category is a reason to belong to a rail, never an invented genre.
 */
enum class DiscoverySection {
    CONTINUE,
    ONLINE,
    FOR_YOU,
    FROM_APP,
    RECENT,
    FAVORITES,
}

/**
 * What the card is. The TV provider does not publish genres, so a provider row
 * is [VIDEO] and an installed application is [APP]. The launcher never guesses
 * "movie" or "series" from a title.
 */
enum class DiscoveryKind { VIDEO, APP, UNKNOWN }

/** Why a card is on screen. Every card carries exactly one real reason. */
enum class DiscoveryReason {
    CONTINUE_WATCHING,
    RECENTLY_LAUNCHED,
    FAVORITE,
    NEW_FROM_APP,
    OFTEN_USED,
    FROM_CURRENT_APP,
    TRENDING,
    DIVERSE_PICK,
}

/** Where the card came from. No source is ever invented. */
enum class DiscoverySource { TV_PROVIDER, LAUNCHER, PACKAGE_MANAGER, ONLINE }

/** Health of the provider read; a failure degrades to local rails, never fake content. */
enum class DiscoverySourceState { OK, EMPTY, NO_PERMISSION, DISABLED, FAILED }

/**
 * A stable card. [id] is the identity used by favorites, hidden cards and focus
 * restoration, so it must not depend on the list position or on the score.
 */
data class DiscoveryItem(
    val id: String,
    val source: DiscoverySource,
    val kind: DiscoveryKind,
    val packageName: String,
    val packageLabel: String? = null,
    val title: String,
    val subtitle: String? = null,
    val landscapeUri: String? = null,
    val posterUri: String? = null,
    val intentUri: String? = null,
    val activityName: String? = null,
    val progress: Float? = null,
    val isContinue: Boolean = false,
    val lastEngagementUtc: Long? = null,
    val reason: DiscoveryReason = DiscoveryReason.NEW_FROM_APP,
    val score: Double = 0.0,
)

/** Landscape art is the shelf's native ratio; the poster is the fallback. */
val DiscoveryItem.artUri: String? get() = landscapeUri ?: posterUri

val DiscoveryItem.isApp: Boolean get() = source == DiscoverySource.PACKAGE_MANAGER

/** One titled row. Empty rails never reach the UI. */
data class DiscoveryRail(
    val section: DiscoverySection,
    val items: List<DiscoveryItem>,
)

/** Immutable result of one refresh. The UI only reads it. */
data class DiscoverySnapshot(
    val profileId: String,
    val rails: List<DiscoveryRail>,
    val generatedAtUtc: Long,
    val providerState: DiscoverySourceState,
) {
    fun rail(section: DiscoverySection): DiscoveryRail? =
        rails.firstOrNull { it.section == section }

    companion object {
        val EMPTY = DiscoverySnapshot(
            profileId = "",
            rails = emptyList(),
            generatedAtUtc = 0L,
            providerState = DiscoverySourceState.EMPTY,
        )
    }
}

/** Default rail when the dock app published nothing: app content first, then Up Next. */
internal fun defaultDiscoverySection(rails: List<DiscoveryRail>): DiscoverySection? {
    val order = listOf(
        DiscoverySection.FROM_APP,
        DiscoverySection.CONTINUE,
        DiscoverySection.ONLINE,
        DiscoverySection.FOR_YOU,
        DiscoverySection.RECENT,
        DiscoverySection.FAVORITES,
    )
    return order.firstOrNull { section -> rails.any { it.section == section && it.items.isNotEmpty() } }
}
