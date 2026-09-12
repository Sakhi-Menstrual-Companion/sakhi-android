package team.sakhi.android.feature.profile

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.koinInject
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.sakhiSystemBackground
import team.sakhi.android.designsystem.toComposeColor
import team.sakhi.android.ui.DetailSheetScaffold
import team.sakhi.android.ui.EmptyState
import team.sakhi.date.DateConverter
import team.sakhi.design.SakhiUIColors
import team.sakhi.logging.Mood
import team.sakhi.logging.Symptom
import team.sakhi.models.FlowIntensity
import team.sakhi.models.LogSource
import team.sakhi.models.PeriodLog
import team.sakhi.repositories.PeriodLogRepository
import team.sakhi.session.SessionManager
import kotlinx.datetime.LocalDate as KLocalDate
import java.time.Instant as JavaInstant
import java.time.LocalDate as JLocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiTertiaryLabel
import team.sakhi.android.common.toSafeUserMessage
import team.sakhi.android.ui.SakhiListDivider

/**
 * Ports iOS `ActivityLogView.swift`'s real per-field audit-trail ledger: month
 * headers, one card per logged day, one row per field-level change with an
 * icon, title, "Added/Removed/Updated by <name>" attribution, and a time.
 *
 * iOS's ledger switches on its own local Swift `buildHistoryEntry`'s field
 * names ("Period Flow", "Symptoms", "Mood", ...) -- that function is NOT
 * shared KMM code (confirmed by reading `LoggingViewModel.swift` directly),
 * it's a parallel, iOS-only implementation. Android's `LoggingViewModel`
 * already uses the real shared `LogDiffer.buildHistoryEntry` (KMM), which
 * produces a different, its own key scheme ("period_present",
 * "flow_intensity", "notes", "symptoms_added"/"symptoms_removed",
 * "moods_added"/"moods_removed", "medications"). This ledger is built against
 * that real shared format, not a port of iOS's non-shared Swift key strings --
 * matching the *behavior* (a real per-field change ledger with the same
 * grouping/attribution/icon structure), not byte-identical field names that
 * were never shared in the first place.
 */
