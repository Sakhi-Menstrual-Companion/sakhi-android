package team.sakhi.android.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.annotation.StringRes
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import org.koin.compose.koinInject
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.HapticImpact
import team.sakhi.android.ui.SakhiAlertKind
import team.sakhi.android.ui.SakhiAlertSheet
import team.sakhi.android.ui.DetailSheetScaffold
import team.sakhi.android.ui.HeightRulerPicker
import team.sakhi.android.ui.PrimaryButton
import team.sakhi.android.ui.SakhiTextField
import team.sakhi.android.ui.WeightWheelPicker
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiTertiaryLabel

private const val CM_PER_INCH = 2.54
private const val HEIGHT_CM_MIN = 50.0
private const val HEIGHT_CM_MAX = 220.0
private const val HEIGHT_IN_MIN = 20.0
private const val HEIGHT_IN_MAX = 87.0
private const val DEFAULT_HEIGHT_CM = 160.0
private const val DEFAULT_WEIGHT_KG = 60.0

/**
 * Real port of iOS `WeightUnit` (`OnboardingInputPickers.swift`) -- kg/lbs
 * conversion is client-side-only in both apps, never persisted or computed in
 * KMM. Kept package-visible since [EditProfileScreen]'s row-value formatter
 * also needs it.
 */
enum class WeightUnit(@StringRes val symbolRes: Int) {
    Kilograms(R.string.edit_profile_weight_unit_kg),
    Pounds(R.string.edit_profile_weight_unit_lbs),
    ;

    fun displayValue(fromKilograms: Double): Int {
        val clamped = fromKilograms.coerceIn(KG_MIN, KG_MAX)
        return when (this) {
            Kilograms -> clamped.roundToInt()
            Pounds -> (clamped * POUNDS_PER_KG).roundToInt()
        }
    }

    fun kilograms(fromDisplayValue: Int): Double {
        val kg = if (this == Kilograms) fromDisplayValue.toDouble() else fromDisplayValue / POUNDS_PER_KG
        return kg.coerceIn(KG_MIN, KG_MAX)
    }

    val selectionRange: IntRange
        get() = when (this) {
            Kilograms -> KG_MIN.roundToInt()..KG_MAX.roundToInt()
            Pounds -> (KG_MIN * POUNDS_PER_KG).roundToInt()..(KG_MAX * POUNDS_PER_KG).roundToInt()
        }

    companion object {
        const val POUNDS_PER_KG = 2.20462
        const val KG_MIN = 30.0
        const val KG_MAX = 150.0
    }
}

