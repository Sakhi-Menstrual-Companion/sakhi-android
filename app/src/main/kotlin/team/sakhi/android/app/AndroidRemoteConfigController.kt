package team.sakhi.android.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import team.sakhi.config.AppConfig
import team.sakhi.config.RemoteConfigStore
import team.sakhi.session.SessionManager

/**
 * Decides *when* Android asks the server what its configuration should be. Counterpart of
 * iOS `RemoteConfigController.swift`; the configuration itself lives in KMM
 * [RemoteConfigStore], shared with iOS.
 *
 * Android had none of this. `appModule()` registered the store and the resolver already read
 * it, but nothing ever called `refresh()` -- so every flag sat on its compiled-in default
 * for ever and the kill switch, which is the reason the feature exists, did nothing here.
 *
 * Three moments, and deliberately no others:
 *
 *  - launch, once DI is up
 *  - returning to the foreground, at most once every [MIN_INTERVAL_MS]
 *  - signing in or out, because that changes who the caller is
 *
 * There is no polling. A flag flipping under a woman mid-flow -- a screen vanishing while
 * she is using it -- is worse than her getting the change a few minutes later, and one of
 * the things behind these flags is Emergency Assistance.
 */
class AndroidRemoteConfigController(
    private val application: Application,
    private val store: RemoteConfigStore,
    private val sessionManager: SessionManager,
    private val versionName: String,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastAttemptMs: Long = 0
    private var startedActivities = 0
    private var started = false

    fun start() {
        if (started) return
        started = true

        // The server gates on this for `min_app_version`. The store reads it through a
        // lambda, so setting it here rather than before construction is safe.
        AppConfig.appVersion = versionName

        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                // 0 -> 1 is the app coming to the foreground. Counting rather than using
                // ProcessLifecycleOwner keeps this off a new dependency, and an Activity
                // recreation on rotation does not read as a foreground because the count
                // never reaches zero in between.
                if (startedActivities++ == 0) refreshIfStale()
            }

            override fun onActivityStopped(activity: Activity) {
                if (startedActivities > 0) startedActivities--
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })

        // Signing in or out changes the merged view: overrides only apply to a real account,
        // and the rollout subject switches between her user id and the anonymous client id.
        scope.launch {
            sessionManager.session
                .map { it?.userId }
                .distinctUntilChanged()
                // The first emission is the session that already existed at launch, which
                // the refresh below covers.
                .drop(1)
                .collect { userId ->
                    // Signing out has to clear before it re-fetches: the cached snapshot may
                    // hold a value set for that one account, and it must not follow whoever
                    // signs in on this device next.
                    if (userId == null) store.clear()
                    refresh()
                }
        }

        scope.launch { refresh() }
    }

    private fun refreshIfStale() {
        val now = System.currentTimeMillis()
        if (now - lastAttemptMs < MIN_INTERVAL_MS) return
        scope.launch { refresh() }
    }

    /**
     * Fetches and replaces the snapshot.
     *
     * A failure is not an error state and is never surfaced: the previous snapshot stays
     * exactly as it was and the app keeps running on cache or on the compiled-in defaults.
     * Config being unreachable must not be something a woman sees.
     */
    private suspend fun refresh() {
        lastAttemptMs = System.currentTimeMillis()
        store.refresh()
    }

    private companion object {
        /** Foregrounds closer together than this reuse the snapshot already in memory. */
        const val MIN_INTERVAL_MS = 5 * 60 * 1000L
    }
}
