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

    // Everything below defers to [SakhiFaces], the app-wide rule. This used to deal faces
    // with its own djb2 hash, which disagreed with the server's (migration 063), so the same
    // woman had one face on the Emergency map and another on her Care screen.

    /** How many faces there are to choose between. iOS `EmergencyAvatarCatalog.count`. */
    val count: Int get() = team.sakhi.android.ui.SakhiFaces.count

    @DrawableRes
    fun drawableAt(index: Int): Int = team.sakhi.android.ui.SakhiFaces.drawableAt(index)

    fun contentScaleAt(index: Int): Float = team.sakhi.android.ui.SakhiFaces.scaleAt(index)

    fun contentScaleFor(id: String): Float = contentScaleAt(dealtIndex(id))

    @DrawableRes
    fun drawableFor(id: String): Int = drawableAt(dealtIndex(id))

    /** Her own pick wins over the hash; everyone else is still dealt one. */
    @DrawableRes
    fun drawableFor(context: android.content.Context, id: String): Int =
        drawableAt(team.sakhi.android.ui.SakhiFaces.indexFor(context, id))

    /** The face she would be dealt with no preference stored. */
    fun dealtIndex(id: String): Int = team.sakhi.android.ui.SakhiFaces.indexFor(id)
}
