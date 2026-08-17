package team.sakhi.android.feature.onboarding

import android.net.Uri
import androidx.annotation.StringRes
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.koinInject
import team.sakhi.android.designsystem.SakhiFontSize
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.HapticImpact
import team.sakhi.android.ui.CloseButton
import team.sakhi.android.ui.HeightRulerPicker
import team.sakhi.android.ui.OnboardingHeaderContentGap
import team.sakhi.android.ui.OnboardingStepTitle
import team.sakhi.android.ui.SakhiFooter
import team.sakhi.android.ui.SakhiModalSheet
import team.sakhi.android.ui.SakhiTextField
import team.sakhi.android.ui.WeightWheelPicker
import team.sakhi.android.ui.rememberSakhiModalSheetState
import team.sakhi.models.HealthCondition
import team.sakhi.onboarding.OnboardingFlowStep
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import team.sakhi.android.designsystem.sakhiDeepRose
import team.sakhi.android.designsystem.sakhiLightPink
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiSystemGray5
import team.sakhi.android.designsystem.sakhiTertiaryLabel
import team.sakhi.android.designsystem.toComposeColor
import team.sakhi.design.SakhiUIColors
import team.sakhi.android.designsystem.sakhiSystemBackground

@Composable
fun OnboardingHealthStepScreen(
    step: OnboardingFlowStep,
    navStateProgress: Float,
    fieldError: String?,
    canGoBack: Boolean,
    uiState: OnboardingHealthUiState,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onDateOfBirthChanged: (LocalDate) -> Unit,
    onHeightUnitChanged: (Boolean) -> Unit,
    onHeightCmChanged: (Double) -> Unit,
    onWeightUnitChanged: (Boolean) -> Unit,
    onWeightKgChanged: (Double) -> Unit,
    onLastPeriodDateChanged: (LocalDate) -> Unit,
    onPreviousLastPeriodMonth: () -> Unit,
    onNextLastPeriodMonth: () -> Unit,
    onPeriodLengthChanged: (String) -> Unit,
    onCycleLengthChanged: (String) -> Unit,
    onConditionToggled: (HealthCondition) -> Unit,
) {
    val copy = healthStepCopy(step)
    val weekdayLabels = listOf(
        stringResource(R.string.onboarding_weekday_s),
        stringResource(R.string.onboarding_weekday_m),
        stringResource(R.string.onboarding_weekday_t),
        stringResource(R.string.onboarding_weekday_w),
        stringResource(R.string.onboarding_weekday_t),
        stringResource(R.string.onboarding_weekday_f),
        stringResource(R.string.onboarding_weekday_s),
    )

    // Scrolling content in a weighted area, action pinned in the shared `SakhiFooter`
    // so the primary button sits at the identical Y on every health step (and every
    // other screen in the app) instead of riding at the end of the scroll content.
    Column(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SakhiSpacing.space6)
            .padding(top = SakhiSpacing.space6),
    ) {
        // iOS has no linear progress bar here -- none of the health steps
        // (`DateOfBirthStep`, `HeightStep`, `WeightStep`, etc.) set `progressDots`,
        // the only progress affordance `OnboardingFlowView`'s shell renders, so this
        // screen shows neither dots nor a bar on iOS. This was an Android-only
        // addition with no iOS counterpart.
        OnboardingStepTitle(
            text = stringResource(copy.titleRes),
            modifier = Modifier.padding(top = SakhiSpacing.space4),
        )
        Text(
            text = stringResource(copy.subtitleRes),
            style = MaterialTheme.typography.bodyMedium,
            color = sakhiSecondaryLabel(),
            modifier = Modifier.padding(top = SakhiSpacing.space1, bottom = OnboardingHeaderContentGap),
        )

        Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4)) {
        when (step) {
            OnboardingFlowStep.DateOfBirth -> DateOfBirthStepContent(
                dateOfBirth = uiState.dateOfBirth,
                onDateSelected = onDateOfBirthChanged,
            )
            OnboardingFlowStep.Height -> HeightStepContent(
                useImperial = uiState.useImperialHeight,
                heightCm = uiState.heightCm,
                onUnitChanged = onHeightUnitChanged,
                onHeightCmChanged = onHeightCmChanged,
            )
            OnboardingFlowStep.Weight -> WeightStepContent(
                useMetric = uiState.useMetricWeight,
                weightKg = uiState.weightKg,
                onUnitChanged = onWeightUnitChanged,
                onWeightKgChanged = onWeightKgChanged,
            )
            OnboardingFlowStep.LastPeriod -> LastPeriodStepContent(
                selectedDate = uiState.lastPeriodDate,
                displayedMonth = uiState.displayedLastPeriodMonth,
                weekdayLabels = weekdayLabels,
                onDateSelected = onLastPeriodDateChanged,
                onPreviousMonth = onPreviousLastPeriodMonth,
                onNextMonth = onNextLastPeriodMonth,
            )
            OnboardingFlowStep.PeriodLength -> DaysLengthStepContent(
                value = uiState.periodLengthText,
                placeholder = stringResource(R.string.onboarding_period_length_placeholder),
                infoButtonText = stringResource(R.string.onboarding_period_length_info_button),
                info = DaysInfo.periodLength,
                onValueChanged = onPeriodLengthChanged,
            )
            OnboardingFlowStep.CycleLength -> DaysLengthStepContent(
                value = uiState.cycleLengthText,
                placeholder = stringResource(R.string.onboarding_cycle_length_placeholder),
                infoButtonText = stringResource(R.string.onboarding_cycle_length_info_button),
                info = DaysInfo.cycleLength,
                onValueChanged = onCycleLengthChanged,
            )
            OnboardingFlowStep.HealthConditions -> HealthConditionsStepContent(
                selectedConditions = uiState.selectedConditions,
                onConditionToggled = onConditionToggled,
            )
            else -> {}
        }

        if (!fieldError.isNullOrBlank()) {
            Text(
                text = fieldError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        }

    }

        // Back is the shared top-bar back button owned by `OnboardingFlowHost`
        // (matching iOS's single top-of-screen `DSBackButton`), so no redundant
        // bottom back button here. `canGoBack`/`onBack` stay on the signature since
        // the host still passes them and the system `BackHandler` uses the same path.
        SakhiFooter(
            primaryLabel = stringResource(R.string.onboarding_continue),
            onPrimaryClick = onContinue,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateOfBirthStepContent(
    dateOfBirth: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
) {
    val zoneId = remember { ZoneId.systemDefault() }
    val minDate = remember { LocalDate.now().minusYears(80) }
    val maxDate = remember { LocalDate.now().minusYears(12) }
    val dateOfBirthFormatter = rememberDateFormatter(R.string.onboarding_date_of_birth_format)
    var showPicker by remember { mutableStateOf(false) }

    // Row styling ported from iOS's `OnboardingDateOfBirthPicker` (`dsCard(.pink)` row,
    // formatted date + pink calendar glyph). The picker itself is deliberately NOT a
    // pixel port of iOS's wheel sheet -- per Karan, "native android ka component use
    // karo, to pick date aur validation sahi rahegi": Compose Material3's own
    // `DatePickerDialog`/`DatePicker` handles date validity (leap years, days-per-month,
    // the min/max bound) correctly out of the box, which a hand-rolled day/month/year
    // wheel has to get right itself. Explicit, deliberate deviation from iOS here.
    Surface(
        shape = RoundedCornerShape(SakhiRadius.xl),
        color = sakhiSystemBackground(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SakhiSpacing.space12 + SakhiSpacing.space2)
                .clickable { showPicker = true }
                .padding(horizontal = SakhiSpacing.space4),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = dateOfBirth.format(dateOfBirthFormatter),
                style = MaterialTheme.typography.bodyLarge,
            )
            Icon(
                imageVector = Icons.Rounded.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }

    if (showPicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = dateOfBirth.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            selectableDates = remember(minDate, maxDate) {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                        val date = java.time.Instant.ofEpochMilli(utcTimeMillis)
                            .atZone(java.time.ZoneOffset.UTC)
                            .toLocalDate()
                        return !date.isBefore(minDate) && !date.isAfter(maxDate)
                    }
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val picked = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneOffset.UTC)
                            .toLocalDate()
                        onDateSelected(picked)
                    }
                    showPicker = false
                }) {
                    Text(stringResource(R.string.onboarding_dob_sheet_done))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.onboarding_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun HeightStepContent(
    useImperial: Boolean,
    heightCm: Double,
    onUnitChanged: (Boolean) -> Unit,
    onHeightCmChanged: (Double) -> Unit,
) {
    val hapticManager = koinInject<AndroidHapticManager>()
    val totalInches = ((heightCm / INCH_TO_CM).roundToNearestInt()).coerceIn(36, 84)
    val feet = totalInches / 12
    val inches = totalInches % 12
    val displayCm = heightCm.coerceIn(100.0, 220.0).roundToNearestInt()

    // Karan: match Weight's card height to this one ("weight ka ruler toh perfect hai"
    // -- only the container height, not the wheel itself). `heightIn(min=)` rather than
    // a hard `height()` so this card's own natural size (already taller than the
    // minimum) is untouched; Weight's shorter natural content is the one actually
    // stretched up to match.
    PinkCard(modifier = Modifier.heightIn(min = HealthPickerCardHeight)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
        ) {
            SegmentedToggle(
                options = listOf(
                    stringResource(R.string.onboarding_height_option_imperial),
                    stringResource(R.string.onboarding_height_option_metric),
                ),
                selectedIndex = if (useImperial) 0 else 1,
                onSelected = { onUnitChanged(it == 0) },
            )

            // iOS `HeightStepContent`: value column (left) + ruler (right) in one
            // `HStack(spacing: .m)`, the value column pinned to the ruler's own
            // 300pt height so both stay vertically centred as one unit.
            //
            // `fillMaxWidth()` matters here: without it the Row sizes to wrap its
            // content instead of spanning the card, so the ruler's `weight(1f)` has no
            // real extra space to claim and collapses to a narrow measured width --
            // reported live as "ruler kafi kam width ka ho rakha hai."
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.height(HeightRulerPickerHeight),
                ) {
                    if (useImperial) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            LargeValueText(text = feet.toString(), fontSize = HeightValueFontSize)
                            UnitText(text = stringResource(R.string.onboarding_unit_ft), fontSize = HeightUnitFontSize)
                            Spacer(modifier = Modifier.width(SakhiSpacing.space1))
                            LargeValueText(text = inches.toString(), fontSize = HeightValueFontSize)
                            UnitText(text = stringResource(R.string.onboarding_unit_in), fontSize = HeightUnitFontSize)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.Bottom) {
                            LargeValueText(text = displayCm.toString(), fontSize = HeightValueFontSize)
                            UnitText(text = stringResource(R.string.onboarding_unit_cm), fontSize = HeightUnitFontSize)
                        }
                    }
                }

                // iOS `.id(weightUnit)`-equivalent: forces fresh internal drag state
                // when the unit toggle flips, instead of reusing a `dragBase` computed
                // against the previous unit's scale.
                key(useImperial) {
                    HeightRulerPicker(
                        value = if (useImperial) totalInches.toFloat() else displayCm.toFloat(),
                        range = if (useImperial) 36f..84f else 100f..220f,
                        onValueChange = { next ->
                            if (useImperial) {
                                onHeightCmChanged(next.toDouble() * INCH_TO_CM)
                            } else {
                                onHeightCmChanged(next.toDouble())
                            }
                        },
                        onHapticSelection = { hapticManager.selection() },
                        onHapticImpact = { hapticManager.impact(HapticImpact.LIGHT) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeightStepContent(
    useMetric: Boolean,
    weightKg: Double,
    onUnitChanged: (Boolean) -> Unit,
    onWeightKgChanged: (Double) -> Unit,
) {
    val hapticManager = koinInject<AndroidHapticManager>()
    val displayWeight = if (useMetric) {
        weightKg.coerceIn(30.0, 150.0).roundToNearestInt()
    } else {
        (weightKg.coerceIn(30.0, 150.0) * POUNDS_PER_KILOGRAM).roundToNearestInt()
    }

    // Same `heightIn(min=)` as `HeightStepContent`'s card. Deliberately TOP-anchored
    // (plain `spacedBy`, no `CenterVertically`): Karan asked for the card's extra height
    // to show up as breathing room directly under the segmented toggle, which
    // `SegmentedToggle` now owns via its own bottom gap. Centring the column instead
    // spread that height above and below the whole group, which is not the ask.
    // Fixed height, not `heightIn(min =)`. The content is shorter than
    // `HealthPickerCardHeight`, so the card rendered at exactly that height either
    // way -- but a min-only constraint leaves the column's max height UNBOUNDED,
    // which makes `weight(1f)` resolve to zero (that is what once made the value
    // text and the whole dial vanish). Pinning the height bounds the column so the
    // leftover slack can be placed deliberately instead of always falling to the
    // bottom, i.e. below the wheel.
    PinkCard(modifier = Modifier.height(HealthPickerCardHeight)) {
        // iOS `WeightStep` is a `VStack(spacing: 0)` with explicit per-child padding,
        // NOT the uniform 12pt stack `HeightStep` uses. Real values from
        // `WeightStep.swift`: 24 above the digits (`.padding(.top, DS.Spacing.l)`),
        // 16 below them (`.padding(.bottom, DS.Spacing.m)`), 12 under the wheel
        // (`.padding(.bottom, DS.Spacing.s)`). `SegmentedToggle` already contributes
        // its own shared 12pt bottom gap, so the digits row only adds the other 12.
        Column(modifier = Modifier.fillMaxHeight()) {
            SegmentedToggle(
                options = listOf(
                    stringResource(R.string.onboarding_weight_option_metric),
                    stringResource(R.string.onboarding_weight_option_imperial),
                ),
                selectedIndex = if (useMetric) 0 else 1,
                onSelected = { onUnitChanged(it == 0) },
            )

            // Karan: the card's leftover height belongs BETWEEN the segment and the
            // digits, not stranded under the wheel. Safe here only because the card
            // height is now fixed -- see the note on `PinkCard` above.
            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = WeightValueTopGap,
                        bottom = WeightValueBottomGap,
                    ),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom,
            ) {
                LargeValueText(text = displayWeight.toString(), fontSize = WeightValueFontSize)
                Spacer(modifier = Modifier.width(SakhiSpacing.space1))
                UnitText(
                    text = if (useMetric) {
                        stringResource(R.string.onboarding_unit_kg)
                    } else {
                        stringResource(R.string.onboarding_unit_lbs)
                    },
                    fontSize = WeightUnitFontSize,
                )
            }

            // iOS `.id(weightUnit)`-equivalent -- see the matching comment on
            // `HeightRulerPicker` above.
            key(useMetric) {
                WeightWheelPicker(
                    modifier = Modifier.padding(bottom = WeightWheelBottomGap),
                    value = displayWeight,
                    range = if (useMetric) 30..150 else 66..331,
                    onValueChange = { next ->
                        onWeightKgChanged(
                            if (useMetric) next.toDouble() else next / POUNDS_PER_KILOGRAM,
                        )
                    },
                    onHapticSelection = { hapticManager.selection() },
                    onHapticImpact = { hapticManager.impact(HapticImpact.LIGHT) },
                )
            }
        }
    }
}

@Composable
private fun LastPeriodStepContent(
    selectedDate: LocalDate,
    displayedMonth: YearMonth,
    weekdayLabels: List<String>,
    onDateSelected: (LocalDate) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    val currentMonth = remember { YearMonth.now() }
    val days = remember(displayedMonth) { monthGrid(displayedMonth) }
    val monthHeaderFormatter = rememberDateFormatter(R.string.onboarding_month_header_format)

    PinkCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPreviousMonth, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.onboarding_previous_month),
                    )
                }
                Text(
                    text = displayedMonth.format(monthHeaderFormatter),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                IconButton(
                    onClick = onNextMonth,
                    enabled = displayedMonth < currentMonth,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.onboarding_next_month),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                weekdayLabels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        // Weekday letters, same role as `SakhiCalendar.weekdayRow`, which
                        // iOS renders in `DS.Colors.tertiaryLabel` -- not secondary.
                        color = sakhiTertiaryLabel(),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // iOS `SakhiCalendarView(cellHeight: 34, rowSpacing: 2, navButtonSize: 32)`
            // -- the outer Column's `spacedBy(space3)` (12dp) was applying between
            // every week row too, not just between the header/weekday/grid blocks.
            // Across up to 6 week rows that's ~50dp of excess height on its own --
            // reported live as the card getting clipped by the footer. Own
            // tightly-spaced Column for just the day grid, separate from the looser
            // 12dp gap above it.
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                days.chunked(7).forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space1),
                    ) {
                        week.forEach { date ->
                            CalendarDayCell(
                                date = date,
                                isSelected = date == selectedDate,
                                enabled = date != null && !date.isAfter(LocalDate.now()),
                                onClick = { if (date != null) onDateSelected(date) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DaysLengthStepContent(
    value: String,
    placeholder: String,
    infoButtonText: String,
    info: DaysInfo,
    onValueChanged: (String) -> Unit,
) {
    var showInfo by remember { mutableStateOf(false) }

    // iOS `PeriodLengthContent`/`CycleLengthContent`: `VStack(alignment: .trailing)`, a
    // plain text button below the field ("What is period length?", 13pt pink, no icon),
    // not the Material `TextButton` + info-glyph Android had. `.days` suffix on the field
    // is `.lato(17)` `secondaryLabel`, matching what Android's `trailingContent` already
    // did -- untouched.
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space1),
    ) {
        SakhiTextField(
            value = value,
            onValueChange = onValueChanged,
            placeholder = placeholder,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            trailingContent = {
                Text(
                    text = stringResource(R.string.onboarding_days_suffix),
                    fontSize = 17.sp,
                    color = sakhiSecondaryLabel(),
                )
            },
        )

        Text(
            text = infoButtonText,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { showInfo = true },
        )
    }

    if (showInfo) {
        DaysInfoSheet(info = info, onDismiss = { showInfo = false })
    }
}

/**
 * Real port of iOS's `DaysInfoSheet` (`OnboardingInputPickers.swift`) -- a `.medium`
 * detent bottom sheet, not an `AlertDialog`. Header row (title left, close button
 * right), explanation paragraph, two stat pills (normal range / average) side by side
 * on `DS.Colors.lightPink`, and a tappable ACOG source line. The actual Safari/Custom
 * Tabs launch for the source link is left as a plain external-browser `Intent` rather
 * than an in-app browser sheet -- iOS's `SFSafariViewController` has no direct Compose
 * equivalent, and a full custom-tab integration is its own scoped piece of work, not
 * this pass's layout/colour parity fix.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DaysInfoSheet(info: DaysInfo, onDismiss: () -> Unit) {
    val sheetState = rememberSakhiModalSheetState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val sourceUrl = stringResource(R.string.onboarding_info_source_url)

    SakhiModalSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        // `SakhiModalSheet`'s own `containerColor` is deliberately `Color.Transparent`
        // (its host content is expected to supply its own fill -- `CountryPicker`, the
        // only other real sheet content in the app, already does this via
        // `.background(colorScheme.background)`). This Column never did, so the sheet
        // rendered as floating text over the scrim with no visible card at all --
        // reported live as "background bhi nahi hai... weird si aa rahi hai."
        // iOS presents this sheet with `.profileStylePresentationBackground()`, whose
        // light-mode fill is `DS.Colors.background` -- the app's pale pink page colour,
        // NOT `systemBackground` (white). The difference is not cosmetic: `CloseButton`
        // is a white glass circle, so on a white sheet its background disappeared and
        // the cross read as a bare floating glyph. Same root cause as the `SheetSurface`
        // fix earlier in this pass.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SakhiSpacing.space6)
                    .padding(top = SakhiSpacing.space5, bottom = SakhiSpacing.space4),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(info.titleRes),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
                // Shared `CloseButton` (plain white circle, no stroke, no shadow) instead
                // of a hand-rolled `IconButton` -- per Karan: "use cross button in this
                // sheet jo header mai ho raha hai," matching every other close affordance
                // in the app rather than a one-off.
                CloseButton(onClick = onDismiss)
            }

            // iOS gets its bottom breathing room from the sheet's safe area on top of
            // the content's own `.padding(.bottom, DS.Spacing.xl)`. Android had the
            // padding but no navigation-bar inset, so on a gesture-nav device the
            // source line sat directly on the home indicator.
            Column(
                modifier = Modifier
                    .padding(horizontal = SakhiSpacing.space6)
                    .padding(bottom = SakhiSpacing.space8)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space6),
            ) {
                Text(
                    text = stringResource(info.explanationRes),
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3)) {
                    InfoStatPill(
                        label = stringResource(R.string.onboarding_info_normal_range),
                        value = stringResource(info.normalRangeRes),
                        modifier = Modifier.weight(1f),
                    )
                    InfoStatPill(
                        label = stringResource(R.string.onboarding_info_average),
                        value = stringResource(info.averageRes),
                        modifier = Modifier.weight(1f),
                    )
                }

                Text(
                    text = stringResource(R.string.onboarding_info_source_acog),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    // Chrome Custom Tabs -- the standard Android equivalent of iOS's
                    // `SFSafariViewController`, opening the ACOG source inside an in-app
                    // web view instead of handing off to the external browser app. Per
                    // Karan: "original link open ho jaye web view mai."
                    modifier = Modifier.clickable {
                        CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(sourceUrl))
                    },
                )
            }
        }
    }
}

/** iOS `statPill`: `DS.Colors.lightPink` fill, `DS.Colors.deepRose` text, `onboardingCard` radius. */
@Composable
private fun InfoStatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(sakhiLightPink(), RoundedCornerShape(SakhiRadius.xl))
            .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space1),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = sakhiDeepRose().copy(alpha = 0.65f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = sakhiDeepRose(),
        )
    }
}

