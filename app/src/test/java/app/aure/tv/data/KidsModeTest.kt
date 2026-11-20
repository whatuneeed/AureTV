package app.aure.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class KidsModeTest {

    private fun app(pkg: String) = TvApp(pkg, "$pkg.Main", pkg, null, null)
    private fun folder(id: String, vararg pkgs: String) =
        HomeItem.FolderItem(id, id, pkgs.map(::app))

    @Test
    fun pinHashVerifiesOnlyTheSamePin() = runBlocking {
        val salt = KidsCrypto.newSalt()
        val hash = KidsCrypto.hash(salt, "1234")
        assertTrue(hash.startsWith("pbkdf2\$sha256\$120000\$"))
        assertTrue(KidsCrypto.matches(salt, hash, "1234"))
        assertFalse(KidsCrypto.matches(salt, hash, "1235"))
        assertFalse(KidsCrypto.matches(KidsCrypto.newSalt(), hash, "1234"))
    }

    @Test
    fun aKidsProfileAllowsOwnAndAllowlistedPackagesOnly() {
        val allowlist = setOf("tv.allowed")
        assertTrue(allowedInKidsProfile("app.aure.tv", "app.aure.tv", allowlist))
        assertTrue(allowedInKidsProfile("app.aure.tv", "tv.allowed", allowlist))
        assertFalse(allowedInKidsProfile("app.aure.tv", "tv.blocked", allowlist))
        assertFalse(allowedInKidsProfile("app.aure.tv", null, allowlist))
        assertFalse(allowedInKidsProfile("app.aure.tv", "", allowlist))
    }

    @Test
    fun everyStoredPinGetsItsOwnSalt() {
        assertNotEquals(KidsCrypto.newSalt(), KidsCrypto.newSalt())
    }

    @Test
    fun recoveryAnswersIgnoreCaseAndSurroundingSpace() = runBlocking {
        val salt = KidsCrypto.newSalt()
        val hash = KidsCrypto.hash(salt, KidsCrypto.normalizeAnswer("  Барсик "))
        assertTrue(KidsCrypto.matches(salt, hash, KidsCrypto.normalizeAnswer("барсик")))
        assertFalse(KidsCrypto.matches(salt, hash, KidsCrypto.normalizeAnswer("Барсика")))
    }

    @Test
    fun invalidBase64SaltFailsClosedWithoutThrowing() = runBlocking {
        val salt = KidsCrypto.newSalt()
        val hash = KidsCrypto.hash(salt, "1234")
        assertFalse(KidsCrypto.matches("!", hash, "1234"))
    }

    @Test
    fun nonPositiveIterationsFailClosedWithoutThrowing() = runBlocking {
        val salt = KidsCrypto.newSalt()
        val digest = KidsCrypto.hash(salt, "1234").substringAfterLast('$')
        listOf("0", "-1").forEach { iterations ->
            assertFalse(KidsCrypto.matches(salt, "pbkdf2\$sha256\$$iterations\$$digest", "1234"))
        }
    }

    @Test
    fun anUnsupportedAlgorithmTagIsRejected() = runBlocking {
        val salt = KidsCrypto.newSalt()
        val digest = KidsCrypto.hash(salt, "1234").substringAfterLast('$')
        assertFalse(KidsCrypto.matches(salt, "pbkdf2\$other\$120000\$$digest", "1234"))
    }

    @Test(timeout = 10_000)
    fun absurdIterationCountIsRejectedWithoutComputing() = runBlocking {
        val salt = KidsCrypto.newSalt()
        val digest = KidsCrypto.hash(salt, "1234").substringAfterLast('$')
        assertFalse(KidsCrypto.matches(salt, "pbkdf2\$sha256\$2147483647\$$digest", "1234"))
    }

    @Test
    fun fourWrongPinsCostNothingAndTheFifthLocksForThirtySeconds() {
        assertEquals(0L, KidsMode.lockDelayMillis(1))
        assertEquals(0L, KidsMode.lockDelayMillis(4))
        assertEquals(30_000L, KidsMode.lockDelayMillis(5))
        assertEquals(60_000L, KidsMode.lockDelayMillis(6))
        assertEquals(120_000L, KidsMode.lockDelayMillis(7))
        assertEquals(300_000L, KidsMode.lockDelayMillis(8))
        assertEquals(300_000L, KidsMode.lockDelayMillis(20))
    }

    @Test
    fun filterKeepsOnlyAllowedAppsAndTheLauncherItself() {
        val items = listOf(
            HomeItem.AppItem(app("tv.kinopoisk")),
            HomeItem.AppItem(app("com.example.blocked")),
            HomeItem.AppItem(app("app.aure.tv")),
        )
        val filtered = kidsFilter(
            items,
            allowlist = setOf("tv.kinopoisk"),
            ownPackage = "app.aure.tv",
        )
        assertEquals(
            listOf("tv.kinopoisk", "app.aure.tv"),
            filtered.map { (it as HomeItem.AppItem).app.packageName },
        )
    }

    @Test
    fun filterLiftsAllowedAppsOutOfFoldersAndDropsEmptyOnes() {
        val items = listOf(
            folder("short", "tv.kinopoisk", "com.example.blocked"),
            folder("empty", "com.example.blocked"),
            folder("hidden", "tv.kinopoisk", "tv.ivi"),
        )
        val filtered = kidsFilter(items, setOf("tv.kinopoisk"), "launcher")
        assertEquals(listOf("short", "hidden"), filtered.map { it.key.removePrefix("folder:") })
        assertEquals(
            listOf("tv.kinopoisk"),
            (filtered[0] as HomeItem.FolderItem).apps.map { it.packageName },
        )
        assertEquals(
            listOf("tv.kinopoisk"),
            (filtered[1] as HomeItem.FolderItem).apps.map { it.packageName },
        )
    }

    @Test
    fun pinAcceptsAtMostFourDigitsAndRejectsOtherCharacters() {
        var digits = PinDigits()
        listOf('1', '2', 'a', '3', '4', '5').forEach { digits = digits.append(it) }
        assertEquals("1234", digits.value)
        assertTrue(digits.complete)
        assertEquals("123", digits.backspace().value)
        assertEquals("", PinDigits("").backspace().value)
    }

    @Test
    fun kidsFlagSurvivesTheProfileFile() {
        val kids = Profile("p2", "Миша", "🐶", 6, kids = true)
        val plain = Profile("p1", "Я", "🍿", 3)
        val parsed = parseProfiles(serializeProfiles(listOf(plain, kids)), "Я")
        assertEquals(listOf(false, true), parsed.profiles.map { it.kids })
    }

    @Test
    fun aFileFromBeforeKidsModeReadsAsAPlainProfile() {
        val parsed = parseProfiles(
            """[{"id":"p1","name":"Я","emoji":"🍿","color":3}]""",
            "Я",
        )
        assertFalse(parsed.profiles[0].kids)
    }

    @Test
    fun leavingKidsWithAPinIsGated() {
        val kids = Profile("p2", "Миша", "🐶", 6, kids = true)
        val plain = Profile("p1", "Я", "🍿", 3)
        assertTrue(kidsLeaveNeedsPin(kids, plain, hasPin = true))
    }

    @Test
    fun leavingKidsWithoutAPinIsNotGated() {
        val kids = Profile("p2", "Миша", "🐶", 6, kids = true)
        val plain = Profile("p1", "Я", "🍿", 3)
        assertFalse(kidsLeaveNeedsPin(kids, plain, hasPin = false))
    }

    @Test
    fun switchingBetweenKidsProfilesOrIntoKidsIsNotGated() {
        val kids = Profile("p2", "Миша", "🐶", 6, kids = true)
        val otherKids = Profile("p3", "Фима", "🐱", 2, kids = true)
        assertFalse(kidsLeaveNeedsPin(kids, otherKids, hasPin = true))
        assertFalse(kidsLeaveNeedsPin(otherKids, kids, hasPin = true))
    }

    @Test
    fun switchingInsideAPlainProfileIsNeverGated() {
        val plain = Profile("p1", "Я", "🍿", 3)
        val kids = Profile("p2", "Миша", "🐶", 6, kids = true)
        assertFalse(kidsLeaveNeedsPin(plain, kids, hasPin = true))
    }
}
