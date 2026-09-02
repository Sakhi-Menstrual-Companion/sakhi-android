import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

// Lint runs inside Android Studio and the Gradle lint task, both of which are on Java 17
// here, but lint's own API targets 11. Matching NIA rather than raising it: a custom check
// compiled above the lint API's own target can fail to load in older tooling.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    compileOnly(libs.lint.api)
    testImplementation(libs.junit)
    testImplementation(libs.lint.checks)
    testImplementation(libs.lint.tests)
}

// How lint finds the checks in this jar. Without this the module compiles, the checks are
// registered nowhere, and every rule silently does nothing.
tasks.jar {
    manifest {
        attributes("Lint-Registry-v2" to "team.sakhi.android.lint.SakhiIssueRegistry")
    }
}
