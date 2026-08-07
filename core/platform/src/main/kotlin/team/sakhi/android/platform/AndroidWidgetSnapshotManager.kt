package team.sakhi.android.platform

import team.sakhi.cycle.CyclePhaseInsight
import team.sakhi.android.common.CycleInsightAdapter
import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import org.json.JSONObject
import team.sakhi.cycle.CycleMath
import team.sakhi.date.DateConverter
import team.sakhi.design.SakhiColors
import team.sakhi.logging.PeriodLogPolicy
import team.sakhi.models.CyclePhase
import team.sakhi.models.FlowIntensity
import team.sakhi.models.LogSource
import team.sakhi.models.PeriodLog
import team.sakhi.models.UserCareRole
import team.sakhi.preferences.UserPreferenceKeys
import team.sakhi.repositories.CycleDataRepository
import team.sakhi.repositories.PeriodLogRepository
import team.sakhi.session.Permission
import team.sakhi.session.SessionContext
import team.sakhi.session.SessionManager
import team.sakhi.platform.PlatformKeyValueStore
import java.util.UUID

/**
 * Android-side equivalent of iOS `WidgetDataManager`: persists a small widget
 * snapshot built from the same real session + repository state the app uses,
 * and drains widget "log today" taps through the real period-log repository
 * instead of a second write path.
 *
 * Important constraint discovered while tracing Android's actual storage tonight:
 * `SakhiPhaseALocalStore` is NOT yet the canonical mirror of all normal app writes
 * the way iOS's Realm store is, so reading widget data from it right now would be
 * incorrect. Until Android's whole product data path is fully local-first, the safe
 * source of truth is the existing repositories plus the active `SessionManager`
 * context, then caching a widget-ready snapshot for the Glance surface to render.
 */
