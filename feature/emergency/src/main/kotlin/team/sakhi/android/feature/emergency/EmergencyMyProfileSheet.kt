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
import androidx.compose.ui.res.stringResource
import team.sakhi.android.designsystem.rememberSakhiFlingBehavior
import team.sakhi.android.ui.SakhiSwitch
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.ui.CloseButton

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
    /** Whether she is offering to help women near her right now. */
    isAvailable: Boolean = false,
    /** Turns being findable on or off. */
    onAvailabilityChange: (Boolean) -> Unit = {},
    /** Opens the face picker. */
    onOpenFacePicker: () -> Unit,
    /** Closes the sheet from the X in its own top-left corner. */
    onClose: () -> Unit,
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
            .verticalScroll(rememberScrollState(), flingBehavior = rememberSakhiFlingBehavior())
            // iOS: `.padding(.bottom, DS.Spacing.xxl)`. No top padding of its own any
            // more -- the close row below carries the clearance off the grabber.
            .padding(bottom = SakhiSpacing.space8),
    ) {
        // Top-left, not top-right. iOS shares that one leading slot between back and
        // close, which is why every X in this app is on the left; `SakhiNavBar` and
        // `OnboardingShell` both put it there too. Nothing to go back to here, so it is a
        // close.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space4)
                .padding(top = SakhiSpacing.space2),
        ) {
            CloseButton(onClick = onClose)
        }

        // Hero: her face, large and centred, with her name and trust level under it.
        //
        // iOS `EmergencyMyProfileView.hero`: a 104 disc in `systemBackground` holding an 84
        // face, 12 between items, 24 above. No ring, no shadow, no scrim, and no line of
        // explainer -- the disc is the sheet's own card weight, so the face reads as sitting
        // on the page rather than as a badge stuck to it.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space5)
                .padding(top = SakhiSpacing.space2),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        ) {
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(sakhiSystemBackground()),
                contentAlignment = Alignment.Center,
            ) {
                Face(index = selectedIndex, size = 84.dp)
            }

            Text(
                text = if (!name.isNullOrEmpty()) name else "Your Sakhi profile",
                style = MaterialTheme.typography.titleMedium,
                // iOS `DS.Typography.sectionHeader` -- Lato Bold 20.
                fontSize = 20.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Bold,
                color = sakhiLabel(),
            )

            // iOS `trustPill`: a 12pt semibold glyph and `cardDescription` (14), 12 / 5.
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(trustColor.copy(alpha = 0.12f))
                    .padding(horizontal = SakhiSpacing.space3, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                androidx.compose.material3.Icon(
                    imageVector = trust.badgeIcon(),
                    contentDescription = null,
                    tint = trustColor,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    text = trust.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = trustColor,
                )
            }
        }

        // What she has actually done. Received rather than only accepted: counting accepted
        // alone flatters someone who declines everything. The caption under each title is
        // what gives the number on the right something to mean, and iOS has it.
        EmergencySectionHeader(title = "What you've done", topPadding = 28.dp)
        EmergencyCard {
            StatRow(
                icon = Icons.Filled.VolunteerActivism,
                tint = SakhiTokens.SectionRose,
                title = "People you've helped",
                caption = "Asks you saw through",
                value = "${profile?.helpedCount ?: 0}",
            )
            EmergencyRowDivider()
            StatRow(
                icon = Icons.Filled.Inbox,
                tint = SakhiTokens.SectionBlue,
                title = "Requests received",
                caption = "Answered or not",
                value = "${profile?.receivedCount ?: 0}",
            )
            EmergencyRowDivider()
            StatRow(
                icon = Icons.Filled.Star,
                tint = SakhiTokens.SectionAmber,
                title = "Times rated",
                caption = "What your trust level counts",
                value = "${profile?.ratingCount ?: 0}",
            )
        }

        // The one place in the app where she can choose to be findable.
        //
        // It existed only inside the responder inbox, and that sheet opens when somebody
        // has already asked her -- so there was no way to turn it on before the fact and
        // no way to see whether it was on. Meanwhile merely opening the nearby list
        // published her as available, which is not a choice she ever made. This is the
        // choice, made once, visible, and reversible.
        EmergencySectionHeader(title = stringResource(R.string.emergency_section_being_found), topPadding = 28.dp)
        EmergencyCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
            ) {
                EmergencyBadgeIcon(
                    icon = Icons.Filled.VolunteerActivism,
                    color = SakhiTokens.SectionRose,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.emergency_available_toggle_title),
                        style = MaterialTheme.typography.bodyLarge,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        color = sakhiLabel(),
                    )
                    Text(
                        text = if (isAvailable) {
                            stringResource(R.string.emergency_available_toggle_on)
                        } else {
                            stringResource(R.string.emergency_available_toggle_off)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = sakhiSecondaryLabel(),
                    )
                }
                SakhiSwitch(checked = isAvailable, onCheckedChange = onAvailabilityChange)
            }
        }

        // A row that opens the picker, not the picker itself.
        //
        // iOS `faceRow`, and its comment is the reason: five faces laid out in a card read
        // as a filter bar rather than a choice about her. Sakhi's own Profile screen makes
        // every choice a row with the current value on the right and a chevron, so this one
        // does too, and the faces get a sheet where they are big enough to tell apart.
        EmergencySectionHeader(title = "Your face", topPadding = 28.dp)
        EmergencyCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenFacePicker)
                    .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
            ) {
                EmergencyBadgeIcon(
                    icon = Icons.Filled.Face,
                    color = SakhiUIColors.BRAND_PINK.toComposeColor(),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Change your face",
                        style = MaterialTheme.typography.bodyLarge,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        color = sakhiLabel(),
                    )
                    Text(
                        text = "Only a drawing, never your photo",
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = sakhiSecondaryLabel(),
                    )
                }
                Face(index = selectedIndex, size = 30.dp)
                EmergencyChevron()
            }
        }
    }
}

