plugins {
    id("sakhi.android.feature")
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "team.sakhi.android.feature.onboarding"
}

dependencies {
    // Safe diagnostic logging for the care-invite path (`SakhiInvite`), which previously
    // swallowed its real failure behind a generic user message.
    implementation(libs.kermit)
    implementation(project(":core:platform"))
    implementation(project(":feature:auth"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.health.connect.client)
    implementation(libs.androidx.browser)
    // Screenshot-test durability lane.
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.compose.ui.test.manifest)
}