class AndroidWidgetSnapshotManager(
    private val appContext: Context,
    private val sessionManager: SessionManager,
    private val cycleDataRepository: CycleDataRepository,
    private val periodLogRepository: PeriodLogRepository,
    private val kvStore: PlatformKeyValueStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    fun start() {
        if (started) return
        started = true

        scope.launch {
            sessionManager.session.collectLatest { session ->
                if (session == null) {
                    clearSnapshot()
                    updateWidget()
                } else {
                    drainPendingWidgetLogs(session)
                    refreshNow(session)
                }
            }
        }
    }

    fun refreshAsync() {
        scope.launch { refreshNow(sessionManager.current) }
    }

    fun handleWidgetLogTodayDeepLink(date: LocalDate = DateConverter.today()) {
        enqueuePendingLog(date)
        scope.launch { drainPendingWidgetLogs(sessionManager.current) }
    }

    private suspend fun refreshNow(session: SessionContext?) {
        if (session == null) {
            clearSnapshot()
            updateWidget()
            return
        }

        val targetUserId = resolvedWidgetTargetUserId(session)
        val usingPartnerData = targetUserId != session.userId
        val cycle = cycleDataRepository.getLatest(targetUserId).getOrNull() ?: return
        val today = DateConverter.today()
        val todayLogs = periodLogRepository.getForDateRange(targetUserId, today, today).getOrNull() ?: return
        val effectiveToday = PeriodLogPolicy.latestAction(todayLogs)
        val hasLoggedToday = effectiveToday?.hasPeriod == true

        // Same shared engine Home's hero uses. Previously this computed phase and
        // countdown from `CycleMath` while Home used `CyclePhaseInsight`, so the widget
        // could show a different phase than the app for the same day -- and
        // `CycleMath.daysUntilNextPeriod` returns null for an in-progress cycle, so the
        // widget's countdown was frequently blank when the app's was not.
        val allCycles = cycleDataRepository.getAll(targetUserId).getOrDefault(emptyList())
        val periodLogDates = periodLogRepository.getAll(targetUserId)
            .getOrDefault(emptyList())
            .filter { it.periodPresent }
            .mapTo(mutableSetOf()) { it.logDate }
        val insight = CycleInsightAdapter.insightFor(
            date = today,
            cycles = allCycles.ifEmpty { listOf(cycle) },
            periodLogDates = periodLogDates,
            stats = CycleMath.computeStatistics(allCycles.filter { it.isComplete }),
        )
        val phase = when (insight.phase.kind) {
            CyclePhaseInsight.PhaseKind.MENSTRUAL -> CyclePhase.MENSTRUAL
            CyclePhaseInsight.PhaseKind.FOLLICULAR -> CyclePhase.FOLLICULAR
            CyclePhaseInsight.PhaseKind.OVULATION -> CyclePhase.OVULATION
            CyclePhaseInsight.PhaseKind.LUTEAL, CyclePhaseInsight.PhaseKind.PMS -> CyclePhase.LUTEAL
            CyclePhaseInsight.PhaseKind.DELAYED -> CyclePhase.DELAYED
            CyclePhaseInsight.PhaseKind.UNKNOWN -> CyclePhase.UNKNOWN
        }
        val dayInCycle = insight.phase.cycleDay
        val cycleLength = cycle.cycleLength
        val daysUntilNextPeriod = insight.prediction.daysUntil.takeIf {
            insight.prediction.status == CyclePhaseInsight.PredictionStatusKind.UPCOMING
        }
        val canLog = if (usingPartnerData) session.can(Permission.LOG_PERIOD) else true
        val hero = heroText(
            phase = phase,
            hasCycleData = true,
            dayInCycle = dayInCycle,
            cycleLength = cycleLength,
            daysUntilNextPeriod = daysUntilNextPeriod,
            isPartnerMode = usingPartnerData,
            hasLoggedToday = hasLoggedToday,
        )
        val showLogButton = canLog && shouldShowLogButton(phase, daysUntilNextPeriod)
        val themePhase = if (phase == CyclePhase.UNKNOWN) CyclePhase.FOLLICULAR else phase
        val light = SakhiColors.resolved(false).forPhase(themePhase)
        val dark = SakhiColors.resolved(true).forPhase(themePhase)

        saveSnapshot(
            WidgetSnapshot(
                phase = phase.value,
                heading = hero.big,
                subtitle = hero.sub,
                showLogButton = showLogButton,
                isTodayLogged = hasLoggedToday,
                backgroundLight = light.bgMid,
                backgroundDark = dark.bgMid,
                primaryLight = light.primary,
                primaryDark = dark.primary,
                secondaryLight = light.secondary,
                secondaryDark = dark.secondary,
                accentLight = light.ring,
                accentDark = dark.ring,
            ),
        )
        updateWidget()
    }

    private suspend fun drainPendingWidgetLogs(session: SessionContext?) {
        val dates = drainPendingLogs()
        if (dates.isEmpty()) return

        val liveSession = session ?: sessionManager.current
        if (liveSession == null) {
            dates.forEach(::enqueuePendingLog)
            return
        }

        val targetUserId = resolvedWidgetTargetUserId(liveSession)
        val usingPartnerData = targetUserId != liveSession.userId
        if (usingPartnerData && !liveSession.can(Permission.LOG_PERIOD)) {
            dates.forEach(::enqueuePendingLog)
            return
        }

        val actorUserId = liveSession.userId
        val source = if (usingPartnerData) liveSession.toCareViewerLogSource() else LogSource.USER
        val nowIso = Clock.System.now().toString()
        var wroteAny = false

        for (date in dates) {
            val sameDayLogs = periodLogRepository.getForDateRange(targetUserId, date, date).getOrElse {
                enqueuePendingLog(date)
                emptyList()
            }
            if (sameDayLogs.isEmpty() && kvStore.get(KEY_PENDING_WIDGET_LOGS)?.contains(DateConverter.localDateToPlainString(date)) == true) {
                continue
            }

            if (usingPartnerData && !PeriodLogPolicy.canCareViewerMutate(sameDayLogs, actorUserId)) {
                enqueuePendingLog(date)
                continue
            }

            if (PeriodLogPolicy.latestAction(sameDayLogs)?.hasPeriod == true) {
                continue
            }

            val sourceLog = sameDayLogs
                .filter { it.sourceUserId.equals(actorUserId, ignoreCase = true) }
                .let(PeriodLogPolicy::latestAction)

            val merged = mergeWidgetLog(
                existing = sourceLog,
                targetUserId = targetUserId,
                actorUserId = actorUserId,
                date = date,
                source = source,
                timestampIso = nowIso,
            )

            periodLogRepository.upsert(merged).onSuccess {
                wroteAny = true
            }.onFailure {
                enqueuePendingLog(date)
            }
        }

        if (wroteAny) {
            refreshNow(liveSession)
        } else {
            updateWidget()
        }
    }

    private fun mergeWidgetLog(
        existing: PeriodLog?,
        targetUserId: String,
        actorUserId: String,
        date: LocalDate,
        source: LogSource,
        timestampIso: String,
    ): PeriodLog {
        return (existing ?: PeriodLog(
            id = UUID.randomUUID().toString(),
            userId = targetUserId,
            logDate = date,
            periodPresent = true,
            createdByUserId = actorUserId,
            sourceUserId = actorUserId,
            loggedBy = source,
            createdAt = timestampIso,
            updatedAt = timestampIso,
        )).copy(
            userId = targetUserId,
            logDate = date,
            periodPresent = true,
            flowIntensity = PeriodLogPolicy.strongestFlow(existing?.flowIntensity, FlowIntensity.LIGHT),
            loggedBy = source,
            createdByUserId = existing?.createdByUserId ?: actorUserId,
            sourceUserId = actorUserId,
            updatedAt = timestampIso,
        )
    }

    private fun resolvedWidgetTargetUserId(session: SessionContext): String {
        return if (!session.isViewingOwnData && session.can(Permission.VIEW_PREDICTIONS)) {
            session.targetUserId
        } else {
            session.userId
        }
    }

    private fun SessionContext.toCareViewerLogSource(): LogSource = when (activeRole) {
        UserCareRole.PARTNER -> LogSource.PARTNER
        UserCareRole.MOTHER -> LogSource.MOTHER
        UserCareRole.FATHER -> LogSource.FATHER
        else -> LogSource.PARENT
    }

    private fun heroText(
        phase: CyclePhase,
        hasCycleData: Boolean,
        dayInCycle: Int?,
        cycleLength: Int?,
        daysUntilNextPeriod: Int?,
        isPartnerMode: Boolean,
        hasLoggedToday: Boolean,
    ): HeroText {
        if (!hasCycleData) {
            return HeroText(
                "",
                appContext.getString(
                    if (isPartnerMode) {
                        R.string.widget_subtitle_she_hasnt_started_tracking
                    } else {
                        R.string.widget_subtitle_start_tracking_today
                    },
                ),
            )
        }

        return when (phase) {
            CyclePhase.MENSTRUAL -> {
                val day = dayInCycle ?: 1
                HeroText(
                    appContext.getString(R.string.widget_hero_cycle_day, day),
                    appContext.getString(
                        if (hasLoggedToday) {
                            if (isPartnerMode) {
                                R.string.widget_subtitle_of_her_period
                            } else {
                                R.string.widget_subtitle_of_your_period
                            }
                        } else {
                            if (isPartnerMode) {
                                R.string.widget_subtitle_of_her_expected_period
                            } else {
                                R.string.widget_subtitle_of_your_expected_period
                            }
                        },
                    ),
                )
            }
            CyclePhase.DELAYED -> {
                val daysDelayed = ((dayInCycle ?: 0) - (cycleLength ?: 0)).coerceAtLeast(1)
                HeroText(
                    quantityString(R.plurals.widget_day_count, daysDelayed),
                    appContext.getString(
                        if (isPartnerMode) {
                            R.string.widget_subtitle_her_period_delayed
                        } else {
                            R.string.widget_subtitle_period_delayed
                        },
                    ),
                )
            }
            else -> {
                val daysUntil = daysUntilNextPeriod
                if (daysUntil == null) {
                    HeroText(
                        "",
                        appContext.getString(
                            if (isPartnerMode) {
                                R.string.widget_subtitle_she_hasnt_started_tracking
                            } else {
                                R.string.widget_subtitle_start_tracking_today
                            },
                        ),
                    )
                } else {
                    val clamped = daysUntil.coerceAtLeast(0)
                    HeroText(
                        quantityString(R.plurals.widget_day_count, clamped),
                        appContext.getString(
                            if (isPartnerMode) {
                                R.string.widget_subtitle_until_her_next_period
                            } else {
                                R.string.widget_subtitle_until_next_period
                            },
                        ),
                    )
                }
            }
        }
    }

    private fun quantityString(@PluralsRes id: Int, quantity: Int): String {
        return appContext.resources.getQuantityString(id, quantity, quantity)
    }

    private fun shouldShowLogButton(phase: CyclePhase, daysUntilNextPeriod: Int?): Boolean {
        return phase == CyclePhase.MENSTRUAL || phase == CyclePhase.DELAYED || daysUntilNextPeriod == 0
    }

    private fun saveSnapshot(snapshot: WidgetSnapshot) {
        kvStore.set(KEY_WIDGET_SNAPSHOT, snapshot.toJson().toString())
    }

    private fun clearSnapshot() {
        kvStore.remove(KEY_WIDGET_SNAPSHOT)
    }

    private fun enqueuePendingLog(date: LocalDate) {
        val pending = pendingLogs().toMutableSet()
        pending += DateConverter.localDateToPlainString(date)
        kvStore.set(KEY_PENDING_WIDGET_LOGS, pending.sorted().joinToString(","))
    }

    private fun drainPendingLogs(): List<LocalDate> {
        val pending = pendingLogs()
        kvStore.remove(KEY_PENDING_WIDGET_LOGS)
        return pending
            .mapNotNull(DateConverter::isoToLocalDate)
            .sorted()
    }

    private fun pendingLogs(): List<String> {
        return kvStore.get(KEY_PENDING_WIDGET_LOGS)
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            .orEmpty()
    }

    private suspend fun updateWidget() {
        runCatching { SakhiPeriodWidget().updateAll(appContext) }
    }

    companion object {
        const val WIDGET_LOG_TODAY_URL = "sakhi://widget/log-today"
        private const val KEY_WIDGET_SNAPSHOT = UserPreferenceKeys.WIDGET_SNAPSHOT
        private const val KEY_PENDING_WIDGET_LOGS = UserPreferenceKeys.WIDGET_PENDING_LOGS

        fun loadSnapshot(context: Context): WidgetSnapshot? {
            val kvStore = PlatformKeyValueStore().apply { init(context) }
            val raw = kvStore.get(KEY_WIDGET_SNAPSHOT) ?: return null
            val fallback = WidgetSnapshot.placeholder(context)
            return runCatching { WidgetSnapshot.fromJson(JSONObject(raw), fallback) }.getOrNull()
        }
    }
}