@Composable
private fun HealthConditionsStepContent(
    selectedConditions: Set<HealthCondition>,
    onConditionToggled: (HealthCondition) -> Unit,
) {
    val hapticManager = koinInject<AndroidHapticManager>()
    // iOS `HealthConditionContent`: `ScrollView(.vertical) { ... }.frame(maxHeight:
    // 320)` -- the row list scrolls internally past 320pt rather than growing the
    // card without bound. Android had neither cap, so with enough conditions the card
    // grew past the footer -- reported live as "container... footer ke niche cut ho
    // raha hai."
    PinkCard {
        Column(
            modifier = Modifier
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            HealthCondition.entries.forEachIndexed { index, condition ->
                HealthConditionRow(
                    condition = condition,
                    isSelected = condition in selectedConditions,
                    onClick = {
                        hapticManager.impact(HapticImpact.LIGHT)
                        onConditionToggled(condition)
                    },
                )
                if (index < HealthCondition.entries.lastIndex) {
                    Spacer(
                        modifier = Modifier
                            .padding(start = SakhiSpacing.space4)
                            .fillMaxWidth()
                            .height(SakhiSpacing.space1 / 8)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    )
                }
            }
        }
    }
}

@Composable
private fun HealthConditionRow(
    condition: HealthCondition,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = SakhiSpacing.space4,
                vertical = SakhiSpacing.space3 + SakhiSpacing.space1 / 4,
            ),
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ConditionTitleDescGap),
        ) {
            // iOS `checkRow`: title `.lato(15, .bold)`, description `.lato(13)`.
            // Android was rendering the description at 11sp, which Karan read as too
            // small next to the iOS build.
            Text(
                text = condition.displayName,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = ConditionTitleFontSize,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = condition.shortDescription,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = ConditionDescFontSize),
                color = sakhiSecondaryLabel(),
            )
        }
        // iOS `LogCheckbox`: a 22pt ROUNDED SQUARE (corner radius 6), not a circle.
        // Unchecked is `Color.clear` with a 1.5pt `separator` stroke -- Android filled
        // it with `colorScheme.background`, the pale pink, which read as a filled box
        // sitting on the white card. Checked is a solid pink fill with a white tick.
        val checkboxShape = RoundedCornerShape(ConditionCheckboxRadius)
        Box(
            modifier = Modifier
                .size(ConditionCheckboxSize)
                .background(
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = checkboxShape,
                )
                .border(
                    width = ConditionCheckboxStroke,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    },
                    shape = checkboxShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(ConditionCheckboxGlyph),
                )
            }
        }
    }
}

