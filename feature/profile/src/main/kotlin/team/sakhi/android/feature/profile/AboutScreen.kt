package team.sakhi.android.feature.profile

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import team.sakhi.android.feature.profile.R
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.HapticImpact
import team.sakhi.android.ui.DetailSheetScaffold
import team.sakhi.android.ui.ProfileSectionLabel
import team.sakhi.android.ui.SakhiNavDirection
import team.sakhi.android.ui.SakhiScreenTransition
import team.sakhi.android.ui.SakhiListDivider
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiSystemBackground
import team.sakhi.android.designsystem.sakhiTertiaryLabel

private const val WEBSITE_URL = "https://sakhi.rachna.co"
private const val INSTAGRAM_URL = "https://instagram.com/sakhi.app"
private const val FEEDBACK_EMAIL = "hello@getswipe.in"

/** Ports iOS `AboutView.swift`: story/team/licenses navigation, connect links, share, app info. */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    contentViewModel: SanityContentViewModel = koinViewModel(),
) {
    var openPage by remember { mutableStateOf<ContentPageId?>(null) }
    val context = LocalContext.current
    val hapticManager = koinInject<AndroidHapticManager>()
    val uiState by contentViewModel.uiState.collectAsState()
    val instagramUrl = uiState.siteSettings?.socialLinks?.instagram?.takeIf { !it.isNullOrBlank() } ?: INSTAGRAM_URL
    val feedbackEmail = uiState.siteSettings?.contactEmail?.takeIf { !it.isNullOrBlank() } ?: FEEDBACK_EMAIL
    val playStoreWebUrl = remember(context) { playStoreWebUrl(context) }
    val shareBody = stringResource(R.string.profile_about_share_body, playStoreWebUrl)
    val shareChooserTitle = stringResource(R.string.profile_about_share_action)

    BackHandler(enabled = openPage != null) { openPage = null }

    // Opening a content page pushes it in from the right; closing pops it back --
    // the same app-wide slide every other screen swap uses, instead of an instant cut.
    SakhiScreenTransition(
        targetState = openPage,
        directionFor = { _, target ->
            if (target != null) SakhiNavDirection.Forward else SakhiNavDirection.Backward
        },
        label = "about_page_transition",
    ) { page ->
    if (page != null) {
        ContentPageScreen(pageId = page, onBack = { openPage = null })
        return@SakhiScreenTransition
    }

    DetailSheetScaffold(
        title = stringResource(R.string.profile_about_title),
        subtitle = stringResource(R.string.profile_about_header_subtitle),
        headerIcon = Icons.Filled.Favorite,
        onBack = onBack,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space5)) {
            AboutActionSectionCard(
                label = stringResource(R.string.profile_about_section_story),
                rows = listOf(
                    AboutActionRow(
                        title = stringResource(R.string.profile_about_story),
                        icon = Icons.Filled.Favorite,
                        onClick = { openPage = ContentPageId.ABOUT_US },
                    ),
                    AboutActionRow(
                        title = stringResource(R.string.profile_about_team),
                        icon = Icons.Filled.Groups,
                        onClick = { openPage = ContentPageId.CREDITS },
                    ),
                    AboutActionRow(
                        title = stringResource(R.string.profile_about_open_source),
                        icon = Icons.Filled.Code,
                        onClick = { openPage = ContentPageId.OPEN_SOURCE_LICENSES },
                    ),
                ),
            )

            AboutActionSectionCard(
                label = stringResource(R.string.profile_about_section_connect),
                rows = listOf(
                    AboutActionRow(
                        title = stringResource(R.string.profile_about_website),
                        icon = Icons.Filled.Language,
                        isExternal = true,
                        onClick = { openUrl(context, WEBSITE_URL) },
                    ),
                    AboutActionRow(
                        title = stringResource(R.string.profile_about_instagram),
                        icon = Icons.Filled.CameraAlt,
                        isExternal = true,
                        onClick = { openUrl(context, instagramUrl) },
                    ),
                    AboutActionRow(
                        title = stringResource(R.string.profile_about_send_feedback),
                        icon = Icons.Filled.Email,
                        isExternal = true,
                        onClick = { openUrl(context, "mailto:$feedbackEmail?subject=Sakhi%20Feedback") },
                    ),
                    AboutActionRow(
                        title = stringResource(R.string.profile_about_rate_play_store),
                        icon = Icons.Filled.Star,
                        isExternal = true,
                        onClick = { openPlayStore(context) },
                    ),
                ),
            )

            AboutSingleActionCard(
                row = AboutActionRow(
                    title = stringResource(R.string.profile_about_share_action),
                    icon = Icons.Filled.Share,
                    onClick = {
                        hapticManager.impact(HapticImpact.LIGHT)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareBody)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, shareChooserTitle).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    },
                ),
            )

            AboutInfoSectionCard(
                label = stringResource(R.string.profile_about_section_app_info),
                rows = listOf(
                    AboutInfoRow(
                        title = stringResource(R.string.profile_about_version_label),
                        value = appVersionName(context),
                        icon = Icons.Filled.Apps,
                    ),
                    AboutInfoRow(
                        title = stringResource(R.string.profile_about_made_with_label),
                        value = stringResource(R.string.profile_about_made_with_love),
                        icon = Icons.Filled.Favorite,
                    ),
                    AboutInfoRow(
                        title = stringResource(R.string.profile_about_team_label),
                        value = stringResource(R.string.profile_about_team_name),
                        icon = Icons.Filled.People,
                    ),
                ),
            )
        }
    }
    }
}

