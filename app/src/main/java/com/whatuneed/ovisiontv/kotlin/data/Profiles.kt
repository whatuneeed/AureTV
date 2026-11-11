package com.whatuneed.ovisiontv.kotlin.data

import android.content.Context
import android.content.SharedPreferences
import com.whatuneed.ovisiontv.kotlin.R
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** One local profile, as Apple TV's "Who's watching?" keeps them. */
data class Profile(
    val id: String,
    val name: String,
    val emoji: String,
    val color: Int,
    /**
     * Kids Mode is local to this launcher: the profile shows only apps its
     * allowlist names, and leaving it asks for a PIN (PHASE 37 parity).
     */
    val kids: Boolean = false,
)

/** Pastel disc colours; [background] fills the circle and [text] paints the emoji. */
data class ProfileColor(val background: Long, val text: Long)

/**
 * Profile pictures, in the order Apple TV shows them: film, animals, emoji.
 * Each profile's emoji is unique, so two discs never look alike.
 */
object ProfileCatalog {
    const val MAX = 3
    const val DEFAULT_ID = "p1"
    const val AVATAR_COLUMNS = 6
    private const val EMOJI_GROUP_SIZE = 6

    // Names live in string-array resources so they follow the launcher language,
    // not the TV's system locale.
    val EMOJIS = listOf(
        "\uD83C\uDF7F", "\uD83D\uDCFD\uFE0F", "\uD83D\uDCFA", "\uD83C\uDFA5",
        "\uD83C\uDF9E\uFE0F", "\uD83C\uDFAC",
        "\uD83D\uDC36", "\uD83D\uDC35", "\uD83D\uDC31", "\uD83D\uDC19", "\uD83E\uDD8A",
        "\uD83D\uDC3C",
        "\uD83D\uDE00", "\uD83D\uDE0E", "\uD83D\uDC38", "\uD83D\uDC28", "\uD83E\uDD84",
        "\uD83D\uDE80",
    )
    private val EMOJI_COLORS = listOf(3, 6, 0, 6, 3, 6, 6, 5, 3, 4, 5, 6, 3, 6, 2, 5, 4, 0)

    val COLORS = listOf(
        ProfileColor(0xFFA9D8F5, 0xFF1F5A82), // sky
        ProfileColor(0xFFF8B9CF, 0xFF8A2A51), // pink
        ProfileColor(0xFFB4E8C2, 0xFF1E6B3A), // mint
        ProfileColor(0xFFF6E59A, 0xFF7A5A07), // lemon
        ProfileColor(0xFFD3C4F7, 0xFF4B2E99), // lavender
        ProfileColor(0xFFFFCFA8, 0xFF8A4512), // peach
        ProfileColor(0xFFD9DCE3, 0xFF3A3D45), // silver
    )

    fun canAdd(profiles: List<Profile>): Boolean = profiles.size < MAX

    fun color(color: Int): ProfileColor = COLORS[color.coerceIn(0, COLORS.lastIndex)]

    fun colorFor(emoji: String): Int {
        val index = EMOJIS.indexOf(emoji)
        return if (index >= 0) EMOJI_COLORS[index] else 0
    }

    /** The placeholder name created on first run; it reads "Me" in the UI language. */
    fun isDefaultName(name: String): Boolean = name == "Я" || name == "Me"

    internal fun repairDefaultName(name: String, defaultName: String): String =
        if (isDefaultName(name)) defaultName else name

    fun groupOf(emoji: String): Int {
        val index = EMOJIS.indexOf(emoji)
        return if (index < 0) 0 else index / EMOJI_GROUP_SIZE
    }

    fun nextId(profiles: List<Profile>): String {
        var next = 1
        profiles.forEach { profile ->
            val number = profile.id.removePrefix("p").toIntOrNull()
            if (number != null && number >= next) next = number + 1
        }
        var id = "p$next"
        while (profiles.any { it.id == id }) {
            next++
            id = "p$next"
        }
        return id
    }

    /** Emoji not used by another profile; [exceptId] is the profile being edited. */
    fun freeEmojis(profiles: List<Profile>, exceptId: String? = null): List<String> {
        val used = profiles.filter { it.id != exceptId }.mapTo(HashSet()) { it.emoji }
        return EMOJIS.filter { it !in used }
    }

    fun nextEmoji(profiles: List<Profile>, exceptId: String? = null): String =
        freeEmojis(profiles, exceptId).firstOrNull() ?: EMOJIS.first()

    /** The picker opens on the current profile, or on the first one if it vanished. */
    fun pickerIndex(profiles: List<Profile>, currentId: String): Int =
        profiles.indexOfFirst { it.id == currentId }.coerceAtLeast(0)

