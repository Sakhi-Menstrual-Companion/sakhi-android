package team.sakhi.android.feature.profile

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.ui.SheetSurface
import team.sakhi.date.DateConverter
import team.sakhi.logging.Mood
import team.sakhi.logging.Symptom
import team.sakhi.models.FlowIntensity
import team.sakhi.models.LogHistoryEntry
import team.sakhi.models.LogSource
import team.sakhi.models.PeriodLog
import team.sakhi.repositories.PeriodLogRepository
import team.sakhi.session.SessionManager
import kotlinx.datetime.LocalDate as KLocalDate

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
    val sessionManager = koinInject<SessionManager>()
    val periodLogRepository = koinInject<PeriodLogRepository>()
    var logs by remember { mutableStateOf<List<PeriodLog>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val userId = sessionManager.current?.targetUserId
        if (userId == null) {
            isLoading = false
            return@LaunchedEffect
        }
        periodLogRepository.getAll(userId)
            .onSuccess { logs = it }
            .onFailure { error = it.message ?: "Failed to load your log history" }
        isLoading = false
    }

    val dayEntries = remember(logs) {
        logs.sortedByDescending { it.logDate.toString() }
            .mapNotNull { log ->
                val rows = buildRows(log)
                if (rows.isEmpty()) null else DayEntry(log, rows)
            }
    }
    val groupedEntries = remember(dayEntries) { groupByMonth(dayEntries) }

    SheetSurface(showDragHandle = true) {
        DetailHeader(title = "Log History", onBack = onBack)

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@SheetSurface
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(SakhiSpacing.space5),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
        ) {
            error?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            if (groupedEntries.isEmpty() && error == null) {
                Text(
                    text = "Nothing logged yet. Your tracking history will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            groupedEntries.forEach { group ->
                MonthHeader(group.monthDate)
                group.days.forEach { entry -> DayBlock(entry) }
            }
        }
    }
}

private data class LedgerRow(
    val id: String,
    val title: String,
    val attribution: String,
    val timestampIso: String,
)

private data class DayEntry(val log: PeriodLog, val rows: List<LedgerRow>)
private data class MonthGroup(val key: String, val monthDate: KLocalDate, val days: List<DayEntry>)

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
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DayBlock(entry: DayEntry) {
    Column(modifier = Modifier.padding(bottom = SakhiSpacing.space4)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space1, vertical = SakhiSpacing.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (entry.log.periodPresent) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            }
            Column(modifier = Modifier.padding(start = SakhiSpacing.space2).weight(1f)) {
                Text(
                    text = dayHeaderLabel(entry.log.logDate),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (entry.log.periodPresent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            Surface(
                shape = RoundedCornerShape(SakhiRadius.full),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = "${entry.rows.size} ${if (entry.rows.size == 1) "entry" else "entries"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = SakhiSpacing.space3, vertical = SakhiSpacing.space1),
                )
            }
        }

        Surface(
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
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = row.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            Text(
                                text = row.attribution,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = shortTime(row.timestampIso),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (index != entry.rows.lastIndex) HorizontalDivider()
                }
            }
        }
    }
}

// ── Row building against the real shared `LogDiffer` key scheme ────────────

private fun buildRows(log: PeriodLog): List<LedgerRow> {
    if (log.history.isEmpty()) return synthesizedRows(log)
    val rows = mutableListOf<LedgerRow>()
    // Shared KMM history entries currently persist only the actor user id
    // (`changedBy`), not a `LogSource`. Each Android period-log record is still
    // source-scoped (`loggedBy`/`sourceUserId`), so the stable, profile-owner
    // perspective lives on the parent log today.
    val byName = sourceName(log.loggedBy)
    for (entry in log.history.asReversed()) {
        entry.changes.forEach { (field, value) ->
            rows += ledgerRowsFor(field, value, byName, entry.timestamp)
        }
    }
    return rows
}

