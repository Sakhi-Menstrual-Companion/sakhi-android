package team.sakhi.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.sakhiSecondaryLabel

/**
 * The invitation Sakhi closes a screen with: a quiet line, and a pink line that opens the site.
 *
 * Her profile has ended this way for a long time, and Karan asked for the same footer under
 * the walk's start button on 2026-09-18, because the shape already reads as finished. Lifted
 * out of `ProfileScreen` into `core/ui` rather than copied, and its two strings moved with it,
 * so the two screens cannot drift into two slightly different invitations. iOS has the same
 * component under the same name.
 *
 * [topPadding] and [bottomPadding] are the only things a caller changes. Profile has a whole
 * scroll of groups above it and wants the full gap; the walk's card has room for far less.
 */
@Composable
fun SakhiConnectFooter(
    modifier: Modifier = Modifier,
    topPadding: Dp = SakhiSpacing.space6,
    bottomPadding: Dp = SakhiSpacing.space10,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topPadding, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space1),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.sakhi_connect_footer_body),
            style = MaterialTheme.typography.bodySmall,
            color = sakhiSecondaryLabel(),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = { uriHandler.openUri(SAKHI_URL) },
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(
                text = stringResource(R.string.sakhi_connect_footer_cta),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Sakhi's own page. The one link this footer has ever pointed at. */
private const val SAKHI_URL = "https://sakhi.rachna.co"
