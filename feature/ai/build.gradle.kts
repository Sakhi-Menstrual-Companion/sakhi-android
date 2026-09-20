plugins {
    id("sakhi.android.feature")
}

android {
    namespace = "team.sakhi.android.feature.ai"

    buildFeatures {
        // The Nearby badge mirrors iOS's `#if DEBUG` stand-in count, so it needs
        // `BuildConfig.DEBUG` in this module.
        buildConfig = true
    }
    testOptions {
        // `ChatUiState.sharePdfUri` is `android.net.Uri`, same as `ReportsUiState`'s -- merely
        // referencing that stub-jar class in local JUnit (no Robolectric) throws
        // `RuntimeException("Stub!")` from its static initializer, which poisons the
        // classloader for every other test in the same file. See `:feature:reports`'
        // `ReportsViewModelTest` for the full writeup of this exact issue.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core:platform"))
    implementation(project(":feature:reports"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.google.maps.compose)
}
