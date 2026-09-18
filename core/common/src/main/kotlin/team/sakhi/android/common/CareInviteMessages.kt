package team.sakhi.android.common

import android.content.Context
import team.sakhi.repositories.CareInviteException

/**
 * The written answer for the two one-woman-one-Sakhi refusals, or null for any other failure.
 *
 * These are a person's situation, not a fault, so they get their own sentence instead of the
 * generic "unable to accept". Both the onboarding join and the Care tab join call this, so the
 * two screens cannot drift apart.
 */
fun Throwable.oneSakhiRefusalMessage(context: Context): String? =
    when ((this as? CareInviteException)?.reason) {
        CareInviteException.Reason.PARTNER_ALREADY_CONNECTED ->
            context.getString(R.string.common_error_partner_already_connected)
        CareInviteException.Reason.PRIMARY_ALREADY_CONNECTED ->
            context.getString(R.string.common_error_primary_already_connected)
        else -> null
    }
