package team.sakhi.android.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.ui.PrimaryButton
import team.sakhi.android.ui.SheetSurface
import team.sakhi.models.UserProfile
import team.sakhi.repositories.UserProfileRepository
import team.sakhi.session.SessionManager

/**
 * Ports iOS `EditProfileView.swift`'s intent (name/height/weight, phone
 * read-only) as one inline form instead of iOS's 3 separate ruler/wheel-picker
 * sub-screens (`NameEditView`/`HeightEditView`/`WeightEditView`) -- same fields,
 * same save target (`UserProfileRepository`), simpler input widgets. Height/
 * weight are hidden for partner-role viewers, matching iOS.
 */
@Composable
fun EditProfileScreen(onBack: () -> Unit) {
    val sessionManager = koinInject<SessionManager>()
    val userProfileRepository = koinInject<UserProfileRepository>()
    val scope = rememberCoroutineScope()

    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var name by remember { mutableStateOf("") }
    var heightCm by remember { mutableStateOf("") }
    var weightKg by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }

    val isPartnerRole = sessionManager.current?.isViewingOwnData == false

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
                heightCm = it?.heightCm?.takeIf { h -> h > 0 }?.let { h -> h.toInt().toString() } ?: ""
                weightKg = it?.weightKg?.takeIf { w -> w > 0 }?.let { w -> w.toInt().toString() } ?: ""
            }
            .onFailure { error = it.message ?: "Failed to load your profile" }
        isLoading = false
    }

    SheetSurface(showDragHandle = true) {
        DetailHeader(title = "Personal Information", onBack = onBack)

        if (isLoading) {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
            return@SheetSurface
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(SakhiSpacing.space5),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
            )

            if (!isPartnerRole) {
                OutlinedTextField(
                    value = heightCm,
                    onValueChange = { value -> if (value.all { it.isDigit() }) heightCm = value },
                    label = { Text("Height (cm)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = weightKg,
                    onValueChange = { value -> if (value.all { it.isDigit() }) weightKg = value },
                    label = { Text("Weight (kg)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            profile?.phone?.takeIf { it.isNotBlank() }?.let { phone ->
                Surface(
                    shape = RoundedCornerShape(SakhiRadius.lg),
                    tonalElevation = SakhiSpacing.space1,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(SakhiSpacing.space4),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(text = "Phone", style = MaterialTheme.typography.bodyLarge)
                        Text(text = phone, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            error?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (saved) {
                Text(text = "Saved", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            PrimaryButton(
                text = "Save",
                enabled = !isSaving,
                onClick = {
                    val userId = sessionManager.current?.userId ?: return@PrimaryButton
                    val current = profile ?: return@PrimaryButton
                    isSaving = true
                    error = null
                    saved = false
                    scope.launch {
                        val updated = current.copy(
                            name = name.trim().ifBlank { current.name },
                            heightCm = heightCm.toDoubleOrNull() ?: current.heightCm,
                            weightKg = weightKg.toDoubleOrNull() ?: current.weightKg,
                        )
                        userProfileRepository.upsert(updated)
                            .onSuccess { profile = updated; saved = true }
                            .onFailure { error = it.message ?: "Couldn't save. Please try again." }
                        isSaving = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
