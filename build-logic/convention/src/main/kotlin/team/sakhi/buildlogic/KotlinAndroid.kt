package team.sakhi.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * The single source of truth for every module's SDK levels and Java version.
 *
 * This exists because these values used to be copy-pasted into all 17 module build files,
 * and they drifted: `:app` was raised to `compileSdk 36` on 2026-08-25 for the Play target-API
 * deadline, and all fifteen library modules silently stayed on 35. Nothing warned, because
 * nothing connected them. Changing a number here now changes it everywhere at once.
 *
 * Do not reintroduce a `compileSdk`, `minSdk`, `compileOptions` or `jvmToolchain` line in an
 * individual module. If a module genuinely needs to differ, it should say so out loud with a
 * comment explaining why, the way `:baseline-profile` does for its Macrobenchmark-imposed
 * `minSdk 28`.
 */
object SakhiSdk {
    /**
     * Must be >= [TARGET_SDK]. Play requires targeting API 36 from 2026-08-31, see the
     * `targetSdk` comment in `app/build.gradle.kts`.
     */
    const val COMPILE = 36

    /** Android 8.0. The floor the product supports. */
    const val MIN = 26

    val JAVA = JavaVersion.VERSION_17
    const val JVM_TOOLCHAIN = 17
}

/**
 * Applies the SDK/Java configuration shared by every Android module in the project.
 */
internal fun Project.configureKotlinAndroid(commonExtension: CommonExtension<*, *, *, *, *, *>) {
    commonExtension.apply {
        compileSdk = SakhiSdk.COMPILE

        defaultConfig {
            minSdk = SakhiSdk.MIN
        }

        compileOptions {
            sourceCompatibility = SakhiSdk.JAVA
            targetCompatibility = SakhiSdk.JAVA
        }
    }

    // Emulators Gradle boots itself, so instrumented tests and baseline profile generation
    // need no manually-created AVD. See GradleManagedDevices.kt.
    configureGradleManagedDevices(commonExtension)

    // `jvmToolchain(17)` in a module's `kotlin { }` block resolves to whichever extension
    // that block belongs to. Set on both the Kotlin and Java extensions here so the module
    // build files need neither.
    extensions.configure<KotlinAndroidProjectExtension> {
        jvmToolchain(SakhiSdk.JVM_TOOLCHAIN)
    }
    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(SakhiSdk.JVM_TOOLCHAIN))
    }

    // Sakhi's own lint checks, attached to every Android module so a design-system rule
    // cannot be bypassed simply by working in a module that forgot to opt in. `:lint` is
    // skipped for itself to avoid a circular dependency.
    if (path != ":lint") {
        dependencies {
            add("lintChecks", project(":lint"))
        }
    }
}
