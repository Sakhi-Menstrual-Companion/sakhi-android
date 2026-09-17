package team.sakhi.android.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The sound Sakhi makes when she is not home yet.
 *
 * The same audio file iOS plays, looping on the alarm stream, with a heavy buzz every 1.6
 * seconds so a phone lying face down on a table still says something. This is the one
 * notification in Sakhi allowed to be louder than the rest: the whole feature exists for
 * this moment.
 *
 * Lives in core so both the walk screen that raises the alarm and the Profile screen where
 * her Sakhi chooses the delay can play the same thing, rather than two near-identical
 * copies that drift.
 */
class StayWithMeAlarmPlayer(private val context: Context) {

    private var player: MediaPlayer? = null
    @Volatile private var buzzing = false

    /** True while it is ringing, so a preview button can offer to stop it. */
    val isPlaying: Boolean get() = player != null

    fun start() {
        if (player != null) return
        player = MediaPlayer.create(context, R.raw.sakhi_alarm)?.apply {
            isLooping = true
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            start()
        }
        if (player != null) {
            buzzing = true
            startBuzzing()
        }
    }

    fun stop() {
        buzzing = false
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
    }

    private fun startBuzzing() {
        val vibrator = vibrator() ?: return
        Thread {
            while (buzzing) {
                runCatching {
                    vibrator.vibrate(VibrationEffect.createOneShot(600, VibrationEffect.DEFAULT_AMPLITUDE))
                }
                Thread.sleep(BUZZ_EVERY_MS)
            }
        }.apply { isDaemon = true }.start()
    }

    private fun vibrator(): Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }.getOrNull()

    private companion object {
        /** iOS buzzes on the same beat. */
        const val BUZZ_EVERY_MS = 1_600L
    }
}
