package app.aure.tv.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
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
        val store = KidsStore(context)
        return allowedInKidsProfile(
            ownPackage = context.packageName,
            packageName = packageName,
            allowlist = store.allowlist(ProfileStore(context).current().id),
        )
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

/** Pure predicate shared by the catalog filter and every launch path. */
internal fun allowedInKidsProfile(
    ownPackage: String,
    packageName: String?,
    allowlist: Set<String>,
): Boolean {
    if (packageName.isNullOrEmpty()) return false
    return packageName == ownPackage || packageName in allowlist
}

/** Stored per profile: PIN hash, recovery word, allowlist and lockout. */
class KidsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun allowlist(profileId: String): Set<String> =
        SafePreferences.stringSet(prefs, KEY_ALLOW + profileId, null) ?: HashSet()

    fun setAllowlist(profileId: String, packages: Set<String>) {
        prefs.edit().putStringSet(KEY_ALLOW + profileId, HashSet(packages)).apply()
    }

    fun hasPin(profileId: String): Boolean =
        !SafePreferences.string(prefs, KEY_PIN_HASH + profileId, null).isNullOrEmpty()

    enum class Verification { PASSED, WRONG, LOCKED, CHANGED }

    private data class Credential(val salt: String, val hash: String, val revision: Long)

    private fun credential(profileId: String, recovery: Boolean): Credential? {
        val salt = SafePreferences.string(
            prefs,
            (if (recovery) KEY_REC_SALT else KEY_PIN_SALT) + profileId,
            null,
        ) ?: return null
        val hash = SafePreferences.string(
            prefs,
            (if (recovery) KEY_REC_HASH else KEY_PIN_HASH) + profileId,
            null,
        ) ?: return null
        return Credential(salt, hash, revisions[profileId] ?: 0L)
    }

    private suspend fun verify(
        profileId: String,
        value: String,
        recovery: Boolean,
        countAttempt: Boolean,
    ): Verification {
        val snapshot = synchronized(prefs) {
            if (countAttempt && lockRemaining(profileId) > 0) return Verification.LOCKED
            credential(profileId, recovery) ?: return Verification.CHANGED
        }
        val matches = KidsCrypto.matches(snapshot.salt, snapshot.hash, value)
        currentCoroutineContext().ensureActive()
        // Never hold this lock during PBKDF2: clearing a PIN must not block the UI.
        return synchronized(prefs) {
            if (credential(profileId, recovery) != snapshot) return Verification.CHANGED
            if (countAttempt) {
                if (lockRemaining(profileId) > 0) return Verification.LOCKED
                if (matches) clearFailures(profileId) else recordFailure(profileId)
            }
            if (matches) Verification.PASSED else Verification.WRONG
        }
    }

    suspend fun checkPin(profileId: String, pin: String): Boolean =
        pin.length == KidsMode.PIN_LENGTH &&
            verify(profileId, pin, recovery = false, countAttempt = false) == Verification.PASSED

    suspend fun verifyPin(profileId: String, pin: String): Verification {
        if (pin.length != KidsMode.PIN_LENGTH) return Verification.WRONG
        return verify(profileId, pin, recovery = false, countAttempt = true)
    }

    suspend fun verifyRecovery(profileId: String, answer: String): Verification =
        verify(profileId, KidsCrypto.normalizeAnswer(answer), recovery = true, countAttempt = true)

    suspend fun setPin(profileId: String, pin: String) {
        val revision = synchronized(prefs) { revisions[profileId] ?: 0L }
        val salt = KidsCrypto.newSalt()
        val hash = KidsCrypto.hash(salt, pin)
        currentCoroutineContext().ensureActive()
        synchronized(prefs) {
            if ((revisions[profileId] ?: 0L) != revision) throw CancellationException("Credentials changed")
            prefs.edit()
                .putString(KEY_PIN_SALT + profileId, salt)
                .putString(KEY_PIN_HASH + profileId, hash)
                .remove(KEY_FAILS + profileId)
                .remove(KEY_LOCK_UNTIL + profileId)
                .apply()
            revisions[profileId] = revision + 1
        }
    }

    fun clearPin(profileId: String) {
        synchronized(prefs) {
            prefs.edit()
                .remove(KEY_PIN_SALT + profileId)
                .remove(KEY_PIN_HASH + profileId)
                .remove(KEY_REC_SALT + profileId)
                .remove(KEY_REC_HASH + profileId)
                .remove(KEY_REC_HINT + profileId)
                .remove(KEY_FAILS + profileId)
                .remove(KEY_LOCK_UNTIL + profileId)
                .apply()
            revisions[profileId] = (revisions[profileId] ?: 0L) + 1
        }
    }

    /** Deleting a profile removes everything it gated, not just its PIN. */
    fun clearProfile(profileId: String) {
        clearPin(profileId)
        prefs.edit().remove(KEY_ALLOW + profileId).apply()
    }

    fun lockRemaining(profileId: String, now: Long = System.currentTimeMillis()): Long {
        val until = SafePreferences.long(prefs, KEY_LOCK_UNTIL + profileId, 0L)
        return (until - now).coerceAtLeast(0L)
    }

    fun recordFailure(profileId: String, now: Long = System.currentTimeMillis()): Int = synchronized(prefs) {
        val fails = SafePreferences.int(prefs, KEY_FAILS + profileId, 0) + 1
        val delay = KidsMode.lockDelayMillis(fails)
        val editor = prefs.edit().putInt(KEY_FAILS + profileId, fails)
        if (delay > 0) editor.putLong(KEY_LOCK_UNTIL + profileId, now + delay)
        editor.apply()
        fails
    }

    fun clearFailures(profileId: String) {
        synchronized(prefs) {
            prefs.edit()
                .remove(KEY_FAILS + profileId)
                .remove(KEY_LOCK_UNTIL + profileId)
                .apply()
        }
    }

    fun recoveryHint(profileId: String): String =
        SafePreferences.string(prefs, KEY_REC_HINT + profileId, "").orEmpty()

    fun hasRecovery(profileId: String): Boolean =
        !SafePreferences.string(prefs, KEY_REC_HASH + profileId, null).isNullOrEmpty()

    /**
     * True when a credential exists but can never verify: a salt/hash was lost,
     * truncated or written by a broken restore. The pad must say so instead of
     * silently clearing every attempt and looking like a dead keypad.
     */
    fun pinDamaged(profileId: String): Boolean =
        credentialDamaged(profileId, recovery = false)

    fun recoveryDamaged(profileId: String): Boolean =
        credentialDamaged(profileId, recovery = true)

    private fun credentialDamaged(profileId: String, recovery: Boolean): Boolean {
        val saltKey = (if (recovery) KEY_REC_SALT else KEY_PIN_SALT) + profileId
        val hashKey = (if (recovery) KEY_REC_HASH else KEY_PIN_HASH) + profileId
        val salt = SafePreferences.string(prefs, saltKey, null)
        val hash = SafePreferences.string(prefs, hashKey, null)
        if (hash.isNullOrEmpty()) return false
        if (salt.isNullOrEmpty()) return true
        return !KidsCrypto.credentialUsable(salt, hash)
    }

    suspend fun setRecovery(profileId: String, hint: String, answer: String) {
        if (answer.trim().isEmpty()) return
        val revision = synchronized(prefs) { revisions[profileId] ?: 0L }
        val salt = KidsCrypto.newSalt()
        val hash = KidsCrypto.hash(salt, KidsCrypto.normalizeAnswer(answer))
        currentCoroutineContext().ensureActive()
        synchronized(prefs) {
            if ((revisions[profileId] ?: 0L) != revision) throw CancellationException("Credentials changed")
            prefs.edit()
                .putString(KEY_REC_HINT + profileId, hint.trim())
                .putString(KEY_REC_SALT + profileId, salt)
                .putString(KEY_REC_HASH + profileId, hash)
                .apply()
            revisions[profileId] = revision + 1
        }
    }

    suspend fun checkRecovery(profileId: String, answer: String): Boolean =
        verify(profileId, KidsCrypto.normalizeAnswer(answer), recovery = true, countAttempt = false) ==
            Verification.PASSED

    companion object {
        private val revisions = mutableMapOf<String, Long>()
        const val FILE = "kids_mode"
        internal const val KEY_ALLOW = "allow_"
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

    suspend fun hash(salt: String, value: String): String {
        val out = withContext(Dispatchers.Default) { pbkdf2(salt, value, KDF_ITERATIONS) }
        return "$KDF\$$KDF_ALG\$$KDF_ITERATIONS\$" + Base64.getEncoder().encodeToString(out)
    }

    suspend fun matches(salt: String, expected: String, value: String): Boolean {
        val parts = expected.split('$')
        if (parts.size != 4 || parts[0] != KDF || parts[1] != KDF_ALG) return false
        val iterations = parts[2].toIntOrNull() ?: return false
        // Cap at our own KDF cost: a larger stored count would be a CPU DoS.
        if (iterations !in 1..KDF_ITERATIONS) return false
        // Fail closed on a corrupt salt, but let cancellation propagate so a
        // cancelled verify does not abort the caller's setPin via ensureActive().
        val out = withContext(Dispatchers.Default) {
            try {
                pbkdf2(salt, value, iterations)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        } ?: return false
        val encoded = Base64.getEncoder().encodeToString(out)
        return expected == "$KDF\$${parts[1]}\$$iterations\$$encoded"
    }

    fun normalizeAnswer(answer: String): String = answer.trim().lowercase(Locale.ROOT)

    /** Shape check only: no KDF work, safe to call before every PIN entry. */
    internal fun credentialUsable(salt: String, expected: String): Boolean {
        val saltOk = runCatching {
            Base64.getDecoder().decode(salt).size == SALT_BYTES
        }.getOrDefault(false)
        if (!saltOk) return false
        val parts = expected.split('$')
        if (parts.size != 4 || parts[0] != KDF || parts[1] != KDF_ALG) return false
        val iterations = parts[2].toIntOrNull() ?: return false
        if (iterations !in 1..KDF_ITERATIONS) return false
        return runCatching {
            Base64.getDecoder().decode(parts[3]).size == 32
        }.getOrDefault(false)
    }

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
            it.app.available &&
                (it.app.packageName == ownPackage || it.app.packageName in allowlist)
        }

        is HomeItem.FolderItem -> {
            val apps = item.apps.filter {
                it.available &&
                    (it.packageName == ownPackage || it.packageName in allowlist)
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
