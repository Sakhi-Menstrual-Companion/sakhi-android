plugins {
    id("sakhi.android.feature")
}

android {
    namespace = "team.sakhi.android.feature.logging"
}

dependencies {
    implementation(project(":core:platform"))
    implementation(libs.kermit)
}
