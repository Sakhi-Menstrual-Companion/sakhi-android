plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "team.sakhi.android.platform"
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
    implementation("team.sakhi:SakhiCore:1.0.0")
    implementation(project(":core:common"))

    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.health.connect.client)
    implementation(libs.androidx.work.runtime.ktx)
    // 1.4.x never left alpha (verified against Maven metadata) — 1.1.0 is the newest stable.
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.9")

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging.ktx)

    testImplementation(kotlin("test"))

    // Maps SDK, FusedLocation, Play Billing, camera/photo-picker adapters land
    // here in later phases (Section 6 of the plan). Each adapter's dependency
    // is added when its Kotlin file is written, not speculatively — avoids
    // dragging in unused SDKs this early.
}
