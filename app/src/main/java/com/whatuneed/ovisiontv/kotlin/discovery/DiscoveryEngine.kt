package com.whatuneed.ovisiontv.kotlin.discovery

import android.content.Context
import android.content.Intent
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.data.ContentPreferences
import com.whatuneed.ovisiontv.kotlin.data.LocalePreference
import com.whatuneed.ovisiontv.kotlin.data.TvApp
import com.whatuneed.ovisiontv.kotlin.data.WatchNextRow
import com.whatuneed.ovisiontv.kotlin.data.WatchNextSource
import com.whatuneed.ovisiontv.kotlin.online.OnlineRepository
import com.whatuneed.ovisiontv.kotlin.online.toDiscoveryItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Everything the pure builder needs; no Android call happens inside it. */
internal data class DiscoveryInputs(
    val profileId: String,
    val nowUtc: Long,
    val ownPackage: String,
    val currentPackage: String?,
    val apps: List<TvApp>,
    /** Packages whose tiles the profile can actually see (Kids filters this). */
    val visiblePackages: Set<String>,
    val kidsMode: Boolean,
    val providerRows: List<WatchNextRow>,
    val favorites: List<DiscoveryItem>,
    val hidden: Set<String>,
    val usage: Map<String, Usage>,
    val shownAt: Map<String, Long>,
    val includeContinue: Boolean,
    val personalization: Boolean,
    val providerState: DiscoverySourceState,
    // Real chart entries from the optional public online source. They bypass
    // the TV provider filter because they are not published by an installed
    // app, but never bypass Kids mode or the hidden list.
    val onlineItems: List<DiscoveryItem> = emptyList(),
)

/**
 * Builds the rails from already-read sources. The function is pure so the
 * profile, Kids and diversity rules are tested on the JVM with synthetic rows.
 */