@Composable
fun ActivityLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sessionManager = koinInject<SessionManager>()
    val periodLogRepository = koinInject<PeriodLogRepository>()
    var logs by remember { mutableStateOf<List<PeriodLog>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        // Real bug found in this session's own critical self-review (same class as
        // MyDataViewModel's `targetUserId` bug): iOS's real `ActivityLogView.swift`
        // reads `DataManager.shared.currentUserID` -- the signed-in device owner's
        // own id, never whoever a partner is viewing in care mode. Not currently
        // reachable by a partner in the live UI today (the only wired nav route,
        // `ProfileSheetScreen.LogHistory`, sits behind `!isPartnerRole` in
        // `profileSettingGroups`; Home's own tap targets for this screen are still
        // unwired no-op placeholders in both branches) -- fixing anyway as defense
        // in depth, since this screen has zero permission gating of its own and
        // would otherwise become a live leak the moment either entry point is wired.
        val userId = sessionManager.current?.userId
        if (userId == null) {
            isLoading = false
            return@LaunchedEffect
        }
        periodLogRepository.getAll(userId)
            .onSuccess { logs = it }
            .onFailure { error = it.toSafeUserMessage(context, R.string.profile_activity_load_failed) }
        isLoading = false
    }

    val dayEntries = remember(logs, context) {
        logs.sortedByDescending { it.logDate.toString() }
            .mapNotNull { log ->
                val rows = buildRows(log, context)
                if (rows.isEmpty()) null else DayEntry(log, rows)
            }
    }
    val groupedEntries = remember(dayEntries) { groupByMonth(dayEntries) }

    DetailSheetScaffold(
        title = stringResource(R.string.profile_item_log_history),
        subtitle = stringResource(R.string.profile_activity_header_subtitle),
        headerIcon = Icons.Filled.CalendarMonth,
        onBack = onBack,
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
                error?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                if (groupedEntries.isEmpty() && error == null) {
                    Surface(
                        color = sakhiSystemBackground(),
                        shape = RoundedCornerShape(SakhiRadius.lg),
                        tonalElevation = SakhiSpacing.space1,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        EmptyState(
                            title = stringResource(R.string.profile_activity_empty_title),
                            subtitle = stringResource(R.string.profile_activity_empty_subtitle),
                            icon = {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.CalendarMonth,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
                groupedEntries.forEach { group ->
                    MonthHeader(group.monthDate)
                    group.days.forEach { entry -> DayBlock(entry) }
                }
            }
        }
    }
}

private data class LedgerRow(
    val id: String,
    val title: String,
    val attribution: String,
    val timestampIso: String,
    val icon: ImageVector,
    val accent: Color,
    val isExternal: Boolean,
)

private data class DayEntry(val log: PeriodLog, val rows: List<LedgerRow>)
private data class MonthGroup(val key: String, val monthDate: KLocalDate, val days: List<DayEntry>)

private val ActivityLogRowBadgeSize = 36.dp
private val ActivityLogRowGlyphSize = 14.dp
private val ActivityLogRowTitleSize = 14.sp
private val ActivityLogRowMetaSize = 11.sp
private val ActivityLogMonthHeaderSize = 11.sp
private val ActivityLogMonthHeaderLetterSpacing = 1.sp
private val ActivityLogDayTitleSize = 15.sp
private val ActivityLogDayMetaSize = 11.sp
private val ActivityLogEntriesPillHorizontalPadding = 8.dp
private val ActivityLogEntriesPillVerticalPadding = 3.dp

private fun groupByMonth(entries: List<DayEntry>): List<MonthGroup> {
    val buckets = LinkedHashMap<String, MutableList<DayEntry>>()
    for (entry in entries) {
        val key = "${entry.log.logDate.year}-${entry.log.logDate.monthNumber}"
        buckets.getOrPut(key) { mutableListOf() }.add(entry)
    }
    return buckets.map { (key, days) -> MonthGroup(key, days.first().log.logDate, days) }
}

@Composable
private fun MonthHeader(date: KLocalDate) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = SakhiSpacing.space5, bottom = SakhiSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
    ) {
        Text(
            text = monthYearLabel(date),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = ActivityLogMonthHeaderSize,
                letterSpacing = ActivityLogMonthHeaderLetterSpacing,
            ),
            color = sakhiSecondaryLabel(),
        )
        SakhiListDivider(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DayBlock(entry: DayEntry) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.padding(bottom = SakhiSpacing.space3),
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space1),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (entry.log.periodPresent) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = dayHeaderLabel(context, entry.log.logDate),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = ActivityLogDayTitleSize,
                        ),
                        color = if (entry.log.periodPresent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    if (isRelativeDate(entry.log.logDate)) {
                        Text(
                            text = fullDateLabel(entry.log.logDate),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = ActivityLogDayMetaSize),
                            color = sakhiSecondaryLabel(),
                        )
                    }
                }
            }
            Surface(
                shape = RoundedCornerShape(SakhiRadius.full),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            ) {
                Text(
                    text = pluralStringResource(
                        R.plurals.profile_activity_entries_count,
                        entry.rows.size,
                        entry.rows.size,
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = ActivityLogDayMetaSize),
                    color = sakhiSecondaryLabel(),
                    modifier = Modifier.padding(
                        horizontal = ActivityLogEntriesPillHorizontalPadding,
                        vertical = ActivityLogEntriesPillVerticalPadding,
                    ),
                )
            }
        }

        Surface(
            color = sakhiSystemBackground(),
            shape = RoundedCornerShape(SakhiRadius.lg),
            tonalElevation = SakhiSpacing.space1,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                entry.rows.forEachIndexed { index, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = row.accent.copy(alpha = 0.12f),
                            modifier = Modifier.size(ActivityLogRowBadgeSize),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = row.icon,
                                    contentDescription = null,
                                    tint = row.accent,
                                    modifier = Modifier.size(ActivityLogRowGlyphSize),
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = row.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = ActivityLogRowTitleSize,
                                ),
                            )
                            Text(
                                text = row.attribution,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = ActivityLogRowMetaSize),
                                color = if (row.isExternal) {
                                    SakhiUIColors.ACT_EXTERNAL.toComposeColor()
                                } else {
                                    sakhiSecondaryLabel()
                                },
                            )
                        }
                        Text(
                            text = shortTime(row.timestampIso),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = ActivityLogRowMetaSize),
                            color = sakhiSecondaryLabel(),
                        )
                    }
                    if (index != entry.rows.lastIndex) {
                        SakhiListDivider(startInset = SakhiSpacing.space4 + 36.dp + SakhiSpacing.space3)
                    }
                }
            }
        }
    }
}

// ── Row building against the real shared `LogDiffer` key scheme ────────────

private fun buildRows(log: PeriodLog, context: Context): List<LedgerRow> {
    if (log.history.isEmpty()) return synthesizedRows(log, context)
    val rows = mutableListOf<LedgerRow>()
    val isExternal = log.loggedBy.isExternalContributor()
    // Shared KMM history entries currently persist only the actor user id
    // (`changedBy`), not a `LogSource`. Each Android period-log record is still
    // source-scoped (`loggedBy`/`sourceUserId`), so the stable, profile-owner
    // perspective lives on the parent log today.
    val byName = sourceName(context, log.loggedBy)
    for (entry in log.history.asReversed()) {
        entry.changes.forEach { (field, value) ->
            rows += ledgerRowsFor(context, field, value, byName, entry.timestamp, isExternal)
        }
    }
    return rows
}

