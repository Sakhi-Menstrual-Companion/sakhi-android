package team.sakhi.android.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.sakhiLabel
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiTertiaryLabel
import team.sakhi.android.platform.StayWithMeAlarmPlayer
import team.sakhi.android.ui.DetailSheetScaffold
import team.sakhi.staywithme.StayWithMeAlarm
import team.sakhi.staywithme.StayWithMeAlarmPreference

/**
 * When this phone starts ringing if she is not home.
 *
 * The Android half of iOS's `RideAlarmSettingsView`, with the same choices, the same
 * default and the same preview button.
 *
 * She tells Sakhi when she should be home. This says how long after that time the person
 * staying with her wants to be woken (Karan, 2026-09-16). It is on their phone rather than
 * hers because it is their alarm: some people want to know the minute she is late, others
 * would rather give a ride twenty minutes for traffic before a phone starts ringing at
 * midnight.
 *
 * Nothing here changes her walk, her time, or when Sakhi tells them she is late. It only
 * moves the alarm on this phone.
 */
@Composable
fun RideAlarmScreen(onBack: () -> Unit) {
    val preference = koinInject<StayWithMeAlarmPreference>()
    val context = LocalContext.current
    val player = remember { StayWithMeAlarmPlayer(context) }
    var chosen by remember { mutableIntStateOf(preference.minutes()) }
    var previewing by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { player.stop() }
    }

    DetailSheetScaffold(
        title = stringResource(R.string.profile_ride_alarm_title),
        subtitle = stringResource(R.string.profile_ride_alarm_subtitle),
        headerIcon = Icons.Filled.Alarm,
        onBack = onBack,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4)) {
            Text(
                text = stringResource(R.string.profile_ride_alarm_section),
                style = MaterialTheme.typography.labelMedium,
                color = sakhiSecondaryLabel(),
                modifier = Modifier.padding(start = SakhiSpacing.space1),
            )

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    StayWithMeAlarm.CHOICES.forEach { minutes ->
                        val isChosen = minutes == chosen
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    chosen = minutes
                                    preference.setMinutes(minutes)
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (minutes == 0) {
                                        stringResource(R.string.profile_ride_alarm_at_her_time)
                                    } else {
                                        stringResource(R.string.profile_ride_alarm_after, minutes)
                                    },
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = sakhiLabel(),
                                )
                                Text(
                                    text = stringResource(
                                        when (minutes) {
                                            0 -> R.string.profile_ride_alarm_detail_0
                                            5 -> R.string.profile_ride_alarm_detail_5
                                            10 -> R.string.profile_ride_alarm_detail_10
                                            15 -> R.string.profile_ride_alarm_detail_15
                                            else -> R.string.profile_ride_alarm_detail_long
                                        },
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = sakhiTertiaryLabel(),
                                )
                            }
                            if (isChosen) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Hearing it once, in daylight, is the only way to know whether it will wake you.
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (previewing) player.stop() else player.start()
                            previewing = !previewing
                        }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (previewing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text(
                            text = stringResource(
                                if (previewing) R.string.profile_ride_alarm_stop else R.string.profile_ride_alarm_hear,
                            ),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = sakhiLabel(),
                        )
                        Text(
                            text = stringResource(R.string.profile_ride_alarm_hear_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = sakhiSecondaryLabel(),
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.profile_ride_alarm_note),
                style = MaterialTheme.typography.bodySmall,
                color = sakhiTertiaryLabel(),
                modifier = Modifier.padding(horizontal = SakhiSpacing.space1),
            )
            Spacer(Modifier.height(SakhiSpacing.space6))
        }
    }
}
