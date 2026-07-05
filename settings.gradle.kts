rootProject.name = "SakhiAndroid"

pluginManagement {
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

include(
    ":app",
    ":core:common",
    ":core:designsystem",
    ":core:ui",
    ":core:platform",
    ":feature:auth",
    ":feature:onboarding",
    ":feature:home",
    ":feature:calendar",
    ":feature:logging",
    ":feature:care",
    ":feature:ai",
    ":feature:recommendations",
    ":feature:profile",
    ":feature:reports",
)
