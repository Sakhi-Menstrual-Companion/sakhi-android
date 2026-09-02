import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import team.sakhi.buildlogic.configureAndroidCompose

/**
 * Adds Compose to a library module: the compiler plugin, the BOM and baseline artifacts,
 * and the shared stability configuration.
 *
 * Apply alongside `sakhi.android.library` (or `sakhi.android.feature`, which already
 * includes the base).
 */
class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        extensions.configure<LibraryExtension> {
            configureAndroidCompose(this)
        }
    }
}
