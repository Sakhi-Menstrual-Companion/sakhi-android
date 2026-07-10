package team.sakhi.android.feature.logging

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.ui.KeyboardSafeScaffold
import team.sakhi.android.ui.PrimaryButton
import team.sakhi.android.ui.SheetSurface
import team.sakhi.logging.DischargeColor
import team.sakhi.logging.Symptom
import team.sakhi.models.FlowIntensity
import kotlin.math.roundToInt

/**
 * Daily log sheet — ports iOS `HomeLoggingSheet.swift` (683 lines): drag handle,
 * header with date + close, flow-intensity drop-icon cards, then the full
 * symptom checklist grouped by section exactly as iOS orders it (Body, Weight,
 * BBT, Pain, Digestive, Physical, Mood, Sleep, Discharge, Log), sticky save bar
 * with idle/saving/saved/failed states.
 *
 * Weight/BBT/discharge-colour/painkiller/doctor-visited: these were previously
 * assumed to need a KMM `PeriodLog` model extension and were left unported.
 * That assumption was wrong — iOS itself never adds fields to `PeriodLog` for
 * these either. Both platforms pack them as special tokens into the existing
 * shared `symptoms: List<String>` field via `LogTokenEncoder` (`_w:`, `_bbt:`,
 * `_dc:`, `_painkiller`, `_doctor` — a real KMM port of iOS
 * `LoggingViewModel`'s identical scheme), decoded back out on load and
 * filtered out of the real `Symptom` set automatically (`Symptom.from`
 * returns null for token strings). `LogTokenEncoder.hasClots`/`TOKEN_CLOTS`
 * exists in KMM too, but iOS's own logging sheet never renders a UI control
 * for it (`vm.dailyLog.hasClots` has a setter but no `checkRow` anywhere in
 * `HomeLoggingSheet.swift`), so it's left out here as well — parity means
 * matching what iOS actually shows, not what its data model could support.
 *
 * Weight/BBT use a plain Material `Slider` instead of iOS's custom horizontal
 * ruler-drag control — same underlying value and range, simpler drag gesture.
 * Discharge colour uses a row of selectable chips instead of iOS's dropdown
 * `Menu` — same 4 options + "None", different presentation.
 *
 * Also not ported: the enum-based `Mood` picker (happy/calm/irritated/...)
 * Android's original version showed here — that widget belongs to Home's
 * day-detail view on iOS (`HomeDayDetailGlassView`), not the logging sheet;
 * it never appears in `HomeLoggingSheet.swift`. It stays out until day-detail
 * is built, rather than living on the wrong screen.
 */
