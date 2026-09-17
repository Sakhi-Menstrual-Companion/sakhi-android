package team.sakhi.android.feature.care

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import team.sakhi.android.designsystem.sakhiButtonFill
import team.sakhi.android.designsystem.sakhiDeepRose
import team.sakhi.android.designsystem.sakhiLightPink
import team.sakhi.android.designsystem.sakhiProfileCardBackground
import team.sakhi.android.designsystem.sakhiSeparator
import team.sakhi.android.designsystem.sakhiSystemBackground

/**
 * The walk screens' palette, the same names and the same values as iOS's `RideStyle`.
 *
 * Kept as one object rather than reached for colour by colour, so a change on either side
 * is a change to a named thing and not to twenty call sites that have to be found by eye.
 */
internal object RideStyle {
    /** The walk itself: the route, the marker, "I'm home". */
    val pink: Color @Composable get() = MaterialTheme.colorScheme.primary
    /** Every icon, and 112. */
    val rose: Color @Composable get() = sakhiDeepRose()
    /** The disc behind an icon. */
    val soft: Color @Composable get() = sakhiLightPink()
    /** The empty part of the track. */
    val track: Color @Composable get() = sakhiButtonFill()
    /** Cards on the panel: white, and a raised surface in dark. */
    val card: Color @Composable get() = sakhiProfileCardBackground()
    /** The panel's ground. */
    val ground: Color @Composable get() = MaterialTheme.colorScheme.background
    /** Buttons floating over the map. */
    val floating: Color @Composable get() = sakhiSystemBackground()
    val hairline: Color @Composable get() = sakhiSeparator()

    /** Past her time, inside the grace window. iOS uses `UIColor.systemOrange`. */
    val late = Color(0xFFFF9500)
    /** Her person has been told. iOS uses `UIColor.systemRed`. */
    val alert = Color(0xFFFF3B30)

    val cardRadius = 22.dp
}
