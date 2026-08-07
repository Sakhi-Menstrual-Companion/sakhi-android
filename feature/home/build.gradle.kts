plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // Screenshot-test durability lane (2026-07-15), second slice -- Home chosen for its
    // real central-hub layout (top bar, hero, cards, bottom action bar), a meaningfully
    // different composition shape from Auth's simple forms and Calendar's grid.
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "team.sakhi.android.feature.home"
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
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation("team.sakhi:SakhiCore:1.0.0")
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:platform"))
    implementation(project(":feature:recommendations"))
    implementation(project(":feature:logging"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.kermit)

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

    // Screenshot-test durability lane.
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.compose.ui.test.manifest)
}
