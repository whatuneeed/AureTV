package com.whatuneed.ovisiontv.kotlin.data

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.media.tv.TvContract
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One real row an app published for the TV home surface. */
data class ShelfCard(
    val packageName: String,
    val title: String,
    val subtitle: String?,
    val thumbnailUri: String?,
    val posterUri: String?,
    val intentUri: String,
    val progress: Float?,
    val engagement: Long,
) {
    /** Landscape art is the shelf's native ratio; posters are the fallback. */
    val artUri: String? get() = thumbnailUri ?: posterUri
}

/** Unfiiltered provider row; ranking stays pure so it can be unit-tested. */
internal data class WatchNextRow(
    val packageName: String,
    val title: String,
    val subtitle: String?,
    val thumbnailUri: String?,
    val posterUri: String?,
    val intentUri: String?,
    val progress: Float?,
    val engagement: Long,
    val continueRow: Boolean,
)

internal fun watchNextProgress(positionMs: Long, durationMs: Long): Float? {
    if (positionMs <= 0L || durationMs <= 0L) return null
    return (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

/**
 * The shelf belongs to the focused dock app, so rows for other packages are
 * dropped before the limit; an app with nothing published gets its branding
 * instead of borrowed or invented content (PHASE 33).
 */
internal fun rankWatchNext(
    rows: List<WatchNextRow>,
    packageName: String,
    includeContinue: Boolean = true,
    limit: Int = WatchNextSource.MAX_CARDS,
): List<WatchNextRow> {
    val seen = HashSet<String>()
    return rows.asSequence()
        .filter { it.packageName == packageName }
        .filter { it.title.isNotBlank() && !it.intentUri.isNullOrBlank() }
        .filter { includeContinue || !it.continueRow }
        .sortedByDescending { it.engagement }
        .filter { seen.add(it.intentUri!!) }
        .take(limit)
        .toList()
}

object WatchNextSource {
    const val MAX_CARDS = 6

    /**
     * Reads Watch Next first and the older Preview Programs listing second:
     * apps that never migrated still publish recommendations there. Only public
     * provider rows are read; private app databases are not touched.
     */
    suspend fun load(
        context: Context,
        packageName: String,
        includeContinue: Boolean,
    ): List<ShelfCard> = withContext(Dispatchers.IO) {
        if (!ContentPreferences.canReadListings(context)) return@withContext emptyList()
        val rows = readWatchNext(context) + readPreviewPrograms(context)
        rankWatchNext(rows, packageName, includeContinue).map { row ->
                ShelfCard(
                    packageName = row.packageName,
                    title = row.title,
                    subtitle = row.subtitle,
                    thumbnailUri = row.thumbnailUri,
                    posterUri = row.posterUri,
                    intentUri = row.intentUri.orEmpty(),
                    progress = row.progress,
                    engagement = row.engagement,
                )
            }
        }

    /**
     * A deep link is only used when it resolves back to the app that published
     * it; a provider row must not be able to open an arbitrary package.
     */
    fun contentIntent(context: Context, card: ShelfCard): Intent? {
        val intent = runCatching {
            Intent.parseUri(card.intentUri, Intent.URI_INTENT_SCHEME)
        }.getOrNull() ?: return null
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val resolved = runCatching {
            context.packageManager.resolveActivity(intent, 0)
        }.getOrNull() ?: return null
        if (resolved.activityInfo?.packageName != card.packageName) return null
        return intent
    }

    private fun readWatchNext(context: Context): List<WatchNextRow> = query(
        context = context,
        uri = TvContract.WatchNextPrograms.CONTENT_URI,
        columns = arrayOf(
            TvContract.BaseTvColumns.COLUMN_PACKAGE_NAME,
            TvContract.WatchNextPrograms.COLUMN_TITLE,
            TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE,
            TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI,
            TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI,
            TvContract.WatchNextPrograms.COLUMN_INTENT_URI,
            TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS,
            TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
            TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
            TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE,
        ),
        titleColumn = TvContract.WatchNextPrograms.COLUMN_TITLE,
        subtitleColumn = TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE,
        durationColumn = TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS,
        positionColumn = TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
        engagementColumn = TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
        typeColumn = TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE,
    )

    @Suppress("DEPRECATION")
    private fun readPreviewPrograms(context: Context): List<WatchNextRow> = query(
        context = context,
        uri = TvContract.PreviewPrograms.CONTENT_URI,
        columns = arrayOf(
            TvContract.BaseTvColumns.COLUMN_PACKAGE_NAME,
            TvContract.PreviewPrograms.COLUMN_TITLE,
            TvContract.PreviewPrograms.COLUMN_THUMBNAIL_URI,
            TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI,
            TvContract.PreviewPrograms.COLUMN_INTENT_URI,
            TvContract.PreviewPrograms.COLUMN_DURATION_MILLIS,
            TvContract.PreviewPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
        ),
        titleColumn = TvContract.PreviewPrograms.COLUMN_TITLE,
        subtitleColumn = null,
        durationColumn = TvContract.PreviewPrograms.COLUMN_DURATION_MILLIS,
        positionColumn = TvContract.PreviewPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
        engagementColumn = null,
        typeColumn = null,
    )

    private fun query(
        context: Context,
        uri: Uri,
        columns: Array<String>,
        titleColumn: String,
        subtitleColumn: String?,
        durationColumn: String,
        positionColumn: String,
        engagementColumn: String?,
        typeColumn: String?,
    ): List<WatchNextRow> {
        val cursor: Cursor = runCatching {
            context.contentResolver.query(uri, columns, null, null, null)
        }.getOrNull() ?: return emptyList()
        return cursor.use {
            val packageIndex = it.getColumnIndex(TvContract.BaseTvColumns.COLUMN_PACKAGE_NAME)
            val titleIndex = it.getColumnIndex(titleColumn)
            val subtitleIndex = subtitleColumn?.let(it::getColumnIndex) ?: -1
            val thumbnailIndex = it.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI)
            val posterIndex = it.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI)
            val intentIndex = it.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_INTENT_URI)
            val durationIndex = it.getColumnIndex(durationColumn)
            val positionIndex = it.getColumnIndex(positionColumn)
            val engagementIndex = engagementColumn?.let(it::getColumnIndex) ?: -1
            val typeIndex = typeColumn?.let(it::getColumnIndex) ?: -1
            if (packageIndex < 0 || titleIndex < 0) return@use emptyList()

            buildList {
                while (it.moveToNext()) {
                    val packageName = it.stringOrNull(packageIndex) ?: continue
                    val title = it.stringOrNull(titleIndex) ?: continue
                    val duration = it.longOrZero(durationIndex)
                    val position = it.longOrZero(positionIndex)
                    val type = if (typeIndex < 0) -1 else it.getInt(typeIndex)
                    add(
                        WatchNextRow(
                            packageName = packageName,
                            title = title,
                            subtitle = it.stringOrNull(subtitleIndex),
                            thumbnailUri = it.stringOrNull(thumbnailIndex),
                            posterUri = it.stringOrNull(posterIndex),
                            intentUri = it.stringOrNull(intentIndex),
                            progress = watchNextProgress(position, duration),
                            engagement = it.longOrZero(engagementIndex),
                            continueRow = type == TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE ||
                                type == TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_NEXT,
                        ),
                    )
                }
            }
        }
    }

    private fun Cursor.stringOrNull(index: Int): String? {
        if (index < 0 || isNull(index)) return null
        return getString(index)?.takeIf { it.isNotBlank() }
    }

    private fun Cursor.longOrZero(index: Int): Long {
        if (index < 0 || isNull(index)) return 0L
        return getLong(index)
    }
}
