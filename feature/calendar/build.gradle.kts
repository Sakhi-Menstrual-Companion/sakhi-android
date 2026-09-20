plugins {
    id("sakhi.android.feature")
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "team.sakhi.android.feature.calendar"
}

dependencies {
    implementation(project(":core:platform"))
    // The bottom bar's leading slot is Care Mode, and it draws what is live for her walk.
    implementation(project(":feature:care"))
    implementation(project(":feature:logging"))
    // Screenshot-test durability lane.
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.compose.ui.test.manifest)
}
