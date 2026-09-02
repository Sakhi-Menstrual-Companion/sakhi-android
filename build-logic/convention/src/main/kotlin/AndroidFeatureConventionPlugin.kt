import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.project
import team.sakhi.buildlogic.configureAndroidCompose
import team.sakhi.buildlogic.configureKotlinAndroid
import team.sakhi.buildlogic.configureLibraryLint

/**
 * Everything a `:feature:*` module needs, so a feature build file can be a namespace and its
 * own extras.
 *
 * The dependency list below is not a guess: each entry was present in ALL ELEVEN feature
 * modules before this plugin existed, so declaring them here removes real duplication rather
 * than inventing a shared surface. Deliberately NOT included:
 *
 *  - `:core:platform` — in 10 of 11, so `:feature:emergency` and friends still ask for it.
 *  - `kermit` — only 3 of 11 actually log.
 *  - roborazzi / screenshot testing — only 4 of 11 opted in, and it is a per-module choice.
 *
 * The point is that a module still declares what makes it different. It just no longer
 * repeats what every module has always shared.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.android")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        extensions.configure<LibraryExtension> {
            configureKotlinAndroid(this)
            lint { configureLibraryLint() }
            configureAndroidCompose(this)
            // Robolectric and the screenshot tests need real resources on the unit-test
            // classpath. Previously set by hand in the modules that remembered to.
            testOptions.unitTests.isIncludeAndroidResources = true
        }

        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

        dependencies {
            // The shared brain. Every feature reads its models and repositories.
            add("implementation", "team.sakhi:SakhiCore:1.0.0")

            add("implementation", project(":core:common"))
            add("implementation", project(":core:designsystem"))
            add("implementation", project(":core:ui"))

            add("implementation", libs.findLibrary("compose-material-icons-extended").get())
            add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
            add("implementation", libs.findLibrary("androidx-lifecycle-runtime-compose").get())

            // Brings junit + coroutines-test transitively (declared `api` there), plus the
            // MainDispatcherRule and session builders that were duplicated across 14 files.
            add("testImplementation", project(":core:testing"))
            add("testImplementation", libs.findLibrary("mockk").get())

            // No exclusions needed here: configureAndroidCompose excludes the duplicate
            // JetBrains Compose stack at the configuration level, so it cannot slip in.
            add("implementation", libs.findLibrary("koin-androidx-compose").get())
        }
    }
}
