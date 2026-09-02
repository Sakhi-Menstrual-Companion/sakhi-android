plugins {
    `kotlin-dsl`
}

group = "team.sakhi.buildlogic"

// Must match the Java level every module compiles against (see KotlinAndroid.kt).
// Gradle runs these plugins in its own daemon JVM, so a mismatch here shows up as a
// confusing "compiled by a more recent version of Java" at plugin-apply time rather
// than in the module that actually failed.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    // compileOnly, not implementation: these plugins are supplied by the consuming
    // build at apply time. Bundling them here would put a second copy of AGP on the
    // classpath, which Gradle rejects across a composite build.
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.compiler.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "sakhi.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidApplicationCompose") {
            id = "sakhi.android.application.compose"
            implementationClass = "AndroidApplicationComposeConventionPlugin"
        }
        register("androidLibrary") {
            id = "sakhi.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "sakhi.android.library.compose"
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "sakhi.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
    }
}
