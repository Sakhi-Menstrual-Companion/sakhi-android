package team.sakhi.android.feature.onboarding

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import org.koin.compose.koinInject
import team.sakhi.android.designsystem.SakhiFontSize
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.HapticImpact
import team.sakhi.android.ui.PrimaryButton
import team.sakhi.android.ui.SakhiTextField
import team.sakhi.models.HealthCondition
import team.sakhi.onboarding.OnboardingFlowStep
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SakhiSpacing.space6),
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
    ) {
        LinearProgressIndicator(
            progress = navStateProgress.coerceIn(0f, 1f),
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = copy.title,
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = copy.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

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
                onDateSelected = onLastPeriodDateChanged,
                onPreviousMonth = onPreviousLastPeriodMonth,
                onNextMonth = onNextLastPeriodMonth,
            )
            OnboardingFlowStep.PeriodLength -> DaysLengthStepContent(
                value = uiState.periodLengthText,
                placeholder = "e.g. 5",
                infoButtonText = "What is period length?",
                info = DaysInfo.periodLength,
                onValueChanged = onPeriodLengthChanged,
            )
            OnboardingFlowStep.CycleLength -> DaysLengthStepContent(
                value = uiState.cycleLengthText,
                placeholder = "e.g. 28",
                infoButtonText = "What is cycle length?",
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

        PrimaryButton(
            text = "Continue",
            onClick = onContinue,
            modifier = Modifier.padding(top = SakhiSpacing.space2),
        )

        if (canGoBack) {
            TextButton(onClick = onBack) {
                Text("Back")
            }
        }
    }
}

@Composable
private fun DateOfBirthStepContent(
    dateOfBirth: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
) {
    val context = LocalContext.current
    val zoneId = remember { ZoneId.systemDefault() }
    val minDate = remember { LocalDate.now().minusYears(80) }
    val maxDate = remember { LocalDate.now().minusYears(12) }

    PinkCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        // iOS presents a wheel-style DOB picker in a bottom sheet. Android uses the
        // platform date picker here until the shared sheet lane is in place.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SakhiSpacing.space12 + SakhiSpacing.space2)
                .clickable {
                    DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            onDateSelected(LocalDate.of(year, month + 1, dayOfMonth))
                        },
                        dateOfBirth.year,
                        dateOfBirth.monthValue - 1,
                        dateOfBirth.dayOfMonth,
                    ).apply {
                        datePicker.minDate = minDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
                        datePicker.maxDate = maxDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
                    }.show()
                }
                .padding(horizontal = SakhiSpacing.space4),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = dateOfBirth.format(DateFormatters.dateOfBirth),
                style = MaterialTheme.typography.bodyLarge,
            )
            Icon(
                imageVector = Icons.Rounded.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
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
    val totalInches = ((heightCm / INCH_TO_CM).roundToNearestInt()).coerceIn(36, 84)
    val feet = totalInches / 12
    val inches = totalInches % 12
    val displayCm = heightCm.coerceIn(100.0, 220.0).roundToNearestInt()

    PinkCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
        ) {
            SegmentedToggle(
                options = listOf("ft/in", "cm"),
                selectedIndex = if (useImperial) 0 else 1,
                onSelected = { onUnitChanged(it == 0) },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (useImperial) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space1),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        LargeValueText(text = feet.toString())
                        UnitText(text = "ft")
                        LargeValueText(text = inches.toString())
                        UnitText(text = "in")
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space1),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        LargeValueText(text = displayCm.toString())
                        UnitText(text = "cm")
                    }
                }
            }

            // iOS uses a custom vertical ruler picker. Android keeps the same
            // canonical cm state and unit toggle, but uses a slider for now.
            Slider(
                value = if (useImperial) totalInches.toFloat() else displayCm.toFloat(),
                onValueChange = { next ->
                    if (useImperial) {
                        onHeightCmChanged(next.toDouble().roundToNearestInt() * INCH_TO_CM)
                    } else {
                        onHeightCmChanged(next.toDouble().roundToNearestInt().toDouble())
                    }
                },
                valueRange = if (useImperial) 36f..84f else 100f..220f,
            )
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
    val displayWeight = if (useMetric) {
        weightKg.coerceIn(30.0, 150.0).roundToNearestInt()
    } else {
        (weightKg.coerceIn(30.0, 150.0) * POUNDS_PER_KILOGRAM).roundToNearestInt()
    }

    PinkCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
        ) {
            SegmentedToggle(
                options = listOf("kg", "lbs"),
                selectedIndex = if (useMetric) 0 else 1,
                onSelected = { onUnitChanged(it == 0) },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom,
            ) {
                LargeValueText(text = displayWeight.toString())
                Spacer(modifier = Modifier.width(SakhiSpacing.space1))
                UnitText(text = if (useMetric) "kg" else "lbs")
            }

            // iOS uses a rotating dial wheel here. Android uses a slider until the
            // matching custom wheel component is built.
            Slider(
                value = displayWeight.toFloat(),
                onValueChange = { next ->
                    val rounded = next.toDouble().roundToNearestInt()
                    onWeightKgChanged(
                        if (useMetric) rounded.toDouble()
                        else rounded / POUNDS_PER_KILOGRAM
                    )
                },
                valueRange = if (useMetric) 30f..150f else 66f..331f,
            )
        }
    }
}

