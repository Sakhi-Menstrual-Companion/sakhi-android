import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Secrets come from secrets.properties (git-ignored, see .gitignore) for local runs,
// or from the same-named env vars in CI (never committed either way). Falls back to
// empty strings so a clean checkout still builds — SakhiCore's BuildConfigProvider
// already defaults to "" too, so a missing secret is a runtime auth failure, not a
// build failure. Never hardcode a real key here.
val secretsProperties = Properties().apply {
    val secretsFile = rootProject.file("secrets.properties")
    if (secretsFile.exists()) {
        secretsFile.inputStream().use { load(it) }
    }
}
fun secret(key: String, default: String = ""): String =
    (System.getenv(key) ?: secretsProperties.getProperty(key, default))

android {
    namespace = "team.sakhi.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "team.sakhi.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        // Temporary fallback keeps clean checkouts buildable, but the new
        // Nearby Places map surfaces still need a Maps-authorized runtime key.
        manifestPlaceholders["googleMapsApiKey"] = secret(
            "GOOGLE_MAPS_API_KEY",
            secret("GOOGLE_PLACES_API_KEY"),
        )

        buildConfigField("String", "SUPABASE_URL", "\"${secret("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${secret("SUPABASE_ANON_KEY")}\"")
        buildConfigField("String", "CLAUDE_API_KEY", "\"${secret("CLAUDE_API_KEY")}\"")
        buildConfigField("String", "GOOGLE_PLACES_API_KEY", "\"${secret("GOOGLE_PLACES_API_KEY")}\"")
        buildConfigField("String", "EXOTEL_SID", "\"${secret("EXOTEL_SID")}\"")
        buildConfigField("String", "EXOTEL_TOKEN", "\"${secret("EXOTEL_TOKEN")}\"")
        buildConfigField("String", "SANITY_PROJECT_ID", "\"${secret("SANITY_PROJECT_ID")}\"")
        buildConfigField("String", "SANITY_DATASET", "\"${secret("SANITY_DATASET", "production")}\"")
        buildConfigField("String", "RAZORPAY_KEY_ID", "\"${secret("RAZORPAY_KEY_ID")}\"")
        buildConfigField("String", "USDA_API_KEY", "\"${secret("USDA_API_KEY")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // No real release keystore yet (BLOCKED ON KARAN, see plan Section 8 /
            // Release readiness) -- signed with the implicit debug key purely so this
            // minified build can be installed and smoke-tested on a real
            // emulator/device. Must be replaced with a real signing config before
            // this build type is ever used for an actual release artifact.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("team.sakhi:SakhiCore:1.0.0")

    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:platform"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:home"))
    implementation(project(":feature:profile"))
    implementation(project(":feature:calendar"))
    implementation(project(":feature:logging"))
    implementation(project(":feature:care"))
    implementation(project(":feature:ai"))
    implementation(project(":feature:reports"))
    implementation(project(":feature:recommendations"))
    // All 10 feature modules are now real and wired.

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.profileinstaller)
    // FragmentActivity is required by MainActivity — BiometricPrompt (:core:platform)
    // needs a FragmentActivity host, not a plain ComponentActivity.
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    // Required by Navigation Compose's type-safe routes (@Serializable route objects).
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")

    implementation(libs.koin.core)
    implementation(libs.koin.android)
    // Excludes org.jetbrains.compose.foundation/runtime: koin-compose(-android) pulls these
    // in at a strict 1.8.2, a duplicate of this app's real androidx.compose 1.11.4 stack
    // under the same package names -- caused a real compile failure (Modifier.weight()
    // resolving against the wrong artifact) before being excluded.
    implementation(libs.koin.compose) {
        exclude(group = "org.jetbrains.compose.foundation")
        exclude(group = "org.jetbrains.compose.runtime")
    }
    implementation(libs.koin.androidx.compose) {
        exclude(group = "org.jetbrains.compose.foundation")
        exclude(group = "org.jetbrains.compose.runtime")
    }

    baselineProfile(project(":baseline-profile"))
}

baselineProfile {
    // Copies any successfully-generated profile into app/src/... so release
    // builds consume it automatically; actual generation still requires a
    // bootable test device/emulator.
    saveInSrc = true
}
