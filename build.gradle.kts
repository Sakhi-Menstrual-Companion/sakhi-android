// Top-level build file. No project-wide dependencies here — each module declares
// exactly what it needs from the version catalog (gradle/libs.versions.toml).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.play.publisher) apply false
    alias(libs.plugins.ksp) apply false
    // Screenshot-test durability lane (2026-07-15) -- applied per-module (currently
    // just feature:auth, the first slice), not project-wide, so it stays opt-in.
    alias(libs.plugins.roborazzi) apply false
    // Only applied in :app (needs a real google-services.json, git-ignored there).
    alias(libs.plugins.google.services) apply false
}
