package team.sakhi.android.feature.emergency

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import team.sakhi.android.designsystem.AppleSystemColors
import team.sakhi.android.designsystem.SakhiRadius
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AssistantDirection
import androidx.compose.material3.ModalBottomSheet
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiSystemBackground
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.graphics.Color
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.emergency.EmergencyState
import team.sakhi.models.EmergencyFormatting
import team.sakhi.models.EmergencyMessage
import team.sakhi.models.EmergencySession
import team.sakhi.android.designsystem.sakhiSystemGray5
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import team.sakhi.android.designsystem.sakhiGroupedBackground
import team.sakhi.android.designsystem.sakhiLabel
import team.sakhi.android.designsystem.sakhiSeparator
import team.sakhi.android.designsystem.toComposeColor
import team.sakhi.design.SakhiUIColors
import androidx.compose.foundation.background
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import team.sakhi.android.ui.SakhiAlertKind
import team.sakhi.android.ui.SakhiAlertSheet

/**
 * Step 4 — the two women are connected.
 *
 * **No route is drawn here, and none should be added.**
 *
 * The original build rendered a live path between the two people. It was tested at the iOS
 * Development Centre on 2025-02-14 and failed: indoor GPS is not accurate enough for the
 * line to mean anything, and a confidently wrong path is worse than no path when someone
 * is trying to find you. The decision recorded in 01-HQ/01-AI/Timeline.md was to show time
 * and distance only, and the server returns no geometry at all to back that up.
 *
 * What she gets instead: who is coming, how far, how long that walk takes, the spot label
 * in plain words, and a way to talk.
 */
