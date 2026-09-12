plugins {
    id("sakhi.android.library")
    id("sakhi.android.library.compose")
}

android {
    namespace = "team.sakhi.android.designsystem"
}

dependencies {
    implementation("team.sakhi:SakhiCore:1.0.0")
    implementation(project(":core:common"))

    testImplementation(kotlin("test"))
}
