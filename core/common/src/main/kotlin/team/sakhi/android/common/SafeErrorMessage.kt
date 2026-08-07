package team.sakhi.android.common

import android.content.Context
import androidx.annotation.StringRes
import java.io.IOException

/**
 * Turns a [Throwable] into something safe to show a user.
 *
 * **Never put `throwable.message` straight into UI state.** Supabase/Ktor exceptions
 * embed the full request URL, the `Authorization: Bearer …` header and the apikey in
 * their message, and Postgres errors leak column/type internals
 * (`invalid input syntax for type uuid: "offline_…"`). All of that was rendering
 * verbatim as red text on Home on a real device. Sakhi handles sensitive health data,
 * so backend detail must not reach the screen at all.
 *
 * Callers pass their own [fallbackRes] so each screen keeps its specific copy
 * ("Failed to load cycle data", "Couldn't refresh care status", …); this only decides
 * between that copy and the shared offline copy, and never returns backend text.
 *
 * The raw cause is still worth logging at the call site for debugging -- log it, do not
 * display it.
 */
fun Throwable.toSafeUserMessage(
    context: Context,
    @StringRes fallbackRes: Int,
): String = if (isOfflineFailure()) {
    context.getString(R.string.common_error_offline)
} else {
    context.getString(fallbackRes)
}

/**
 * True when the failure is a connectivity problem rather than a real backend rejection,
 * so the UI can say "you're offline" instead of a generic failure. Walks the cause chain
 * because Ktor/Supabase wrap the underlying [IOException]; depth-capped so a cyclic
 * cause chain cannot spin.
 */
fun Throwable.isOfflineFailure(): Boolean {
    var t: Throwable? = this
    var depth = 0
    while (t != null && depth < 8) {
        if (t is IOException) return true
        t = t.cause
        depth++
    }
    return false
}
