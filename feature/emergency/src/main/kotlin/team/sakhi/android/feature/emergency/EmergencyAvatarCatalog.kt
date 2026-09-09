package team.sakhi.android.feature.emergency

import androidx.annotation.DrawableRes

/**
 * Which stand-in face belongs to which Sakhi. Port of iOS `EmergencyAvatarCatalog.swift`.
 *
 * Shared so the map pin, the waiting screen and anywhere else showing a Sakhi agree. They
 * each had their own idea of what she looked like before this, which is worse than no face
 * at all: the woman pinned on the map and the woman being waited on are the same person,
 * and looking different in two places says they are not.
 *
 * **Stand-ins, not photographs.** `photoUrl` exists on the models and is deliberately not
 * used. Before anyone has accepted, the real faces of women nearby have no business on
 * screen, where a glance over her shoulder catches them.
 */
internal object EmergencyAvatarCatalog {

    // Five, not three. The hash below is shared with iOS so one Sakhi gets one face on
    // both platforms -- but that only holds if both sides divide by the same number.
    // Android shipped three drawables against iOS's five, so `% 3` and `% 5` disagreed and
    // the same woman looked different on her friend's phone. That is the exact bug the
    // shared hash exists to prevent.
    private val faces = intArrayOf(
        team.sakhi.android.ui.R.drawable.nearby_sakhi_1,
        team.sakhi.android.ui.R.drawable.nearby_sakhi_2,
        team.sakhi.android.ui.R.drawable.nearby_sakhi_3,
        team.sakhi.android.ui.R.drawable.nearby_sakhi_4,
        team.sakhi.android.ui.R.drawable.nearby_sakhi_5,
    )

    /** How many faces there are to choose between. iOS `EmergencyAvatarCatalog.count`. */
    val count: Int get() = faces.size

    @DrawableRes
    fun drawableAt(index: Int): Int = faces[index % faces.size]

    /**
     * iOS `NearbyFacePile.contentScales`.
     *
     * The artwork carries a wide transparent margin that differs per file, so drawn raw the
     * head covers roughly half the disc and reads as faded rather than small.
     */
    private val contentScales = floatArrayOf(1.50f, 1.39f, 1.57f, 1.28f, 1.29f)

    fun contentScaleAt(index: Int): Float = contentScales[index % contentScales.size]

    fun contentScaleFor(id: String): Float = contentScales[indexFor(id)]

    @DrawableRes
    fun drawableFor(id: String): Int = faces[indexFor(id)]

    /**
     * Her own pick wins over the hash; everyone else is still dealt one.
     *
     * Only her own id can have a preference, so a Sakhi in the nearby list has never
     * stored anything under her id on this device and the hash answers for her exactly as
     * before. iOS `EmergencyAvatarCatalog.imageName(for:)` does the same.
     */
    @DrawableRes
    fun drawableFor(context: android.content.Context, id: String): Int =
        SakhiAvatarPreference.chosenIndex(context, id)?.let { drawableAt(it) } ?: drawableFor(id)

    /**
     * The face she would be dealt with no preference stored. Exposed so her own profile can
     * open with the right one highlighted before she has chosen anything.
     */
    fun dealtIndex(id: String): Int = indexFor(id)

    /**
     * djb2, written out rather than using [String.hashCode].
     *
     * iOS hashes by hand because Swift seeds `String.hashValue` randomly per process, which
     * would deal everyone a new face on every launch. Kotlin's `hashCode` is stable, so that
     * specific hazard does not exist here -- but the *same* algorithm is used deliberately,
     * because it is the only way the two platforms give one Sakhi the same face. A woman who
     * looks different on her friend's Android and her own iPhone is the same bug one step
     * further out.
     */
    private fun indexFor(id: String): Int {
        var hash = 5381L
        for (byte in id.encodeToByteArray()) {
            // `and 0xFF` to match Swift's `String.utf8` giving unsigned bytes; Kotlin's
            // Byte is signed, so anything non-ASCII would otherwise diverge.
            hash = hash * 33 + (byte.toInt() and 0xFF)
        }
        // Swift's `abs(hash) % count` on a 64-bit Int. Masking to 63 bits keeps the value
        // non-negative without overflowing on `abs(Long.MIN_VALUE)`.
        return ((hash and Long.MAX_VALUE) % faces.size).toInt()
    }
}
