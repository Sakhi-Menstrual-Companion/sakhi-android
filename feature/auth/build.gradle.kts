plugins {
    id("sakhi.android.feature")
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "team.sakhi.android.feature.auth"
}

dependencies {
    implementation(project(":core:platform"))
    implementation(libs.kermit)
    // Screenshot-test durability lane.
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.compose.ui.test.manifest)
}
