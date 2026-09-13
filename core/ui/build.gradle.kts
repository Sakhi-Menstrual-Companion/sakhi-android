plugins {
    id("sakhi.android.library")
    id("sakhi.android.library.compose")
}

android {
    namespace = "team.sakhi.android.ui"
}

dependencies {
    implementation("team.sakhi:SakhiCore:1.0.0")
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    // BackHandler, so back closes the quick-log tray.
    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.material.icons.extended)
    // collectAsStateWithLifecycle, so FeatureAccessGate re-evaluates on state changes.
    implementation(libs.androidx.lifecycle.runtime.compose)
    // FeatureAccessGate reaches the shared FeatureAccessResolver through Koin.
    implementation(libs.koin.compose)

    testImplementation(kotlin("test"))
}
