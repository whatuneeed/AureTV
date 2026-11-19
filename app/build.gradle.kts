import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The key and its passwords never live in the project or in the backups: the
// file is outside the workspace and can be moved with OVISIONTV_SIGNING_PROPERTIES.
// Without it release stays unsigned so lint and tests still run anywhere.
val signingPropertiesFile = File(
    System.getenv("OVISIONTV_SIGNING_PROPERTIES")
        ?: "${System.getProperty("user.home")}/.ovisiontv/keystore.properties",
)
val signingProperties = Properties().apply {
    if (signingPropertiesFile.isFile) signingPropertiesFile.inputStream().use { load(it) }
}

// Empty means the in-app updater is honestly disabled until the owner
// publishes the GitHub metadata page. The value must be HTTPS; the code
// revalidates it before every request.
val updateMetadataUrl = System.getenv("OVISIONTV_UPDATE_URL")?.trim().orEmpty()

composeCompiler {
    // Compose 2.0 can write per-composable metrics; keep them out of the APK
    // so a heavy screen is visible in CI logs instead of only as a line count.
    reportsDestination = layout.buildDirectory.dir("compose-reports")
    metricsDestination = layout.buildDirectory.dir("compose-metrics")
}

android {
    namespace = "com.whatuneed.ovisiontv.kotlin"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.whatuneed.ovisiontv.kotlin"
        minSdk = 28
        targetSdk = 35
        // versionCode grows with every build shared outside this machine;
        // versionName names the batch of changes the build contains.
        versionCode = 136
        versionName = "0.5.63"
        buildConfigField(
            "String",
            "UPDATE_METADATA_URL",
            "\"${updateMetadataUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"",
        )
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (signingProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(signingProperties.getProperty("storeFile"))
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
                // v2 is the scheme the release gate and minSdk 28 expect.
                enableV2Signing = true
            }
        }
    }

    bundle {
        language {
            // LocalePreference switches the UI language at runtime, so the base
            // module must keep every translation instead of splitting by locale.
            enableSplit = false
        }
    }

    lint {
        // Errors fail the build; warnings stay visible in the report.
        abortOnError = true
        warningsAsErrors = false
    }

    testOptions {
        // Activity-level tests drive real Windows/dispatchers, so the device
        // must not add its own transition animations on top of Compose's clock.
        // Compose-only tests keep using the test clock; this removes a known
        // source of emulator-only timing differences.
        animationsDisabled = true
    }

    buildTypes {
        release {
            // The project has no reflection or name-based resource lookup; the
            // shipped ProGuard rules come from Compose, media3 and AndroidX.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.core:core-ktx:1.13.1")
    // Compose and the other libraries ship baseline profiles; without this
    // installer the merged profile never reaches the device.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")

    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.03"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
