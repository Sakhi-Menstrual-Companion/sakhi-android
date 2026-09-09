package team.sakhi.android.feature.emergency

import android.content.Context
import androidx.core.content.edit

/**
 * Remembers which stand-in face she chose, so it is hers rather than one dealt to her.
 * Port of iOS `SakhiAvatarPreference.swift`.
 *
 * Local only, on purpose. `EmergencyProfileDetail.photoUrl` exists on the server and is
 * deliberately never rendered — the whole avatar system is stand-ins precisely so real
 * faces of women nearby are not on screen. Storing a chosen index here keeps that intact:
 * what syncs is nothing, and the worst case of losing it is that she is dealt the hashed
 * face again, which is what everyone starts with anyway.
 *
 * Keyed per user id, so two accounts on one device do not inherit each other's pick.
 */
object SakhiAvatarPreference {

    private const val PREFS = "sakhi.emergency.avatar"
    private const val KEY_PREFIX = "avatarIndex."

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Her chosen index, or null when she has never picked and the hash still decides. */
    fun chosenIndex(context: Context, userId: String): Int? {
        val p = prefs(context)
        val key = KEY_PREFIX + userId
        return if (p.contains(key)) p.getInt(key, 0) else null
    }

    fun setChosenIndex(context: Context, userId: String, index: Int) {
        prefs(context).edit { putInt(KEY_PREFIX + userId, index) }
    }

    fun clear(context: Context, userId: String) {
        prefs(context).edit { remove(KEY_PREFIX + userId) }
    }
}