@Composable
fun LoggingSheet(
    viewModel: LoggingViewModel = koinViewModel(),
    onClose: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // iOS's Logging sheet config is the one exception that uses
    // `.presentationDragIndicator(.visible)` (see `HomeView.swift`
    // `makeLoggingSheetConfiguration`) -- every other sheet hides it.
    SheetSurface(showDragHandle = true) {
        KeyboardSafeScaffold(
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = formattedHeaderDate(uiState.selectedDate),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.logging_close),
                        )
                    }
                }
                HorizontalDivider()
            },
            body = {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (uiState.isLoadingEntry) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(SakhiSpacing.space6),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space4),
                        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                    ) {
                        Text(
                            text = stringResource(R.string.logging_flow),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            FlowIntensity.entries.forEach { flow ->
                                FlowCard(
                                    flow = flow,
                                    selected = uiState.selectedFlow == flow,
                                    enabled = uiState.canLogPeriod,
                                    onClick = {
                                        viewModel.onFlowSelected(if (uiState.selectedFlow == flow) null else flow)
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    if (uiState.selectedFlow != null) {
                        HorizontalDivider()

                        Text(
                            text = stringResource(R.string.logging_symptoms),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space3),
                        )

                        Surface(
                            shape = RoundedCornerShape(SakhiRadius.xl),
                            tonalElevation = SakhiSpacing.space1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = SakhiSpacing.space6),
                        ) {
                            Column {
                                symptomSection(
                                    title = stringResource(R.string.logging_section_body),
                                    symptoms = listOf(Symptom.ACNE),
                                    selected = uiState.selectedSymptoms,
                                    enabled = uiState.canEditSymptoms,
                                    onToggle = viewModel::toggleSymptom,
                                )
                                HorizontalDivider(modifier = Modifier.padding(start = SakhiSpacing.space5))
                                ExpandableValueRow(
                                    label = stringResource(R.string.logging_section_weight),
                                    value = uiState.weightKg,
                                    enabled = uiState.canEditSymptoms,
                                    formatValue = {
                                        context.getString(R.string.logging_weight_value, it.roundToInt())
                                    },
                                    defaultValue = 60.0,
                                    valueRange = 30f..150f,
                                    onValueChange = viewModel::onWeightChanged,
                                )
                                HorizontalDivider(modifier = Modifier.padding(start = SakhiSpacing.space5))
                                ExpandableValueRow(
                                    label = stringResource(R.string.logging_section_bbt),
                                    value = uiState.bbtCelsius,
                                    enabled = uiState.canEditSymptoms,
                                    formatValue = { context.getString(R.string.logging_bbt_value, it) },
                                    defaultValue = 35.0,
                                    valueRange = 30f..42f,
                                    onValueChange = viewModel::onBbtChanged,
                                )
                                symptomSection(
                                    title = stringResource(R.string.logging_section_pain),
                                    symptoms = listOf(
                                        Symptom.CRAMPS,
                                        Symptom.BACK_PAIN,
                                        Symptom.PELVIS_PAIN,
                                        Symptom.BREAST_TENDERNESS,
                                        Symptom.HEADACHE,
                                    ),
                                    selected = uiState.selectedSymptoms,
                                    enabled = uiState.canEditSymptoms,
                                    onToggle = viewModel::toggleSymptom,
                                )
                                symptomSection(
                                    title = stringResource(R.string.logging_section_digestive),
                                    symptoms = listOf(Symptom.BLOATING, Symptom.NAUSEA, Symptom.DIARRHEA, Symptom.CONSTIPATION),
                                    selected = uiState.selectedSymptoms,
                                    enabled = uiState.canEditSymptoms,
                                    onToggle = viewModel::toggleSymptom,
                                )
                                symptomSection(
                                    title = stringResource(R.string.logging_section_physical),
                                    symptoms = listOf(
                                        Symptom.FATIGUE,
                                        Symptom.DIZZINESS,
                                        Symptom.FEVER,
                                        Symptom.CHILLS,
                                        Symptom.WATER_RETENTION,
                                    ),
                                    selected = uiState.selectedSymptoms,
                                    enabled = uiState.canEditSymptoms,
                                    onToggle = viewModel::toggleSymptom,
                                )
                                symptomSection(
                                    title = stringResource(R.string.logging_section_mood),
                                    symptoms = listOf(
                                        Symptom.MOOD_SWINGS,
                                        Symptom.IRRITABILITY,
                                        Symptom.ANXIETY,
                                        Symptom.SADNESS_LOW_MOOD,
                                        Symptom.BRAIN_FOG,
                                    ),
                                    selected = uiState.selectedSymptoms,
                                    enabled = uiState.canEditSymptoms,
                                    onToggle = viewModel::toggleSymptom,
                                )
                                symptomSection(
                                    title = stringResource(R.string.logging_section_sleep),
                                    symptoms = listOf(Symptom.INSOMNIA, Symptom.RESTLESS_SLEEP),
                                    selected = uiState.selectedSymptoms,
                                    enabled = uiState.canEditSymptoms,
                                    onToggle = viewModel::toggleSymptom,
                                )
                                Text(
                                    text = stringResource(R.string.logging_section_discharge).uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space2),
                                )
                                DischargeColorRow(
                                    selected = uiState.dischargeColor,
                                    enabled = uiState.canEditSymptoms,
                                    onSelect = viewModel::onDischargeColorSelected,
                                )
                                HorizontalDivider(modifier = Modifier.padding(start = SakhiSpacing.space5))
                                SymptomRow(
                                    label = Symptom.UNUSUAL_DISCHARGE_SMELL.displayName,
                                    checked = Symptom.UNUSUAL_DISCHARGE_SMELL in uiState.selectedSymptoms,
                                    enabled = uiState.canEditSymptoms,
                                    onClick = { viewModel.toggleSymptom(Symptom.UNUSUAL_DISCHARGE_SMELL) },
                                )
                                HorizontalDivider(modifier = Modifier.padding(start = SakhiSpacing.space5))
                                SymptomRow(
                                    label = Symptom.VAGINAL_ITCHING.displayName,
                                    checked = Symptom.VAGINAL_ITCHING in uiState.selectedSymptoms,
                                    enabled = uiState.canEditSymptoms,
                                    onClick = { viewModel.toggleSymptom(Symptom.VAGINAL_ITCHING) },
                                )
                                Text(
                                    text = stringResource(R.string.logging_section_log).uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space2),
                                )
                                SymptomRow(
                                    label = stringResource(R.string.logging_painkiller_taken),
                                    checked = uiState.painkillerTaken,
                                    enabled = uiState.canEditSymptoms,
                                    onClick = viewModel::togglePainkillerTaken,
                                )
                                HorizontalDivider(modifier = Modifier.padding(start = SakhiSpacing.space5))
                                SymptomRow(
                                    label = stringResource(R.string.logging_doctor_visited),
                                    checked = uiState.doctorVisited,
                                    enabled = uiState.canEditSymptoms,
                                    onClick = viewModel::toggleDoctorVisited,
                                )
                            }
                        }
                    }

                    if (uiState.canEditNotes) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space4),
                            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
                        ) {
                            Text(
                                text = stringResource(R.string.logging_notes),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            )
                            OutlinedTextField(
                                value = uiState.notes,
                                onValueChange = viewModel::onNotesChanged,
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                                placeholder = { Text(stringResource(R.string.logging_notes_placeholder)) },
                            )
                        }
                    }

                    if (!uiState.canMutateSelectedDate && uiState.session?.isViewingOwnData == false) {
                        Text(
                            text = stringResource(R.string.logging_partner_lock_message),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space2),
                        )
                    }

                    uiState.error?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space2),
                        )
                    }

                    Spacer(modifier = Modifier.height(SakhiSpacing.space8))
                }
            },
            footer = {
                HorizontalDivider()
                SaveBar(
                    isSaving = uiState.isSaving,
                    isSaved = uiState.saveMessage != null,
                    hasError = uiState.error != null && !uiState.isSaving,
                    enabled = uiState.canLogPeriod && uiState.canMutateSelectedDate,
                    onClick = viewModel::save,
                )
            }
        )
    }
}

