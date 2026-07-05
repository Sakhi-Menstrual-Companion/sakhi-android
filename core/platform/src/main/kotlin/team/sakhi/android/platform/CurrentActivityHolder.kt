package team.sakhi.android.platform

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.fragment.app.FragmentActivity

/**
 * Tracks the current resumed [FragmentActivity] so app-lifetime singletons (like
 * [AndroidBiometricAdapter]) can reach a live Activity without each screen having to
 * inject one manually. Registered once from `Application.onCreate`.
 */
class CurrentActivityHolder : Application.ActivityLifecycleCallbacks {
    var current: FragmentActivity? = null
        private set

    override fun onActivityResumed(activity: Activity) {
        (activity as? FragmentActivity)?.let { current = it }
    }

    override fun onActivityPaused(activity: Activity) {
        if (current === activity) current = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
