plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "team.sakhi.android.common"
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
}

dependencies {
    api("team.sakhi:SakhiCore:1.0.0")

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    // api, not implementation: SakhiCore's own public functions return kotlinx.datetime
    // types (LocalDate etc.) directly, and SakhiCore itself only depends on it via
    // `implementation` (hidden from consumers), so every module touching cycle/period
    // dates needs this on its own classpath too — declared once here, flows to every
    // :feature:* module via :core:common instead of repeating per module (verified by
    // a real "Cannot access class 'LocalDate'" build failure in :feature:home).
    api("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
}
