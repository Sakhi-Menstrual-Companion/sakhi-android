rootProject.name = "SakhiAndroid"

pluginManagement {
    // The project's own Gradle convention plugins. Must be included HERE, in
    // pluginManagement, not as a regular composite build: the modules apply these by id,
    // which Gradle resolves before the normal build graph exists.
    includeBuild("build-logic")

    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

// SakhiCore (KMM) is the shared brain: business logic, stores, repositories,
// Room KMP database, and the Koin DI graph. Android never forks its rules.
includeBuild("../00-Shared/SakhiCore")

// PredictionSDK (KMM) is the shared cycle *detector* — `SakhiPredictionEngine`,
// including `detectCycles(logs)`. iOS has always linked BOTH shared frameworks
// (`SakhiCore.xcframework` + `PredictionSDK.xcframework`, see 01-iOS's
// project.pbxproj; 79 Swift files `import SakhiCore`, 5 `import PredictionSDK`).
// Android only ever included SakhiCore, so nothing on this platform could reach
// the detector at all — which is why logging a period saved the log row but never
// produced a CycleData, leaving Home permanently on "Track your first period".
// Adding it here is the Gradle equivalent of iOS linking the second framework.
includeBuild("../00-Shared/02-Prediction-Engine")

include(
    ":app",
    ":baseline-profile",
    // Custom lint checks that enforce the design system, see lint/README or the
    // detectors themselves for what each rule catches.
    ":lint",
    ":core:common",
    ":core:designsystem",
    ":core:ui",
    ":core:platform",
    // Shared test infrastructure: the main-dispatcher rule and session builders that were
    // otherwise copy-pasted into every ViewModel test.
    ":core:testing",
    ":feature:auth",
    ":feature:onboarding",
    ":feature:home",
    ":feature:calendar",
    ":feature:logging",
    ":feature:care",
    ":feature:emergency",
    ":feature:ai",
    ":feature:recommendations",
    ":feature:profile",
    ":feature:reports",
)