@Composable
private fun LastPeriodStepContent(
    selectedDate: LocalDate,
    displayedMonth: YearMonth,
    onDateSelected: (LocalDate) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    val currentMonth = remember { YearMonth.now() }
    val days = remember(displayedMonth) { monthGrid(displayedMonth) }

    PinkCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPreviousMonth) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronLeft,
                        contentDescription = "Previous month",
                    )
                }
                Text(
                    text = displayedMonth.format(DateFormatters.monthHeader),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                IconButton(
                    onClick = onNextMonth,
                    enabled = displayedMonth < currentMonth,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = "Next month",
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                WeekdayLabels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

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

@Composable
private fun DaysLengthStepContent(
    value: String,
    placeholder: String,
    infoButtonText: String,
    info: DaysInfo,
    onValueChanged: (String) -> Unit,
) {
    var showInfo by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
    ) {
        SakhiTextField(
            value = value,
            onValueChange = onValueChanged,
            placeholder = placeholder,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            trailingContent = {
                Text(
                    text = "days",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )

        TextButton(
            onClick = { showInfo = true },
            modifier = Modifier.align(Alignment.End),
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                modifier = Modifier.size(SakhiSpacing.space4),
            )
            Spacer(modifier = Modifier.width(SakhiSpacing.space1))
            Text(infoButtonText)
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(info.title) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                ) {
                    Text(info.explanation)
                    InfoPill(label = "Normal range", value = info.normalRange)
                    InfoPill(label = "Average", value = info.average)
                    Text(
                        text = "Source: American College of Obstetricians and Gynecologists (ACOG)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) {
                    Text("Done")
                }
            },
        )
    }
}

@Composable
private fun HealthConditionsStepContent(
    selectedConditions: Set<HealthCondition>,
    onConditionToggled: (HealthCondition) -> Unit,
) {
    val hapticManager = koinInject<AndroidHapticManager>()
    PinkCard {
        Column {
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
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space1 / 2),
        ) {
            Text(
                text = condition.displayName,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = condition.shortDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .size(SakhiSpacing.space5)
                .background(
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
                    shape = CircleShape,
                )
                .border(
                    width = SakhiSpacing.space1 / 8,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(SakhiSpacing.space3),
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(SakhiRadius.full),
            )
            .padding(SakhiSpacing.space1),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = if (selected) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent,
                        shape = RoundedCornerShape(SakhiRadius.full),
                    )
                    .clickable { onSelected(index) }
                    .padding(vertical = SakhiSpacing.space2 + SakhiSpacing.space1 / 4),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    ),
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
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(SakhiRadius.xl),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(SakhiSpacing.space4),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
            content = content,
        )
    }
}

