package com.whatuneed.ovisiontv.kotlin.data

import android.content.Context
import android.content.SharedPreferences
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * A kids profile gates only what OVisionTV itself shows. It is not Android
 * parental control: system Settings, another HOME app or a third-party
 * launcher can still leave this mode, and the Settings copy says so.
 */
object KidsMode {
    const val PIN_LENGTH = 4
    const val FREE_ATTEMPTS = 4
    private const val KDF = "pbkdf2"
    private const val KDF_ALG = "sha256"
    // A four-digit PIN has little entropy; the delay and attempt limit matter
    // as much as the hash cost.
    private const val KDF_ITERATIONS = 120_000

    fun isKids(context: Context): Boolean = ProfileStore(context).current().kids

    /** Whether [packageName] may appear and launch under the current profile. */
    fun allowed(context: Context, packageName: String?): Boolean {
        if (!isKids(context)) return true
        if (packageName.isNullOrEmpty()) return false
        if (packageName == context.packageName) return true
        val store = KidsStore(context)
        return packageName in store.allowlist(ProfileStore(context).current().id)
    }

    /** Milliseconds left in the current lockout, or 0. Survives restarts. */
    fun lockRemaining(context: Context, profileId: String, now: Long = System.currentTimeMillis()): Long =
        KidsStore(context).lockRemaining(profileId, now)

    fun lockDelayMillis(fails: Int): Long = when {
        fails <= FREE_ATTEMPTS -> 0L
        fails == FREE_ATTEMPTS + 1 -> 30_000L
        fails == FREE_ATTEMPTS + 2 -> 60_000L
        fails == FREE_ATTEMPTS + 3 -> 120_000L
        else -> 300_000L
    }
}

/** Stored per profile: PIN hash, recovery word, allowlist and lockout. */
class KidsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun allowlist(profileId: String): Set<String> =
        prefs.getStringSet(KEY_ALLOW + profileId, null)?.toHashSet() ?: HashSet()

    fun setAllowlist(profileId: String, packages: Set<String>) {
        prefs.edit().putStringSet(KEY_ALLOW + profileId, HashSet(packages)).apply()
    }

    fun hasPin(profileId: String): Boolean = !prefs.getString(KEY_PIN_HASH + profileId, null).isNullOrEmpty()

    fun checkPin(profileId: String, pin: String): Boolean {
        if (pin.length < KidsMode.PIN_LENGTH) return false
        val salt = prefs.getString(KEY_PIN_SALT + profileId, null) ?: return false
        val expected = prefs.getString(KEY_PIN_HASH + profileId, null) ?: return false
        return KidsCrypto.matches(salt, expected, pin)
    }

    fun setPin(profileId: String, pin: String) {
        val salt = KidsCrypto.newSalt()
        prefs.edit()
            .putString(KEY_PIN_SALT + profileId, salt)
            .putString(KEY_PIN_HASH + profileId, KidsCrypto.hash(salt, pin))
            .apply()
        // A new PIN starts from a clean slate: an old lockout must not outlive it.
        clearFailures(profileId)
    }

    fun clearPin(profileId: String) {
        prefs.edit()
            .remove(KEY_PIN_SALT + profileId)
            .remove(KEY_PIN_HASH + profileId)
            .remove(KEY_REC_SALT + profileId)
            .remove(KEY_REC_HASH + profileId)
            .remove(KEY_REC_HINT + profileId)
            .remove(KEY_FAILS + profileId)
            .remove(KEY_LOCK_UNTIL + profileId)
            .apply()
    }

    /** Deleting a profile removes everything it gated, not just its PIN. */
    fun clearProfile(profileId: String) {
        clearPin(profileId)
        prefs.edit().remove(KEY_ALLOW + profileId).apply()
    }

    fun lockRemaining(profileId: String, now: Long = System.currentTimeMillis()): Long {
        val until = prefs.getLong(KEY_LOCK_UNTIL + profileId, 0L)
        return (until - now).coerceAtLeast(0L)
    }

    fun recordFailure(profileId: String, now: Long = System.currentTimeMillis()): Int {
        val fails = prefs.getInt(KEY_FAILS + profileId, 0) + 1
        val delay = KidsMode.lockDelayMillis(fails)
        val editor = prefs.edit().putInt(KEY_FAILS + profileId, fails)
        if (delay > 0) editor.putLong(KEY_LOCK_UNTIL + profileId, now + delay)
        editor.apply()
        return fails
    }

    fun clearFailures(profileId: String) {
        prefs.edit()
            .remove(KEY_FAILS + profileId)
            .remove(KEY_LOCK_UNTIL + profileId)
            .apply()
    }

    fun recoveryHint(profileId: String): String =
        prefs.getString(KEY_REC_HINT + profileId, "").orEmpty()

    fun hasRecovery(profileId: String): Boolean =
        !prefs.getString(KEY_REC_HASH + profileId, null).isNullOrEmpty()

    fun setRecovery(profileId: String, hint: String, answer: String) {
        if (answer.trim().isEmpty()) return
        val salt = KidsCrypto.newSalt()
        prefs.edit()
            .putString(KEY_REC_HINT + profileId, hint.trim())
            .putString(KEY_REC_SALT + profileId, salt)
            .putString(KEY_REC_HASH + profileId, KidsCrypto.hash(salt, KidsCrypto.normalizeAnswer(answer)))
            .apply()
    }

    fun checkRecovery(profileId: String, answer: String): Boolean {
        val salt = prefs.getString(KEY_REC_SALT + profileId, null) ?: return false
        val expected = prefs.getString(KEY_REC_HASH + profileId, null) ?: return false
        return KidsCrypto.matches(salt, expected, KidsCrypto.normalizeAnswer(answer))
    }

    companion object {
        const val FILE = "kids_mode"
        private const val KEY_ALLOW = "allow_"
        private const val KEY_PIN_SALT = "pin_salt_"
        private const val KEY_PIN_HASH = "pin_hash_"
        private const val KEY_FAILS = "fail_"
        private const val KEY_LOCK_UNTIL = "lock_until_"
        private const val KEY_REC_SALT = "rec_salt_"
        private const val KEY_REC_HASH = "rec_hash_"
        private const val KEY_REC_HINT = "rec_hint_"
    }
}

