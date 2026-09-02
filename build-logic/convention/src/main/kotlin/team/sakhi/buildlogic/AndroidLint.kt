package team.sakhi.buildlogic

import com.android.build.api.dsl.Lint

/**
 * Where lint is allowed to fail the build.
 *
 * A library module is linted in isolation, without the merged manifest, so any check that
 * depends on app-level context cannot be evaluated there and reports a false positive. That
 * is not hypothetical: `:feature:home` reported four `MissingPermission` ERRORS for
 * `ACCESS_NETWORK_STATE`, which IS declared in `app/src/main/AndroidManifest.xml`. The
 * feature module has no manifest of its own and the ConnectivityManager call comes from
 * SakhiCore, so lint had no way to see the permission from there.
 *
 * The fix is not to disable the check. It is to run it from the one place with enough
 * information: `:app`, with `checkDependencies = true`, which analyses every module against
 * the merged manifest. Libraries still run lint and still print findings, they just do not
 * gate the build on a verdict they cannot reach correctly.
 */
internal fun Lint.configureLibraryLint() {
    // Findings are still reported, and :app re-analyses this module properly.
    abortOnError = false
    checkReleaseBuilds = false
}

internal fun Lint.configureAppLint() {
    // The real gate. Sees the merged manifest, so its verdict is trustworthy.
    checkDependencies = true
    abortOnError = true
    warningsAsErrors = false
    htmlReport = true
    xmlReport = true
}
