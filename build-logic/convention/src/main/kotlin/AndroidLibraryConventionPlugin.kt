import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import team.sakhi.buildlogic.configureKotlinAndroid
import team.sakhi.buildlogic.configureLibraryLint

/**
 * Base plugin for every non-Compose Android library module.
 *
 * Replaces the android{} block that used to be copy-pasted into each one: compileSdk,
 * minSdk, compileOptions and jvmToolchain now come from `SakhiSdk` in one place.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.android")

        extensions.configure<LibraryExtension> {
            configureKotlinAndroid(this)
            lint { configureLibraryLint() }
            // Library modules never set their own targetSdk; the app decides that, and a
            // library declaring one only produces a lint warning about it being ignored.
            testOptions.unitTests.isIncludeAndroidResources = true
        }
    }
}
