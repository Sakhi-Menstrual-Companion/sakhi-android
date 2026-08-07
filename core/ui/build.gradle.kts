plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "team.sakhi.android.ui"
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
}

dependencies {
    // Needed by FeatureAccessGate to reach the shared FeatureAccessResolver. Same
    // exclusions as every other module: koin-compose pulls org.jetbrains.compose
    // foundation/runtime at a strict 1.8.2, duplicating this app's androidx.compose
    // stack under the same package names.
    // collectAsStateWithLifecycle, so FeatureAccessGate re-evaluates on state changes.
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.koin.compose) {
        exclude(group = "org.jetbrains.compose.foundation")
        exclude(group = "org.jetbrains.compose.runtime")
    }

    implementation("team.sakhi:SakhiCore:1.0.0")
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.tooling.preview)
}
