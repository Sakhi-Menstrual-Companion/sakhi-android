package team.sakhi.android.feature.emergency

import android.content.Context
import team.sakhi.android.ui.SakhiFaces

/**
 * Her own pick of face. Kept as a name here so the Emergency screens read the same, but the
 * store is [SakhiFaces], which every other screen reads too: one face per person, app-wide.
 */
object SakhiAvatarPreference {
    fun chosenIndex(context: Context, userId: String): Int? = SakhiFaces.chosenIndex(context, userId)
    fun setChosenIndex(context: Context, userId: String, index: Int) = SakhiFaces.setChosenIndex(context, userId, index)
    fun clear(context: Context, userId: String) = SakhiFaces.clear(context, userId)
}
