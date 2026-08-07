package team.sakhi.android.feature.profile

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.ui.DetailSheetScaffold
import team.sakhi.models.UserProfile
import team.sakhi.repositories.UserProfileRepository
import team.sakhi.session.SessionManager
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiSystemBackground
import team.sakhi.android.designsystem.sakhiTertiaryLabel
import team.sakhi.android.common.toSafeUserMessage

private enum class EditProfileRoute {
    Root, Name, Height, Weight
}

/**
 * Real port of iOS `EditProfileView.swift`'s actual interaction pattern, not
 * just its fields: an Edit/Done-gated set of rows (Name/Height/Weight), each
 * drilling into its own dedicated editor screen with a real custom drag-driven
 * `HeightRulerPicker`/`WeightWheelPicker` (`:core:ui`), matching iOS's
 * `NameEditView`/`HeightEditView`/`WeightEditView`. Previously ported as one
 * flat inline form with plain text fields -- a real, previously-unflagged
 * interaction-pattern gap found doing a genuine iOS side-by-side comparison,
 * now closed. Height/weight stay hidden for partner-role viewers, matching
 * iOS's `isPartnerRole` gate. No product-logic change: still the same
 * `UserProfileRepository.upsert`/`SessionManager` this screen always used.
 */
@Composable
fun EditProfileScreen(onBack: () -> Unit) {
    val sessionManager = koinInject<SessionManager>()
    val userProfileRepository = koinInject<UserProfileRepository>()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var route by remember { mutableStateOf(EditProfileRoute.Root) }
    var isEditing by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // Mirrors iOS `EditProfileView`'s own `@State` fields exactly, including
    // scope/lifetime: `name`/`heightCm`/`weightKg` are the *committed* values
    // (populated once from the real profile), `useMetricHeight`/
    // `useMetricWeight` are session-only display prefs that reset to metric
    // every time this screen is freshly entered -- iOS never persists them
    // either (confirmed: `populate()` never touches them).
    var name by remember { mutableStateOf("") }
    var heightCm by remember { mutableDoubleStateOf(0.0) }
    var weightKg by remember { mutableDoubleStateOf(0.0) }
    var useMetricHeight by remember { mutableStateOf(true) }
    var useMetricWeight by remember { mutableStateOf(true) }

    // Reactive, not a one-shot `sessionManager.current` snapshot -- same fix as
    // `NotificationsScreen.kt` earlier tonight: a same-user own-data/partner-view
    // session flip while this screen is open must actually update which fields show.
    val currentSession by sessionManager.session.collectAsStateWithLifecycle()
    val isPartnerRole = currentSession?.isViewingOwnData == false

    // Same gap as elsewhere in the app: system back from the Name/Height/Weight
    // sub-editors used to skip straight past Root and close the whole sheet.
    BackHandler(enabled = route != EditProfileRoute.Root) { route = EditProfileRoute.Root }

    LaunchedEffect(Unit) {
        val userId = sessionManager.current?.userId
        if (userId == null) {
            isLoading = false
            return@LaunchedEffect
        }
        userProfileRepository.get(userId)
            .onSuccess {
                profile = it
                name = it?.name.orEmpty()
                heightCm = it?.heightCm?.takeIf { h -> h > 0 } ?: 0.0
                weightKg = it?.weightKg?.takeIf { w -> w > 0 } ?: 0.0
            }
            .onFailure { error = it.toSafeUserMessage(context, R.string.edit_profile_load_failed) }
        isLoading = false
    }

    fun persist(newName: String, newHeightCm: Double, newWeightKg: Double) {
        val userId = sessionManager.current?.userId ?: return
        val current = profile ?: UserProfile(id = userId, name = "", email = "", phone = "")
        scope.launch {
            val updated = current.copy(
                name = mergeEditedProfileName(current.name, newName),
                heightCm = newHeightCm.takeIf { it > 0 } ?: current.heightCm,
                weightKg = newWeightKg.takeIf { it > 0 } ?: current.weightKg,
            )
            userProfileRepository.upsert(updated)
                .onSuccess { profile = updated }
                .onFailure { error = it.toSafeUserMessage(context, R.string.edit_profile_save_failed) }
        }
    }

    when (route) {
        EditProfileRoute.Root -> {
            DetailSheetScaffold(
                title = stringResource(R.string.edit_profile_title),
                subtitle = stringResource(
                    if (isPartnerRole) {
                        R.string.edit_profile_subtitle_partner
                    } else {
                        R.string.edit_profile_subtitle_self
                    },
                ),
                headerIcon = Icons.Filled.Person,
                onBack = onBack,
                trailingHeaderContent = {
                    TextButton(onClick = { isEditing = !isEditing }) {
                        Text(
                            text = stringResource(
                                if (isEditing) R.string.edit_profile_done else R.string.edit_profile_edit,
                            ),
                        )
                    }
                },
            ) {
                if (isLoading) {
                    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4)) {
                        Surface(
                            color = sakhiSystemBackground(),
                            shape = RoundedCornerShape(SakhiRadius.xl),
                            tonalElevation = SakhiSpacing.space1,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column {
                                EditProfileRow(
                                    label = stringResource(R.string.edit_profile_name),
                                    value = name.ifBlank { stringResource(R.string.edit_profile_not_set) },
                                    isEditing = isEditing,
                                    onClick = { route = EditProfileRoute.Name },
                                )
                                if (!isPartnerRole) {
                                    val notSetText = stringResource(R.string.edit_profile_not_set)
                                    RowDivider()
                                    EditProfileRow(
                                        label = stringResource(R.string.edit_profile_height),
                                        value = heightDisplay(context, heightCm, useMetricHeight, notSetText),
                                        isEditing = isEditing,
                                        onClick = { route = EditProfileRoute.Height },
                                    )
                                    RowDivider()
                                    EditProfileRow(
                                        label = stringResource(R.string.edit_profile_weight),
                                        value = weightDisplay(context, weightKg, useMetricWeight, notSetText),
                                        isEditing = isEditing,
                                        onClick = { route = EditProfileRoute.Weight },
                                    )
                                }
                            }
                        }

                        profile?.phone?.takeIf { it.isNotBlank() }?.let { phone ->
                            Surface(
                                color = sakhiSystemBackground(),
                                shape = RoundedCornerShape(SakhiRadius.xl),
                                tonalElevation = SakhiSpacing.space1,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(SakhiSpacing.space4),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(text = stringResource(R.string.edit_profile_phone), style = MaterialTheme.typography.bodyLarge)
                                    Text(text = phone, style = MaterialTheme.typography.bodyMedium, color = sakhiSecondaryLabel())
                                }
                            }
                        }

                        error?.let {
                            Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        EditProfileRoute.Name -> {
            NameEditScreen(
                initialName = name,
                onBack = { route = EditProfileRoute.Root },
                onSave = { newName ->
                    name = mergeEditedProfileName(name, newName)
                    persist(newName, heightCm, weightKg)
                    route = EditProfileRoute.Root
                },
            )
        }

        EditProfileRoute.Height -> {
            HeightEditScreen(
                initialHeightCm = heightCm,
                initialUseMetric = useMetricHeight,
                onBack = { route = EditProfileRoute.Root },
                onSave = { newHeightCm, newUseMetric ->
                    heightCm = newHeightCm
                    useMetricHeight = newUseMetric
                    persist(name, newHeightCm, weightKg)
                    route = EditProfileRoute.Root
                },
            )
        }

        EditProfileRoute.Weight -> {
            WeightEditScreen(
                initialWeightKg = weightKg,
                initialUseMetric = useMetricWeight,
                onBack = { route = EditProfileRoute.Root },
                onSave = { newWeightKg, newUseMetric ->
                    weightKg = newWeightKg
                    useMetricWeight = newUseMetric
                    persist(name, heightCm, newWeightKg)
                    route = EditProfileRoute.Root
                },
            )
        }
    }
}

internal fun mergeEditedProfileName(currentName: String, submittedName: String): String {
    return submittedName.trim().ifBlank { currentName }
}

@Composable
private fun EditProfileRow(
    label: String,
    value: String,
    isEditing: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isEditing, onClick = onClick)
            .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space4),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isEditing) MaterialTheme.colorScheme.primary else sakhiSecondaryLabel(),
                maxLines = 1,
            )
            if (isEditing) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = sakhiTertiaryLabel(),
                    modifier = Modifier.height(SakhiSpacing.space5),
                )
            }
        }
    }
}

@Composable
private fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
    )
}

private fun heightDisplay(
    context: Context,
    heightCm: Double,
    useMetric: Boolean,
    notSetText: String,
): String {
    if (heightCm <= 0) return notSetText
    return if (useMetric) {
        context.getString(
            R.string.edit_profile_height_value_metric,
            heightCm.roundToInt(),
            context.getString(R.string.edit_profile_height_unit_cm),
        )
    } else {
        val totalIn = heightCm / 2.54
        val feet = (totalIn / 12).toInt()
        val inches = (totalIn % 12).toInt()
        context.getString(R.string.edit_profile_height_value_imperial, feet, inches)
    }
}

private fun weightDisplay(
    context: Context,
    weightKg: Double,
    useMetric: Boolean,
    notSetText: String,
): String {
    if (weightKg <= 0) return notSetText
    val unit = if (useMetric) WeightUnit.Kilograms else WeightUnit.Pounds
    return context.getString(
        R.string.edit_profile_weight_value_format,
        unit.displayValue(weightKg),
        context.getString(unit.symbolRes),
    )
}