internal fun buildSnapshot(inputs: DiscoveryInputs): DiscoverySnapshot {
    val appsByPackage = inputs.apps.associateBy { it.packageName }
    val installed = appsByPackage.keys
    val visible = inputs.visiblePackages
    val hidden = inputs.hidden

    val providerItems = dedupeProvider(
        inputs.providerRows
            .asSequence()
            .filter { it.packageName != inputs.ownPackage }
            .filter { it.packageName in visible }
            .filter { it.title.isNotBlank() && !it.intentUri.isNullOrBlank() }
            .filter {
                inputs.includeContinue || !(it.continueRow || it.progress != null)
            }
            .map { row -> row.toDiscoveryItem(appsByPackage[row.packageName]?.label) }
            .filterNot { it.id in hidden }
            .toList(),
    )

    val favoriteIds = inputs.favorites.mapTo(HashSet()) { it.id }
    val shownIds = shownIdsByItem(inputs.shownAt)
    val ranking = RankingContext(
        nowUtc = inputs.nowUtc,
        usage = if (inputs.personalization) inputs.usage else emptyMap(),
        favoriteIds = favoriteIds,
        shownIds = shownIds,
        currentPackage = inputs.currentPackage,
        profileId = inputs.profileId,
    )

    val rails = ArrayList<DiscoveryRail>(5)

    // Continue Watching: only rows that carry a real resume point or a
    // CONTINUE/NEXT type. The app-specific shelf still shows everything.
    var continueIds: Set<String> = emptySet()
    if (inputs.includeContinue) {
        val continuing = providerItems.filter { it.progress != null || it.isContinue }
        val ranked = rankSection(
            candidates = continuing,
            section = DiscoverySection.CONTINUE,
            context = ranking,
            limit = SECTION_LIMIT,
            maxPerPackage = 2,
        )
        if (ranked.isNotEmpty()) {
            continueIds = ranked.mapTo(HashSet()) { it.id }
            rails += DiscoveryRail(DiscoverySection.CONTINUE, ranked)
        }
    }

    // For You: every visible provider row, at most two per app. A rail with
    // fewer than three cards is not worth a section. A card already in
    // Continue Watching is not repeated in the next rail.
    //
    // Kids profiles keep the app-specific rails only: a cross-app "For You"
    // would let the launcher choose between apps a parent allowed, which is a
    // decision the profile did not grant. Allowed rows still appear under the
    // app that published them.
    if (!inputs.kidsMode) {
        val forYou = rankSection(
            candidates = providerItems.filterNot { it.id in continueIds },
            section = DiscoverySection.FOR_YOU,
            context = ranking,
            limit = SECTION_LIMIT,
            maxPerPackage = 2,
        )
        if (forYou.size >= 3) rails += DiscoveryRail(DiscoverySection.FOR_YOU, forYou)
    }

    // Online Top Movies are opt-in and never appear for a Kids profile. The
    // rail is used exactly as published, without ranking across providers.
    if (!inputs.kidsMode) {
        val online = inputs.onlineItems
            .asSequence()
            .filter { it.id !in hidden }
            .filter { !it.intentUri.isNullOrBlank() }
            .take(SECTION_LIMIT)
            .toList()
        if (online.isNotEmpty()) {
            rails += DiscoveryRail(DiscoverySection.ONLINE, online)
        }
    }

    // From this app: the old tvOS top shelf, kept as one rail among the others.
    val current = inputs.currentPackage
    if (current != null && current != inputs.ownPackage) {
        val own = providerItems.filter { it.packageName == current }
        val fromApp = rankSection(
            candidates = own,
            section = DiscoverySection.FROM_APP,
            context = ranking,
            limit = SECTION_LIMIT,
            maxPerPackage = SECTION_LIMIT,
            // This is the app's own shelf, not a cross-app guess: an older
            // row still belongs here, exactly as it did before discovery.
            minScore = 0.0,
        )
        if (fromApp.isNotEmpty()) rails += DiscoveryRail(DiscoverySection.FROM_APP, fromApp)
    }

    // Recently used: only packages the launcher itself launched. This is not a
    // view history; it is the launcher's own interaction history.
    if (inputs.personalization && !inputs.kidsMode) {
        val recent = rankSection(
            candidates = recentAppItems(
                usage = inputs.usage,
                appsByPackage = appsByPackage,
                visible = visible,
                ownPackage = inputs.ownPackage,
                hidden = hidden,
                nowUtc = inputs.nowUtc,
            ),
            section = DiscoverySection.RECENT,
            context = ranking,
            limit = SECTION_LIMIT,
            maxPerPackage = SECTION_LIMIT,
            minScore = DiscoveryWeights.RECENT_MIN_SCORE,
            forcedReason = DiscoveryReason.RECENTLY_LAUNCHED,
        )
        if (recent.size >= 2) rails += DiscoveryRail(DiscoverySection.RECENT, recent)
    }

    // Favorites are explicit, so they survive a personalization reset. Kids and
    // an uninstalled app still filter them out.
    val favorites = if (inputs.kidsMode) {
        emptyList()
    } else {
        inputs.favorites
            .asSequence()
            .filter { it.packageName != inputs.ownPackage }
            .filter { it.packageName in installed }
            .filterNot { it.id in hidden }
            .toList()
    }
    val favoriteRail = rankSection(
        candidates = favorites,
        section = DiscoverySection.FAVORITES,
        context = ranking,
        limit = SECTION_LIMIT,
        maxPerPackage = SECTION_LIMIT,
        minScore = 0.0,
        forcedReason = DiscoveryReason.FAVORITE,
    )
    if (favoriteRail.isNotEmpty()) rails += DiscoveryRail(DiscoverySection.FAVORITES, favoriteRail)

    // The dock app's own shelf leads, as on tvOS; the rest follow.
    val ordered = rails.sortedBy { SECTION_ORDER.indexOf(it.section) }

    // A home screen is not a feed. Five honest rails can still add up to a
    // wall of pills, so one section is dropped when all five are populated:
    // Recently Used is the one Continue Watching already covers.
    val visibleRails = if (ordered.size <= MAX_SECTIONS) {
        ordered
    } else {
        ordered.filterNot { it.section == DiscoverySection.RECENT }.take(MAX_SECTIONS)
    }

    return DiscoverySnapshot(
        profileId = inputs.profileId,
        rails = visibleRails,
        generatedAtUtc = inputs.nowUtc,
        providerState = inputs.providerState,
    )
}

