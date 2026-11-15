package com.whatuneed.ovisiontv.kotlin.backup

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal data class EncryptedPayload(
    val params: EncryptionParams,
    val ciphertext: ByteArray,
)

/**
 * Password envelope for exported archives. This is deliberately plain JCE:
 * AES-256-GCM with a PBKDF2-HMAC-SHA256 key. No custom construction, no key
 * escrow, no recovery path — a lost password means a lost file.
 */
internal object BackupEncryption {
    private const val CIPHER = "AES-256-GCM"
    private const val KDF = "PBKDF2-HMAC-SHA256"
    private const val SALT_BYTES = 32
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256
    private const val AES = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun encrypt(
        plain: ByteArray,
        password: CharArray,
        iterations: Int = BackupContract.KDF_ITERATIONS,
    ): EncryptedPayload {
        if (password.size < BackupContract.PASSWORD_MIN_CHARS) {
            throw BackupException(
                BackupStage.CRYPTO,
                "password must be at least ${BackupContract.PASSWORD_MIN_CHARS} characters",
            )
        }
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val key = derive(password, salt, iterations)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, AES), GCMParameterSpec(TAG_BITS, nonce))
            val ciphertext = cipher.doFinal(plain)
            return EncryptedPayload(
                params = EncryptionParams(
                    cipher = CIPHER,
                    kdf = KDF,
                    iterations = iterations,
                    salt = Base64.getEncoder().encodeToString(salt),
                    nonce = Base64.getEncoder().encodeToString(nonce),
                    tagBits = TAG_BITS,
                ),
                ciphertext = ciphertext,
            )
        } catch (error: Exception) {
            throw BackupException(BackupStage.CRYPTO, "could not encrypt backup", error)
        } finally {
            key.fill(0)
        }
    }

    fun decrypt(
        ciphertext: ByteArray,
        params: EncryptionParams,
        password: CharArray,
    ): ByteArray {
        if (!params.isValid()) {
            throw BackupException(BackupStage.CRYPTO, "unsupported encryption parameters")
        }
        val salt = decode(params.salt)
        val nonce = decode(params.nonce)
        if (nonce.size != NONCE_BYTES) {
            throw BackupException(BackupStage.CRYPTO, "invalid nonce")
        }
        val key = derive(password, salt, params.iterations)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, AES), GCMParameterSpec(params.tagBits, nonce))
            cipher.doFinal(ciphertext)
        } catch (error: Exception) {
            throw BackupException(BackupStage.CRYPTO, "wrong password or damaged file", error)
        } finally {
            key.fill(0)
        }
    }

    private fun derive(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } catch (error: Exception) {
            throw BackupException(BackupStage.CRYPTO, "could not derive key", error)
        } finally {
            spec.clearPassword()
        }
    }

    private fun decode(value: String): ByteArray = try {
        Base64.getDecoder().decode(value)
    } catch (error: IllegalArgumentException) {
        throw BackupException(BackupStage.CRYPTO, "invalid encryption header", error)
    }
}