private data class HeroText(val big: String, val sub: String)

data class WidgetSnapshot(
    val phase: String,
    val heading: String,
    val subtitle: String,
    val showLogButton: Boolean,
    val isTodayLogged: Boolean,
    val backgroundLight: String,
    val backgroundDark: String,
    val primaryLight: String,
    val primaryDark: String,
    val secondaryLight: String,
    val secondaryDark: String,
    val accentLight: String,
    val accentDark: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("phase", phase)
        .put("heading", heading)
        .put("subtitle", subtitle)
        .put("showLogButton", showLogButton)
        .put("isTodayLogged", isTodayLogged)
        .put("backgroundLight", backgroundLight)
        .put("backgroundDark", backgroundDark)
        .put("primaryLight", primaryLight)
        .put("primaryDark", primaryDark)
        .put("secondaryLight", secondaryLight)
        .put("secondaryDark", secondaryDark)
        .put("accentLight", accentLight)
        .put("accentDark", accentDark)

    companion object {
        fun placeholder(context: Context): WidgetSnapshot = WidgetSnapshot(
            phase = CyclePhase.FOLLICULAR.value,
            heading = context.resources.getQuantityString(R.plurals.widget_day_count, 5, 5),
            subtitle = context.getString(R.string.widget_subtitle_until_next_period),
            showLogButton = false,
            isTodayLogged = false,
            backgroundLight = "#FCEBF1",
            backgroundDark = "#1A0E14",
            primaryLight = "#BB2968",
            primaryDark = "#F6A9C8",
            secondaryLight = "#9A6A80",
            secondaryDark = "#C9A5B4",
            accentLight = "#F61887",
            accentDark = "#F61887",
        )

        fun fromJson(json: JSONObject, fallback: WidgetSnapshot): WidgetSnapshot = WidgetSnapshot(
            phase = json.optString("phase", fallback.phase),
            heading = json.optString("heading", fallback.heading),
            subtitle = json.optString("subtitle", fallback.subtitle),
            showLogButton = json.optBoolean("showLogButton", fallback.showLogButton),
            isTodayLogged = json.optBoolean("isTodayLogged", fallback.isTodayLogged),
            backgroundLight = json.optString("backgroundLight", fallback.backgroundLight),
            backgroundDark = json.optString("backgroundDark", fallback.backgroundDark),
            primaryLight = json.optString("primaryLight", fallback.primaryLight),
            primaryDark = json.optString("primaryDark", fallback.primaryDark),
            secondaryLight = json.optString("secondaryLight", fallback.secondaryLight),
            secondaryDark = json.optString("secondaryDark", fallback.secondaryDark),
            accentLight = json.optString("accentLight", fallback.accentLight),
            accentDark = json.optString("accentDark", fallback.accentDark),
        )
    }
}