private fun ledgerRowsFor(field: String, value: String, byName: String, timestampIso: String): List<LedgerRow> {
    val id = "$timestampIso-$field"
    return when (field) {
        "period_present" -> listOf(
            LedgerRow(id, "Period", "Updated by $byName", timestampIso),
        )
        "flow_intensity" -> {
            val newValue = value.substringAfter("-> ").trim()
            val removed = newValue == "null"
            val display = FlowIntensity.entries.firstOrNull { it.value == newValue }?.displayName
            listOf(
                LedgerRow(
                    id = id,
                    title = if (removed || display == null) "Flow" else "Flow · $display",
                    attribution = "${if (removed) "Removed" else "Updated"} by $byName",
                    timestampIso = timestampIso,
                ),
            )
        }
        "notes" -> listOf(LedgerRow(id, "Notes", "Updated by $byName", timestampIso))
        "symptoms_added" -> value.split(",").filter { it.isNotBlank() }.map { name ->
            LedgerRow("$id-$name", symptomDisplayName(name), "Added by $byName", timestampIso)
        }
        "symptoms_removed" -> value.split(",").filter { it.isNotBlank() }.map { name ->
            LedgerRow("$id-$name", symptomDisplayName(name), "Removed by $byName", timestampIso)
        }
        "moods_added" -> value.split(",").filter { it.isNotBlank() }.map { name ->
            LedgerRow("$id-$name", moodDisplayName(name), "Added by $byName", timestampIso)
        }
        "moods_removed" -> value.split(",").filter { it.isNotBlank() }.map { name ->
            LedgerRow("$id-$name", moodDisplayName(name), "Removed by $byName", timestampIso)
        }
        "medications" -> listOf(LedgerRow(id, "Medications", "Updated by $byName", timestampIso))
        else -> emptyList()
    }
}

private fun synthesizedRows(log: PeriodLog): List<LedgerRow> {
    val attribution = "Added by ${sourceName(log.loggedBy)}"
    val ts = log.updatedAt.ifBlank { log.createdAt }
    val rows = mutableListOf<LedgerRow>()
    if (log.periodPresent) {
        log.flowIntensity?.let {
            rows += LedgerRow("flow", "Flow · ${it.displayName}", attribution, ts)
        }
    }
    log.symptoms.filterNot { it.startsWith("_") }.forEach { name ->
        rows += LedgerRow("s-$name", symptomDisplayName(name), attribution, ts)
    }
    log.moods.forEach { name ->
        rows += LedgerRow("m-$name", moodDisplayName(name), attribution, ts)
    }
    log.notes?.takeIf { it.isNotBlank() }?.let { notes ->
        val preview = if (notes.length > 45) notes.take(45) + "…" else notes
        rows += LedgerRow("note", preview, attribution, ts)
    }
    return rows
}

private fun symptomDisplayName(value: String): String = Symptom.from(value)?.displayName ?: value
private fun moodDisplayName(value: String): String = Mood.from(value)?.displayName ?: value

private fun sourceName(source: LogSource): String = when (source) {
    LogSource.USER -> "You"
    LogSource.PARTNER -> "Your Sakhi"
    LogSource.MOTHER -> "Your mother"
    LogSource.FATHER -> "Your father"
    LogSource.PARENT -> "Your parent"
    LogSource.SYSTEM -> "Sakhi"
}

// ── Formatters ───────────────────────────────────────────────────────────────

private fun monthYearLabel(date: KLocalDate): String {
    val months = arrayOf(
        "JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE",
        "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER",
    )
    return "${months[date.monthNumber - 1]} ${date.year}"
}

private fun dayHeaderLabel(date: KLocalDate): String {
    val today = DateConverter.today()
    val yesterday = DateConverter.subtractDays(today, 1)
    return when (date) {
        today -> "Today"
        yesterday -> "Yesterday"
        else -> {
            val days = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            val months = arrayOf(
                "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
            )
            "${days[date.dayOfWeek.ordinal]}, ${date.dayOfMonth} ${months[date.monthNumber - 1]}"
        }
    }
}

private fun shortTime(timestampIso: String): String {
    val instant = runCatching { Instant.parse(timestampIso) }.getOrNull() ?: return ""
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val hour24 = local.hour
    val amPm = if (hour24 < 12) "AM" else "PM"
    val hour12 = when {
        hour24 == 0 -> 12
        hour24 > 12 -> hour24 - 12
        else -> hour24
    }
    val minute = local.minute.toString().padStart(2, '0')
    return "$hour12:$minute $amPm"
}
