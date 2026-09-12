import java.util.Properties

plugins {
    id("sakhi.android.application")
    id("sakhi.android.application.compose")
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.play.publisher)
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
    // 36 since 2026-08-25, to satisfy the Play target API rule below. compileSdk must be
    // at least targetSdk, so these two move together.
    //
    // AGP 8.6.1 was only tested up to compileSdk 35 and prints a warning about 36. That is
    // a warning, not a failure, and it is silenced by android.suppressUnsupportedCompileSdk
    // in gradle.properties. Bumping AGP instead would drag the Gradle wrapper, SakhiCore and
    // PredictionSDK along with it, because Gradle hard-rejects mixed AGP versions across a
    // composite build (see SakhiCore's plugins block). That is a separate piece of work and
    // it is not what the Play deadline needs.

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
        // Android 16. Raised from 35 on 2026-08-25 because Play blocks updates from
        // 2026-08-31 for anything targeting more than one release behind, and Play Console
        // had already flagged this app as non-compliant at 35.
        // https://support.google.com/googleplay/android-developer/answer/11926878
        //
        // The three targetSdk-36 behaviour changes that could have bitten were checked and
        // none of them apply: edge-to-edge is already opted into properly via
        // enableEdgeToEdge() in MainActivity rather than the opt-out flag Android 16 ignores;
        // predictive back is already on via enableOnBackInvokedCallback in the manifest; and
        // no activity pins android:screenOrientation, so the large-screen orientation rule
        // has nothing to override.
        // ── Versioning rule, follow this on every upload ────────────────────────
        //
        // versionCode is Play's own integer and the user never sees it. It goes up by one
        // on EVERY upload and can never repeat, because Play refuses a code it has already
        // seen, so this only ever goes up, whether or not versionName moves.
        //
        // History: 1 went up by hand on 2026-08-13. 2 went up through the API. 3 went to
        // the internal track. 4 was built and signed on 2026-08-25 for the target-API-36
        // bump; Play Console showed an update in review on 2026-08-26, so 4 has to be
        // assumed spent whether or not that review is the one. Skipping to 5 costs
        // nothing, and re-using a code Play has already seen is a hard upload failure.
        //
        // versionName is the string the user reads. It changes only when the release means
        // something different: 2.0.1 to 2.0.2 for a fix, to 2.1.0 for a feature. The two
        // numbers are independent and are not meant to match each other.
        versionCode = 5
        // NOTE: iOS `MARKETING_VERSION` is still 1.0, so the two platforms no longer carry
        // the same number. Bring iOS into step if they are meant to match.
        versionName = "2.0.2"
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

            // Native crash reports are unreadable without these.
            //
            // We ship no C++ of our own, but three dependencies bring prebuilt .so files:
            // libsqliteJni (Room), libtensorflowlite_jni (the prediction engine) and
            // libandroidx.graphics.path. A crash or ANR inside any of them arrives in Play
            // Console as raw addresses, which cannot be acted on. Play warns about exactly
            // this on upload.
            //
            // SYMBOL_TABLE rather than FULL, because FULL only adds line-number data that
            // prebuilt libraries do not carry anyway.
            //
            // Note for whoever chases Play's "you've not uploaded debug symbols" warning
            // next: as of versionCode 3 this setting produces nothing, and that is not a
            // misconfiguration. All three libraries arrive fully stripped. `file` reports
            // them as "stripped" and `nm` reports "no symbols", so there is no symbol table
            // to extract and the AAB gets no BUNDLE-METADATA debugsymbols entry. Play shows
            // that warning whenever a bundle contains any native code, whether or not we
            // could have done anything about it. Building SQLite and TensorFlow Lite from
            // source purely to symbolicate their frames is not worth it.
            //
            // The setting stays because it costs nothing and starts working by itself the
            // day any of these ships an unstripped build.
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            signingConfig = if (releaseKeystoreFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        // Records the lint findings that already existed when the custom checks were added,
        // so NEW problems fail the build while the backlog stays visible instead of being
        // silently disabled. Regenerate deliberately with:
        //
        //   ./gradlew :app:updateLintBaseline
        //
        // Today it holds 4 `MissingPermission` false positives from the PredictionSDK
        // included build: it calls ConnectivityManager but, being a library, cannot declare
        // ACCESS_NETWORK_STATE itself. The app DOES declare it (AndroidManifest.xml line 4),
        // lint just cannot see across the composite-build boundary. Do not "fix" those by
        // adding permissions to a shared KMM module.
        baseline = file("lint-baseline.xml")
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
    implementation(libs.androidx.core.splashscreen)
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
    // The org.jetbrains.compose.foundation/runtime exclusions these used to carry are now
    // applied once at the configuration level, in build-logic's configureAndroidCompose.
    // See that file for why the duplicate Compose stack is a real compile failure and not a
    // style preference.
    implementation(libs.koin.compose)
    implementation(libs.koin.androidx.compose)

    baselineProfile(project(":baseline-profile"))
}

baselineProfile {
    // Copies any successfully-generated profile into app/src/... so release
    // builds consume it automatically; actual generation still requires a
    // bootable test device/emulator.
    saveInSrc = true
}

// ── Google Play publishing ──────────────────────────────────────────────────
//
// Uploads the signed AAB to a Play track without opening the Console:
//
//   ./gradlew :app:publishReleaseBundle          # to the configured track
//   ./gradlew :app:bundleRelease                 # build only, no upload
//
// Needs a Play Developer API service-account JSON, path in secrets.properties as
// PLAY_SERVICE_ACCOUNT_JSON. Without it every publish task is disabled rather than
// failing the build, so a clean checkout still builds and `bundleRelease` still works.
//
// Google requires the FIRST bundle for a package to be uploaded by hand in the Console.
// Until that has happened once, the API rejects uploads for this package no matter how
// the credentials are set up.
play {
    val credentialsPath = secret("PLAY_SERVICE_ACCOUNT_JSON")
    val credentialsFile = if (credentialsPath.isNotBlank()) rootProject.file(credentialsPath) else null

    enabled.set(credentialsFile?.exists() == true)
    if (credentialsFile?.exists() == true) {
        serviceAccountCredentials.set(credentialsFile)
    }

    // Safe default: nothing reaches the public store by accident. Override per run with
    // `-Pplay.track=production`.
    track.set(providers.gradleProperty("play.track").orElse("internal"))
    defaultToAppBundles.set(true)
    // "completed" publishes the release outright; "draft" leaves it for review in the
    // Console. Draft by default, because a store release is not a thing to do by typo.
    releaseStatus.set(
        providers.gradleProperty("play.status").map { com.github.triplet.gradle.androidpublisher.ReleaseStatus.valueOf(it.uppercase()) }
            .orElse(com.github.triplet.gradle.androidpublisher.ReleaseStatus.DRAFT),
    )
}