@Composable
private fun InfoPill(
    label: String,
    value: String,
) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        shape = RoundedCornerShape(SakhiRadius.lg),
    ) {
        Column(
            modifier = Modifier.padding(SakhiSpacing.space3),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space1),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
        }
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
    Box(
        modifier = modifier
            .height(SakhiSpacing.space8 + SakhiSpacing.space1 / 2)
            .background(
                color = if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(SakhiRadius.full),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = date?.dayOfMonth?.toString().orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = when {
                date == null -> androidx.compose.ui.graphics.Color.Transparent
                isSelected -> MaterialTheme.colorScheme.onPrimary
                enabled -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            },
        )
    }
}

@Composable
private fun LargeValueText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium.copy(
            fontSize = SakhiFontSize.xxxxl,
            fontWeight = FontWeight.Bold,
        ),
    )
}

@Composable
private fun UnitText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private data class HealthStepCopy(
    val title: String,
    val subtitle: String,
)

private data class DaysInfo(
    val title: String,
    val explanation: String,
    val normalRange: String,
    val average: String,
) {
    companion object {
        val periodLength = DaysInfo(
            title = "Period Length",
            explanation = "Your period length is the number of days you bleed each cycle. It starts on the first day of noticeable bleeding and ends when it stops completely. A period lasting 3–8 days is considered normal, and the average is about 5 days.",
            normalRange = "3–8 days",
            average = "5 days",
        )
        val cycleLength = DaysInfo(
            title = "Cycle Length",
            explanation = "Your cycle length is the number of days from the first day of one period to the first day of the next. Every body is different, a cycle anywhere from 21 to 35 days is completely normal. The average is 28 days.",
            normalRange = "21–35 days",
            average = "28 days",
        )
    }
}

private object DateFormatters {
    val dateOfBirth: DateTimeFormatter = DateTimeFormatter.ofPattern("dd / MM / yyyy", Locale.getDefault())
    val monthHeader: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
}

private val WeekdayLabels = listOf("S", "M", "T", "W", "T", "F", "S")

private fun healthStepCopy(step: OnboardingFlowStep): HealthStepCopy = when (step) {
    OnboardingFlowStep.DateOfBirth -> HealthStepCopy(
        title = "When were you born?",
        subtitle = "It helps me understand you more deeply.",
    )
    OnboardingFlowStep.Height -> HealthStepCopy(
        title = "Tell me your height",
        subtitle = "It helps me understand you more deeply.",
    )
    OnboardingFlowStep.Weight -> HealthStepCopy(
        title = "Tell me your weight",
        subtitle = "It helps me understand you more deeply.",
    )
    OnboardingFlowStep.LastPeriod -> HealthStepCopy(
        title = "When did your last period begin?",
        subtitle = "It helps me understand you more deeply.",
    )
    OnboardingFlowStep.PeriodLength -> HealthStepCopy(
        title = "How many days does your period last?",
        subtitle = "It helps me understand you more deeply.",
    )
    OnboardingFlowStep.CycleLength -> HealthStepCopy(
        title = "How many days is your cycle?",
        subtitle = "It helps me understand you more deeply.",
    )
    OnboardingFlowStep.HealthConditions -> HealthStepCopy(
        title = "Anything we should know?",
        subtitle = "This helps Sakhi give you advice that actually fits you. You can always update this later.",
    )
    else -> HealthStepCopy("", "")
}

private fun monthGrid(month: YearMonth): List<LocalDate?> {
    val firstDay = month.atDay(1)
    val leadingBlanks = firstDay.dayOfWeek.value % 7
    val days = MutableList<LocalDate?>(leadingBlanks) { null }
    repeat(month.lengthOfMonth()) { dayIndex ->
        days += month.atDay(dayIndex + 1)
    }
    while (days.size % 7 != 0) {
        days += null
    }
    return days
}

private fun Double.roundToNearestInt(): Int = roundToInt().coerceAtLeast(0)

private const val INCH_TO_CM = 2.54
private const val POUNDS_PER_KILOGRAM = 2.20462
