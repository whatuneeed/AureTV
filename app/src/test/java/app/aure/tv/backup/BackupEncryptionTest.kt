package app.aure.tv.backup

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64

class BackupEncryptionTest {

    private val password = "correct horse battery".toCharArray()

    private fun params(
        salt: ByteArray = ByteArray(32) { it.toByte() },
        nonce: ByteArray = ByteArray(12) { it.toByte() },
    ) = EncryptionParams(
        cipher = "AES-256-GCM",
        kdf = "PBKDF2-HMAC-SHA256",
        iterations = BackupContract.KDF_ITERATIONS,
        salt = Base64.getEncoder().encodeToString(salt),
        nonce = Base64.getEncoder().encodeToString(nonce),
        tagBits = 128,
    )

    private fun decrypt(params: EncryptionParams) {
        try {
            BackupEncryption.decrypt(ByteArray(1), params, password)
            error("malformed encryption parameters must not reach the cipher")
        } catch (expected: BackupException) {
            assertEquals(BackupStage.CRYPTO, expected.stage)
        }
    }

    @Test
    fun aSaltThatIsNotThirtyTwoBytesIsRejectedBeforeKeyDerivation() {
        decrypt(params(salt = ByteArray(16)))
        decrypt(params(salt = ByteArray(64)))
    }

    @Test
    fun aNonceThatIsNotTwelveBytesIsRejected() {
        decrypt(params(nonce = ByteArray(8)))
        decrypt(params(nonce = ByteArray(16)))
    }

    @Test
    fun anOversizedEncodedParameterIsRejectedBeforeBase64Decoding() {
        decrypt(params().copy(salt = "A".repeat(257)))
        decrypt(params().copy(nonce = "A".repeat(257)))
    }
}