@Composable
private fun symptomSection(
    title: String,
    symptoms: List<Symptom>,
    selected: Set<Symptom>,
    enabled: Boolean,
    onToggle: (Symptom) -> Unit,
    isLast: Boolean = false,
) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space2),
    )
    symptoms.forEachIndexed { index, symptom ->
        SymptomRow(
            label = symptom.displayName,
            checked = symptom in selected,
            enabled = enabled,
            onClick = { onToggle(symptom) },
        )
        if (!isLast || index != symptoms.lastIndex) {
            HorizontalDivider(modifier = Modifier.padding(start = SakhiSpacing.space5))
        }
    }
}

@Composable
private fun SymptomRow(label: String, checked: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                selected = checked
                role = Role.Checkbox
                stateDescription = if (checked) {
                    context.getString(R.string.logging_selected)
                } else {
                    context.getString(R.string.logging_not_selected)
                }
            }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        LogCheckbox(checked = checked)
    }
}

@Composable
private fun LogCheckbox(checked: Boolean) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .background(
                color = if (checked) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(6.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun FlowCard(
    flow: FlowIntensity,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dropCount = when (flow) {
        FlowIntensity.SPOTTING -> 1
        FlowIntensity.LIGHT -> 2
        FlowIntensity.MEDIUM -> 3
        FlowIntensity.HEAVY -> 4
    }
    // iOS's local FlowLevel enum (HomeLoggingSheet.swift) uses "Slight"/"Moderate"
    // here, not the shared KMM FlowIntensity.displayName ("Light"/"Medium") --
    // matching the real rendered iOS copy, not the KMM string.
    val context = LocalContext.current
    val label = context.getString(flowLabelRes(flow))

    Surface(
        shape = RoundedCornerShape(SakhiRadius.lg),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .semantics {
                this.selected = selected
                role = Role.RadioButton
                stateDescription = if (selected) {
                    context.getString(R.string.logging_selection_state, label)
                } else {
                    context.getString(R.string.logging_not_selected_state, label)
                }
            }
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(vertical = SakhiSpacing.space5),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space1),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                repeat(dropCount) {
                    Icon(
                        imageVector = Icons.Filled.WaterDrop,
                        contentDescription = null,
                        tint = if (selected) Color.White else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = if (selected) Color.White else MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Ports iOS `weightRow`/`bbtRow`: tap to add (seeds `defaultValue`) or expand
 * an inline slider once a value exists, tap again to collapse. iOS uses a
 * custom drag ruler; this uses a plain Material `Slider` over the same range.
 */
@Composable
private fun ExpandableValueRow(
    label: String,
    value: Double?,
    enabled: Boolean,
    formatValue: (Double) -> String,
    defaultValue: Double,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Double?) -> Unit,
) {
    var expanded by remember(value != null) { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) {
                    if (value != null) {
                        expanded = !expanded
                    } else {
                        onValueChange(defaultValue)
                        expanded = true
                    }
                }
                .padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (value != null) {
                Text(
                    text = formatValue(value),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    text = "+",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (expanded && value != null) {
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toDouble()) },
                valueRange = valueRange,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SakhiSpacing.space5)
                    .padding(bottom = SakhiSpacing.space2),
            )
        }
    }
}

/** Ports iOS `dischargeColorRow`'s options as a chip row instead of a dropdown `Menu`. */
@Composable
private fun DischargeColorRow(
    selected: DischargeColor?,
    enabled: Boolean,
    onSelect: (DischargeColor?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space3),
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
    ) {
        val context = LocalContext.current
        DischargeColor.entries.forEach { color ->
            val isSelected = selected == color
            Surface(
                shape = RoundedCornerShape(SakhiRadius.full),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .semantics {
                        this.selected = isSelected
                        role = Role.RadioButton
                        stateDescription = if (isSelected) {
                            context.getString(R.string.logging_selection_state, color.displayName)
                        } else {
                            context.getString(R.string.logging_not_selected_state, color.displayName)
                        }
                    }
                    .clickable(enabled = enabled) { onSelect(color) },
            ) {
                Text(
                    text = color.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = SakhiSpacing.space3, vertical = SakhiSpacing.space2),
                )
            }
        }
    }
}

