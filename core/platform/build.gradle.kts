plugins {
    id("sakhi.android.library")
    id("sakhi.android.library.compose")
}

android {
    namespace = "team.sakhi.android.platform"
}

dependencies {
    implementation("team.sakhi:SakhiCore:1.0.0")
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))

    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.health.connect.client)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.work.runtime.ktx)
    // 1.4.x never left alpha (verified against Maven metadata) — 1.1.0 is the newest stable.
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    // Per-app language switching (`AppCompatDelegate.setApplicationLocales`) — works down to
    // minSdk 26 without requiring `AppCompatActivity`; 1.6.0+ ships its own manifest-merged
    // backport service that persists the choice and re-applies it on cold start.
    implementation("androidx.appcompat:appcompat:1.7.0")

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging.ktx)
    implementation(libs.google.play.services.location)

    testImplementation(kotlin("test"))
    testImplementation(libs.mockk)
}
