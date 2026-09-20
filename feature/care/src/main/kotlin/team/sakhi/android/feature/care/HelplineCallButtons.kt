package team.sakhi.android.feature.care

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Woman
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.sakhi.android.designsystem.sakhiLabel
import team.sakhi.android.designsystem.sakhiProfileCardBackground
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiTertiaryLabel

/**
 * 112, 108 and 181, as three buttons that are read by their picture.
 *
 * The exact counterpart of iOS's `HelplineCallButtons.swift`, down to the colours and the
 * layout, because the two apps must not disagree about what an emergency looks like.
 *
 * In an emergency nobody reads (Karan, 2026-09-16). These are found by their picture first:
 * the police shield in blue, the ambulance cross in red, the women's helpline in Sakhi's
 * pink, each glyph on a soft square of its own colour the way the system draws its own
 * icons. The number is the biggest thing under it, the name quieter below, and a small
 * phone in the corner says that pressing it makes a call.
 *
 * An earlier iOS version used solid discs, a coloured border and a green phone badge. Karan
 * found it loud and ugly, so there are no strokes and no badges: colour lives only in the
 * icon. Android was built to match the version that survived, not the one that did not.
 *
 * These are the only place on the walk screens that step outside Sakhi's own colours. A blue
 * shield and a red cross are recognised in a glance by people who have never seen Sakhi, and
 * that is worth more here than matching the palette.
 *
 * Each button only opens the dialler with the number filled in. Sakhi never places a call.
 */
@Composable
internal fun HelplineCallButtons(
    onCall: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val services = listOf(
        HelplineService(
            number = "112",
            name = stringResource(R.string.care_swm_help_police),
            spokenName = stringResource(R.string.care_swm_help_police),
            icon = Icons.Filled.Security,
            color = PoliceBlue,
        ),
        HelplineService(
            number = "108",
            name = stringResource(R.string.care_swm_help_ambulance),
            spokenName = stringResource(R.string.care_swm_help_ambulance),
            icon = Icons.Filled.LocalHospital,
            color = AmbulanceRed,
        ),
        HelplineService(
            number = "181",
            name = stringResource(R.string.care_swm_help_women),
            spokenName = stringResource(R.string.care_swm_women_helpline),
            icon = Icons.Filled.Woman,
            color = MaterialTheme.colorScheme.primary,
        ),
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        services.forEach { service ->
            HelplineCallButton(
                service = service,
                onClick = { onCall(service.number) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

internal data class HelplineService(
    val number: String,
    val name: String,
    val spokenName: String,
    val icon: ImageVector,
    val color: Color,
)

@Composable
private fun HelplineCallButton(
    service: HelplineService,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val callDescription = stringResource(R.string.care_swm_help_call_a11y, service.spokenName, service.number)
    // iOS's `HelplineCallButton`: a white card, the glyph on a disc of its own colour, the
    // number and then the name, centred. The Material `surfaceVariant` this used was grey.
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = callDescription },
        shape = RoundedCornerShape(16.dp),
        color = sakhiProfileCardBackground(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(service.color.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = service.icon,
                    contentDescription = null,
                    tint = service.color,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = service.number,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = sakhiLabel(),
                )
                Text(
                    text = service.name,
                    fontSize = 12.sp,
                    color = sakhiSecondaryLabel(),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Not Sakhi's palette on purpose, and not the system's either: these two are the colours
 * India's own services are recognised by. iOS uses `UIColor.systemBlue` and `systemRed`;
 * these are their Material equivalents so the two apps read the same.
 */
private val PoliceBlue = Color(0xFF0A84FF)
private val AmbulanceRed = Color(0xFFFF3B30)
