plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "team.sakhi.android.feature.reports"
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
    }

    // `ReportsUiState.sharePdfUri` is `android.net.Uri`, and merely referencing that
    // stub-jar class in a local JUnit run (no Robolectric) throws `RuntimeException
    // ("Stub!")` from its static initializer, which then poisons the classloader for
    // every other test in the same file. `isReturnDefaultValues` makes Android stub
    // methods return safe defaults instead, which is all `ReportsViewModelTest` needs.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("team.sakhi:SakhiCore:1.0.0")
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:platform"))
    implementation(project(":core:ui"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
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