private fun appVersionName(context: android.content.Context): String = runCatching {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val version = packageInfo.versionName ?: "1.0"
    val build = PackageInfoCompat.getLongVersionCode(packageInfo)
    "$version ($build)"
}.getOrDefault("1.0 (1)")

private fun openUrl(context: android.content.Context, url: String) {
    val intent = if (url.startsWith("mailto:")) {
        Intent(Intent.ACTION_SENDTO, android.net.Uri.parse(url))
    } else {
        Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun openPlayStore(context: android.content.Context) {
    val marketIntent = Intent(
        Intent.ACTION_VIEW,
        android.net.Uri.parse("market://details?id=${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val webIntent = Intent(
        Intent.ACTION_VIEW,
        android.net.Uri.parse(playStoreWebUrl(context)),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    if (runCatching { context.startActivity(marketIntent) }.isFailure) {
        runCatching { context.startActivity(webIntent) }
    }
}

private fun playStoreWebUrl(context: android.content.Context): String =
    "https://play.google.com/store/apps/details?id=${context.packageName}"

private data class AboutActionRow(
    val title: String,
    val icon: ImageVector,
    val isExternal: Boolean = false,
    val onClick: () -> Unit,
)

private data class AboutInfoRow(
    val title: String,
    val value: String,
    val icon: ImageVector,
)

private val AboutRowDividerInset = SakhiSpacing.space4 + 36.dp + SakhiSpacing.space3
private val AboutLeadingGlyphSize = 16.dp
private val AboutTrailingIconSize = 12.dp

@Composable
private fun AboutActionSectionCard(
    label: String,
    rows: List<AboutActionRow>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
        ProfileSectionLabel(text = label)
        Surface(
            color = sakhiSystemBackground(),
            shape = RoundedCornerShape(SakhiRadius.xl),
            tonalElevation = SakhiSpacing.space1,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                rows.forEachIndexed { index, row ->
                    AboutActionRowContent(row = row)
                    if (index != rows.lastIndex) {
                        SakhiListDivider(startInset = AboutRowDividerInset)
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutSingleActionCard(
    row: AboutActionRow,
) {
    Surface(
        color = sakhiSystemBackground(),
        shape = RoundedCornerShape(SakhiRadius.xl),
        tonalElevation = SakhiSpacing.space1,
        modifier = Modifier.fillMaxWidth(),
    ) {
        AboutActionRowContent(row = row)
    }
}

@Composable
private fun AboutActionRowContent(
    row: AboutActionRow,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = row.onClick)
            .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AboutLeadingIcon(icon = row.icon)
        Text(
            text = row.title,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
            ),
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (row.isExternal) Icons.Filled.ArrowOutward else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = sakhiTertiaryLabel(),
            modifier = Modifier.size(AboutTrailingIconSize),
        )
    }
}

@Composable
private fun AboutInfoSectionCard(
    label: String,
    rows: List<AboutInfoRow>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
        ProfileSectionLabel(text = label)
        Surface(
            color = sakhiSystemBackground(),
            shape = RoundedCornerShape(SakhiRadius.xl),
            tonalElevation = SakhiSpacing.space1,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                rows.forEachIndexed { index, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
                        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AboutLeadingIcon(icon = row.icon)
                        Text(
                            text = row.title,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal,
                            ),
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = row.value,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = sakhiSecondaryLabel(),
                        )
                    }
                    if (index != rows.lastIndex) {
                        SakhiListDivider(startInset = AboutRowDividerInset)
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutLeadingIcon(
    icon: ImageVector,
) {
    Row(
        modifier = Modifier
            .size(36.dp)
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = CircleShape,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(AboutLeadingGlyphSize),
        )
    }
}
