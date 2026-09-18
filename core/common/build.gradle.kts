plugins {
    id("sakhi.android.library")
}

android {
    namespace = "team.sakhi.android.common"
}

dependencies {
    api("team.sakhi:SakhiCore:1.0.0")
    // The shared cycle detector, the same module iOS links as
    // PredictionSDK.xcframework. `api` so the engine's models stay visible to any
    // module that consumes a coordinator result, matching how SakhiCore is exposed.
    api("team.sakhi:PredictionSDK:1.0.0")

    implementation(libs.kermit)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    // api, not implementation: SakhiCore's own public functions return kotlinx.datetime
    // types (LocalDate etc.) directly, and SakhiCore itself only depends on it via
    // `implementation` (hidden from consumers), so every module touching cycle/period
    // dates needs this on its own classpath too — declared once here, flows to every
    // :feature:* module via :core:common instead of repeating per module (verified by
    // a real "Cannot access class 'LocalDate'" build failure in :feature:home).
    api("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
}