    /** The profile's own picture while it is still free, otherwise the first free one. */
    fun avatarStartIndex(profiles: List<Profile>, editingId: String?): Int {
        val free = freeEmojis(profiles, editingId)
        val own = profiles.firstOrNull { it.id == editingId }?.emoji
        val ownIndex = if (own != null && own in free) EMOJIS.indexOf(own) else -1
        val index = if (ownIndex >= 0) ownIndex else EMOJIS.indexOf(free.firstOrNull() ?: EMOJIS.first())
        return index.coerceAtLeast(0)
    }

    /**
     * Moves the avatar cursor one step in the grid, skipping pictures another
     * profile owns. A direction with no free disc keeps the cursor in place.
     */
    fun avatarMove(
        from: Int,
        dx: Int,
        dy: Int,
        profiles: List<Profile>,
        editingId: String?,
    ): Int {
        if (dx == 0 && dy == 0) return from
        val free = freeEmojis(profiles, editingId).toHashSet()
        val rows = EMOJIS.size / AVATAR_COLUMNS
        var row = (from / AVATAR_COLUMNS).coerceIn(0, rows - 1)
        var column = (from % AVATAR_COLUMNS).coerceIn(0, AVATAR_COLUMNS - 1)
        if (dx != 0) {
            val step = if (dx > 0) 1 else -1
            var next = column + step
            while (next in 0 until AVATAR_COLUMNS) {
                if (EMOJIS[row * AVATAR_COLUMNS + next] in free) {
                    column = next
                    break
                }
                next += step
            }
        } else {
            val step = if (dy > 0) 1 else -1
            var next = row + step
            while (next in 0 until rows) {
                if (EMOJIS[next * AVATAR_COLUMNS + column] in free) {
                    row = next
                    break
                }
                next += step
            }
        }
        return row * AVATAR_COLUMNS + column
    }

    /** Fallback for a profile whose emoji was cleared by hand-editing. */
    fun initials(name: String): String {
        val letters = name.trim().split(Regex("\\s+")).mapNotNull { part ->
            part.takeIf { it.isNotEmpty() }?.codePointAt(0)?.let { String(Character.toChars(it)) }
        }
        // A blank name yields no initials; the UI shows profile_avatar_placeholder
        // instead, so the fallback glyph lives in resources, not in this file.
        return letters.take(2).joinToString("").uppercase(Locale.ROOT)
    }
}

internal data class ParsedProfiles(val profiles: List<Profile>, val repaired: Boolean)

/**
 * Reads the saved list and repairs what hand-editing or an older build can
 * break: a missing id, a duplicate emoji, an out-of-range colour or a blank
 * name. One bad entry costs that entry, never the whole list.
 */
internal fun parseProfiles(raw: String?, defaultName: String): ParsedProfiles {
    if (raw.isNullOrBlank()) return ParsedProfiles(defaultProfiles(defaultName), true)
    val entries = try {
        JSONArray(raw)
    } catch (_: Exception) {
        return ParsedProfiles(defaultProfiles(defaultName), true)
    }
    if (entries.length() == 0) return ParsedProfiles(defaultProfiles(defaultName), true)

    var repaired = false
    val ids = HashSet<String>()
    val parsed = ArrayList<Profile>(entries.length())
    for (index in 0 until entries.length()) {
        val entry = entries.optJSONObject(index)
        if (entry == null) {
            repaired = true
            continue
        }
        val id = entry.optString("id").trim()
        if (id.isEmpty() || !ids.add(id)) {
            repaired = true
            continue
        }
        val rawName = entry.optString("name").trim()
        val name = ProfileCatalog.repairDefaultName(rawName, defaultName)
        if (name.isEmpty()) {
            parsed += Profile(id, defaultName, "", 0)
            repaired = true
            continue
        }
        if (name != rawName) repaired = true
        parsed += Profile(
            id = id,
            name = name,
            emoji = entry.optString("emoji"),
            color = entry.optInt("color", 0),
            kids = entry.optBoolean("kids", false),
        )
    }
    if (parsed.isEmpty()) return ParsedProfiles(defaultProfiles(defaultName), true)

    val used = HashSet<String>()
    val fixed = parsed.mapIndexed { index, profile ->
        var emoji = profile.emoji
        if (emoji.isEmpty() || emoji in used) {
            emoji = ProfileCatalog.EMOJIS.firstOrNull {
                it !in used && !emojiTakenLater(parsed, index, it)
            } ?: ProfileCatalog.EMOJIS.first()
            repaired = true
        }
        used += emoji
        var color = profile.color
        if (color !in ProfileCatalog.COLORS.indices) {
            color = ProfileCatalog.colorFor(emoji)
            repaired = true
        }
        profile.copy(emoji = emoji, color = color)
    }
    return ParsedProfiles(fixed, repaired)
}

