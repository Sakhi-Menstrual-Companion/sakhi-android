package team.sakhi.android.feature.emergency

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.emergency.EmergencyState

/**
 * Root of Emergency Assistance on Android. Draws whatever step the shared
 * [team.sakhi.emergency.EmergencyStore] reports.
 *
 * This screen owns no step logic — it does not know that picking a requirement leads to
 * picking a spot. KMM decides that, which is what lets a request accepted while the app
 * was backgrounded resume on the correct screen, and what keeps this identical to iOS.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyFlowScreen(
    onClose: () -> Unit,
    onFindSafePlaces: () -> Unit,
    deepLinkRequestId: String? = null,
    openResponderInbox: Boolean = false,
    viewModel: EmergencyViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val nearbyCount by viewModel.nearbyAvailableCount.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        viewModel.onLocationPermissionResult(granted.values.any { it })
    }

    LaunchedEffect(deepLinkRequestId) {
        if (deepLinkRequestId != null) {
            viewModel.openSession(deepLinkRequestId)
        } else {
            viewModel.restore()
        }
    }

    // Restore lands on Idle when she has nothing in flight, which is the point to start a
    // fresh request — that is what tapping the map button asked for.
    LaunchedEffect(state) {
        if (deepLinkRequestId == null && state is EmergencyState.Idle) viewModel.begin()
    }

    LaunchedEffect(Unit) {
        if (!uiState.hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = {
                        viewModel.dismiss()
                        onClose()
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.emergency_close),
                        )
                    }
                },
            )
        },
    ) { padding ->
        AnimatedContent(
            targetState = state.stepKey(),
            transitionSpec = {
                (
                    slideInVertically(animationSpec = spring(dampingRatio = 0.86f, stiffness = 380f)) { it / 12 } +
                        fadeIn()
                    ).togetherWith(fadeOut())
            },
            label = "emergency-step",
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) { key ->
            // Read the live value rather than closing over the animated key, so an offer
            // arriving mid-step updates the list instead of animating the whole screen.
            when (val current = remember(key) { state }) {
                is EmergencyState.Loading -> EmergencyLoading()

                is EmergencyState.Idle,
                is EmergencyState.ChoosingRequirement,
                -> if (nearbyCount == 0) {
                    // main's presentNoActiveRequestBottomSheet(): empty state when nobody
                    // is around, picker otherwise. Null keeps showing the picker rather
                    // than flashing an empty state while the count is still loading.
                    EmergencyNoNearbyStep(viewModel = viewModel, onFindSafePlaces = onFindSafePlaces)
                } else {
                    EmergencyRequirementStep(
                        viewModel = viewModel,
                        onFindSafePlaces = onFindSafePlaces,
                        startOnResponderInbox = openResponderInbox,
                    )
                }

                is EmergencyState.ChoosingSpot -> EmergencySpotStep(
                    viewModel = viewModel,
                    requirement = current.requirement,
                )

                is EmergencyState.WaitingForHelp -> EmergencyWaitingStep(
                    viewModel = viewModel,
                    step = current,
                )

                is EmergencyState.InSession -> EmergencySessionStep(
                    viewModel = viewModel,
                    step = current,
                )

                is EmergencyState.Completed -> EmergencyFeedbackStep(
                    viewModel = viewModel,
                    session = current.session,
                    onDone = onClose,
                )

                is EmergencyState.Failed -> EmergencyError(
                    message = current.message,
                    onRetry = viewModel::begin,
                )
            }
        }
    }
}

/**
 * Animating on the state object itself would restart the transition every time an offer
 * arrives. Keying on step identity keeps it to real step changes.
 */
private fun EmergencyState.stepKey(): String = when (this) {
    is EmergencyState.Idle -> "idle"
    is EmergencyState.Loading -> "loading"
    is EmergencyState.ChoosingRequirement -> "requirement"
    is EmergencyState.ChoosingSpot -> "spot"
    is EmergencyState.WaitingForHelp -> "waiting"
    is EmergencyState.InSession -> "session"
    is EmergencyState.Completed -> "completed"
    is EmergencyState.Failed -> "failed"
}

@Composable
internal fun EmergencyLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(
                text = stringResource(R.string.emergency_one_moment),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
