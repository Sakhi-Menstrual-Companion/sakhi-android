// Standalone settings for the included build that holds this project's Gradle
// convention plugins. It resolves plugins itself and shares the root version catalog,
// so plugin versions can never drift from the app's.
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