private fun emojiTakenLater(profiles: List<Profile>, self: Int, emoji: String): Boolean =
    (self + 1 until profiles.size).any { profiles[it].emoji == emoji }

private fun defaultProfiles(defaultName: String): List<Profile> = listOf(
    Profile(
        id = ProfileCatalog.DEFAULT_ID,
        name = defaultName,
        emoji = ProfileCatalog.EMOJIS.first(),
        color = ProfileCatalog.colorFor(ProfileCatalog.EMOJIS.first()),
    ),
)

internal fun serializeProfiles(profiles: List<Profile>): String {
    val array = JSONArray()
    profiles.forEach { profile ->
        array.put(
            JSONObject()
                .put("id", profile.id)
                .put("name", profile.name)
                .put("color", profile.color)
                .put("emoji", profile.emoji)
                .put("kids", profile.kids),
        )
    }
    return array.toString()
}

/**
 * Local profiles, stored beside the launcher settings rather than inside them.
 * The first profile keeps the original layout storage, so adding profiles does
 * not move or lose the layout an earlier build wrote.
 */
class ProfileStore(context: Context) {
    // The placeholder name follows the launcher's language, not the system one.
    private val appContext = LocalePreference.wrap(context.applicationContext)
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun list(): List<Profile> {
        val raw = prefs.getString(KEY_LIST, null)
        val parsed = parseProfiles(raw, appContext.getString(R.string.profile_default_name))
        if (parsed.repaired) prefs.edit().putString(KEY_LIST, serializeProfiles(parsed.profiles)).apply()
        return parsed.profiles
    }

    fun current(): Profile {
        val profiles = list()
        val id = prefs.getString(KEY_CURRENT, null)
        val found = profiles.firstOrNull { it.id == id }
        if (found == null && id != null) {
            prefs.edit().putString(KEY_CURRENT, profiles.first().id).apply()
        }
        return found ?: profiles.first()
    }

    fun setCurrent(id: String) {
        if (list().none { it.id == id }) return
        prefs.edit().putString(KEY_CURRENT, id).apply()
    }

    fun add(name: String): Profile? {
        val profiles = list()
        if (!ProfileCatalog.canAdd(profiles)) return null
        val emoji = ProfileCatalog.nextEmoji(profiles)
        val profile = Profile(
            id = ProfileCatalog.nextId(profiles),
            name = name.trim().ifEmpty { appContext.getString(R.string.profile_default_name) },
            emoji = emoji,
            color = ProfileCatalog.colorFor(emoji),
        )
        prefs.edit().putString(KEY_LIST, serializeProfiles(profiles + profile)).apply()
        return profile
    }

    fun update(profile: Profile) {
        val profiles = list()
        if (profiles.none { it.id == profile.id }) return
        prefs.edit()
            .putString(
                KEY_LIST,
                serializeProfiles(profiles.map { if (it.id == profile.id) profile else it }),
            )
            .apply()
    }

    /** Removes the profile with its layout; the last profile stays. */
    fun delete(id: String): Boolean {
        val profiles = list()
        if (profiles.size <= 1) return false
        val keep = profiles.filterNot { it.id == id }
        if (keep.size == profiles.size) return false
        val wasCurrent = prefs.getString(KEY_CURRENT, ProfileCatalog.DEFAULT_ID) == id
        prefs.edit().putString(KEY_LIST, serializeProfiles(keep)).apply()
        HomeOrderStore(appContext, homePrefsName(id)).clearLayout()
        // A reused id must not inherit the deleted profile's PIN or allowlist.
        KidsStore(appContext).clearProfile(id)
        if (wasCurrent) prefs.edit().putString(KEY_CURRENT, keep.first().id).apply()
        return true
    }

    fun askOnBoot(): Boolean = prefs.getBoolean(KEY_ASK_ON_BOOT, true)

    fun setAskOnBoot(on: Boolean) {
        prefs.edit().putBoolean(KEY_ASK_ON_BOOT, on).apply()
    }

    fun askOnWake(): Boolean = prefs.getBoolean(KEY_ASK_ON_WAKE, true)

    fun setAskOnWake(on: Boolean) {
        prefs.edit().putBoolean(KEY_ASK_ON_WAKE, on).apply()
    }

    companion object {
        const val FILE = "profiles"
        private const val KEY_LIST = "list"
        private const val KEY_CURRENT = "current"
        private const val KEY_ASK_ON_BOOT = "ask_on_boot"
        private const val KEY_ASK_ON_WAKE = "ask_on_wake"

        /**
         * p1 keeps the original file that also holds language, saver and content
         * settings; every other profile gets a layout-only file of its own.
         */
        fun homePrefsName(id: String): String =
            if (id == ProfileCatalog.DEFAULT_ID) LauncherPrefs.NAME else "${LauncherPrefs.NAME}_$id"
    }
}