/** PBKDF2 storage, kept in the same shape an earlier build wrote. */
internal object KidsCrypto {
    private const val KDF = "pbkdf2"
    private const val KDF_ALG = "sha256"
    private const val KDF_ITERATIONS = 120_000
    private const val SALT_BYTES = 16

    fun newSalt(): String {
        val bytes = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun hash(salt: String, value: String): String {
        val out = pbkdf2(salt, value, KDF_ITERATIONS)
        return "$KDF\$$KDF_ALG\$$KDF_ITERATIONS\$" + Base64.getEncoder().encodeToString(out)
    }

    fun matches(salt: String, expected: String, value: String): Boolean {
        val parts = expected.split('$')
        if (parts.size != 4 || parts[0] != KDF) return false
        val iterations = parts[2].toIntOrNull() ?: return false
        val out = pbkdf2(salt, value, iterations)
        val encoded = Base64.getEncoder().encodeToString(out)
        return expected == "$KDF\$${parts[1]}\$$iterations\$$encoded"
    }

    fun normalizeAnswer(answer: String): String = answer.trim().lowercase(Locale.ROOT)

    private fun pbkdf2(salt: String, value: String, iterations: Int): ByteArray {
        val saltBytes = Base64.getDecoder().decode(salt)
        val spec = PBEKeySpec(value.toCharArray(), saltBytes, iterations, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }
}

/**
 * Hides tiles the kids profile does not allow, including inside folders.
 * A folder stays only while something allowed remains in it.
 */
internal fun kidsFilter(
    items: List<HomeItem>,
    allowlist: Set<String>,
    ownPackage: String,
): List<HomeItem> = items.mapNotNull { item ->
    when (item) {
        is HomeItem.AppItem -> item.takeIf {
            it.app.packageName == ownPackage || it.app.packageName in allowlist
        }

        is HomeItem.FolderItem -> {
            val apps = item.apps.filter {
                it.packageName == ownPackage || it.packageName in allowlist
            }
            item.copy(apps = apps).takeIf { apps.isNotEmpty() }
        }
    }
}

/**
 * Switching out of a kids profile is gated by its PIN: without one a
 * misconfigured profile must not trap the child. Switching into kids, or
 * between kids profiles, is not gated.
 */
internal fun kidsLeaveNeedsPin(current: Profile, target: Profile, hasPin: Boolean): Boolean =
    current.kids && !target.kids && target.id != current.id && hasPin

/** The PIN pad can only accept four digits; a longer value is ignored. */
internal data class PinDigits(val value: String = "") {
    val complete: Boolean get() = value.length == PIN_LENGTH

    fun append(digit: Char): PinDigits =
        if (digit !in '0'..'9' || value.length == PIN_LENGTH) this else copy(value + digit)

    fun backspace(): PinDigits = if (value.isEmpty()) this else copy(value.dropLast(1))

    companion object {
        const val PIN_LENGTH = 4
    }
}
