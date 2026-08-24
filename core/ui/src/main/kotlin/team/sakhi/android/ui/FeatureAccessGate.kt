package team.sakhi.android.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.annotation.DrawableRes
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import team.sakhi.config.RemoteConfigStore
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import team.sakhi.access.AppFeature
import team.sakhi.access.BlockReason
import team.sakhi.access.FeatureAccessResolver
import team.sakhi.access.FeatureAccessState
import team.sakhi.sync.SyncPauseState
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clipToBounds
import team.sakhi.android.designsystem.sakhiPageBackgroundBrush
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiTertiaryLabel

/**
 * Whole-feature access gate — the Android counterpart of iOS's
 * `FeatureAccessGate.swift`.
 *
 * Asks the KMM-owned [FeatureAccessResolver] whether the current viewer may use
 * [feature]; if not, shows a gentle, reason-specific explainer instead of the content.
 * Field-level masking *inside* a screen is a separate concern and stays where it is.
 *
 * Why this existed only on iOS until now: `FeatureAccessResolver`, `AppFeature` and
 * `BlockReason` have all been in `SakhiCore` and registered in Koin the whole time —
 * Android simply never built the UI, so a blocked feature had no honest way to explain
 * itself. Every string and image below is taken from the Swift source rather than
 * written fresh, so the two platforms say the same thing to the same person.
 */
@Composable
fun FeatureAccessGate(
    feature: AppFeature,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val resolver = koinInject<FeatureAccessResolver>()
    val accessState = koinInject<FeatureAccessState>()

    // Observe the state the resolver reads, so the gate re-evaluates when it changes —
    // offline mode toggled, account changed, connectivity lost or restored. iOS gets
    // this from `@ObservedObject access = FeatureAccessManager.shared`; its comment is
    // explicit that the decision is "re-evaluated automatically whenever
    // FeatureAccessManager publishes a change".
    //
    // Without collecting these, `resolve()` runs once at first composition and the gate
    // is frozen: a user who goes offline keeps seeing the feature, and one who comes
    // back online stays blocked until something unrelated forces a recomposition.
    // Verified: with connectivity fully down (0 networks) the already-open chat did not
    // gate at all.
    val isGuest by accessState.isGuest.collectAsStateWithLifecycle()
    val cloudAvailable by accessState.cloudAvailable.collectAsStateWithLifecycle()
    val onlinePaused by accessState.isOnlineAccountPaused.collectAsStateWithLifecycle()
    // The resolver checks remote config before every other rule, so a flag flipping on the
    // server has to re-run this. Without the snapshot in the key, a feature switched off
    // mid-session stayed on screen until something else happened to recompose the gate.
    val configValues by koinInject<RemoteConfigStore>().values.collectAsStateWithLifecycle()

    val decision = remember(feature, isGuest, cloudAvailable, onlinePaused, configValues) {
        resolver.resolve(feature)
    }
    if (decision.granted) {
        content()
    } else {
        FeatureAccessBlocked(
            reason = decision.reason,
            onBack = onBack,
            modifier = modifier,
            // `AppFeature.name` is the same string the server keys on.
            featureKey = feature.name,
        )
    }
}

/**
 * The "why + what to do" explainer. Copy and primary action are driven by the reason so
 * the message is always honest, matching iOS's `FeatureAccessBlockedView`.
 */