@Composable
private fun SaveBar(
    isSaving: Boolean,
    isSaved: Boolean,
    hasError: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val label = when {
        isSaving -> stringResource(R.string.logging_save_saving)
        isSaved -> stringResource(R.string.logging_save_saved)
        hasError -> stringResource(R.string.logging_save_retry)
        else -> stringResource(R.string.logging_save)
    }

    // PrimaryButton is text-only (core:ui has no leading-icon variant yet), so
    // the saved/failed checkmark and error glyphs iOS shows next to the label
    // aren't rendered here -- the label copy alone already matches each phase.
    PrimaryButton(
        text = label,
        onClick = onClick,
        enabled = enabled && !isSaving,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space4),
    )
}

private fun flowLabelRes(flow: FlowIntensity): Int = when (flow) {
    FlowIntensity.SPOTTING -> R.string.logging_flow_spotting
    FlowIntensity.LIGHT -> R.string.logging_flow_light
    FlowIntensity.MEDIUM -> R.string.logging_flow_medium
    FlowIntensity.HEAVY -> R.string.logging_flow_heavy
}

private fun formattedHeaderDate(date: kotlinx.datetime.LocalDate): String {
    val javaDate = java.time.LocalDate.of(date.year, date.monthNumber, date.dayOfMonth)
    val formatter = java.time.format.DateTimeFormatter.ofPattern("d MMMM", java.util.Locale.getDefault())
    return javaDate.format(formatter)
}
