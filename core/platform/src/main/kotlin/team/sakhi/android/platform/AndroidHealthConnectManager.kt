package team.sakhi.android.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.BasalBodyTemperatureRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.MenstruationFlowRecord
import androidx.health.connect.client.records.MenstruationPeriodRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate as JavaLocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import team.sakhi.localdb.SakhiPhaseALocalStore
import team.sakhi.localdb.SharedLocalRecordCodec
import team.sakhi.logging.PeriodLogPolicy
import team.sakhi.models.FlowIntensity
import team.sakhi.models.HealthSample
import team.sakhi.models.LogSource
import team.sakhi.models.PeriodLog
import team.sakhi.repositories.PeriodLogRepository
import team.sakhi.session.SessionContext
import team.sakhi.session.SessionManager
import team.sakhi.sync.DataMigration
import team.sakhi.sync.HealthImportPolicy
import team.sakhi.sync.OfflineUpgradeDataset
import team.sakhi.validation.ValidationRules

private const val HEALTH_CONNECT_PROVIDER_PACKAGE = "com.google.android.apps.healthdata"


/** Declared by every app that integrates with Health Connect. */


private const val HEALTH_PERMISSIONS_RATIONALE_ACTION =


    "androidx.health.connect.action.SHOW_PERMISSIONS_RATIONALE"
private const val HEALTH_CONNECT_LOOKBACK_DAYS = 180L
private const val HEALTH_CONNECT_INSIGHTS_DAYS = 7L
private const val TYPE_SLEEP = "sleep_session"
private const val TYPE_STEPS = "steps"
private const val TYPE_TEMPERATURE = "basal_body_temperature"
private const val KEY_ENABLED = "health_connect.enabled"
private const val KEY_LAST_SYNC_AT = "health_connect.last_sync_at"

enum class HealthConnectAvailability {
    Available,
    NotInstalled,
    NotSupported,
}

data class DailyHealthValue(
    val date: LocalDate,
    val value: Double,
)

data class HealthConnectInsights(
    val sleepEntries: List<DailyHealthValue> = emptyList(),
    val stepEntries: List<DailyHealthValue> = emptyList(),
    val temperatureEntries: List<DailyHealthValue> = emptyList(),
)

data class HealthConnectSyncResult(
    val importedPeriodLogs: Int,
    val importedSleepSamples: Int,
    val importedStepSamples: Int,
    val importedTemperatureSamples: Int,
    val syncedAtIso: String,
)

data class OnboardingHealthConnectImportResult(
    val failureMessage: String?,
    val dateOfBirth: JavaLocalDate? = null,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val lastPeriodDate: JavaLocalDate? = null,
    val periodLength: Int? = null,
    val cycleLength: Int? = null,
) {
    val hasImportedData: Boolean
        get() = dateOfBirth != null ||
            heightCm != null ||
            weightKg != null ||
            lastPeriodDate != null ||
            periodLength != null ||
            cycleLength != null
}

