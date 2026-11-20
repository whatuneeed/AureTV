package app.aure.tv.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KidsCredentialShapeTest {

    @Test
    fun a_fresh_credential_is_usable() = runBlocking {
        val salt = KidsCrypto.newSalt()
        val hash = KidsCrypto.hash(salt, "1234")

        assertTrue(KidsCrypto.credentialUsable(salt, hash))
    }

    @Test
    fun a_missing_or_broken_salt_is_not_usable() {
        val hash = "pbkdf2\$sha256\$120000\$" + "A".repeat(43) + "="

        assertFalse(KidsCrypto.credentialUsable("", hash))
        assertFalse(KidsCrypto.credentialUsable("!", hash))
    }

    @Test
    fun a_broken_hash_is_not_usable() {
        val salt = KidsCrypto.newSalt()

        assertFalse(KidsCrypto.credentialUsable(salt, ""))
        assertFalse(KidsCrypto.credentialUsable(salt, "pbkdf2\$sha256\$120000"))
        assertFalse(KidsCrypto.credentialUsable(salt, "pbkdf2\$sha256\$120000\$!"))
    }
}
