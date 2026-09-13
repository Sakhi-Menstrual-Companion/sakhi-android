package team.sakhi.android.feature.care

import androidx.annotation.DrawableRes
import team.sakhi.android.ui.SakhiFaces

/**
 * The face each person wears in Care Mode: [SakhiFaces], the same rule as everywhere else,
 * so one person looks the same on the Emergency map, on her Care screen and on her
 * partner's phone. The other person's index comes from the server (migration 063).
 */
internal object CareAvatars {
    @DrawableRes
    fun drawable(index: Int): Int = SakhiFaces.drawableAt(index)
    fun scale(index: Int): Float = SakhiFaces.scaleAt(index)
    fun indexFor(userId: String): Int = SakhiFaces.indexFor(userId)
    /** Her own face on her own phone: her pick if she made one, else the shared hash. */
    fun selfIndex(context: android.content.Context, userId: String): Int = SakhiFaces.indexFor(context, userId)
}