/**
 * The five faces, big enough to tell apart.
 *
 * A dialog rather than a sheet, at Karan's call. iOS pushes a second sheet here, but on
 * Android a sheet sliding up over a sheet reads as going somewhere; this is one tap that
 * changes one thing and then gets out of the way, which is what a dialog is for.
 *
 * Metrics follow iOS `EmergencyFacePickerView`: a disc per face with the chosen one ringed
 * in brand pink at 2.5.
 */
@Composable
fun EmergencyFacePickerDialog(
    userId: String,
    onPicked: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    var selected by remember(userId) {
        mutableIntStateOf(
            SakhiAvatarPreference.chosenIndex(context, userId)
                ?: EmergencyAvatarCatalog.dealtIndex(userId),
        )
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        // The default caps a dialog at a width built for a title and two buttons, which
        // squeezed five faces down to about 40dp each.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(SakhiRadius.xxl),
            color = sakhiSystemBackground(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space6),
        ) {
            Column(
                modifier = Modifier.padding(SakhiSpacing.space6),
                verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space5),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "Your face",
                        fontSize = 20.sp,
                        lineHeight = 23.sp,
                        fontWeight = FontWeight.Bold,
                        color = sakhiLabel(),
                    )
                    Text(
                        text = "This is what nearby Sakhis see when you help.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        color = sakhiSecondaryLabel(),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
                ) {
                    repeat(EmergencyAvatarCatalog.count) { index ->
                        val isSelected = index == selected
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                // The card is already white, so the disc under each face
                                // takes the grouped grey -- on white the faces would float.
                                .background(sakhiGroupedBackground())
                                .border(
                                    width = if (isSelected) 2.5.dp else 0.dp,
                                    color = if (isSelected) {
                                        SakhiUIColors.BRAND_PINK.toComposeColor()
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = CircleShape,
                                )
                                .clickable {
                                    selected = index
                                    SakhiAvatarPreference.setChosenIndex(context, userId, index)
                                    onPicked()
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Face(index = index, size = 44.dp)
                        }
                    }
                }
            }
        }
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

/** A line under each title, so the number on the right has something to mean. iOS `statRow`. */
@Composable
private fun StatRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    caption: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // iOS: `.padding(.horizontal, DS.Spacing.m)` (16) and `.vertical, .s` (12).
            .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
    ) {
        EmergencyBadgeIcon(icon = icon, color = tint)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = sakhiLabel(),
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.labelMedium,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = sakhiSecondaryLabel(),
            )
        }
        Text(
            text = value,
            // iOS `DS.Typography.cardTitle` -- Lato Bold 17.
            fontSize = 17.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Bold,
            color = sakhiLabel(),
        )
    }
}