private fun ledgerRowsFor(
    context: Context,
    field: String,
    value: String,
    byName: String,
    timestampIso: String,
    isExternal: Boolean,
): List<LedgerRow> {
    val id = "$timestampIso-$field"
    return when (field) {
        "period_present" -> listOf(
            ledgerRow(
                id = id,
                title = context.getString(R.string.profile_activity_field_period),
                attribution = context.getString(R.string.profile_activity_updated_by, byName),
                timestampIso = timestampIso,
                icon = Icons.Filled.WaterDrop,
                accent = SakhiUIColors.ACT_HEALTH.toComposeColor(),
                isExternal = isExternal,
            ),
        )
        "flow_intensity" -> {
            val newValue = value.substringAfter("-> ").trim()
            val removed = newValue == "null"
            val display = FlowIntensity.entries.firstOrNull { it.value == newValue }?.displayName
            listOf(
                ledgerRow(
                    id = id,
                    title = if (removed || display == null) {
                        context.getString(R.string.profile_activity_field_flow)
                    } else {
                        context.getString(R.string.profile_activity_flow_title, display)
                    },
                    attribution = context.getString(
                        if (removed) R.string.profile_activity_removed_by else R.string.profile_activity_updated_by,
                        byName,
                    ),
                    timestampIso = timestampIso,
                    icon = Icons.Filled.WaterDrop,
                    accent = SakhiUIColors.ACT_HEALTH.toComposeColor(),
                    isExternal = isExternal,
                ),
            )
        }
        "notes" -> listOf(
            ledgerRow(
                id = id,
                title = context.getString(R.string.profile_activity_field_notes),
                attribution = context.getString(R.string.profile_activity_updated_by, byName),
                timestampIso = timestampIso,
                icon = Icons.Filled.Description,
                accent = SakhiUIColors.ACT_NOTES.toComposeColor(),
                isExternal = isExternal,
            ),
        )
        "symptoms_added" -> value.split(",").filter { it.isNotBlank() }.map { name ->
            ledgerRow(
                id = "$id-$name",
                title = symptomDisplayName(name),
                attribution = context.getString(R.string.profile_activity_added_by, byName),
                timestampIso = timestampIso,
                icon = Icons.Filled.Favorite,
                accent = SakhiUIColors.ACT_HEALTH.toComposeColor(),
                isExternal = isExternal,
            )
        }
        "symptoms_removed" -> value.split(",").filter { it.isNotBlank() }.map { name ->
            ledgerRow(
                id = "$id-$name",
                title = symptomDisplayName(name),
                attribution = context.getString(R.string.profile_activity_removed_by, byName),
                timestampIso = timestampIso,
                icon = Icons.Filled.Favorite,
                accent = SakhiUIColors.ACT_HEALTH.toComposeColor(),
                isExternal = isExternal,
            )
        }
        "moods_added" -> value.split(",").filter { it.isNotBlank() }.map { name ->
            ledgerRow(
                id = "$id-$name",
                title = moodDisplayName(name),
                attribution = context.getString(R.string.profile_activity_added_by, byName),
                timestampIso = timestampIso,
                icon = Icons.Filled.SentimentSatisfied,
                accent = SakhiUIColors.ACT_MOOD.toComposeColor(),
                isExternal = isExternal,
            )
        }
        "moods_removed" -> value.split(",").filter { it.isNotBlank() }.map { name ->
            ledgerRow(
                id = "$id-$name",
                title = moodDisplayName(name),
                attribution = context.getString(R.string.profile_activity_removed_by, byName),
                timestampIso = timestampIso,
                icon = Icons.Filled.SentimentSatisfied,
                accent = SakhiUIColors.ACT_MOOD.toComposeColor(),
                isExternal = isExternal,
            )
        }
        "medications" -> listOf(
            ledgerRow(
                id = id,
                title = context.getString(R.string.profile_activity_field_medications),
                attribution = context.getString(R.string.profile_activity_updated_by, byName),
                timestampIso = timestampIso,
                icon = Icons.Filled.Medication,
                accent = SakhiUIColors.ACT_MEDICATION.toComposeColor(),
                isExternal = isExternal,
            ),
        )
        else -> emptyList()
    }
}

