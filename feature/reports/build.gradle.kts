plugins {
    id("sakhi.android.feature")
}

android {
    namespace = "team.sakhi.android.feature.reports"

    testOptions {
        // `ReportsUiState.sharePdfUri` is `android.net.Uri`, and merely referencing that
        // stub-jar class in a local JUnit run (no Robolectric) throws `RuntimeException
        // ("Stub!")` from its static initializer, which then poisons the classloader for
        // every other test in the same file. `isReturnDefaultValues` makes Android stub
        // methods return safe defaults instead, which is all `ReportsViewModelTest` needs.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core:platform"))
    implementation(libs.androidx.core.ktx)
}
