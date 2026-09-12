plugins {
    id("sakhi.android.feature")
}

android {
    namespace = "team.sakhi.android.feature.care"
}

dependencies {
    implementation(project(":core:platform"))
    implementation(project(":feature:onboarding"))
    // The Stay With Me map, the same maps-compose version feature:emergency uses.
    implementation(libs.google.maps.compose)
}
