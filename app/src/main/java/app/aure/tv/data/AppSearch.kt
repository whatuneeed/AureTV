package app.aure.tv.data

import java.util.Locale

/**
 * Search reaches every installed app, including the ones inside folders and the
 * ones hidden from the grid: hiding a tile is not uninstalling it.
 */
fun homeApps(items: List<HomeItem>): List<TvApp> = buildList {
    for (item in items) {
        when (item) {
            is HomeItem.AppItem -> add(item.app)
            is HomeItem.FolderItem -> addAll(item.apps)
        }
    }
}.distinctBy { it.packageName }

/**
 * Matches labels only: a remote cannot usefully type a package name, and the
 * app name is the word the user sees on the grid.
 *
 * Prefix matches come first, then a word inside the name, then any occurrence,
 * so typing "ки" offers "Кино" before "Пикник".
 */
fun searchApps(apps: List<TvApp>, query: String): List<TvApp> {
    val needle = fold(query)
    if (needle.isEmpty()) return emptyList()
    val tokens = needle.split(' ')
    return apps.asSequence()
        .filter { it.available }
        .mapNotNull { app ->
            val name = fold(app.label)
            val words = name.split(' ')
            val rank = when {
                name == needle -> 0
                name.startsWith(needle) -> 1
                // Any word order: "kids youtube" finds "YouTube Kids".
                tokens.all { token -> words.any { it.startsWith(token) } } -> 2
                name.contains(needle) -> 3
                tokens.all { token -> name.contains(token) } -> 4
                else -> return@mapNotNull null
            }
            Ranked(app, rank, name)
        }
        .distinctBy { it.app.packageName }
        .sortedWith(compareBy({ it.rank }, { it.name }, { it.app.packageName }))
        .map { it.app }
        .toList()
}

// ё is typed as е in a hurry, and case must not decide a match. Non-breaking
// spaces and hyphen variants are typed as an ordinary space, so "youtube kids"
// still finds "YouTube Kids" on a keyboard without a dash.
private fun fold(text: String): String = text.trim()
    .lowercase(Locale.ROOT)
    .replace('ё', 'е')
    .replace(SEPARATORS, " ")
    .replace(WHITESPACE, " ")

private val SEPARATORS = Regex("[\\u00A0\\u202F\\u2011\\u2013\\u2014-]")
private val WHITESPACE = Regex("\\s+")

private data class Ranked(val app: TvApp, val rank: Int, val name: String)
