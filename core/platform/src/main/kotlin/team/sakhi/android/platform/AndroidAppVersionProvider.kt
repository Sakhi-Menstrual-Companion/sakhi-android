package team.sakhi.android.platform

import android.content.Context
import android.content.pm.PackageManager

/** Wraps `PackageManager` version lookup for `UpdateGateController.checkNow`. */
class AndroidAppVersionProvider(private val appContext: Context) {
    val currentVersion: String
        get() = runCatching {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
        }.getOrNull() ?: "0.0.0"
}
