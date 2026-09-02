package team.sakhi.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

/**
 * Compose setup shared by every module that draws UI.
 *
 * Two things here were previously repeated by hand in fifteen build files:
 *
 * 1. The Compose BOM plus the same baseline artifact list.
 * 2. The duplicate-Compose-stack exclusions, which appeared 26 times across the module build
 *    files. They are now applied once at the configuration level, see the note inline below,
 *    so a module can declare any koin artifact normally and cannot reintroduce the clash.
 */
internal fun Project.configureAndroidCompose(commonExtension: CommonExtension<*, *, *, *, *, *>) {
    val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

    commonExtension.buildFeatures {
        compose = true
    }

    // Applied to every configuration rather than to the koin dependency that drags them in.
    //
    // koin-compose(-android) depends on org.jetbrains.compose foundation/runtime at a strict
    // 1.8.2 — a SECOND copy of this app's real androidx.compose stack, under the same package
    // names. That is not a version conflict Gradle can resolve, it is two different artifacts
    // claiming the same classes, and it produced a real compile failure once
    // (`Modifier.weight()` resolving against the wrong one).
    //
    // The old fix was a per-dependency `exclude` block, repeated 26 times across the module
    // build files, and every new module had to remember it. Excluding at the configuration
    // level means a module can declare any koin artifact normally and still cannot end up
    // with the duplicate stack. Safe because this is an androidx-Compose app: the JetBrains
    // multiplatform Compose artifacts have no legitimate reason to be on the classpath.
    configurations.configureEach {
        exclude(mapOf("group" to "org.jetbrains.compose.foundation"))
        exclude(mapOf("group" to "org.jetbrains.compose.runtime"))
    }

    extensions.configure<ComposeCompilerGradlePluginExtension> {
        // Names the shared types the Compose compiler should treat as stable. Without it,
        // every SakhiCore type is inferred unstable (SakhiCore has no Compose plugin), which
        // blocks skipping across most of the UI. The file documents its own safety rules and
        // the deliberate exclusions.
        stabilityConfigurationFiles.add(
            rootProject.layout.projectDirectory.file("config/compose-stability.conf"),
        )

        // Opt-in recomposition diagnostics, off by default so normal builds pay nothing:
        //
        //   ./gradlew :feature:home:compileDebugKotlin -PcomposeMetrics
        //
        // Then read build/compose-reports/*-classes.txt for which types resolved stable and
        // *-composables.txt for which composables are skippable. This is the only way to
        // check the stability config rather than assume it.
        if (providers.gradleProperty("composeMetrics").isPresent) {
            val dir = layout.buildDirectory.dir("compose-reports")
            reportsDestination.set(dir)
            metricsDestination.set(dir)
        }
    }

    dependencies {
        val bom = libs.findLibrary("compose-bom").get()
        add("implementation", platform(bom))
        add("androidTestImplementation", platform(bom))

        add("implementation", libs.findLibrary("compose-ui").get())
        add("implementation", libs.findLibrary("compose-ui-graphics").get())
        add("implementation", libs.findLibrary("compose-foundation").get())
        add("implementation", libs.findLibrary("compose-material3").get())
        add("implementation", libs.findLibrary("compose-ui-tooling-preview").get())
        add("debugImplementation", libs.findLibrary("compose-ui-tooling").get())
    }
}
