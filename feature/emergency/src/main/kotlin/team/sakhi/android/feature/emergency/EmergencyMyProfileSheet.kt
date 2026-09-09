package team.sakhi.android.feature.emergency

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.SakhiTokens
import team.sakhi.android.designsystem.sakhiGroupedBackground
import team.sakhi.android.designsystem.sakhiLabel
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiSystemBackground
import team.sakhi.android.designsystem.toComposeColor
import team.sakhi.design.SakhiUIColors
import team.sakhi.models.EmergencyFormatting
import team.sakhi.models.EmergencyProfileDetail

/**
 * Her own profile. Port of iOS `EmergencyMyProfileView.swift`.
 *
 * Deliberately not `EmergencyProfileDetailSheet` with a flag. That screen exists to help
 * her decide whether to trust a stranger, so it leads with Block and a request button. This
 * one is hers: it leads with the face she gets to choose and what she has given, and there
 * is nobody here to block or ask.
 *
 * Everything shown is real. Before the profile loads, and for a woman nobody has asked yet,
 * the numbers are honestly zero.
 */
@Composable
fun EmergencyMyProfileSheet(
    userId: String,
    profile: EmergencyProfileDetail?,
    name: String? = null,
    /** Re-reads the chosen face after she taps one. The choice is stored per user, locally. */
    onFaceChosen: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Local state, not a straight read of the preference: the store is plain
    // SharedPreferences, which Compose cannot observe, so tapping a face would write the
    // choice and leave the ring on the old one until the sheet was reopened.
    var selectedIndex by remember(userId) {
        mutableIntStateOf(
            SakhiAvatarPreference.chosenIndex(context, userId)
                ?: EmergencyAvatarCatalog.dealtIndex(userId),
        )
    }
    val trust = EmergencyFormatting.trustLevel(profile?.ratingCount ?: 0)
    val trustColor = trust.colorHex.toComposeColor()

    Column(
        modifier = modifier
            .fillMaxWidth()
            // No ground of its own. Figma `EA-02` is the same blush sheet as every other
            // step in the flow; painting `sakhiGroupedBackground()` here made this the one
            // grey screen in a pink flow.
            .verticalScroll(rememberScrollState())
            .padding(bottom = SakhiSpacing.space8),
    ) {
        // Figma `hero`: `pt-18 pb-4 px-20`, 10 between items, centred. Her face sits in a
        // disc tinted with her own trust colour at 12%, which is also the trust pill's
        // fill -- the two read as one statement about where she stands.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space5)
                .padding(top = 18.dp, bottom = SakhiSpacing.space1),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(trustColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Face(index = selectedIndex, size = 64.dp)
            }

            Text(
                text = if (!name.isNullOrEmpty()) name else "Your Sakhi profile",
                style = MaterialTheme.typography.titleMedium,
                fontSize = 20.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Bold,
                color = sakhiLabel(),
            )

            Text(
                text = "This is the face people see when you help.",
                style = MaterialTheme.typography.bodyMedium,
                color = sakhiSecondaryLabel(),
                textAlign = TextAlign.Center,
            )

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(trustColor.copy(alpha = 0.12f))
                    // Figma `trust badge`: `px-14 py-6`, 6 between glyph and word.
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                androidx.compose.material3.Icon(
                    imageVector = trust.badgeIcon(),
                    contentDescription = null,
                    tint = trustColor,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = trust.displayName,
                    // Figma `Type/cardTitle`: Lato Bold 17.
                    fontSize = 17.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = trustColor,
                )
            }
        }

        // What she has actually done. Received rather than only accepted: counting accepted
        // alone flatters someone who declines everything.
        //
        // No captions under the titles. They were an Android addition; the frame is three
        // plain rows with a number on the right, and the captions turned a compact block
        // into the tallest thing on the sheet.
        EmergencySectionHeader(title = "What you've done", topPadding = 22.dp - SakhiSpacing.space4)
        EmergencyCard {
            StatRow(
                icon = Icons.Filled.VolunteerActivism,
                tint = SakhiTokens.SectionRose,
                title = "People you've helped",
                value = "${profile?.helpedCount ?: 0}",
            )
            EmergencyRowDivider()
            StatRow(
                icon = Icons.Filled.Inbox,
                tint = SakhiTokens.SectionBlue,
                title = "Requests received",
                value = "${profile?.receivedCount ?: 0}",
            )
            EmergencyRowDivider()
            StatRow(
                icon = Icons.Filled.Star,
                tint = SakhiTokens.SectionAmber,
                title = "Times rated",
                value = "${profile?.ratingCount ?: 0}",
            )
        }

        // The faces themselves, inline. Figma `avatar picker`: five 54dp targets 12 apart,
        // each holding a 44dp face, the chosen one ringed in brand pink. This used to be a
        // row that pushed a whole second sheet, which put two taps and a transition between
        // her and a choice that is one tap wide.
        EmergencySectionHeader(title = "Choose your face", topPadding = 22.dp - SakhiSpacing.space4)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space5),
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        ) {
            repeat(EmergencyAvatarCatalog.count) { index ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(sakhiSystemBackground())
                        .border(
                            width = if (isSelected) 2.dp else 0.dp,
                            color = if (isSelected) {
                                SakhiUIColors.BRAND_PINK.toComposeColor()
                            } else {
                                Color.Transparent
                            },
                            shape = CircleShape,
                        )
                        .clickable {
                            selectedIndex = index
                            SakhiAvatarPreference.setChosenIndex(context, userId, index)
                            onFaceChosen()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Face(index = index, size = 44.dp)
                }
            }
        }

        // Figma `note`: 10 under the picker, the full 362 card width.
        Text(
            text = "Only a drawing, never your photo. It is what nearby Sakhis see.",
            style = MaterialTheme.typography.labelMedium,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = sakhiSecondaryLabel(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space5)
                .padding(top = 10.dp),
        )
    }
}

/** One face, cropped so the head fills the disc rather than floating in its margin. */
@Composable
private fun Face(index: Int, size: androidx.compose.ui.unit.Dp) {
    Image(
        painter = painterResource(EmergencyAvatarCatalog.drawableAt(index)),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .scale(EmergencyAvatarCatalog.contentScaleAt(index)),
    )
}

/** One count, through the flow's shared row so it lines up with every other card in it. */
@Composable
private fun StatRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    value: String,
) {
    EmergencyRow(
        title = title,
        leading = { EmergencyBadgeIcon(icon = icon, color = tint) },
        accessory = {
            Text(
                text = value,
                // Figma `Type/cardTitle`: Lato Bold 17, right against the card's 16 inset.
                fontSize = 17.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
                color = sakhiLabel(),
            )
        },
    )
}
