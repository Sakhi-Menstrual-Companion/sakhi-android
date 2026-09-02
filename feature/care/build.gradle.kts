plugins {
    id("sakhi.android.feature")
}

android {
    namespace = "team.sakhi.android.feature.care"
}

dependencies {
    implementation(project(":core:platform"))
    implementation(project(":feature:onboarding"))
}
