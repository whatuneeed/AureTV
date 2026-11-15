// Dependency checksums are pinned in gradle/verification-metadata.xml. After a
// deliberate dependency version change, regenerate it with:
// ./gradlew --write-verification-metadata sha256 :app:assembleRelease :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
plugins {
    id("com.android.application") version "8.7.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
