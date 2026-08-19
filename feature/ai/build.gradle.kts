plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "team.sakhi.android.feature.ai"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
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
        // The Nearby badge mirrors iOS's `#if DEBUG` stand-in count, so it needs
        // `BuildConfig.DEBUG` in this module.
        buildConfig = true
    }

    // `ChatUiState.sharePdfUri` is `android.net.Uri`, same as `ReportsUiState`'s -- merely
    // referencing that stub-jar class in local JUnit (no Robolectric) throws
    // `RuntimeException("Stub!")` from its static initializer, which poisons the
    // classloader for every other test in the same file. See `:feature:reports`'
    // `ReportsViewModelTest` for the full writeup of this exact issue.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("team.sakhi:SakhiCore:1.0.0")
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:platform"))
    implementation(project(":feature:reports"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.google.maps.compose)
    // Excludes org.jetbrains.compose.foundation/runtime: koin-compose-android pulls these
    // in at a strict 1.8.2, a duplicate of this app's real androidx.compose 1.11.4 stack
    // under the same package names -- caused a real compile failure (Modifier.weight()
    // resolving against the wrong artifact) before being excluded.
    implementation(libs.koin.androidx.compose) {
        exclude(group = "org.jetbrains.compose.foundation")
        exclude(group = "org.jetbrains.compose.runtime")
    }

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
