package com.whatuneed.ovisiontv.kotlin.data

import android.content.Context
import android.database.Cursor
import android.media.tv.TvContract
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * One real row an app published for the TV home surface. The discovery engine
 * maps these rows into its own model; this type stays the provider DTO, so the
 * ranking of one app's shelf keeps its pure, testable shape.
 */
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
    /** Minutes left at the last save; null for a not-started or finished row. */
    val remainingMinutes: Int? = null,
    /** 0/1/2 = wide, 3 = square, 4/5 = portrait; null when the app did not say. */
    val posterArtAspect: Int? = null,
    /** The provider says this row may be launched by another app. */
    val browsable: Boolean = true,
    /**
     * A real preview clip published by the provider for this row. Android TV
     * exposes this as `preview_video_uri`; it is optional and stays null when
     * the app has no trailer. It is never synthesized from the title.
     */
    val previewVideoUri: String? = null,
)

internal fun watchNextProgress(positionMs: Long, durationMs: Long): Float? {
    if (positionMs <= 0L || durationMs <= 0L) return null
    return (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

/** Whole minutes left, rounded up; null when the row has no real resume point. */
internal fun watchNextRemainingMinutes(positionMs: Long, durationMs: Long): Int? {
    if (positionMs <= 0L || durationMs <= positionMs) return null
    return ((durationMs - positionMs + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
}

/**
 * App-specific ranking: the shelf belongs to the focused dock app, so rows for
 * other packages are dropped before the limit; an app with nothing published
 * gets its branding instead of borrowed or invented content (PHASE 33).
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
        .filter { it.browsable }
        .filter { it.title.isNotBlank() && !it.intentUri.isNullOrBlank() }
        .filter { includeContinue || !it.continueRow }
        .sortedByDescending { it.engagement }
        .filter { row -> row.intentUri?.let { seen.add(it) } == true }
        .take(limit)
        .toList()
}

object WatchNextSource {
    const val MAX_CARDS = 6
    private const val MAX_ROWS = 500
    private const val MAX_TITLE_CHARS = 512
    private const val MAX_SUBTITLE_CHARS = 512
    private const val MAX_URI_CHARS = 4_096
    private const val MAX_PACKAGE_CHARS = 255

    /**
     * Reads Watch Next first and the older Preview Programs listing second:
     * apps that never migrated still publish recommendations there. Only public
     * provider rows are read; private app databases are not touched.
     *
     * [packageName] null means "every installed app", which is what the
     * cross-app rails need. The app-specific shelf passes its own package and
     * keeps the old ranking.
     */
    internal suspend fun loadRows(
        context: Context,
        packageName: String? = null,
    ): List<WatchNextRow> = withContext(Dispatchers.IO) {
        if (!ContentPreferences.canReadListings(context)) return@withContext emptyList()
        currentCoroutineContext().ensureActive()
        val watchNext = readWatchNext(context, packageName)
        // A provider that took a long time must not keep the old query alive
        // after the shelf has moved on or the screen has gone.
        currentCoroutineContext().ensureActive()
        watchNext + readPreviewPrograms(context, packageName)
    }

    private fun readWatchNext(context: Context, packageName: String?): List<WatchNextRow> = query(
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
            TvContract.WatchNextPrograms.COLUMN_POSTER_ART_ASPECT_RATIO,
            TvContract.WatchNextPrograms.COLUMN_BROWSABLE,
            TvContract.WatchNextPrograms.COLUMN_PREVIEW_VIDEO_URI,
        ),
        titleColumn = TvContract.WatchNextPrograms.COLUMN_TITLE,
        subtitleColumn = TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE,
        durationColumn = TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS,
        positionColumn = TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
        engagementColumn = TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
        typeColumn = TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE,
        posterArtAspectColumn = TvContract.WatchNextPrograms.COLUMN_POSTER_ART_ASPECT_RATIO,
        browsableColumn = TvContract.WatchNextPrograms.COLUMN_BROWSABLE,
        previewVideoColumn = TvContract.WatchNextPrograms.COLUMN_PREVIEW_VIDEO_URI,
        packageName = packageName,
    )

    @Suppress("DEPRECATION")
    private fun readPreviewPrograms(
        context: Context,
        packageName: String?,
    ): List<WatchNextRow> = query(
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
            TvContract.PreviewPrograms.COLUMN_PREVIEW_VIDEO_URI,
        ),
        titleColumn = TvContract.PreviewPrograms.COLUMN_TITLE,
        subtitleColumn = null,
        durationColumn = TvContract.PreviewPrograms.COLUMN_DURATION_MILLIS,
        positionColumn = TvContract.PreviewPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
        engagementColumn = null,
        typeColumn = null,
        posterArtAspectColumn = TvContract.WatchNextPrograms.COLUMN_POSTER_ART_ASPECT_RATIO,
        browsableColumn = null,
        previewVideoColumn = TvContract.PreviewPrograms.COLUMN_PREVIEW_VIDEO_URI,
        packageName = packageName,
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
        posterArtAspectColumn: String?,
        browsableColumn: String?,
        previewVideoColumn: String?,
        packageName: String?,
    ): List<WatchNextRow> {
        val selection = packageName?.let { "${TvContract.BaseTvColumns.COLUMN_PACKAGE_NAME} = ?" }
        val selectionArgs = packageName?.let { arrayOf(it) }
        val cursor: Cursor = runCatching {
            context.contentResolver.query(uri, columns, selection, selectionArgs, null)
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
            val posterAspectIndex = posterArtAspectColumn?.let(it::getColumnIndex) ?: -1
            val browsableIndex = browsableColumn?.let(it::getColumnIndex) ?: -1
            val previewVideoIndex = previewVideoColumn?.let(it::getColumnIndex) ?: -1
            if (packageIndex < 0 || titleIndex < 0) return@use emptyList()

            runCatching {
                buildList {
                    var count = 0
                    while (it.moveToNext() && count < MAX_ROWS) {
                        count++
                        val rowPackage = it.stringOrNull(packageIndex)
                            ?.takeIf { value -> value.length <= MAX_PACKAGE_CHARS }
                            ?: continue
                        val title = boundedWatchText(it.stringOrNull(titleIndex), MAX_TITLE_CHARS)
                            ?: continue
                        val duration = it.longOrZero(durationIndex)
                        val position = it.longOrZero(positionIndex)
                        val type = if (typeIndex < 0) -1 else it.getInt(typeIndex)
                        val browsable = browsableIndex < 0 || it.isNull(browsableIndex) ||
                            it.getInt(browsableIndex) != 0
                        if (!browsable) continue
                        val remaining = watchNextRemainingMinutes(position, duration)
                        add(
                            WatchNextRow(
                                packageName = rowPackage,
                                title = title,
                                subtitle = boundedWatchText(
                                    it.stringOrNull(subtitleIndex),
                                    MAX_SUBTITLE_CHARS,
                                ),
                                thumbnailUri = boundedWatchUri(
                                    it.stringOrNull(thumbnailIndex),
                                    MAX_URI_CHARS,
                                ),
                                posterUri = boundedWatchUri(it.stringOrNull(posterIndex), MAX_URI_CHARS),
                                intentUri = boundedWatchUri(it.stringOrNull(intentIndex), MAX_URI_CHARS),
                                progress = watchNextProgress(position, duration),
                                engagement = it.longOrZero(engagementIndex),
                                continueRow = type == TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE ||
                                    type == TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_NEXT,
                                remainingMinutes = remaining,
                                posterArtAspect = if (posterAspectIndex >= 0 && !it.isNull(posterAspectIndex)) {
                                    it.getInt(posterAspectIndex)
                                } else {
                                    null
                                },
                                browsable = browsable,
                                previewVideoUri = boundedWatchUri(
                                    it.stringOrNull(previewVideoIndex),
                                    MAX_URI_CHARS,
                                ),
                            ),
                        )
                    }
                }
            }.getOrDefault(emptyList())
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

/** Provider text is untrusted; truncate before it reaches layout or storage. */
internal fun boundedWatchText(value: String?, max: Int): String? =
    value?.take(max)?.takeIf { it.isNotBlank() }

/** A malformed overlong deep link is dropped rather than truncated into a different intent. */
internal fun boundedWatchUri(value: String?, max: Int): String? =
    value?.takeIf { it.isNotBlank() && it.length <= max }
