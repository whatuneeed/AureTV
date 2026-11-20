package app.aure.tv.discovery

import kotlin.math.exp
import kotlin.math.ln

/**
 * Transparent rules, one place to change them. The maximum of the positive
 * parts is 1.0, so a reader can compare a score with the threshold by hand.
 */
internal object DiscoveryWeights {
    const val CONTINUE = 0.35
    const val RECENCY = 0.20
    const val AFFINITY = 0.15
    const val FAVORITE = 0.10
    const val CURRENT_APP = 0.10
    const val SOURCE = 0.10

    const val SHOWN = 0.25
    const val SAME_PACKAGE = 0.10

    const val MIN_SCORE = 0.25
    const val RECENT_MIN_SCORE = 0.10
    const val EXPLORATION_SCORE = 0.35

    const val DAY_MS = 86_400_000L
    const val RECENCY_HALF_LIFE_DAYS = 14.0
    const val SHOWN_DAYS = 7.0
    const val EXPLORATION_DAYS = 30.0
}

internal data class RankingContext(
    val nowUtc: Long,
    val usage: Map<String, Usage>,
    val favoriteIds: Set<String>,
    val shownIds: Map<String, Long>,
    val currentPackage: String?,
    val profileId: String,
) {
    val maxLaunches: Int get() = usage.values.maxOfOrNull { it.count } ?: 0
}

/**
 * A provider row without an engagement time is not worthless: some apps still
 * publish the row but never set the column. A neutral factor keeps it visible
 * without pretending it is fresh; 0.75 plus source confidence still clears the
 * For You threshold, so a brand-new row is not silently dropped.
 */
internal fun recencyFactor(lastUtc: Long?, nowUtc: Long): Double {
    if (lastUtc == null || lastUtc <= 0L) return 0.75
    val ageDays = (nowUtc - lastUtc).coerceAtLeast(0L).toDouble() / DiscoveryWeights.DAY_MS
    return exp(-ln(2.0) * ageDays / DiscoveryWeights.RECENCY_HALF_LIFE_DAYS)
        .coerceIn(0.0, 1.0)
}

internal fun affinityFactor(count: Int, maxLaunches: Int): Double {
    if (count <= 0 || maxLaunches <= 0) return 0.0
    return (ln(1.0 + count) / ln(1.0 + maxLaunches)).coerceIn(0.0, 1.0)
}

/** 1.0 right after it was shown, linearly down to 0 after a week. */
internal fun recentlyShownFactor(shownUtc: Long?, nowUtc: Long): Double {
    if (shownUtc == null || shownUtc <= 0L) return 0.0
    val ageDays = (nowUtc - shownUtc).coerceAtLeast(0L).toDouble() / DiscoveryWeights.DAY_MS
    if (ageDays >= DiscoveryWeights.SHOWN_DAYS) return 0.0
    return (1.0 - ageDays / DiscoveryWeights.SHOWN_DAYS).coerceIn(0.0, 1.0)
}

internal fun sourceConfidence(source: DiscoverySource): Double = when (source) {
    DiscoverySource.TV_PROVIDER -> 1.0
    DiscoverySource.LAUNCHER -> 0.8
    DiscoverySource.PACKAGE_MANAGER -> 0.7
    DiscoverySource.ONLINE -> 0.8
}

internal fun reasonFor(item: DiscoveryItem, context: RankingContext): DiscoveryReason {
    val usage = context.usage[item.packageName]
    return when {
        item.progress != null || item.isContinue -> DiscoveryReason.CONTINUE_WATCHING
        item.id in context.favoriteIds -> DiscoveryReason.FAVORITE
        item.source == DiscoverySource.TV_PROVIDER &&
            context.currentPackage != null &&
            item.packageName == context.currentPackage -> DiscoveryReason.FROM_CURRENT_APP
        usage != null && isRecent(usage.lastUtc, context.nowUtc) -> DiscoveryReason.RECENTLY_LAUNCHED
        usage != null && usage.count >= 3 -> DiscoveryReason.OFTEN_USED
        item.source == DiscoverySource.TV_PROVIDER -> DiscoveryReason.NEW_FROM_APP
        item.source == DiscoverySource.ONLINE -> DiscoveryReason.TRENDING
        else -> DiscoveryReason.RECENTLY_LAUNCHED
    }
}

internal fun scoreItem(
    item: DiscoveryItem,
    context: RankingContext,
    samePackageRank: Int = 0,
): Double {
    val continueWeight = if (item.progress != null || item.isContinue) 1.0 else 0.0
    val usage = context.usage[item.packageName]
    val lastTouch = item.lastEngagementUtc ?: usage?.lastUtc
    val recency = recencyFactor(lastTouch, context.nowUtc)
    val affinity = affinityFactor(usage?.count ?: 0, context.maxLaunches)
    val favorite = if (item.id in context.favoriteIds) 1.0 else 0.0
    val current = if (context.currentPackage != null &&
        item.packageName == context.currentPackage
    ) {
        1.0
    } else {
        0.0
    }
    val shown = recentlyShownFactor(context.shownIds[item.id], context.nowUtc)
    val repeat = when {
        samePackageRank <= 1 -> 0.0
        samePackageRank == 2 -> 0.5
        else -> 1.0
    }
    return (
        DiscoveryWeights.CONTINUE * continueWeight +
            DiscoveryWeights.RECENCY * recency +
            DiscoveryWeights.AFFINITY * affinity +
            DiscoveryWeights.FAVORITE * favorite +
            DiscoveryWeights.CURRENT_APP * current +
            DiscoveryWeights.SOURCE * sourceConfidence(item.source) -
            DiscoveryWeights.SHOWN * shown -
            DiscoveryWeights.SAME_PACKAGE * repeat
        ).coerceIn(0.0, 1.0)
}

