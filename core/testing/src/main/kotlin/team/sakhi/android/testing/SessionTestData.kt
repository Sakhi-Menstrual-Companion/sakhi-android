package team.sakhi.android.testing

import team.sakhi.models.CarePartnership
import team.sakhi.models.PartnerInvitation
import team.sakhi.models.UserCareRole
import team.sakhi.session.SessionContext
import team.sakhi.session.SessionPermissions

/**
 * Builders for the session objects nearly every ViewModel test needs.
 *
 * `sessionContext(...)` was hand-written in TWELVE test files and `permissions(...)` in five.
 * They were not identical, which is the interesting part and the reason this file documents
 * its defaults rather than just providing them:
 *
 *  - `:feature:home`'s version defaulted `targetUserId` to the literal `"user-1"`, so passing
 *    only a different `userId` produced a PARTNER-viewing session.
 *  - `:feature:reports`' version defaulted `targetUserId = userId` and derived `activeRole`
 *    from whether the two matched, so the same call produced a SELF session.
 *
 * Those are opposite meanings for identical-looking code, and quietly replacing one with the
 * other would change what a test asserts without changing a single assertion line. This
 * builder therefore takes the *reports* behaviour (derive the role from the ids, which is what
 * the real `SessionContext.isViewingOwnData` does) and makes it explicit. Existing tests were
 * migrated only where that matches what they already meant.
 */
fun sessionContext(
    userId: String = "user-1",
    targetUserId: String = userId,
    userName: String = "Test User",
    // Derived, not defaulted to PRIMARY_USER: a session whose target is someone else IS a
    // partner session, and hardcoding the role lets a test construct a state the app cannot
    // actually reach.
    activeRole: UserCareRole = if (userId == targetUserId) {
        UserCareRole.PRIMARY_USER
    } else {
        UserCareRole.PARTNER
    },
    permissions: SessionPermissions = SessionPermissions.primaryUser,
    sentInvitations: List<PartnerInvitation> = emptyList(),
    activePartnership: CarePartnership? = null,
): SessionContext = SessionContext(
    userId = userId,
    userName = userName,
    activeRole = activeRole,
    targetUserId = targetUserId,
    permissions = permissions,
    sentInvitations = sentInvitations,
    activePartnership = activePartnership,
)

/**
 * A permission set that grants everything by default, so a test only names what it wants to
 * take away. Matches the shape the duplicated local copies used.
 *
 * Prefer `SessionPermissions.primaryUser` when a test just needs "a normal owner session";
 * this builder is for the cases that deliberately withhold one capability.
 */
fun permissions(
    canViewPeriodDates: Boolean = true,
    canLogPeriod: Boolean = true,
    canViewSymptoms: Boolean = true,
    canViewMoods: Boolean = true,
    canViewMedications: Boolean = true,
    canViewPredictions: Boolean = true,
    canViewCycleHistory: Boolean = true,
    canViewDailyLogs: Boolean = true,
    canViewOvulationTests: Boolean = true,
    canViewTemperature: Boolean = true,
    canViewWeight: Boolean = true,
    canViewNotes: Boolean = true,
    canViewDischarge: Boolean = true,
    canViewSexualActivity: Boolean = true,
): SessionPermissions = SessionPermissions(
    canViewPeriodDates = canViewPeriodDates,
    canLogPeriod = canLogPeriod,
    canViewSymptoms = canViewSymptoms,
    canViewMoods = canViewMoods,
    canViewMedications = canViewMedications,
    canViewPredictions = canViewPredictions,
    canViewCycleHistory = canViewCycleHistory,
    canViewDailyLogs = canViewDailyLogs,
    canViewOvulationTests = canViewOvulationTests,
    canViewTemperature = canViewTemperature,
    canViewWeight = canViewWeight,
    canViewNotes = canViewNotes,
    canViewDischarge = canViewDischarge,
    canViewSexualActivity = canViewSexualActivity,
)
