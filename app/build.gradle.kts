import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// google-services.json is git-ignored (Firebase project config, see .gitignore) and
// the google-services plugin hard-fails the build if applied without it -- apply it
// imperatively, only when the real file is present, so a clean checkout without it
// still builds (same fallback pattern as the release signingConfig below).
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
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
    // Kotlin source package namespace -- unrelated to the public app identity below,
    // intentionally NOT renamed (would mean renaming every package declaration across
    // the whole Kotlin source tree for no functional benefit).
    namespace = "team.sakhi.android"
    compileSdk = 35

    defaultConfig {
        // Renamed 2026-08-13 to com.rachna.mysakhi. The earlier com.rachna.sakhi Play
        // Console app already had a different upload certificate registered against it
        // (SHA-1 A4:22:BB:9C:...), whose keystore is not on this machine, so a fresh
        // package means a fresh Play app that accepts the current upload key instead of
        // needing a Google upload-key reset.
        //
        // This is now PERMANENT once the first bundle is uploaded, Play never allows a
        // package rename after that. It deliberately diverges from iOS's bundle id
        // (com.galgotiasuniversity.rachnasakhi, see 01-iOS's project.pbxproj); the two
        // stores are independent namespaces, so that divergence is fine.
        //
        // Anything keyed on the package name must be registered under com.rachna.mysakhi
        // or it silently fails at runtime in release: Firebase (google-services.json),
        // the Google Maps/Places key's Android app restriction, and Supabase phone auth's
        // SHA-1/SHA-256 allowlist. Release upload-key SHA-1 is
        // 2B:03:38:B6:BC:91:15:06:1E:39:4E:A6:2E:45:5A:8B:C1:42:FE:89.
        applicationId = "com.rachna.mysakhi"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"   // matches iOS MARKETING_VERSION; one launch, one number
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
        // Static Maps runs as a WEB SERVICE call, so it needs the key at runtime, not just
        // the manifest placeholder the Maps SDK reads.
        buildConfigField("String", "GOOGLE_MAPS_API_KEY", "\"${secret("GOOGLE_MAPS_API_KEY", secret("GOOGLE_PLACES_API_KEY"))}\"")
        buildConfigField("String", "EXOTEL_SID", "\"${secret("EXOTEL_SID")}\"")
        buildConfigField("String", "EXOTEL_TOKEN", "\"${secret("EXOTEL_TOKEN")}\"")
        buildConfigField("String", "SANITY_PROJECT_ID", "\"${secret("SANITY_PROJECT_ID")}\"")
        buildConfigField("String", "SANITY_DATASET", "\"${secret("SANITY_DATASET", "production")}\"")
        buildConfigField("String", "RAZORPAY_KEY_ID", "\"${secret("RAZORPAY_KEY_ID")}\"")
        buildConfigField("String", "USDA_API_KEY", "\"${secret("USDA_API_KEY")}\"")
    }

    // Real release keystore, generated 2026-08-13 (keystore/sakhi-release.jks,
    // git-ignored). Falls back to the debug config on a clean checkout where the
    // keystore/secrets aren't present (e.g. CI without the real file), so the
    // project still builds -- but any actual release artifact must be built
    // where the real keystore file and secrets.properties entries exist.
    val releaseKeystoreFile = rootProject.file(secret("RELEASE_KEYSTORE_PATH", "keystore/sakhi-release.jks"))
    signingConfigs {
        if (releaseKeystoreFile.exists()) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = secret("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = secret("RELEASE_KEYSTORE_ALIAS")
                keyPassword = secret("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (releaseKeystoreFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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
    implementation(project(":feature:emergency"))
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

    implementation(libs.kermit)

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
