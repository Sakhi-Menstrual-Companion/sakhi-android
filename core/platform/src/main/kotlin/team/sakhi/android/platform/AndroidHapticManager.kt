package team.sakhi.android.platform

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import team.sakhi.platform.PlatformKeyValueStore
import team.sakhi.preferences.UserPreferenceDefaults
import team.sakhi.preferences.UserPreferenceKeys

enum class HapticImpact { LIGHT, MEDIUM, HEAVY }
enum class HapticNotification { SUCCESS, WARNING, ERROR }

/**
 * Ports iOS `HapticManager.swift`: same API shape (`impact`/`success`/
 * `warning`/`error`/`selection`), same persisted-preference gate
 * (`HAPTICS_ENABLED`, shared KMM key). Android has no per-style haptic
 * generator API like `UIImpactFeedbackGenerator` -- approximated with
 * `VibrationEffect` duration/amplitude (API 26+, this app's minSdk), with a
 * legacy plain-duration fallback for pre-O (dead code at minSdk 26, kept only
 * because `Vibrator.vibrate(Long)` requires it structurally).
 */
class AndroidHapticManager(
    private val appContext: Context,
    private val kvStore: PlatformKeyValueStore,
) {
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private val isEnabled: Boolean
        get() = kvStore.getBool(UserPreferenceKeys.HAPTICS_ENABLED, UserPreferenceDefaults.HAPTICS_ENABLED)

    fun impact(style: HapticImpact = HapticImpact.MEDIUM) {
        if (!isEnabled) return
        val (durationMs, amplitude) = when (style) {
            HapticImpact.LIGHT -> 10L to 80
            HapticImpact.MEDIUM -> 20L to 150
            HapticImpact.HEAVY -> 30L to 255
        }
        vibrateOneShot(durationMs, amplitude)
    }

    fun success() = notification(HapticNotification.SUCCESS)
    fun warning() = notification(HapticNotification.WARNING)
    fun error() = notification(HapticNotification.ERROR)

    fun notification(type: HapticNotification) {
        if (!isEnabled) return
        when (type) {
            HapticNotification.SUCCESS -> vibrateOneShot(15L, 180)
            HapticNotification.WARNING -> vibratePattern(longArrayOf(0, 20, 40, 20))
            HapticNotification.ERROR -> vibratePattern(longArrayOf(0, 30, 50, 30, 50, 30))
        }
    }

    fun selection() {
        if (!isEnabled) return
        vibrateOneShot(8L, 60)
    }

    private fun vibrateOneShot(durationMs: Long, amplitude: Int) {
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(durationMs)
        }
    }

    private fun vibratePattern(pattern: LongArray) {
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(pattern, -1)
        }
    }
}
