package team.sakhi.android.feature.logging

import team.sakhi.android.ui.SakhiAlertKind
import team.sakhi.android.ui.SakhiAlertSheet
import team.sakhi.android.ui.CloseButton
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import kotlinx.datetime.LocalDate
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.HapticImpact
import team.sakhi.android.ui.SakhiFooter
import team.sakhi.android.ui.SakhiListDivider
import team.sakhi.android.ui.flowDisplayNameRes
import team.sakhi.android.ui.HorizontalRulerSlider
import team.sakhi.android.ui.KeyboardSafeScaffold
import team.sakhi.android.ui.SakhiNavBar
import team.sakhi.android.ui.SheetSurface
import team.sakhi.logging.DischargeColor
import team.sakhi.logging.Symptom
import team.sakhi.models.FlowIntensity
import kotlin.math.roundToInt
import team.sakhi.android.designsystem.sakhiSystemBackground
import androidx.compose.foundation.border

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
 * Also not ported: the enum-based `Mood` picker (happy/calm/irritated/...)
 * Android's original version showed here — that widget belongs to Home's
 * day-detail view on iOS (`HomeDayDetailGlassView`), not the logging sheet;
 * it never appears in `HomeLoggingSheet.swift`. It stays out until day-detail
 * is built, rather than living on the wrong screen.
 *
 * `hasPeriodData` mirrors iOS's `HomeView` -> `HomeLoggingSheet` constructor
 * pass-down of `viewModel.hasPeriodData` (`HomeViewModel+CyclePhase.swift`):
 * whether the user has *any* period history ever, not just today's flow.
 * The full symptom/weight/BBT/discharge/log section below is gated on
 * `selectedFlow != null || hasPeriodData` so an established user can still log
 * symptoms on a day with no flow selected, matching iOS's `contentBody`.
 */