@Composable
internal fun NameEditScreen(
    initialName: String,
    onBack: () -> Unit,
    onSave: (String) -> Unit,
) {
    var draft by remember { mutableStateOf(initialName) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    val hasChanges = draft.trim() != initialName.trim()

    fun exit() {
        if (hasChanges) showDiscardDialog = true else onBack()
    }

    DetailSheetScaffold(
        title = stringResource(R.string.edit_profile_name_title),
        onBack = ::exit,
    ) {
        Text(
            text = stringResource(R.string.edit_profile_name_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = sakhiSecondaryLabel(),
        )
        SakhiTextField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = stringResource(R.string.edit_profile_name_placeholder),
        )
        PrimaryButton(
            text = stringResource(R.string.edit_profile_done),
            onClick = { onSave(draft) },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (showDiscardDialog) {
        UnsavedChangesDialog(
            onDismiss = { showDiscardDialog = false },
            onSave = {
                showDiscardDialog = false
                onSave(draft)
            },
            onDiscard = {
                showDiscardDialog = false
                onBack()
            },
        )
    }
}

@Composable
internal fun HeightEditScreen(
    initialHeightCm: Double,
    initialUseMetric: Boolean,
    onBack: () -> Unit,
    onSave: (heightCm: Double, useMetric: Boolean) -> Unit,
) {
    val hapticManager = koinInject<AndroidHapticManager>()
    val fallbackCm = initialHeightCm.takeIf { it > 0 } ?: DEFAULT_HEIGHT_CM

    var draftCm by remember { mutableFloatStateOf(fallbackCm.toFloat()) }
    var draftMetric by remember { mutableStateOf(initialUseMetric) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val hasChanges = abs(draftCm - fallbackCm) > 0.5f || draftMetric != initialUseMetric

    fun exit() {
        if (hasChanges) showDiscardDialog = true else onBack()
    }

    val rulerRange = if (draftMetric) {
        HEIGHT_CM_MIN.toFloat()..HEIGHT_CM_MAX.toFloat()
    } else {
        HEIGHT_IN_MIN.toFloat()..HEIGHT_IN_MAX.toFloat()
    }
    val rulerValue = if (draftMetric) draftCm else (draftCm / CM_PER_INCH.toFloat())
    val unitLabel = stringResource(
        if (draftMetric) R.string.edit_profile_height_unit_cm else R.string.edit_profile_height_unit_in,
    )
    val displayValue = if (draftMetric) draftCm.roundToInt() else (draftCm / CM_PER_INCH.toFloat()).roundToInt()

    DetailSheetScaffold(
        title = stringResource(R.string.edit_profile_height_title),
        onBack = ::exit,
    ) {
        Text(
            text = stringResource(R.string.edit_profile_height_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = sakhiSecondaryLabel(),
        )

        UnitPillToggle(
            isFirstSelected = draftMetric,
            firstLabel = stringResource(R.string.edit_profile_height_unit_cm),
            secondLabel = stringResource(R.string.edit_profile_height_unit_in),
            onSelectFirst = { draftMetric = true },
            onSelectSecond = { draftMetric = false },
        )

        PickerValueHeader(value = displayValue.toString(), unit = unitLabel)

        HeightRulerPicker(
            value = rulerValue,
            range = rulerRange,
            onValueChange = { newDisplayValue ->
                draftCm = if (draftMetric) newDisplayValue else newDisplayValue * CM_PER_INCH.toFloat()
            },
            onHapticSelection = { hapticManager.selection() },
            onHapticImpact = { hapticManager.impact(HapticImpact.LIGHT) },
        )

        PrimaryButton(
            text = stringResource(R.string.edit_profile_done),
            onClick = { onSave(draftCm.toDouble(), draftMetric) },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (showDiscardDialog) {
        UnsavedChangesDialog(
            onDismiss = { showDiscardDialog = false },
            onSave = {
                showDiscardDialog = false
                onSave(draftCm.toDouble(), draftMetric)
            },
            onDiscard = {
                showDiscardDialog = false
                onBack()
            },
        )
    }
}

@Composable
internal fun WeightEditScreen(
    initialWeightKg: Double,
    initialUseMetric: Boolean,
    onBack: () -> Unit,
    onSave: (weightKg: Double, useMetric: Boolean) -> Unit,
) {
    val hapticManager = koinInject<AndroidHapticManager>()
    val fallbackKg = initialWeightKg.takeIf { it > 0 } ?: DEFAULT_WEIGHT_KG

    var draftKg by remember { mutableFloatStateOf(fallbackKg.toFloat()) }
    var draftMetric by remember { mutableStateOf(initialUseMetric) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val hasChanges = abs(draftKg - fallbackKg) > 0.5f || draftMetric != initialUseMetric

    fun exit() {
        if (hasChanges) showDiscardDialog = true else onBack()
    }

    val weightUnit = if (draftMetric) WeightUnit.Kilograms else WeightUnit.Pounds
    val selectedValue = weightUnit.displayValue(draftKg.toDouble())

    DetailSheetScaffold(
        title = stringResource(R.string.edit_profile_weight_title),
        onBack = ::exit,
    ) {
        Text(
            text = stringResource(R.string.edit_profile_weight_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = sakhiSecondaryLabel(),
        )

        UnitPillToggle(
            isFirstSelected = draftMetric,
            firstLabel = stringResource(R.string.edit_profile_weight_unit_kg),
            secondLabel = stringResource(R.string.edit_profile_weight_unit_lbs),
            onSelectFirst = { draftMetric = true },
            onSelectSecond = { draftMetric = false },
        )

        PickerValueHeader(value = selectedValue.toString(), unit = stringResource(weightUnit.symbolRes))

        WeightWheelPicker(
            value = selectedValue,
            range = weightUnit.selectionRange,
            onValueChange = { newDisplayValue ->
                draftKg = weightUnit.kilograms(newDisplayValue).toFloat()
            },
            onHapticSelection = { hapticManager.selection() },
            onHapticImpact = { hapticManager.impact(HapticImpact.LIGHT) },
        )

        PrimaryButton(
            text = stringResource(R.string.edit_profile_done),
            onClick = { onSave(draftKg.toDouble(), draftMetric) },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (showDiscardDialog) {
        UnsavedChangesDialog(
            onDismiss = { showDiscardDialog = false },
            onSave = {
                showDiscardDialog = false
                onSave(draftKg.toDouble(), draftMetric)
            },
            onDiscard = {
                showDiscardDialog = false
                onBack()
            },
        )
    }
}

@Composable
private fun PickerValueHeader(value: String, unit: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            text = unit,
            style = MaterialTheme.typography.titleMedium,
            color = sakhiSecondaryLabel(),
            modifier = Modifier.padding(start = SakhiSpacing.space1, bottom = 2.dp),
        )
    }
}

@Composable
private fun UnitPillToggle(
    isFirstSelected: Boolean,
    firstLabel: String,
    secondLabel: String,
    onSelectFirst: () -> Unit,
    onSelectSecond: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
    ) {
        PillOption(label = firstLabel, selected = isFirstSelected, onClick = onSelectFirst)
        PillOption(label = secondLabel, selected = !isFirstSelected, onClick = onSelectSecond)
    }
}

@Composable
private fun PillOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(SakhiRadius.full),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        tonalElevation = if (selected) SakhiSpacing.space1 else 0.dp,
        onClick = onClick,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else sakhiSecondaryLabel(),
            modifier = Modifier.padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space2),
        )
    }
}

@Composable
private fun UnsavedChangesDialog(
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    SakhiAlertSheet(
        kind = SakhiAlertKind.Warning,
        title = stringResource(R.string.edit_profile_discard_title),
        message = stringResource(R.string.edit_profile_discard_message),
        primaryLabel = stringResource(R.string.edit_profile_discard_save),
        onPrimaryClick = onSave,
        secondaryLabel = stringResource(R.string.edit_profile_discard_discard),
        onSecondaryClick = onDiscard,
        onDismissRequest = onDismiss,
    )
}
