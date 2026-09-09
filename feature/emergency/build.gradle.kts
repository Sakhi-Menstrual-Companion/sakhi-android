plugins {
    id("sakhi.android.feature")
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "team.sakhi.android.feature.emergency"

    buildFeatures {
        // `EmergencyDemoMode` guards itself on `BuildConfig.DEBUG` so no release build can
        // ever put a made-up Sakhi in front of a woman who needs help.
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:platform"))
    // The flow is a map with a sheet over it, same as iOS. Uses the same
    // maps-compose version feature:ai already pulls in.
    implementation(libs.google.maps.compose)
    // Render-parity lane. `EmergencyOnboardingParityTest` is what stops this screen
    // drifting away from account onboarding's page shape a second time.
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.compose.ui.test.manifest)
}
