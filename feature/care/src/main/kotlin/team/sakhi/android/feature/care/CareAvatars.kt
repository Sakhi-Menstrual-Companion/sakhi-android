package team.sakhi.android.feature.care

import androidx.annotation.DrawableRes

/**
 * The face each person wears in Care Mode.
 *
 * The same five stand-in faces Emergency Assistance uses, so one person looks the same
 * everywhere in the app and on both phones. Which face is theirs is kept on the server now
 * (migration 063), assigned there on first read from a hash of the user id; this holds the
 * same hash for the one case the server cannot answer, which is her own face on her own
 * screen.
 *
 * Stand-ins, never photographs: nobody's real picture is stored or shown.
 */
internal object CareAvatars {

    private val faces = intArrayOf(
        team.sakhi.android.ui.R.drawable.nearby_sakhi_1,
        team.sakhi.android.ui.R.drawable.nearby_sakhi_2,
        team.sakhi.android.ui.R.drawable.nearby_sakhi_3,
        team.sakhi.android.ui.R.drawable.nearby_sakhi_4,
        team.sakhi.android.ui.R.drawable.nearby_sakhi_5,
    )

    /**
     * The artwork carries a wide, uneven transparent margin, so drawn raw a head covers
     * about half its circle. These are the same scales the Emergency face pile uses.
     */
    private val scales = floatArrayOf(1.50f, 1.39f, 1.57f, 1.28f, 1.29f)

    @DrawableRes
    fun drawable(index: Int): Int = faces[((index % faces.size) + faces.size) % faces.size]

    fun scale(index: Int): Float = scales[((index % scales.size) + scales.size) % scales.size]

    /**
     * The server's own rule (`care_avatar_index_for`), for her own face before any round
     * trip: the bytes of the user id added up, modulo five. Kept identical on purpose, so
     * the face she sees for herself is the face her person sees for her.
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
}