@Composable
fun FeatureAccessBlocked(
    reason: BlockReason?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** The `remote_config` key, for the paused state's notify button. */
    featureKey: String? = null,
) {
    val accessState = koinInject<FeatureAccessState>()
    val syncPauseState = koinInject<SyncPauseState>()
    val remoteConfigStore = koinInject<RemoteConfigStore>()
    val scope = rememberCoroutineScope()
    val copy = blockedCopyFor(reason)

    Column(
        modifier = modifier
            .fillMaxSize()
            // Opaque, matching iOS's `.profileStaticPageBackground()`. Without it the
            // explainer drew straight over whatever screen it replaced, so Home's
            // hero and cards showed through the copy and it was unreadable. It has to be
            // the page BRUSH, not the flat background role: in dark that role is pure
            // black, while iOS's modifier paints the follicular phase gradient.
            .background(sakhiPageBackgroundBrush())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = SakhiSpacing.space6),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            BackButton(onClick = onBack)
        }

        Spacer(modifier = Modifier.weight(1f))

        // The image leads, drawn taller than the box that shows it so it reads large
        // without pushing the copy down. Metrics are `IntroCarouselStep.Layout` on iOS --
        // a 235 container over a 300 visual -- which `SakhiIllustratedActionView` now uses
        // too, so this screen and the onboarding carousel sit at the same heights on both
        // platforms rather than at approximately the same heights.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(235.dp)
                .clipToBounds(),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(copy.image),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
            )
        }

        Text(
            text = stringResource(copy.title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            // iOS `Layout.titleTopGap`.
            modifier = Modifier.padding(top = 52.dp),
        )
        Text(
            text = stringResource(copy.message),
            style = MaterialTheme.typography.bodyMedium,
            color = sakhiSecondaryLabel(),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = SakhiSpacing.space2),
        )

        PrimaryButton(
            text = stringResource(copy.primaryLabel),
            onClick = {
                if (reason == BlockReason.OFFLINE_NEEDS_INTERNET) {
                    // Mirrors iOS `resumeOnline()`: release the held sync queue FIRST,
                    // then clear the flag. Clearing the flag alone (which is all this
                    // did before the pause flag existed) would have re-opened the
                    // feature while sync stayed paused forever — writes would queue up
                    // silently and never leave the device.
                    syncPauseState.resume()
                    accessState.setOnlineAccountPaused(false)
                } else if (reason == BlockReason.REMOTELY_DISABLED && featureKey != null) {
                    // Registers her interest, then leaves. The push is sent by the
                    // `notify-feature-available` Edge Function when the flag is switched
                    // back on. A failure is deliberately silent: she is already on a
                    // screen saying something is unavailable.
                    scope.launch {
                        remoteConfigStore.requestNotification(featureKey)
                        onBack()
                    }
                } else {
                    onBack()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = SakhiSpacing.space5),
        )

        Spacer(modifier = Modifier.weight(1f))
    }
}

private data class BlockedCopy(
    @StringRes val title: Int,
    @StringRes val message: Int,
    @StringRes val primaryLabel: Int,
    @DrawableRes val image: Int,
)

/** Reason → copy/image, transcribed from iOS `FeatureAccessBlockedView`. */
private fun blockedCopyFor(reason: BlockReason?): BlockedCopy = when (reason) {
    BlockReason.OFFLINE_NEEDS_INTERNET -> BlockedCopy(
        title = R.string.feature_gate_offline_title,
        message = R.string.feature_gate_offline_message,
        primaryLabel = R.string.feature_gate_resume_online,
        image = R.drawable.condition_offline,
    )
    BlockReason.NOT_SIGNED_IN -> BlockedCopy(
        title = R.string.feature_gate_not_signed_in_title,
        message = R.string.feature_gate_not_signed_in_message,
        primaryLabel = R.string.feature_gate_go_back,
        image = R.drawable.condition_join_family,
    )
    BlockReason.PARTNER_NO_PERMISSION -> BlockedCopy(
        title = R.string.feature_gate_no_permission_title,
        message = R.string.feature_gate_no_permission_message,
        primaryLabel = R.string.feature_gate_go_back,
        image = R.drawable.care_partner_onboarding,
    )
    BlockReason.REMOTELY_DISABLED -> BlockedCopy(
        title = R.string.feature_gate_paused_title,
        message = R.string.feature_gate_paused_message,
        // The notify button, not "Go back" -- this one records a request and then leaves.
        primaryLabel = R.string.feature_gate_notify_me,
        // Not the offline illustration: nothing is wrong with her connection and the
        // screen should not suggest otherwise.
        //
        // This pointed at `condition_upgrade`, which despite its name is a drawing of a
        // woman having her hair blow-dried in a salon. It has been deleted. `join_family`
        // is the nearest thing the asset set has to "we are working on it", because of the
        // gears behind it.
        image = R.drawable.condition_join_family,
    )
    // iOS treats an unmapped reason as `.unknown` with its own copy, and uses the same
    // care-partner illustration as `.noPermission`.
    null -> BlockedCopy(
        title = R.string.feature_gate_unknown_title,
        message = R.string.feature_gate_unknown_message,
        primaryLabel = R.string.feature_gate_go_back,
        image = R.drawable.care_partner_onboarding,
    )
}
