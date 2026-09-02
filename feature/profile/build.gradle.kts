plugins {
    id("sakhi.android.feature")
}

android {
    namespace = "team.sakhi.android.feature.profile"
}

dependencies {
    implementation(project(":core:platform"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.health.connect.client)
}