@Composable
fun LoggingSheet(
    viewModel: LoggingViewModel = koinViewModel(),
    hasPeriodData: Boolean = false,
    // Non-null when opened from a specific date elsewhere (e.g. Calendar's "Log"
    // button, matching iOS's `LoggingViewModel(date: selectedDate.wrappedValue, ...)`
    // constructor-time date), so the sheet opens showing that date rather than
    // whatever this fresh ViewModel instance defaults to (today).
    initialDate: LocalDate? = null,
    onClose: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hapticManager = koinInject<AndroidHapticManager>()
    val isPartnerView = uiState.session?.isViewingOwnData == false
    val isPartnerFlowBlocked = isPartnerView && (!uiState.canLogPeriod || !uiState.canMutateSelectedDate)
    var activeDialog by remember { mutableStateOf<LoggingDialogState?>(null) }

    LaunchedEffect(initialDate) {
        if (initialDate != null) viewModel.selectDate(initialDate)
    }
    // Identifies the last `saveAttemptId` this composable has already reacted
    // to as a failure, so a fast failure can't be missed. A naive "was isSaving
    // true, and is it now false with an error" check is vulnerable to
    // StateFlow's latest-value-only delivery: an offline save can flip
    // isSaving true then false+error within a single emission window, and a
    // collector that never observes the intermediate `true` frame would think
    // no save was ever attempted. Comparing `saveAttemptId` instead works off
    // the final state alone, so it can't be skipped this way. -1 is a
    // sentinel below any real attempt id (`LoggingUiState.saveAttemptId`
    // starts at 0 and only increases).
    var lastHandledFailureAttemptId by remember { mutableStateOf(-1) }
    var showSaveFailure by remember { mutableStateOf(false) }
    var suppressedInlineError by remember { mutableStateOf<String?>(null) }

    // Dismiss on the monotonic `savedAttemptId`, NOT on `saveMessage`.
    //
    // `saveMessage` is set on success and then cleared again by the `loadEntry` that follows
    // it. Those two writes land in one `MutableStateFlow` conflation window now that the
    // re-read is local rather than a network round trip, so this effect only ever saw the
    // cleared value and the sheet sat open over a save that had already succeeded.
    //
    // `savedAttemptId` only moves forward, so the transition cannot be conflated away. The
    // remembered baseline stops a sheet reopened on an already-saved day from closing itself
    // immediately.
    var lastDismissedSaveId by rememberSaveable { mutableIntStateOf(uiState.savedAttemptId) }
    LaunchedEffect(uiState.savedAttemptId) {
        if (uiState.savedAttemptId != lastDismissedSaveId) {
            lastDismissedSaveId = uiState.savedAttemptId
            onClose()
        }
    }

    LaunchedEffect(uiState.isSaving, uiState.saveMessage, uiState.error, uiState.saveAttemptId) {
        val finishedWithSaveError = !uiState.isSaving &&
            uiState.saveMessage == null &&
            uiState.error != null &&
            uiState.saveAttemptId != lastHandledFailureAttemptId

        when {
            uiState.isSaving -> {
                showSaveFailure = false
                suppressedInlineError = null
            }

            uiState.saveMessage != null -> {
                showSaveFailure = false
                suppressedInlineError = null
            }

            finishedWithSaveError -> {
                showSaveFailure = true
                suppressedInlineError = uiState.error
                lastHandledFailureAttemptId = uiState.saveAttemptId
                delay(3_000)
                if (!uiState.isSaving && uiState.saveMessage == null && uiState.error == suppressedInlineError) {
                    showSaveFailure = false
                }
            }

            uiState.error == null -> {
                showSaveFailure = false
                suppressedInlineError = null
            }
        }
    }

    if (activeDialog != null) {
        LoggingPartnerAlert(
            dialogState = activeDialog,
            onDismiss = { activeDialog = null },
            onConfirm = {
                val dialogState = activeDialog
                activeDialog = null
                if (dialogState == LoggingDialogState.AccessRemoved) {
                    onClose()
                }
            },
        )
    }

    // iOS's Logging sheet config is the one exception that uses
    // `.presentationDragIndicator(.visible)` (see `HomeView.swift`
    // `makeLoggingSheetConfiguration`) -- every other sheet hides it.
    // The grabber belongs HERE, inside the visible surface. The host's Material handle
    // rendered in the sheet's transparent container, above this card, which is why it looked
    // like it had escaped the sheet.
    SheetSurface(showDragHandle = true) {
        KeyboardSafeScaffold(
            topBar = {
                // Shared nav bar, so this close button matches Profile's exactly. Its
                // "title" is two stacked lines, so it goes in the `leading` slot.
                SakhiNavBar(
                    onClose = onClose,
                    // Karan: pull space out from under the grabber and put it below the
                    // date row instead, above the header divider. The default 20/8 left
                    // 30dp above the date (20 + the handle's own 10) and only 8dp under
                    // the phase line. iOS's logging header is weighted the same way
                    // round -- `.padding(.top, 22).padding(.bottom, 18)`.
                    topPadding = LogHeaderTopPadding,
                    bottomPadding = LogHeaderBottomPadding,
                    leading = {
                        // iOS header: VStack(alignment: .leading, spacing: 2) of the date
                        // (lato 20 bold) over the phase name (lato 13, secondaryLabel).
                        // Android showed the date alone.
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = formattedHeaderDate(uiState.selectedDate),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            uiState.phaseName?.let { phase ->
                                Text(
                                    text = phase,
                                    fontSize = 13.sp,
                                    color = sakhiSecondaryLabel(),
                                )
                            }
                        }
                    },
                )
                SakhiListDivider()
            },
            body = {
                // iOS `contentBody` is a plain `VStack(spacing: 0)` -- the flow section
                // and the "Symptoms" header are PINNED, and only `symptomsScrollCard`
                // is a `ScrollView`. Android scrolled the whole body, so the flow
                // buttons scrolled away with everything else.
                Column(modifier = Modifier.fillMaxSize()) {
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
                                        blocked = isPartnerFlowBlocked,
                                        onClick = {
                                            if (isPartnerFlowBlocked) {
                                                hapticManager.error()
                                                activeDialog = LoggingDialogState.OwnershipLocked
                                            } else {
                                                viewModel.onFlowSelected(if (uiState.selectedFlow == flow) null else flow)
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                        }
                    }

                    // iOS gates this whole section on `hasPeriodData` (any period
                    // history ever, not just today's flow) -- HomeLoggingSheet.swift's
                    // `contentBody`, sourced from `HomeViewModel+CyclePhase.swift`'s
                    // `hasPeriodData`. An established user must still be able to log
                    // symptoms/weight/BBT on a day where they don't mark a flow.
                    // `selectedFlow != null` is kept as an additional OR so a brand-new
                    // user's very first flow selection (before any cycle history exists)
                    // still reveals the section immediately, matching existing behavior.
                    if (uiState.selectedFlow != null || hasPeriodData) {
                        // iOS draws `Divider().opacity(0.25)` here, not a full-strength
                        // separator -- Karan read Android's as a hard rule that did not
                        // belong. Keeping it at iOS's weight rather than deleting it.
                        SakhiListDivider(modifier = Modifier.alpha(FlowSectionDividerAlpha))

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
                            // iOS: `.background(DS.Colors.systemBackground)` -- the symptom
                            // block is a plain WHITE card. `tonalElevation` on a lightPink
                            // `surface` tinted it pink instead.
                            color = sakhiSystemBackground(),
                            modifier = Modifier
                                .fillMaxWidth()
                                // Takes the height left over after the pinned flow
                                // section, so the card itself is SHORTER than the sheet
                                // and scrolls inside its own bounds -- iOS's
                                // `symptomsScrollCard`.
                                .weight(1f)
                                .padding(horizontal = SakhiSpacing.space6),
                        ) {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                // Each block below is hidden entirely (not merely disabled)
                                // when the viewer lacks the specific granular permission --
                                // matches iOS `HomeLoggingSheet`'s per-section
                                // `Feature(.xxx)` gates, whose default style is `.hide`.
                                if (uiState.canEditSymptoms) {
                                    symptomSection(
                                        title = stringResource(R.string.logging_section_body),
                                        symptoms = listOf(Symptom.ACNE),
                                        selected = uiState.selectedSymptoms,
                                        enabled = uiState.canEditSymptoms,
                                        onToggle = viewModel::toggleSymptom,
                                    )
                                }
                                if (uiState.canViewWeight) {
                                    // No leading divider here. `symptomSection` already
                                    // draws a TRAILING one after its last row, so adding
                                    // one here stacked two hairlines at the same y --
                                    // reported live as the rule under "Acne" looking
                                    // thicker than every other. It was not thicker: both
                                    // are 2px, but two overlapping hairlines darkened it
                                    // to #BFBFC1 against the normal #DCDCDD.
                                    ExpandableValueRow(
                                        label = stringResource(R.string.logging_section_weight),
                                        value = uiState.weightKg,
                                        enabled = uiState.canViewWeight,
                                        formatValue = {
                                            context.getString(R.string.logging_weight_value, it.roundToInt())
                                        },
                                        defaultValue = 60.0,
                                        valueRange = 30f..150f,
                                        rulerStep = 1f,
                                        onHapticSelection = hapticManager::selection,
                                        onHapticImpact = { hapticManager.impact(HapticImpact.LIGHT) },
                                        onValueChange = viewModel::onWeightChanged,
                                    )
                                }
                                if (uiState.canViewTemperature) {
                                    SakhiListDivider(startInset = SakhiSpacing.space5)
                                    ExpandableValueRow(
                                        label = stringResource(R.string.logging_section_bbt),
                                        value = uiState.bbtCelsius,
                                        enabled = uiState.canViewTemperature,
                                        formatValue = { context.getString(R.string.logging_bbt_value, it) },
                                        defaultValue = 35.0,
                                        valueRange = 30f..42f,
                                        rulerStep = 0.1f,
                                        onHapticSelection = hapticManager::selection,
                                        onHapticImpact = { hapticManager.impact(HapticImpact.LIGHT) },
                                        onValueChange = viewModel::onBbtChanged,
                                    )
                                }
                                if (uiState.canEditSymptoms) {
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
                                }
                                if (uiState.canEditMoods) {
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
                                        enabled = uiState.canEditMoods,
                                        onToggle = viewModel::toggleSymptom,
                                    )
                                }
                                if (uiState.canViewDailyLogs) {
                                    symptomSection(
                                        title = stringResource(R.string.logging_section_sleep),
                                        symptoms = listOf(Symptom.INSOMNIA, Symptom.RESTLESS_SLEEP),
                                        selected = uiState.selectedSymptoms,
                                        enabled = uiState.canViewDailyLogs,
                                        onToggle = viewModel::toggleSymptom,
                                    )
                                }
                                if (uiState.canViewDischarge) {
                                    CardSectionLabel(
                                        text = stringResource(R.string.logging_section_discharge),
                                    )
                                    DischargeColorRow(
                                        selected = uiState.dischargeColor,
                                        enabled = uiState.canViewDischarge,
                                        onSelect = viewModel::onDischargeColorSelected,
                                    )
                                    SakhiListDivider(startInset = SakhiSpacing.space5)
                                    SymptomRow(
                                        label = Symptom.UNUSUAL_DISCHARGE_SMELL.displayName,
                                        checked = Symptom.UNUSUAL_DISCHARGE_SMELL in uiState.selectedSymptoms,
                                        enabled = uiState.canViewDischarge,
                                        onClick = { viewModel.toggleSymptom(Symptom.UNUSUAL_DISCHARGE_SMELL) },
                                    )
                                    SakhiListDivider(startInset = SakhiSpacing.space5)
                                    SymptomRow(
                                        label = Symptom.VAGINAL_ITCHING.sheetLabel(),
                                        checked = Symptom.VAGINAL_ITCHING in uiState.selectedSymptoms,
                                        enabled = uiState.canViewDischarge,
                                        onClick = { viewModel.toggleSymptom(Symptom.VAGINAL_ITCHING) },
                                    )
                                }
                                if (uiState.canViewMedications) {
                                    CardSectionLabel(
                                        text = stringResource(R.string.logging_section_log),
                                    )
                                    SymptomRow(
                                        label = stringResource(R.string.logging_painkiller_taken),
                                        checked = uiState.painkillerTaken,
                                        enabled = uiState.canViewMedications,
                                        onClick = viewModel::togglePainkillerTaken,
                                    )
                                    SakhiListDivider(startInset = SakhiSpacing.space5)
                                    SymptomRow(
                                        label = stringResource(R.string.logging_doctor_visited),
                                        checked = uiState.doctorVisited,
                                        enabled = uiState.canViewMedications,
                                        onClick = viewModel::toggleDoctorVisited,
                                    )
                                }
                            }
                        }
                    }

                    uiState.error?.let { error ->
                        if (error != suppressedInlineError) {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space2),
                            )
                        }
                    }

                }
            },
            footer = {
                SakhiListDivider()
                SaveBar(
                    isSaving = uiState.isSaving,
                    isSaved = uiState.saveMessage != null,
                    hasError = showSaveFailure,
                    enabled = true,
                    onClick = {
                        when {
                            isPartnerView && !uiState.canLogPeriod -> {
                                hapticManager.error()
                                activeDialog = LoggingDialogState.AccessRemoved
                            }

                            isPartnerView && !uiState.canMutateSelectedDate -> {
                                hapticManager.error()
                                activeDialog = LoggingDialogState.OwnershipLocked
                            }

                            else -> viewModel.save()
                        }
                    },
                )
            }
        )
    }
}