/** Stable within one day: the rail must not reshuffle on every resume. */
internal fun dailyTieBreak(profileId: String, nowUtc: Long, id: String): Int {
    val epochDay = nowUtc / DiscoveryWeights.DAY_MS
    var hash = profileId.hashCode()
    hash = hash * 31 + epochDay.hashCode()
    hash = hash * 31 + id.hashCode()
    return hash and 0x7fffffff
}

private data class Scored(
    val item: DiscoveryItem,
    val base: Double,
    val gate: Double,
)

/**
 * Rank one rail: threshold, score, per-package cap, no two cards of the same
 * app in a row when an alternative exists, and one exploration slot for a
 * package that has not been launched for a month.
 *
 * The cap is a preference, not a wall: when a rail has fewer candidates than
 * the cap allows, the remaining cards still fill it instead of leaving a gap.
 */
internal fun rankSection(
    candidates: List<DiscoveryItem>,
    section: DiscoverySection,
    context: RankingContext,
    limit: Int,
    maxPerPackage: Int = 2,
    minScore: Double = DiscoveryWeights.MIN_SCORE,
    forcedReason: DiscoveryReason? = null,
): List<DiscoveryItem> {
    if (limit <= 0 || candidates.isEmpty()) return emptyList()
    val pool = LinkedHashMap<String, DiscoveryItem>()
    candidates.forEach { item ->
        val previous = pool[item.id]
        if (previous == null ||
            (item.lastEngagementUtc ?: 0L) > (previous.lastEngagementUtc ?: 0L)
        ) {
            pool[item.id] = item
        }
    }

    // The "already shown" penalty only reorders; it must never delete a card
    // from the rail, so the threshold is checked without it.
    val thresholdContext = context.copy(shownIds = emptyMap())
    val scored = pool.values
        .map { Scored(it, scoreItem(it, context), scoreItem(it, thresholdContext)) }
        .filter { it.gate >= minScore }
        .sortedWith(
            compareByDescending<Scored> { it.base }
                .thenByDescending { dailyTieBreak(context.profileId, context.nowUtc, it.item.id) },
        )
    if (scored.isEmpty()) return emptyList()

    val exploration = scored.firstOrNull {
        it.base >= DiscoveryWeights.EXPLORATION_SCORE &&
            isUnfamiliar(it.item.packageName, context)
    }
    val reserved = if (exploration != null) 1 else 0
    val target = (limit - reserved).coerceAtLeast(0)
    val cap = maxPerPackage.coerceAtLeast(1)

    val result = ArrayList<Scored>(limit)
    val used = HashSet<String>()
    val counts = HashMap<String, Int>()
    var lastPackage: String? = null

    fun take(chosen: Scored) {
        used += chosen.item.id
        result += chosen
        counts[chosen.item.packageName] = (counts[chosen.item.packageName] ?: 0) + 1
        lastPackage = chosen.item.packageName
    }

    // A card of the same app next to the previous one is deferred whenever a
    // different app can fill the slot instead. The score is recomputed with the
    // package rank so the second card of an app is worth less than the first.
    fun choose(allowOverCap: Boolean): Scored? {
        val available = scored.filterNot { it.item.id in used }
        if (available.isEmpty()) return null
        val underCap = available.filter { (counts[it.item.packageName] ?: 0) < cap }
        val pool = when {
            underCap.isNotEmpty() -> underCap
            allowOverCap -> available
            else -> return null
        }
        val different = pool.filterNot { it.item.packageName == lastPackage }
        val source = if (different.isNotEmpty()) different else pool
        return source.maxWithOrNull(
            compareBy<Scored> { scoreItem(it.item, context, counts[it.item.packageName] ?: 0) }
                .thenBy { dailyTieBreak(context.profileId, context.nowUtc, it.item.id) },
        )
    }

    while (result.size < target) {
        val chosen = choose(allowOverCap = false) ?: break
        take(chosen)
    }

    if (exploration != null &&
        result.none { it.item.id == exploration.item.id } &&
        result.size < limit
    ) {
        take(exploration)
    }

    // A short rail is worse than a third card from one app: once the caps and
    // the exploration slot have had their say, fill what is left.
    while (result.size < limit) {
        val chosen = choose(allowOverCap = true) ?: break
        take(chosen)
    }

    return result.map { chosen ->
        val forced = forcedReason
        val reason = when {
            forced != null -> forced
            exploration != null && chosen.item.id == exploration.item.id ->
                DiscoveryReason.DIVERSE_PICK
            else -> reasonFor(chosen.item, context)
        }
        chosen.item.copy(reason = reason, score = chosen.base)
    }
}

internal fun isUnfamiliar(packageName: String, context: RankingContext): Boolean {
    val usage = context.usage[packageName] ?: return true
    val age = context.nowUtc - usage.lastUtc
    return age >= (DiscoveryWeights.EXPLORATION_DAYS * DiscoveryWeights.DAY_MS).toLong()
}

private fun isRecent(lastUtc: Long, nowUtc: Long): Boolean =
    nowUtc - lastUtc in 0..(7L * DiscoveryWeights.DAY_MS)