class AndroidHealthConnectManager(
    private val appContext: Context,
    private val sessionManager: SessionManager,
    private val kvStore: team.sakhi.platform.PlatformKeyValueStore,
    private val periodLogRepository: PeriodLogRepository,
    private val localStore: SakhiPhaseALocalStore,
) {
    val requiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(MenstruationFlowRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(BasalBodyTemperatureRecord::class),
    )

    val onboardingRequiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(MenstruationFlowRecord::class),
        HealthPermission.getReadPermission(MenstruationPeriodRecord::class),
        HealthPermission.getReadPermission(HeightRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
    )

    fun availability(): HealthConnectAvailability {
        return when (HealthConnectClient.getSdkStatus(appContext, HEALTH_CONNECT_PROVIDER_PACKAGE)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.Available
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.NotInstalled
            else -> HealthConnectAvailability.NotSupported
        }
    }

    /**
     * Apps installed on this device that actually integrate with Health Connect, i.e.
     * the ones capable of supplying anything worth importing.
     *
     * Discovered by querying for the Health Connect permissions-rationale activity,
     * which every integrating app must declare. This runs BEFORE any permission is
     * granted, which is the whole point: reading records to find contributing apps
     * would require the permission we are trying to decide whether to ask for.
     *
     * Sakhi itself and the Health Connect provider are excluded -- neither is a source
     * the user would recognise as "another app".
     *
     * Used to decide whether offering the import option is meaningful at all. With no
     * such apps the option previously still launched Health Connect's own onboarding,
     * which is a confusing detour to a dead end.
     */
    fun availableSourceApps(): List<HealthConnectSourceApp> {
        val pm = appContext.packageManager
        val rationale = Intent(HEALTH_PERMISSIONS_RATIONALE_ACTION)
        return runCatching {
            pm.queryIntentActivities(rationale, 0)
                .mapNotNull { resolved ->
                    val pkg = resolved.activityInfo?.packageName ?: return@mapNotNull null
                    if (pkg == appContext.packageName || pkg == HEALTH_CONNECT_PROVIDER_PACKAGE) {
                        return@mapNotNull null
                    }
                    HealthConnectSourceApp(
                        packageName = pkg,
                        label = resolved.loadLabel(pm)?.toString().orEmpty().ifBlank { pkg },
                    )
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        }.getOrDefault(emptyList())
    }

    fun isEnabled(): Boolean = kvStore.getBool(KEY_ENABLED, false)

    fun lastSyncedAtIso(): String? = kvStore.get(KEY_LAST_SYNC_AT)?.takeIf { it.isNotBlank() }

    fun disableIntegration() {
        kvStore.setBool(KEY_ENABLED, false)
        kvStore.remove(KEY_LAST_SYNC_AT)
    }

    fun installIntent(): Intent {
        return Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$HEALTH_CONNECT_PROVIDER_PACKAGE"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    suspend fun hasAllPermissions(): Boolean {
        if (availability() != HealthConnectAvailability.Available) return false
        return client().permissionController.getGrantedPermissions().containsAll(requiredPermissions)
    }

    suspend fun hasAllOnboardingPermissions(): Boolean {
        if (availability() != HealthConnectAvailability.Available) return false
        return client().permissionController.getGrantedPermissions().containsAll(onboardingRequiredPermissions)
    }

    suspend fun importOnboardingSnapshot(): OnboardingHealthConnectImportResult {
        return when (availability()) {
            HealthConnectAvailability.NotInstalled -> OnboardingHealthConnectImportResult(
                failureMessage = appContext.getString(R.string.platform_health_connect_not_available),
            )

            HealthConnectAvailability.NotSupported -> OnboardingHealthConnectImportResult(
                failureMessage = appContext.getString(R.string.platform_health_connect_not_supported),
            )

            HealthConnectAvailability.Available -> {
                val granted = client().permissionController.getGrantedPermissions()
                if (!granted.containsAll(onboardingRequiredPermissions)) {
                    return OnboardingHealthConnectImportResult(
                        failureMessage = appContext.getString(R.string.platform_health_connect_access_not_granted),
                    )
                }

                val heightCm = readLatestHeightCm()?.takeIf(ValidationRules::isValidHeightCm)
                val weightKg = readLatestWeightKg()?.takeIf(ValidationRules::isValidWeightKg)
                val periodBlocks = onboardingPeriodBlocks(readOnboardingPeriodDates())
                val latestPeriod = latestOnboardingPeriodBlock(periodBlocks)
                    ?.firstOrNull()
                    ?.takeIf { ValidationRules.isValidLogDate(it.toKmmLocalDate()) }
                val observedPeriodLength = observedOnboardingPeriodLength(periodBlocks)
                val observedCycleLength = observedOnboardingCycleLength(periodBlocks)

                OnboardingHealthConnectImportResult(
                    failureMessage = if (
                        heightCm == null &&
                        weightKg == null &&
                        latestPeriod == null &&
                        observedPeriodLength == null &&
                        observedCycleLength == null
                    ) {
                        appContext.getString(R.string.platform_health_connect_onboarding_no_data)
                    } else {
                        null
                    },
                    heightCm = heightCm,
                    weightKg = weightKg,
                    lastPeriodDate = latestPeriod,
                    periodLength = observedPeriodLength,
                    cycleLength = observedCycleLength,
                )
            }
        }
    }

    suspend fun syncNow(): HealthConnectSyncResult {
        val session = requireOwnSession()
        val granted = client().permissionController.getGrantedPermissions()
        require(granted.containsAll(requiredPermissions)) {
            appContext.getString(R.string.platform_health_connect_permissions_not_granted)
        }

        val nowIso = Clock.System.now().toString()
        val importedFlowRecords = importPeriodFlow(session, nowIso)
        val importedSleep = importSleepSamples(session)
        val importedSteps = importStepSamples(session)
        val importedTemperature = importTemperatureSamples(session)

        kvStore.setBool(KEY_ENABLED, true)
        kvStore.set(KEY_LAST_SYNC_AT, nowIso)

        return HealthConnectSyncResult(
            importedPeriodLogs = importedFlowRecords,
            importedSleepSamples = importedSleep,
            importedStepSamples = importedSteps,
            importedTemperatureSamples = importedTemperature,
            syncedAtIso = nowIso,
        )
    }

    suspend fun loadInsights(): HealthConnectInsights {
        val session = sessionManager.current ?: return HealthConnectInsights()
        val envelopes = localStore.exportRecords(OfflineUpgradeDataset.HEALTH_SAMPLES)
        val samples = envelopes
            .map(SharedLocalRecordCodec::decodeHealthSample)
            .filter { it.userId.equals(session.targetUserId, ignoreCase = true) }

        return HealthConnectInsights(
            sleepEntries = aggregateByDay(samples, TYPE_SLEEP),
            stepEntries = aggregateByDay(samples, TYPE_STEPS),
            temperatureEntries = aggregateByDay(samples, TYPE_TEMPERATURE, average = true),
        )
    }

    private suspend fun importPeriodFlow(
        session: SessionContext,
        nowIso: String,
    ): Int {
        val recordClient = client()
        val timeRange = historicalTimeRange()
        val records = recordClient.readRecords(
            ReadRecordsRequest(
                recordType = MenstruationFlowRecord::class,
                timeRangeFilter = timeRange,
            ),
        ).records
        if (records.isEmpty()) return 0

        val importedDates = records.map(::recordDate).distinct().sorted()
        val latestBlock = latestContiguousBlock(importedDates)

        val existingLogs = periodLogRepository.getForDateRange(
            userId = session.targetUserId,
            from = importedDates.first(),
            to = importedDates.last(),
        ).getOrDefault(emptyList())

        val latestUserPeriodStart = PeriodLogPolicy.effectiveActions(existingLogs)
            .filter { it.loggedBy == LogSource.USER && it.flowIntensity != null }
            .maxByOrNull { it.logDate }
            ?.logDate

        val canonicalByDate = PeriodLogPolicy.effectiveActions(existingLogs)
            .associateBy { it.logDate }

        var importedCount = 0
        records.sortedBy { it.time }.forEach { record ->
            val date = recordDate(record)
            val flow = record.flow.toFlowIntensity() ?: return@forEach

            if (
                latestBlock != null &&
                latestUserPeriodStart != null &&
                HealthImportPolicy.shouldSkipImportDate(
                    date = date,
                    hkBlockStart = latestBlock.first(),
                    hkBlockDates = latestBlock,
                    userPeriodStart = latestUserPeriodStart,
                )
            ) {
                return@forEach
            }

            val merged = mergeImportedPeriodLog(
                userId = session.targetUserId,
                date = date,
                importedFlow = flow,
                canonical = canonicalByDate[date],
                timestampIso = nowIso,
            )
            periodLogRepository.upsert(merged).getOrThrow()
            importedCount += 1
        }

        return importedCount
    }

    private suspend fun importSleepSamples(session: SessionContext): Int {
        val records = client().readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = insightsTimeRange(),
            ),
        ).records

        records.forEach { record ->
            localStore.upsertHealthSample(
                HealthSample(
                    id = stableHealthSampleId(
                        type = TYPE_SLEEP,
                        userId = session.targetUserId,
                        startDate = record.startTime.toString(),
                        endDate = record.endTime.toString(),
                        value = Duration.between(record.startTime, record.endTime).toMinutes() / 60.0,
                    ),
                    userId = session.targetUserId,
                    typeIdentifier = TYPE_SLEEP,
                    unit = "hours",
                    value = Duration.between(record.startTime, record.endTime).toMinutes() / 60.0,
                    startDate = record.startTime.toString(),
                    endDate = record.endTime.toString(),
                    createdAt = Clock.System.now().toString(),
                ),
            )
        }

        return records.size
    }

    private suspend fun importStepSamples(session: SessionContext): Int {
        val records = client().readRecords(
            ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = insightsTimeRange(),
            ),
        ).records

        records.forEach { record ->
            localStore.upsertHealthSample(
                HealthSample(
                    id = stableHealthSampleId(
                        type = TYPE_STEPS,
                        userId = session.targetUserId,
                        startDate = record.startTime.toString(),
                        endDate = record.endTime.toString(),
                        value = record.count.toDouble(),
                    ),
                    userId = session.targetUserId,
                    typeIdentifier = TYPE_STEPS,
                    unit = "count",
                    value = record.count.toDouble(),
                    startDate = record.startTime.toString(),
                    endDate = record.endTime.toString(),
                    createdAt = Clock.System.now().toString(),
                ),
            )
        }

        return records.size
    }

    private suspend fun importTemperatureSamples(session: SessionContext): Int {
        val records = client().readRecords(
            ReadRecordsRequest(
                recordType = BasalBodyTemperatureRecord::class,
                timeRangeFilter = insightsTimeRange(),
            ),
        ).records

        records.forEach { record ->
            localStore.upsertHealthSample(
                HealthSample(
                    id = stableHealthSampleId(
                        type = TYPE_TEMPERATURE,
                        userId = session.targetUserId,
                        startDate = record.time.toString(),
                        endDate = record.time.toString(),
                        value = record.temperature.inCelsius,
                    ),
                    userId = session.targetUserId,
                    typeIdentifier = TYPE_TEMPERATURE,
                    unit = "celsius",
                    value = record.temperature.inCelsius,
                    startDate = record.time.toString(),
                    endDate = record.time.toString(),
                    createdAt = Clock.System.now().toString(),
                ),
            )
        }

        return records.size
    }

    private suspend fun readLatestHeightCm(): Double? {
        val records = client().readRecords(
            ReadRecordsRequest(
                recordType = HeightRecord::class,
                timeRangeFilter = historicalTimeRange(),
            ),
        ).records

        return records
            .maxByOrNull { it.time }
            ?.height
            ?.inMeters
            ?.times(100.0)
    }

    private suspend fun readLatestWeightKg(): Double? {
        val records = client().readRecords(
            ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = historicalTimeRange(),
            ),
        ).records

        return records
            .maxByOrNull { it.time }
            ?.weight
            ?.inKilograms
    }

    private suspend fun readOnboardingPeriodDates(): List<JavaLocalDate> {
        val flowDates = client().readRecords(
            ReadRecordsRequest(
                recordType = MenstruationFlowRecord::class,
                timeRangeFilter = historicalTimeRange(),
            ),
        ).records
            .filter { it.flow.toFlowIntensity() != null }
            .map(::onboardingFlowDate)

        val periodDates = client().readRecords(
            ReadRecordsRequest(
                recordType = MenstruationPeriodRecord::class,
                timeRangeFilter = historicalTimeRange(),
            ),
        ).records
            .flatMap(::onboardingPeriodDates)

        return (flowDates + periodDates).distinct().sorted()
    }

    private fun mergeImportedPeriodLog(
        userId: String,
        date: LocalDate,
        importedFlow: FlowIntensity,
        canonical: PeriodLog?,
        timestampIso: String,
    ): PeriodLog {
        val base = canonical ?: PeriodLog(
            id = DataMigration.stablePeriodLogId(
                userId = userId,
                logDate = date.toString(),
                sourceUserId = userId,
            ),
            userId = userId,
            logDate = date,
            periodPresent = true,
            flowIntensity = importedFlow,
            loggedBy = LogSource.SYSTEM,
            createdByUserId = userId,
            sourceUserId = userId,
            notes = appContext.getString(R.string.platform_health_connect_imported_note),
            createdAt = timestampIso,
            updatedAt = timestampIso,
        )

        return base.copy(
            periodPresent = true,
            flowIntensity = PeriodLogPolicy.strongestFlow(base.flowIntensity, importedFlow),
            loggedBy = if (base.loggedBy == LogSource.USER) LogSource.USER else LogSource.SYSTEM,
            notes = base.notes ?: appContext.getString(R.string.platform_health_connect_imported_note),
            updatedAt = timestampIso,
        )
    }

    private fun aggregateByDay(
        samples: List<HealthSample>,
        typeIdentifier: String,
        average: Boolean = false,
    ): List<DailyHealthValue> {
        return samples
            .filter { it.typeIdentifier == typeIdentifier }
            .groupBy { parseIsoDate(it.startDate) }
            .mapNotNull { (date, entries) ->
                date ?: return@mapNotNull null
                val value = if (average) {
                    entries.map(HealthSample::value).average()
                } else {
                    entries.sumOf(HealthSample::value)
                }
                DailyHealthValue(date = date, value = value)
            }
            .sortedByDescending { it.date }
            .take(HEALTH_CONNECT_INSIGHTS_DAYS.toInt())
    }

    private fun latestContiguousBlock(dates: List<LocalDate>): List<LocalDate>? {
        if (dates.isEmpty()) return null
        val sorted = dates.distinct().sorted()
        val blocks = mutableListOf<MutableList<LocalDate>>()
        var current = mutableListOf(sorted.first())
        for (date in sorted.drop(1)) {
            val previous = current.last()
            if (daysSinceEpoch(date) - daysSinceEpoch(previous) == 1L) {
                current.add(date)
            } else {
                blocks += current
                current = mutableListOf(date)
            }
        }
        blocks += current
        return blocks.last()
    }

    private fun onboardingPeriodBlocks(dates: List<JavaLocalDate>): List<List<JavaLocalDate>> {
        if (dates.isEmpty()) return emptyList()

        val sorted = dates.distinct().sorted()
        val blocks = mutableListOf<MutableList<JavaLocalDate>>()
        var current = mutableListOf(sorted.first())

        for (date in sorted.drop(1)) {
            val previous = current.last()
            if (date.toEpochDay() - previous.toEpochDay() == 1L) {
                current += date
            } else {
                blocks += current
                current = mutableListOf(date)
            }
        }

        blocks += current
        return blocks
    }

    private fun latestOnboardingPeriodBlock(blocks: List<List<JavaLocalDate>>): List<JavaLocalDate>? {
        return blocks.lastOrNull()
    }

    private fun observedOnboardingPeriodLength(blocks: List<List<JavaLocalDate>>): Int? {
        val today = JavaLocalDate.now()
        val completedLengths = blocks.mapNotNull { block ->
            val last = block.lastOrNull() ?: return@mapNotNull null
            if (last >= today) return@mapNotNull null
            val count = block.size
            if (count !in 1..14) return@mapNotNull null
            count
        }
        if (completedLengths.isEmpty()) return null
        return completedLengths.average().roundToInt().takeIf { it in 1..14 }
    }

    private fun observedOnboardingCycleLength(blocks: List<List<JavaLocalDate>>): Int? {
        val starts = blocks.mapNotNull { it.firstOrNull() }.sorted()
        if (starts.size < 2) return null

        val validIntervals = starts
            .zip(starts.drop(1))
            .mapNotNull { (previous, next) ->
                val days = (next.toEpochDay() - previous.toEpochDay()).toInt()
                days.takeIf(ValidationRules::isValidCycleLength)
            }
        if (validIntervals.isEmpty()) return null

        return validIntervals.average().roundToInt().takeIf(ValidationRules::isValidCycleLength)
    }

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(appContext)

    private fun historicalTimeRange(): TimeRangeFilter {
        val end = Instant.now()
        val start = end.minus(Duration.ofDays(HEALTH_CONNECT_LOOKBACK_DAYS))
        return TimeRangeFilter.between(start, end)
    }

    private fun insightsTimeRange(): TimeRangeFilter {
        val end = Instant.now()
        val start = end.minus(Duration.ofDays(HEALTH_CONNECT_INSIGHTS_DAYS))
        return TimeRangeFilter.between(start, end)
    }

    private fun requireOwnSession(): SessionContext {
        val session = requireNotNull(sessionManager.current) {
            appContext.getString(R.string.platform_health_connect_session_not_ready)
        }
        require(session.isViewingOwnData) {
            appContext.getString(R.string.platform_health_connect_own_data_only)
        }
        return session
    }

    private fun recordDate(record: MenstruationFlowRecord): LocalDate {
        val localDate = record.time.atZone(ZoneId.systemDefault()).toLocalDate()
        return LocalDate(localDate.year, localDate.monthValue, localDate.dayOfMonth)
    }

    private fun onboardingFlowDate(record: MenstruationFlowRecord): JavaLocalDate {
        return record.time.atZone(ZoneId.systemDefault()).toLocalDate()
    }

    private fun onboardingPeriodDates(record: MenstruationPeriodRecord): List<JavaLocalDate> {
        val start = record.startTime.atZone(ZoneId.systemDefault()).toLocalDate()
        val end = record.endTime.atZone(ZoneId.systemDefault()).toLocalDate()
        if (end.isBefore(start)) return listOf(start)

        val dates = mutableListOf<JavaLocalDate>()
        var cursor = start
        while (!cursor.isAfter(end)) {
            dates += cursor
            cursor = cursor.plusDays(1)
        }
        return dates
    }

    private fun parseIsoDate(iso: String): LocalDate? {
        return runCatching {
            val localDate = Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate()
            LocalDate(localDate.year, localDate.monthValue, localDate.dayOfMonth)
        }.getOrNull()
    }

    private fun daysSinceEpoch(date: LocalDate): Long {
        return java.time.LocalDate.of(date.year, date.monthNumber, date.dayOfMonth).toEpochDay()
    }

    private fun JavaLocalDate.toKmmLocalDate(): LocalDate = LocalDate(year, monthValue, dayOfMonth)

    private fun stableHealthSampleId(
        type: String,
        userId: String,
        startDate: String,
        endDate: String,
        value: Double,
    ): String {
        return UUID.nameUUIDFromBytes(
            "$type|$userId|$startDate|$endDate|$value".encodeToByteArray(),
        ).toString()
    }

    // Health Connect models `MenstruationFlowRecord.flow` as a plain `Int`
    // constant (`FLOW_LIGHT`/`FLOW_MEDIUM`/`FLOW_HEAVY`/`FLOW_UNKNOWN`), not a
    // nested enum type -- there is no `MenstruationFlowRecord.Flow` class.
    private fun Int.toFlowIntensity(): FlowIntensity? = when (this) {
        MenstruationFlowRecord.FLOW_LIGHT -> FlowIntensity.LIGHT
        MenstruationFlowRecord.FLOW_MEDIUM -> FlowIntensity.MEDIUM
        MenstruationFlowRecord.FLOW_HEAVY -> FlowIntensity.HEAVY
        else -> null
    }
}

/** An installed app that can act as a Health Connect data source for the import step. */
data class HealthConnectSourceApp(
    val packageName: String,
    val label: String,
)