private enum class LoggingDialogState {
    AccessRemoved,
    OwnershipLocked,
}

@Composable
private fun LoggingPartnerAlert(
    dialogState: LoggingDialogState?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val state = dialogState ?: return
    val title = when (state) {
        LoggingDialogState.AccessRemoved -> stringResource(R.string.logging_partner_access_removed_title)
        LoggingDialogState.OwnershipLocked -> stringResource(R.string.logging_partner_ownership_title)
    }
    val message = when (state) {
        LoggingDialogState.AccessRemoved -> stringResource(R.string.logging_partner_access_removed_message)
        LoggingDialogState.OwnershipLocked -> stringResource(R.string.logging_partner_lock_message)
    }

    SakhiAlertSheet(
        kind = SakhiAlertKind.Warning,
        title = title,
        message = message,
        primaryLabel = stringResource(android.R.string.ok),
        onPrimaryClick = onConfirm,
        onDismissRequest = onDismiss,
    )
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
    CardSectionLabel(text = title)
    symptoms.forEachIndexed { index, symptom ->
        SymptomRow(
            label = symptom.sheetLabel(),
            checked = symptom in selected,
            enabled = enabled,
            onClick = { onToggle(symptom) },
        )
        if (!isLast || index != symptoms.lastIndex) {
            SakhiListDivider(startInset = SakhiSpacing.space5)
        }
    }
}