@Composable
private fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    // iOS: track is `DS.Colors.gray5` (`UIColor.systemGray5`, neutral grey), not the app's
    // pink page background; the selected pill is `DS.Colors.profileCardBackground` (white)
    // with a soft drop shadow, not a flat `colorScheme.surface` (this app's pink-tinted
    // brand surface) -- same root-cause bug pattern as `PinkCard` above. Label size is
    // `.lato(16, ...)`, not the default `bodyMedium` (14sp via the token scale).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Karan: the extra card height should land BELOW the segment, not be spread
            // around the whole card. Owned here rather than at each call site so every
            // container using this toggle gets the same gap automatically.
            .padding(bottom = SegmentedToggleBottomGap)
            .background(
                color = sakhiSystemGray5(),
                shape = RoundedCornerShape(SakhiRadius.full),
            )
            .padding(SakhiSpacing.space1),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (selected) {
                            Modifier.shadow(elevation = 3.dp, shape = RoundedCornerShape(SakhiRadius.full))
                        } else {
                            Modifier
                        },
                    )
                    .background(
                        color = if (selected) sakhiSystemBackground() else Color.Transparent,
                        shape = RoundedCornerShape(SakhiRadius.full),
                    )
                    .clickable { onSelected(index) }
                    .padding(vertical = SakhiSpacing.space2 + SakhiSpacing.space1 / 4),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 16.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun PinkCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Same root-cause bug class found across this whole session: iOS `dsCard(.pink)` fills
    // `DS.Colors.profileCardBackground` (plain white in light mode), not a tinted surface.
    // `colorScheme.surface` is bound to the brand `lightPink` in this app's theme, which is
    // why every card in this flow (DOB, Height, Weight, HealthConditions) read pink-tinted
    // instead of white -- likely the single biggest driver of "ekdum alag hai" (completely
    // different) next to iOS.
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(SakhiRadius.xl),
        color = sakhiSystemBackground(),
    ) {
        Column(
            modifier = Modifier.padding(SakhiSpacing.space4),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
            content = content,
        )
    }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate?,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // iOS `SakhiCalendarView.dayCell` in `solidSelection` mode (what
    // `OnboardingCalendarPicker` uses): the selection is a fixed-size RING, not a
    // background on the cell. Painting the cell background meant the highlight
    // stretched to the full weighted column width and rendered as a wide pill.
    // Real iOS values: cell 34 high and full width, ring `dotSize + 8` where
    // `dotSize = min(cellHeight - 10, 36)` = 24, so a 32pt circle at 2.5pt stroke,
    // in `periodColor` (`CAL_PERIOD_LIGHT`), with the day text in that same colour.
    val periodColor = SakhiUIColors.CAL_PERIOD_LIGHT.toComposeColor()
    Box(
        modifier = modifier
            .height(CalendarCellHeight)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(CalendarSelectionRingSize)
                    .border(
                        width = CalendarSelectionRingStroke,
                        color = periodColor,
                        shape = CircleShape,
                    ),
            )
        }
        Text(
            text = date?.dayOfMonth?.toString().orEmpty(),
            fontSize = CalendarDayFontSize,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = when {
                date == null -> androidx.compose.ui.graphics.Color.Transparent
                isSelected -> periodColor
                enabled -> MaterialTheme.colorScheme.onSurface
                else -> sakhiSecondaryLabel().copy(alpha = 0.4f)
            },
        )
    }
}