internal const val SECTION_LIMIT = 8
internal const val MAX_SECTIONS = 4

private val SECTION_ORDER = listOf(
    DiscoverySection.FROM_APP,
    DiscoverySection.CONTINUE,
    DiscoverySection.ONLINE,
    DiscoverySection.FOR_YOU,
    DiscoverySection.RECENT,
    DiscoverySection.FAVORITES,
)

private fun dedupeProvider(items: List<DiscoveryItem>): List<DiscoveryItem> {
    val byId = LinkedHashMap<String, DiscoveryItem>()
    items.forEach { item ->
        val previous = byId[item.id]
        if (previous == null ||
            (item.lastEngagementUtc ?: 0L) > (previous.lastEngagementUtc ?: 0L)
        ) {
            byId[item.id] = item
        }
    }
    return byId.values.toList()
}

private fun WatchNextRow.toDiscoveryItem(label: String?): DiscoveryItem = DiscoveryItem(
    id = "tv|$packageName|$intentUri",
    source = DiscoverySource.TV_PROVIDER,
    kind = DiscoveryKind.VIDEO,
    packageName = packageName,
    packageLabel = label,
    title = title,
    subtitle = subtitle,
    landscapeUri = thumbnailUri,
    posterUri = posterUri,
    intentUri = intentUri,
    progress = progress,
    isContinue = continueRow,
    lastEngagementUtc = engagement.takeIf { it > 0L },
)

private fun recentAppItems(
    usage: Map<String, Usage>,
    appsByPackage: Map<String, TvApp>,
    visible: Set<String>,
    ownPackage: String,
    hidden: Set<String>,
    nowUtc: Long,
): List<DiscoveryItem> {
    val floor = nowUtc - RECENT_WINDOW_MS
    return usage.entries
        .asSequence()
        .filter { it.value.lastUtc >= floor }
        .filter { it.key != ownPackage && it.key in visible }
        .sortedByDescending { it.value.lastUtc }
        .mapNotNull { (packageName, value) ->
            val app = appsByPackage[packageName] ?: return@mapNotNull null
            app.toDiscoveryItem(value).takeIf { it.id !in hidden }
        }
        .toList()
}

private fun TvApp.toDiscoveryItem(usage: Usage): DiscoveryItem = DiscoveryItem(
    id = "app|$packageName",
    source = DiscoverySource.PACKAGE_MANAGER,
    kind = DiscoveryKind.APP,
    packageName = packageName,
    packageLabel = label,
    title = label,
    landscapeUri = banner,
    posterUri = icon,
    activityName = activityName,
    lastEngagementUtc = usage.lastUtc,
    reason = DiscoveryReason.RECENTLY_LAUNCHED,
)

private const val RECENT_WINDOW_MS = 30L * 86_400_000L

/** "CONTINUE|<item id>" -> item id; max wins if a card appeared in two rails. */
internal fun shownIdsByItem(shownAt: Map<String, Long>): Map<String, Long> {
    if (shownAt.isEmpty()) return emptyMap()
    val result = HashMap<String, Long>()
    shownAt.forEach { (key, value) ->
        val id = key.substringAfter('|')
        if (id.isNotEmpty()) result[id] = maxOf(result[id] ?: 0L, value)
    }
    return result
}

/**
 * Reads the sanctioned sources on IO and hands an immutable snapshot to Compose.
 * The engine owns no global state: a profile change creates a new instance, so
 * two profiles can never share usage, favorites or hidden cards.
 */