/**
 * The label this symptom shows *in the logging sheet*, which is not always its
 * `displayName`.
 *
 * iOS does the same thing, just implicitly: `HomeLoggingSheet` hardcodes
 * "Water Retention / Swelling" and "Vaginal Itching / Irritation" for these two rows,
 * while its own `Symptom.displayName` returns the shorter "Water Retention" and
 * "Itching / Irritation" — and its day-detail chips render the short form. Android's
 * `displayName` already matches iOS's enum exactly, so the longer wording has to live
 * here at the sheet rather than on the shared enum, otherwise the chips would drift.
 */
@Composable
private fun Symptom.sheetLabel(): String = when (this) {
    Symptom.WATER_RETENTION -> stringResource(R.string.logging_symptom_water_retention)
    Symptom.VAGINAL_ITCHING -> stringResource(R.string.logging_symptom_vaginal_itching)
    else -> displayName
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
            )
            // iOS `LogCheckbox` strokes the box at 1.5pt in `separator` when off and in
            // pink when on. Android drew no stroke at all, so an unchecked box was a
            // fully transparent 22dp square -- invisible. Every symptom row looked like
            // it had no control next to it.
            .border(
                width = 1.5.dp,
                color = if (checked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
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
    blocked: Boolean,
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
        // iOS: `.fill(selected ? DS.Colors.pink : DS.Colors.systemBackground)` -- the
        // unselected chip is a plain WHITE card, not Material's lavender `surfaceVariant`.
        color = if (selected) MaterialTheme.colorScheme.primary else sakhiSystemBackground(),
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
            .alpha(if (blocked) 0.55f else 1f)
            .clickable(onClick = onClick),
    ) {
        Column(
            // iOS: `.padding(.vertical, 22)` on the flow card (HomeLoggingSheet.swift).
            // `space5` is 20, which read a touch short next to iOS's.
            modifier = Modifier.padding(vertical = FlowCardVerticalPadding),
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
 * an inline horizontal ruler once a value exists, tap again to collapse.
 */
@Composable
private fun ExpandableValueRow(
    label: String,
    value: Double?,
    enabled: Boolean,
    formatValue: (Double) -> String,
    defaultValue: Double,
    valueRange: ClosedFloatingPointRange<Float>,
    rulerStep: Float,
    onHapticSelection: () -> Unit,
    onHapticImpact: () -> Unit,
    onValueChange: (Double?) -> Unit,
) {
    // NOT `remember(value != null)`. Tapping "+" sets the value and expands in the same
    // click, but making `value != null` a remember KEY meant that first write flipped
    // the key false -> true, so Compose threw the state away and re-initialised
    // `expanded` back to false on the very next recomposition. The value appeared and
    // the ruler never opened -- exactly the "+ should open the value AND the scale"
    // report. iOS holds `weightExpanded`/`bbtExpanded` as plain `@State` for this
    // reason; the `value != null` guard on the slider below already handles a cleared
    // value, so the key bought nothing.
    var expanded by remember { mutableStateOf(false) }

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
                    color = sakhiSecondaryLabel(),
                )
            }
        }
        if (expanded && value != null) {
            HorizontalRulerSlider(
                value = value.toFloat(),
                range = valueRange,
                step = rulerStep,
                anchorFraction = 0.9f,
                onValueChange = { onValueChange(it.toDouble()) },
                onHapticSelection = onHapticSelection,
                onHapticImpact = onHapticImpact,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SakhiSpacing.space5)
                    .padding(bottom = SakhiSpacing.space2),
            )
        }
    }
}

