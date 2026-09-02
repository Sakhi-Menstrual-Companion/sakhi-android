package team.sakhi.buildlogic

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import javax.inject.Inject
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.process.ExecOperations

/**
 * Dumps what the built bundle actually declares: permissions, targetSdk, versionCode,
 * launchable activities, supported screens.
 */
@CacheableTask
abstract class GenerateBadgingTask : DefaultTask() {

    @get:OutputFile
    abstract val badging: RegularFileProperty

    @get:PathSensitive(PathSensitivity.NONE)
    @get:InputFile
    abstract val apk: RegularFileProperty

    @get:PathSensitive(PathSensitivity.NONE)
    @get:InputFile
    abstract val aapt2Executable: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun taskAction() {
        badging.get().asFile.parentFile.mkdirs()
        badging.get().asFile.outputStream().use { out ->
            execOperations.exec {
                commandLine(
                    aapt2Executable.get().asFile.absolutePath,
                    "dump",
                    "badging",
                    apk.get().asFile.absolutePath,
                )
                standardOutput = out
            }
        }
    }
}

/**
 * Fails if the built bundle's manifest no longer matches the checked-in golden file.
 *
 * This is a privacy and store-compliance guard, not a style check. Sakhi is a women's health
 * and safety app that already declares ACCESS_COARSE_LOCATION, READ_CONTACTS and
 * DETECT_SCREEN_CAPTURE. A permission added by a transitive dependency, or a targetSdk that
 * quietly moves, currently reaches the store with nobody having reviewed it. That has already
 * happened once: Play flagged this app as non-compliant on target API before anyone noticed.
 *
 * A diff here is not necessarily a bug. It means "a human should look at this before it
 * ships", which for permissions is exactly the right bar.
 */
@CacheableTask
abstract class CheckBadgingTask : DefaultTask() {

    // A task with no declared output always re-runs. This one is never read.
    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @get:PathSensitive(PathSensitivity.NONE)
    @get:InputFile
    abstract val goldenBadging: RegularFileProperty

    @get:PathSensitive(PathSensitivity.NONE)
    @get:InputFile
    abstract val generatedBadging: RegularFileProperty

    @get:Input
    abstract val updateBadgingTaskName: Property<String>

    override fun getGroup(): String = LifecycleBasePlugin.VERIFICATION_GROUP

    @TaskAction
    fun taskAction() {
        val golden = goldenBadging.get().asFile.readLines()
        val generated = generatedBadging.get().asFile.readLines()
        if (golden == generated) return

        // Report the actual differing lines. "files are not equal" on a 200-line manifest
        // dump tells the reader nothing about whether a permission just appeared.
        val added = generated - golden.toSet()
        val removed = golden - generated.toSet()

        val detail = buildString {
            if (added.isNotEmpty()) {
                appendLine("  ADDED (present in the new build, not in the golden file):")
                added.forEach { appendLine("    + $it") }
            }
            if (removed.isNotEmpty()) {
                appendLine("  REMOVED (in the golden file, gone from the new build):")
                removed.forEach { appendLine("    - $it") }
            }
        }

        throw GradleException(
            "The app manifest changed.\n\n" +
                detail +
                "\nCheck this deliberately, especially any uses-permission or targetSdk line: " +
                "a permission Sakhi did not intend to ship is a privacy problem and a store " +
                "review problem.\n\nIf the change IS intended, accept it with:\n" +
                "  ./gradlew ${updateBadgingTaskName.get()}\n",
        )
    }
}

/**
 * Locates `aapt2` in the installed SDK.
 *
 * AGP only exposes `sdkComponents.aapt2` from a later version than the 8.6.1 pinned here, and
 * bumping AGP drags the Gradle wrapper plus both KMM composite builds with it (Gradle rejects
 * mixed AGP versions across a composite build). Resolving it from the SDK avoids that for the
 * sake of one file path.
 *
 * Picks the HIGHEST installed build-tools rather than a pinned one, because the badging output
 * format is stable across versions and pinning would break on any machine without that exact
 * version installed.
 */
private fun Project.resolveAapt2(): File {
    val sdkDir = listOfNotNull(
        providers.gradleProperty("sdk.dir").orNull,
        System.getenv("ANDROID_HOME"),
        System.getenv("ANDROID_SDK_ROOT"),
        rootProject.file("local.properties")
            .takeIf { it.exists() }
            ?.readLines()
            ?.firstOrNull { it.startsWith("sdk.dir=") }
            ?.substringAfter("sdk.dir="),
    ).firstOrNull()?.let(::File)
        ?: throw GradleException(
            "Could not find the Android SDK. Set sdk.dir in local.properties or ANDROID_HOME.",
        )

    val buildTools = File(sdkDir, "build-tools")
    val newest = buildTools.listFiles()
        ?.filter { File(it, "aapt2").canExecute() }
        ?.maxByOrNull { it.name }
        ?: throw GradleException("No build-tools with aapt2 found under $buildTools")

    return File(newest, "aapt2")
}

/**
 * Wires the badging tasks for the RELEASE variant only.
 *
 * Deliberately not every variant, unlike the upstream sample this is based on. Sakhi's
 * variant list includes the baseline-profile plugin's generated `nonMinifiedRelease` and
 * `benchmarkRelease`, so registering per-variant would mean four golden files that must all
 * be regenerated together, three of which never reach a user. `release` is the artifact that
 * ships, and it is the only one whose permissions matter.
 */
internal fun Project.configureBadgingTasks(
    componentsExtension: ApplicationAndroidComponentsExtension,
) {
    componentsExtension.onVariants { variant ->
        if (variant.name != "release") return@onVariants

        val generateBadging = tasks.register<GenerateBadgingTask>("generateReleaseBadging") {
            apk.set(variant.artifacts.get(SingleArtifact.APK_FROM_BUNDLE))
            aapt2Executable.set(resolveAapt2())
            badging.set(
                layout.buildDirectory.file("outputs/apk_from_bundle/release/release-badging.txt"),
            )
        }

        val updateBadgingTaskName = "updateReleaseBadging"
        tasks.register<Copy>(updateBadgingTaskName) {
            from(generateBadging.map(GenerateBadgingTask::badging))
            into(layout.projectDirectory)
        }

        tasks.register<CheckBadgingTask>("checkReleaseBadging") {
            goldenBadging.set(layout.projectDirectory.file("release-badging.txt"))
            generatedBadging.set(generateBadging.flatMap(GenerateBadgingTask::badging))
            this.updateBadgingTaskName.set(updateBadgingTaskName)
            output.set(layout.buildDirectory.dir("intermediates/checkReleaseBadging"))
        }
    }
}