class DiscoveryEngine(
    context: Context,
    prefsName: String,
    private val profileId: String,
) {
    private val appContext = context.applicationContext
    private val store = DiscoveryStore(appContext, prefsName)
    private val settings = DiscoverySettings(appContext, prefsName)
    private val online = OnlineRepository(java.io.File(appContext.cacheDir, "online-top-movies.json"))

    fun recordLaunch(packageName: String) = store.recordLaunch(packageName)

    /** Home combines this with its Kids Mode and Reduce Motion gates. */
    fun previewAutoplay(): Boolean = settings.previewAutoplay()

    /** Only the hero the viewer actually saw is penalised, never an unseen rail. */
    fun markShown(section: DiscoverySection, item: DiscoveryItem) {
        if (!settings.personalization()) return
        store.markShown(section, listOf(item), System.currentTimeMillis())
    }

    fun hide(item: DiscoveryItem) = store.hide(item)

    fun unhide(id: String) = store.unhide(id)

    fun removeFavorite(id: String) = store.removeFavorite(id)

    fun addFavorite(item: DiscoveryItem) = store.addFavorite(item)

    fun isFavorite(id: String): Boolean = store.isFavorite(id)

    fun favorites(): List<DiscoveryItem> = store.favorites()

    fun hidden(): List<DiscoveryItem> = store.hidden()

    fun contentIntent(item: DiscoveryItem): Intent? =
        DiscoveryIntents.contentIntent(appContext, item)

    suspend fun refresh(
        currentPackage: String?,
        apps: List<TvApp>,
        visiblePackages: Set<String>,
        kidsMode: Boolean,
        nowUtc: Long = System.currentTimeMillis(),
    ): DiscoverySnapshot = withContext(Dispatchers.IO) {
        if (!settings.recommendations()) {
            return@withContext DiscoverySnapshot(
                profileId = profileId,
                rails = emptyList(),
                generatedAtUtc = nowUtc,
                providerState = DiscoverySourceState.DISABLED,
            )
        }
        // A missing provider permission only removes provider rows. The
        // launcher still owns its own launch history and explicit favorites,
        // so those rails keep working instead of turning the shelf into a
        // banner for a permission the viewer may have refused on purpose.
        val permitted = ContentPreferences.canReadListings(appContext)
        val rows: List<WatchNextRow>
        val providerState: DiscoverySourceState
        if (!permitted) {
            rows = emptyList()
            providerState = DiscoverySourceState.NO_PERMISSION
        } else {
            var readFailed = false
            rows = try {
                WatchNextSource.loadRows(appContext)
            } catch (_: Exception) {
                readFailed = true
                emptyList()
            }
            providerState = when {
                readFailed -> DiscoverySourceState.FAILED
                rows.isEmpty() -> DiscoverySourceState.EMPTY
                else -> DiscoverySourceState.OK
            }
        }

        val onlineItems = if (settings.online() && !kidsMode) {
            try {
                online.topMovies(chartCountry()).map { movie ->
                    movie.toDiscoveryItem(
                        ownPackage = appContext.packageName,
                        sourceLabel = appContext.getString(R.string.online_source_label),
                        previewLabel = appContext.getString(R.string.trailer_preview),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        val personalization = settings.personalization()
        val snapshot = buildSnapshot(
            DiscoveryInputs(
                profileId = profileId,
                nowUtc = nowUtc,
                ownPackage = appContext.packageName,
                currentPackage = currentPackage,
                apps = apps,
                visiblePackages = visiblePackages,
                kidsMode = kidsMode,
                providerRows = rows,
                favorites = store.favorites(),
                hidden = store.hiddenIds(),
                usage = if (personalization) store.usage() else emptyMap(),
                shownAt = if (personalization) store.shownAt() else emptyMap(),
                includeContinue = settings.continueWatching(),
                personalization = personalization,
                providerState = providerState,
                onlineItems = onlineItems,
            ),
        )
        snapshot
    }

    /**
     * The public chart is localized by storefront, not by the UI language
     * alone. Russian UI asks the Russian store; otherwise the system country
     * is used, with the US chart as the honest fallback.
     */
    private fun chartCountry(): String {
        if (LocalePreference.get(appContext) == LocalePreference.RUSSIAN) return "ru"
        val country = java.util.Locale.getDefault().country.lowercase(java.util.Locale.ROOT)
        return country.takeIf { it.length == 2 } ?: "us"
    }
}