/** Ports iOS `dischargeColorRow` as a single menu row with trailing value/chevron. */
@Composable
private fun DischargeColorRow(
    selected: DischargeColor?,
    enabled: Boolean,
    onSelect: (DischargeColor?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = selected?.displayName ?: stringResource(R.string.logging_none)

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    role = Role.Button
                    stateDescription = selectedLabel
                }
                .clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.logging_discharge_color),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = selectedLabel,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.size(SakhiSpacing.space1))
            Icon(
                imageVector = Icons.Filled.UnfoldMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.logging_none)) },
                trailingIcon = {
                    if (selected == null) {
                        Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                    }
                },
                onClick = {
                    expanded = false
                    onSelect(null)
                },
            )
            DischargeColor.entries.forEach { color ->
                DropdownMenuItem(
                    text = { Text(color.displayName) },
                    trailingIcon = {
                        if (selected == color) {
                            Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(color)
                    },
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

    // Wrapped in the shared `SakhiFooter` so the save button sits at the identical
    // position, inset and spacing as the primary action on every other screen. It goes
    // through `primarySlot` rather than `primaryLabel` because this button is stateful
    // (saving spinner / saved / retry), which the plain label API cannot express.
    SakhiFooter(
        primaryLabel = label,
        onPrimaryClick = onClick,
        showSecondarySlot = false,
        primarySlot = {
    Button(
        onClick = onClick,
        enabled = enabled && !isSaving,
        shape = RoundedCornerShape(SakhiRadius.full),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        // `SakhiFooter` now owns the horizontal margin, the top gap and the
        // navigation-bar inset, so only the button's own height belongs here.
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                isSaving -> CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                isSaved -> Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                hasError -> Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(label)
        }
    }
        },
    )
}

// Delegates to core:ui so the sheet, the quick-log menu and Home's chip cannot disagree
// about what the same logged value is called -- they already had, before this.
private fun flowLabelRes(flow: FlowIntensity): Int = flowDisplayNameRes(flow)

@Composable
private fun formattedHeaderDate(date: kotlinx.datetime.LocalDate): String {
    val javaDate = java.time.LocalDate.of(date.year, date.monthNumber, date.dayOfMonth)
    val formatter = java.time.format.DateTimeFormatter.ofPattern(
        LocalContext.current.getString(R.string.logging_header_date_format),
        java.util.Locale.getDefault(),
    )
    return javaDate.format(formatter)
}

/** iOS `.padding(.vertical, 22)` on each flow-intensity card. */
private val FlowCardVerticalPadding = 22.dp

/**
 * The BODY / PAIN / DISCHARGE group titles inside the symptoms card.
 *
 * Real port of iOS `HomeLoggingSheet.cardSectionLabel`:
 * `.font(.lato(11, .bold))`, `.padding(.horizontal, DS.Spacing.m)`,
 * `.padding(.top, 18)`, `.padding(.bottom, 4)`.
 *
 * Android had a symmetric 8dp above and below, which is what Karan saw as the space
 * sitting under the title instead of above it: iOS puts 18 above and only 4 below, so
 * each title reads as attached to the rows it introduces rather than floating between
 * groups. Extracted to one composable because the same block was inlined three times.
 */
@Composable
private fun CardSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
        ),
        color = sakhiSecondaryLabel(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SakhiSpacing.space4)
            .padding(top = CardSectionLabelTopPadding, bottom = CardSectionLabelBottomPadding),
    )
}

/** iOS `contentBody`: `Divider().opacity(0.25)` between flow and symptoms. */
private const val FlowSectionDividerAlpha = 0.25f

/** iOS `cardSectionLabel`: `.padding(.top, 18)`. */
private val CardSectionLabelTopPadding = 18.dp

/** iOS `cardSectionLabel`: `.padding(.bottom, 4)`. */
private val CardSectionLabelBottomPadding = 4.dp

/** Leaves 22dp above the date once the drag handle's own 10dp is counted. */
private val LogHeaderTopPadding = 12.dp

/** iOS's logging header uses 18 under the title block; 16 keeps the header compact. */
private val LogHeaderBottomPadding = 16.dp
