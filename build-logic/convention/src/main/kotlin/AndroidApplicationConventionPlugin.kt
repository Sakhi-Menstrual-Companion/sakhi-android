import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import team.sakhi.buildlogic.SakhiSdk
import org.gradle.kotlin.dsl.getByType
import team.sakhi.buildlogic.configureAppLint
import team.sakhi.buildlogic.configureBadgingTasks
import team.sakhi.buildlogic.configureKotlinAndroid

/**
 * Base plugin for the `:app` module.
 *
 * `:app` keeps its own android{} block for the things only it has (applicationId, signing,
 * versioning, Play publishing). What it no longer owns is the SDK/Java configuration, which
 * now comes from `SakhiSdk` like every other module — the drift this was written to end had
 * `:app` on compileSdk 36 while all fifteen libraries sat on 35.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.android")

        extensions.configure<ApplicationExtension> {
            configureKotlinAndroid(this)
            lint { configureAppLint() }
            // Only an application declares this. Play requires API 36 from 2026-08-31; see
            // the versioning notes in app/build.gradle.kts.
            defaultConfig.targetSdk = SakhiSdk.COMPILE
        }

        // Locks the shipped manifest (permissions, targetSdk, versionCode) against a
        // checked-in golden file. See Badging.kt for why this matters here specifically.
        configureBadgingTasks(extensions.getByType<ApplicationAndroidComponentsExtension>())
    }
}
