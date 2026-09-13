package team.sakhi.android.ui

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.core.content.edit

/**
 * The one rule for which stand-in face a person wears, everywhere in the app.
 *
 * Before this there were two. Emergency dealt faces with a djb2 hash of the user id, Care
 * used the server's (migration 063: the sixteen bytes of the id added up, modulo five). The
 * same woman could look like one face on the Emergency map, another on her Care screen, and
 * a third on her partner's phone. This is the server's rule, so every screen and both phones
 * land on the same face:
 *
 *  - her own pick, when she has made one on this phone ([chosenIndex]);
 *  - otherwise the server's hash of her id ([indexFor]).
 *
 * Stand-ins, never photographs: nobody's real picture is stored or shown.
 */
object SakhiFaces {

    private val faces = intArrayOf(
        R.drawable.nearby_sakhi_1,
        R.drawable.nearby_sakhi_2,
        R.drawable.nearby_sakhi_3,
        R.drawable.nearby_sakhi_4,
        R.drawable.nearby_sakhi_5,
    )

    /**
     * The artwork carries a wide, uneven transparent margin, so drawn raw a head covers about
     * half its circle. iOS `NearbyFacePile.contentScales`.
     */
    private val scales = floatArrayOf(1.50f, 1.39f, 1.57f, 1.28f, 1.29f)

    val count: Int get() = faces.size

    @DrawableRes
    fun drawableAt(index: Int): Int = faces[wrap(index)]

    fun scaleAt(index: Int): Float = scales[wrap(index)]

    /**
     * The server's `care_avatar_index_for`: the bytes of the user id added up, modulo five.
     * Kept identical on purpose, so the face she sees for herself is the face her person is
     * sent for her.
     */
    fun indexFor(userId: String): Int {
        val hex = userId.replace("-", "")
        if (hex.length < 32) return 0
        var sum = 0
        for (i in 0 until 16) {
            sum += hex.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: 0
        }
        return sum % faces.size
    }

    /** Her pick if she made one, the shared hash if not. */
    fun indexFor(context: Context, userId: String): Int =
        chosenIndex(context, userId) ?: indexFor(userId)

    // ── Her own pick ─────────────────────────────────────────────────────────
    //
    // Same file and key the Emergency profile has always written, so a face she picked
    // before this still holds.

    private const val PREFS = "sakhi.emergency.avatar"
    private const val KEY_PREFIX = "avatarIndex."

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Her chosen index, or null when she has never picked and the hash still decides. */
    fun chosenIndex(context: Context, userId: String): Int? {
        val p = prefs(context)
        val key = KEY_PREFIX + userId
        return if (p.contains(key)) p.getInt(key, 0) else null
    }

    fun setChosenIndex(context: Context, userId: String, index: Int) {
        prefs(context).edit { putInt(KEY_PREFIX + userId, wrap(index)) }
    }

    fun clear(context: Context, userId: String) {
        prefs(context).edit { remove(KEY_PREFIX + userId) }
    }

    private fun wrap(index: Int): Int = ((index % faces.size) + faces.size) % faces.size
}