private fun synthesizedRows(log: PeriodLog, context: Context): List<LedgerRow> {
    val attribution = context.getString(R.string.profile_activity_added_by, sourceName(context, log.loggedBy))
    val isExternal = log.loggedBy.isExternalContributor()
    val ts = log.updatedAt.ifBlank { log.createdAt }
    val rows = mutableListOf<LedgerRow>()
    if (log.periodPresent) {
        log.flowIntensity?.let {
            rows += ledgerRow(
                id = "flow",
                title = context.getString(R.string.profile_activity_flow_title, it.displayName),
                attribution = attribution,
                timestampIso = ts,
                icon = Icons.Filled.WaterDrop,
                accent = SakhiUIColors.ACT_HEALTH.toComposeColor(),
                isExternal = isExternal,
            )
        }
    }
    log.symptoms.filterNot { it.startsWith("_") }.forEach { name ->
        rows += ledgerRow(
            id = "s-$name",
            title = symptomDisplayName(name),
            attribution = attribution,
            timestampIso = ts,
            icon = Icons.Filled.Favorite,
            accent = SakhiUIColors.ACT_HEALTH.toComposeColor(),
            isExternal = isExternal,
        )
    }
    log.moods.forEach { name ->
        rows += ledgerRow(
            id = "m-$name",
            title = moodDisplayName(name),
            attribution = attribution,
            timestampIso = ts,
            icon = Icons.Filled.SentimentSatisfied,
            accent = SakhiUIColors.ACT_MOOD.toComposeColor(),
            isExternal = isExternal,
        )
    }
    log.notes?.takeIf { it.isNotBlank() }?.let { notes ->
        val preview = if (notes.length > 45) notes.take(45) + "…" else notes
        rows += ledgerRow(
            id = "note",
            title = preview,
            attribution = attribution,
            timestampIso = ts,
            icon = Icons.Filled.Description,
            accent = SakhiUIColors.ACT_NOTES.toComposeColor(),
            isExternal = isExternal,
        )
    }
    return rows
}

private fun ledgerRow(
    id: String,
    title: String,
    attribution: String,
    timestampIso: String,
    icon: ImageVector,
    accent: Color,
    isExternal: Boolean,
): LedgerRow = LedgerRow(
    id = id,
    title = title,
    attribution = attribution,
    timestampIso = timestampIso,
    icon = icon,
    accent = accent,
    isExternal = isExternal,
)

private fun symptomDisplayName(value: String): String = Symptom.from(value)?.displayName ?: value
private fun moodDisplayName(value: String): String = Mood.from(value)?.displayName ?: value

private fun sourceName(context: Context, source: LogSource): String = when (source) {
    LogSource.USER -> context.getString(R.string.profile_activity_source_you)
    LogSource.PARTNER -> context.getString(R.string.profile_activity_source_partner)
    LogSource.MOTHER -> context.getString(R.string.profile_activity_source_mother)
    LogSource.FATHER -> context.getString(R.string.profile_activity_source_father)
    LogSource.PARENT -> context.getString(R.string.profile_activity_source_parent)
    LogSource.SYSTEM -> context.getString(R.string.profile_activity_source_sakhi)
}

private fun LogSource.isExternalContributor(): Boolean = when (this) {
    LogSource.PARTNER,
    LogSource.MOTHER,
    LogSource.FATHER,
    LogSource.PARENT,
    -> true
    LogSource.USER,
    LogSource.SYSTEM,
    -> false
}

// ── Formatters ───────────────────────────────────────────────────────────────

@Composable
private fun monthYearLabel(date: KLocalDate): String {
    val locale = Locale.getDefault()
    return DateTimeFormatter.ofPattern(
        LocalContext.current.getString(R.string.profile_activity_month_year_format),
        locale,
    )
        .format(date.toJavaLocalDate())
        .uppercase(locale)
}

@Composable
private fun fullDateLabel(date: KLocalDate): String =
    DateTimeFormatter.ofPattern(
        LocalContext.current.getString(R.string.profile_activity_full_date_format),
        Locale.getDefault(),
    ).format(date.toJavaLocalDate())

private fun dayHeaderLabel(context: Context, date: KLocalDate): String {
    val today = DateConverter.today()
    val yesterday = DateConverter.subtractDays(today, 1)
    return when (date) {
        today -> context.getString(R.string.profile_activity_today)
        yesterday -> context.getString(R.string.profile_activity_yesterday)
        else -> DateTimeFormatter.ofPattern(
            context.getString(R.string.profile_activity_day_header_date_format),
            Locale.getDefault(),
        ).format(date.toJavaLocalDate())
    }
}

private fun isRelativeDate(date: KLocalDate): Boolean {
    val today = DateConverter.today()
    val yesterday = DateConverter.subtractDays(today, 1)
    return date == today || date == yesterday
}

private fun shortTime(timestampIso: String): String {
    val instant = runCatching { JavaInstant.parse(timestampIso) }.getOrNull() ?: return ""
    return DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
        .format(instant.atZone(ZoneId.systemDefault()))
}

private fun KLocalDate.toJavaLocalDate(): JLocalDate = JLocalDate.of(year, monthNumber, dayOfMonth)