/**
 * iOS uses two different sizes here, not one shared token: Height's ft/in/cm digits are
 * `.lato(38, .bold)`, Weight's are `DS.Typography.largeValue` = `.lato(64, .bold)` -- so
 * [fontSize] is required, not defaulted, forcing every call site to state which one it
 * means rather than silently sharing a value that matches neither.
 */
@Composable
private fun LargeValueText(text: String, fontSize: androidx.compose.ui.unit.TextUnit) {
    Text(
        text = text,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** Height's unit labels are `.lato(17)`, Weight's is `DS.Typography.valueUnit` = `.lato(22)`. */
@Composable
private fun UnitText(text: String, fontSize: androidx.compose.ui.unit.TextUnit) {
    Text(
        text = text,
        fontSize = fontSize,
        color = sakhiSecondaryLabel(),
    )
}

private val HeightValueFontSize = 38.sp
private val HeightUnitFontSize = 17.sp
private val WeightValueFontSize = 64.sp
private val WeightUnitFontSize = 22.sp
// Must match `HeightRulerPicker.kt`'s `rulerHeight` -- reduced together, live, because
// the 300dp card was getting clipped by the footer on-device.
private val HeightRulerPickerHeight = 260.dp

/**
 * Breathing room under the segmented unit toggle. Owned by `SegmentedToggle` itself so
 * every onboarding container that uses it picks this up, rather than each call site
 * remembering to add its own bottom padding.
 */
private val SegmentedToggleBottomGap = 12.dp

// iOS `WeightStep.swift`: the digits sit 24pt (`DS.Spacing.l`) below the segmented
// toggle and 16pt (`DS.Spacing.m`) above the wheel, and the wheel keeps only 12pt
// (`DS.Spacing.s`) under it. `SegmentedToggle` already owns 12 of the 24.
private val WeightValueTopGap = 12.dp
private val WeightValueBottomGap = 16.dp
private val WeightWheelBottomGap = 12.dp

// iOS `OnboardingCalendarPicker` -> `SakhiCalendarView(cellHeight: 34, rowSpacing: 2)`.
// The ring is `dotSize + 8` where `dotSize = min(cellHeight - 10, 36)`, i.e. 32pt at a
// 2.5pt stroke, and the day label is `.lato(13)`.
private val CalendarCellHeight = 34.dp
private val CalendarSelectionRingSize = 32.dp
private val CalendarSelectionRingStroke = 2.5.dp
private val CalendarDayFontSize = 13.sp

// iOS `HealthConditionStep.checkRow` + `LogCheckbox`.
private val ConditionTitleFontSize = 15.sp
private val ConditionDescFontSize = 13.sp
private val ConditionTitleDescGap = 3.dp
private val ConditionCheckboxSize = 22.dp
private val ConditionCheckboxRadius = 6.dp
private val ConditionCheckboxStroke = 1.5.dp
private val ConditionCheckboxGlyph = 14.dp

/**
 * Shared minimum card height for `HeightStepContent`/`WeightStepContent` -- Karan
 * asked for the two containers to be the same height (Weight's card was naturally
 * much shorter, since `WeightWheelPicker` at 148dp is far smaller than the ruler),
 * without changing either picker itself. Reduced alongside the ruler height above.
 * Nudged up slightly (340 -> 364) on Karan's ask; the added height lands under the
 * toggle via `SegmentedToggleBottomGap`, and stays clear of the footer that forced the
 * earlier reduction.
 */
private val HealthPickerCardHeight = 364.dp

private data class HealthStepCopy(
    val titleRes: Int,
    val subtitleRes: Int,
)

private data class DaysInfo(
    val titleRes: Int,
    val explanationRes: Int,
    val normalRangeRes: Int,
    val averageRes: Int,
) {
    companion object {
        val periodLength = DaysInfo(
            titleRes = R.string.onboarding_days_info_period_title,
            explanationRes = R.string.onboarding_days_info_period_explanation,
            normalRangeRes = R.string.onboarding_days_info_period_range,
            averageRes = R.string.onboarding_days_info_period_average,
        )
        val cycleLength = DaysInfo(
            titleRes = R.string.onboarding_days_info_cycle_title,
            explanationRes = R.string.onboarding_days_info_cycle_explanation,
            normalRangeRes = R.string.onboarding_days_info_cycle_range,
            averageRes = R.string.onboarding_days_info_cycle_average,
        )
    }
}

@Composable
private fun rememberDateFormatter(
    @StringRes patternRes: Int,
): DateTimeFormatter {
    val pattern = stringResource(patternRes)
    return remember(pattern) {
        DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
    }
}

private fun healthStepCopy(step: OnboardingFlowStep): HealthStepCopy = when (step) {
    OnboardingFlowStep.DateOfBirth -> HealthStepCopy(
        titleRes = R.string.onboarding_health_dob_title,
        subtitleRes = R.string.onboarding_health_generic_subtitle,
    )
    OnboardingFlowStep.Height -> HealthStepCopy(
        titleRes = R.string.onboarding_health_height_title,
        subtitleRes = R.string.onboarding_health_generic_subtitle,
    )
    OnboardingFlowStep.Weight -> HealthStepCopy(
        titleRes = R.string.onboarding_health_weight_title,
        subtitleRes = R.string.onboarding_health_generic_subtitle,
    )
    OnboardingFlowStep.LastPeriod -> HealthStepCopy(
        titleRes = R.string.onboarding_health_last_period_title,
        subtitleRes = R.string.onboarding_health_generic_subtitle,
    )
    OnboardingFlowStep.PeriodLength -> HealthStepCopy(
        titleRes = R.string.onboarding_health_period_length_title,
        subtitleRes = R.string.onboarding_health_generic_subtitle,
    )
    OnboardingFlowStep.CycleLength -> HealthStepCopy(
        titleRes = R.string.onboarding_health_cycle_length_title,
        subtitleRes = R.string.onboarding_health_generic_subtitle,
    )
    OnboardingFlowStep.HealthConditions -> HealthStepCopy(
        titleRes = R.string.onboarding_health_conditions_title,
        subtitleRes = R.string.onboarding_health_conditions_subtitle,
    )
    else -> HealthStepCopy(
        titleRes = R.string.onboarding_continue,
        subtitleRes = R.string.onboarding_health_generic_subtitle,
    )
}

private fun monthGrid(month: YearMonth): List<LocalDate?> {
    val firstDay = month.atDay(1)
    val leadingBlanks = firstDay.dayOfWeek.value % 7
    val days = MutableList<LocalDate?>(leadingBlanks) { null }
    repeat(month.lengthOfMonth()) { dayIndex ->
        days += month.atDay(dayIndex + 1)
    }
    // iOS `SakhiCalendarView` fixes the grid at `cellHeight * 6 + rowSpacing * 5`
    // and `daysFor` always yields 42 slots, so the card is the same height in every
    // month. Padding only to a multiple of 7 gave 5-row months a visibly shorter
    // card that jumped when you paged.
    while (days.size < CALENDAR_GRID_SLOTS) {
        days += null
    }
    return days
}

/** 6 weeks x 7 days -- iOS renders a constant 6-row grid regardless of the month. */
private const val CALENDAR_GRID_SLOTS = 42

private fun Double.roundToNearestInt(): Int = roundToInt().coerceAtLeast(0)

private const val INCH_TO_CM = 2.54
private const val POUNDS_PER_KILOGRAM = 2.20462