// `ModalBottomSheet` is still an experimental Material3 API; opted in next to its one use.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EmergencySessionStep(
    viewModel: EmergencyViewModel,
    step: EmergencyState.InSession,
) {
    val context = LocalContext.current
    var showCompleteConfirm by remember { mutableStateOf(false) }
    var showChat by remember { mutableStateOf(false) }
    var isCompleting by remember { mutableStateOf(false) }
    var isCancelling by remember { mutableStateOf(false) }

    val session = step.session
    val isSeeker = session.viewerIsRequester
    val uiStateForArea by viewModel.uiState.collectAsStateWithLifecycle()

    // `fillMaxWidth`, not `fillMaxSize`. Filling forced the column to the sheet's full
    // height whatever the content was, so the sheet opened tall with a block of empty page
    // under the last card. Wrapping means the sheet is as big as its content and the scroll
    // only engages when there is something to scroll.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Status card ──────────────────────────────────────────────────────
        // iOS `statusCard`: the mark, her name, what she is doing, and the walking time.
        // Android had a photo avatar, "on her way" / "needs your help", a Done link in the
        // header, and a pair of metric tiles carrying the exact distance as well.
        Surface(
            shape = RoundedCornerShape(SakhiRadius.xl),
            color = sakhiSystemBackground(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space4)
                .padding(top = SakhiSpacing.space4),
        ) {
            Row(
                modifier = Modifier.padding(SakhiSpacing.space3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
            ) {
                EmergencyMarkAvatar()
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = session.counterpartName ?: stringResource(R.string.emergency_your_sakhi),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    // configureSeekerInterface / configureHelperInterface.
                    Text(
                        text = stringResource(
                            if (isSeeker) R.string.emergency_is_coming else R.string.emergency_is_waiting,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = sakhiSecondaryLabel(),
                    )
                }
                // timeLabel: "< 1 min" below a minute, "N min" otherwise.
                Text(
                    text = sessionTimeText(context, session.etaMinutes),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        EmergencySectionHeader(
            title = stringResource(R.string.emergency_section_requirement),
            modifier = Modifier.padding(top = SakhiSpacing.space6),
        )
        EmergencyCard {
            EmergencyRow(
                title = EmergencyFormatting.requirementShortName(session.requirement),
                leading = {
                    EmergencyBadgeIcon(session.requirement.icon(), session.requirement.accentColor())
                },
            )
        }

        EmergencySectionHeader(
            title = stringResource(R.string.emergency_section_destination),
            modifier = Modifier.padding(top = SakhiSpacing.space6),
        )
        EmergencyCard {
            Row(
                modifier = Modifier.padding(SakhiSpacing.space3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = destinationText(context, session.spotLabel, uiStateForArea.areaDescription),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                // Sakhi shows a number; the maps app is the right place for turn-by-turn,
                // and it is honest about its own accuracy. Shown to both women now: iOS
                // gives the seeker this row too, and Android hid it from her.
                IconButton(onClick = { openWalkingDirections(context, session) }) {
                    Icon(
                        imageVector = Icons.Filled.AssistantDirection,
                        contentDescription = stringResource(R.string.emergency_open_in_maps),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        // ── Actions ──────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .padding(horizontal = SakhiSpacing.space4)
                .padding(top = SakhiSpacing.space6),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
        ) {
            // Blue rather than brand pink, as the mockup has it: this is the one action on
            // the screen that reaches the other person rather than acting on the request,
            // and it reads as a different kind of thing.
            SessionAction(
                title = stringResource(R.string.emergency_contact),
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                contentColor = Color.White,
                // iOS `Color(UIColor.systemBlue)` -- dynamic, #0A84FF in dark.
                containerColor = AppleSystemColors.blue,
                onClick = { showChat = true },
            )

            // completeRequestButton -- hidden for the seeker on `main`. The woman who
            // walked over is the one who says the help happened.
            if (!isSeeker) {
                SessionAction(
                    title = stringResource(
                        if (isCompleting) R.string.emergency_completing else R.string.emergency_finish,
                    ),
                    // iOS `tint: Color(UIColor.systemGreen)` -- dynamic, #30D158 in dark.
                    contentColor = AppleSystemColors.green,
                    containerColor = sakhiSystemGray5().copy(alpha = 0.4f),
                    isBusy = isCompleting,
                    onClick = {
                        isCompleting = true
                        showCompleteConfirm = true
                    },
                )
            }

            SessionAction(
                title = stringResource(
                    if (isCancelling) R.string.emergency_cancelling else R.string.emergency_cancel,
                ),
                contentColor = sakhiSecondaryLabel(),
                containerColor = sakhiSystemGray5().copy(alpha = 0.4f),
                isBusy = isCancelling,
                onClick = {
                    isCancelling = true
                    viewModel.cancelRequest(session.requestId)
                },
            )
        }

        Spacer(modifier = Modifier.size(SakhiSpacing.space10))
    }

    // iOS opens the thread as a sheet from Contact, at the large detent. Android had it
    // inline under the header, which made the session screen a chat screen and pushed the
    // requirement, destination and the two request actions off the bottom.
    if (showChat) {
        // Fully expanded, as iOS opens this thread at the large detent. Left partially
        // expanded, Compose clips the bottom of the content -- and the bottom of this
        // content is the message field and the send button, so the thread opened with no
        // way to reply.
        ModalBottomSheet(
            onDismissRequest = { showChat = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            EmergencyChatSheet(viewModel = viewModel, step = step, onBack = { showChat = false })
        }
    }

    if (showCompleteConfirm) {
        SakhiAlertSheet(
            kind = SakhiAlertKind.Success,
            title = if (session.viewerIsRequester) {
                stringResource(R.string.emergency_did_she_reach_you)
            } else {
                stringResource(R.string.emergency_all_done)
            },
            primaryLabel = stringResource(R.string.emergency_yes_we_are_done),
            onPrimaryClick = {
                showCompleteConfirm = false
                viewModel.completeSession()
            },
            secondaryLabel = stringResource(R.string.emergency_not_yet),
            onSecondaryClick = { showCompleteConfirm = false; isCompleting = false },
            onDismissRequest = { showCompleteConfirm = false; isCompleting = false },
        )
    }
}

/**
 * The thread, as iOS presents it: a sheet opened from Contact, not a panel welded under the
 * session header. Port of `EmergencyChatView.swift`.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun EmergencyChatSheet(
    viewModel: EmergencyViewModel,
    step: EmergencyState.InSession,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val session = step.session
    val messages = step.messages
    val currentUserId = if (session.viewerIsRequester) session.requesterId else session.responderId

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // iOS puts this thread in a NavigationStack titled with the other woman's name and
        // a pink "Back" on the left. Android had no header at all, so the thread opened with
        // nothing saying who it was with.
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = session.counterpartName ?: stringResource(R.string.emergency_your_sakhi),
                )
            },
            navigationIcon = {
                TextButton(onClick = onBack) {
                    Text(
                        text = stringResource(R.string.emergency_back),
                        // iOS `.font(.lato(15)).foregroundColor(DS.Colors.pink)`.
                        fontSize = 15.sp,
                        color = SakhiUIColors.BRAND_PINK.toComposeColor(),
                    )
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = Color.Transparent,
            ),
            // Inside a sheet, so no status-bar inset -- same as the other steps.
            windowInsets = WindowInsets(0, 0, 0, 0),
        )

        Box(modifier = Modifier.weight(1f)) {
            if (messages.isEmpty()) {
                Text(
                    text = stringResource(R.string.emergency_say_hello),
                    style = MaterialTheme.typography.bodySmall,
                    color = sakhiSecondaryLabel(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = SakhiSpacing.space6),
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = SakhiSpacing.space5),
                verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
                contentPadding = PaddingValues(vertical = SakhiSpacing.space3),
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        isMine = message.senderId == currentUserId,
                        senderName = if (message.senderId == currentUserId) {
                            stringResource(R.string.emergency_you)
                        } else {
                            session.counterpartName?.substringBefore(' ')
                                ?: stringResource(R.string.emergency_your_sakhi)
                        },
                        avatarName = session.counterpartName,
                    )
                }
            }
        }

        // iOS `inputBar` opens with a hairline over the whole width.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(sakhiSeparator().copy(alpha = 0.18f)),
        )

        val canSend = uiState.messageDraft.isNotBlank()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                // iOS `.padding(.horizontal, DS.Spacing.m)` = 16, `.padding(.vertical, 10)`.
                .padding(horizontal = SakhiSpacing.space4, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
            // iOS `HStack(alignment: .bottom, spacing: DS.Spacing.s)` = 12.
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        ) {
            // iOS fills the field with `groupedBackground` and draws no border at all. An
            // `OutlinedTextField` brought Material's hard outline, its underline indicator
            // and a 56dp minimum height -- the same three things that were wrong on the
            // Location step's spot field.
            val fieldStyle = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 15.sp,
                color = sakhiLabel(),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(sakhiGroupedBackground())
                    // iOS `.padding(.horizontal, 14).padding(.vertical, 10)`.
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = uiState.messageDraft,
                    onValueChange = viewModel::onMessageDraftChanged,
                    textStyle = fieldStyle,
                    cursorBrush = SolidColor(SakhiUIColors.BRAND_PINK.toComposeColor()),
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (uiState.messageDraft.isEmpty()) {
                            Text(
                                text = stringResource(R.string.emergency_message_hint),
                                style = fieldStyle.copy(color = sakhiSecondaryLabel()),
                            )
                        }
                        inner()
                    },
                )
            }

            // iOS: a 36pt circle, pink when there is something to send and `gray5` when not,
            // carrying a white arrow-up. Android had a bare icon button with a Send glyph.
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (canSend) SakhiUIColors.BRAND_PINK.toComposeColor() else sakhiSystemGray5())
                    .clickable(enabled = canSend) { viewModel.sendMessage() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowUpward,
                    contentDescription = stringResource(R.string.emergency_send),
                    tint = Color.White,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

/** iOS `secondaryAction` / the Contact button: one 56dp bar, label carrying the colour. */
@Composable
private fun SessionAction(
    title: String,
    contentColor: Color,
    containerColor: Color,
    icon: ImageVector? = null,
    isBusy: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(enabled = !isBusy, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = contentColor,
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.size(SakhiSpacing.space1))
            } else if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(SakhiSpacing.space1))
            }
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = contentColor)
        }
    }
}

/** iOS `timeText`: "< 1 min" below a minute, "N min" otherwise. */
private fun sessionTimeText(context: android.content.Context, etaMinutes: Int?): String =
    if (etaMinutes == null || etaMinutes == 0) {
        context.getString(R.string.emergency_under_a_minute)
    } else {
        context.getString(R.string.emergency_n_min, etaMinutes)
    }

/**
 * iOS `destinationText`: `"{spot} at {area}".capitalized`.
 *
 * The spot label is what she typed; the area comes from reverse geocoding on this device,
 * never from the server.
 */
private fun destinationText(
    context: android.content.Context,
    spotLabel: String?,
    area: String?,
): String {
    val spot = spotLabel?.trim().orEmpty()
    return when {
        spot.isEmpty() && area == null -> context.getString(R.string.emergency_location_shared)
        spot.isEmpty() -> area!!
        area == null -> spot.replaceFirstChar { it.uppercase() }
        else -> context.getString(
            R.string.emergency_spot_at_area_full,
            spot.replaceFirstChar { it.uppercase() },
            area,
        )
    }
}

// `Metric` drew the pair of tiles carrying exact distance and walking time. iOS puts the
// walking time in the status card and shows no exact distance on this screen at all.


/**
 * The responder's actual instructions. The spot label does more work here than any map
 * would: it is the part GPS cannot give you.
 */
// `WhereToGo` was the helper-only destination block. iOS shows the DESTINATION section to
// both women, so this is now `EmergencyCard` in the body above.


/**
 * Bubble plus the sender name above it.
 *
 * `main` used MessageKit with a 16dp `messageTopLabel` showing the sender, and coloured
 * bubbles pink for the current user, gray for the other person.
 */
@Composable
private fun MessageBubble(
    message: EmergencyMessage,
    isMine: Boolean,
    senderName: String,
    avatarName: String?,
) {
    // iOS: `HStack(alignment: .bottom, spacing: DS.Spacing.xs)` with a 30pt avatar beside
    // every bubble (MessageKit's `configureAvatarView`) and a 50pt spacer on the far side so
    // a bubble never runs the full width. Android drew a bare column with neither.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
    ) {
        if (isMine) Spacer(modifier = Modifier.width(50.dp))
        if (!isMine) EmergencyAvatar(name = avatarName, photoUrl = null, size = 30.dp)

        Column(
            modifier = Modifier.weight(1f, fill = false),
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
    Text(
        text = senderName,
        style = MaterialTheme.typography.labelSmall,
        color = sakhiSecondaryLabel(),
        modifier = Modifier.height(16.dp),
    )
    Row(
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            // iOS: `isMine ? DS.Colors.pink : DS.Colors.groupedBackground`.
            color = if (isMine) {
                SakhiUIColors.BRAND_PINK.toComposeColor()
            } else {
                sakhiGroupedBackground()
            },
        ) {
            Text(
                text = message.body,
                // iOS `.font(.lato(15))`, and the incoming colour is `DS.Colors.label`, not
                // a secondary grey -- her words are the content, not a caption.
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                color = if (isMine) Color.White else sakhiLabel(),
                modifier = Modifier.padding(
                    horizontal = SakhiSpacing.space3,
                    vertical = SakhiSpacing.space2,
                ),
            )
        }
    }
        }

        // iOS puts the viewer's own avatar on the trailing side of her own bubbles, and a
        // 50pt spacer on the far side of an incoming one so it never runs the full width.
        if (isMine) EmergencyAvatar(name = null, photoUrl = null, size = 30.dp)
        if (!isMine) Spacer(modifier = Modifier.width(50.dp))
    }
}

/**
 * Hands off to a maps app for the walk. Sakhi shows a number; a maps app is the right
 * place for turn-by-turn, and it is honest about its own accuracy.
 */
private fun openWalkingDirections(context: android.content.Context, session: EmergencySession) {
    val uri = Uri.parse(
        "google.navigation:q=${session.location.latitude},${session.location.longitude}&mode=w",
    )
    val intent = Intent(Intent.ACTION_VIEW, uri)
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
        return
    }
    // No Google Maps on the device — fall back to whatever handles a geo: URI.
    val fallback = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("geo:${session.location.latitude},${session.location.longitude}"),
    )
    if (fallback.resolveActivity(context.packageManager) != null) {
        context.startActivity(fallback)
    }
}
