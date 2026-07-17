package team.sakhi.android.feature.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Shield
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.toComposeColor
import team.sakhi.android.ui.DetailSheetScaffold
import team.sakhi.design.SakhiUIColors

/** Ports iOS `HelpSupportView.swift`: FAQ + Safety Guidelines navigation rows. */
@Composable
fun HelpSupportScreen(onBack: () -> Unit) {
    var openPage by remember { mutableStateOf<ContentPageId?>(null) }

    openPage?.let { id ->
        ContentPageScreen(pageId = id, onBack = { openPage = null })
        return
    }

    DetailSheetScaffold(
        title = stringResource(R.string.profile_help_support_title),
        subtitle = stringResource(R.string.profile_help_support_header_subtitle),
        headerIcon = Icons.AutoMirrored.Filled.Help,
        onBack = onBack,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space5)) {
            SettingsSectionCard(
                label = null,
                rows = listOf(
                    SettingsSectionRow(
                        title = stringResource(R.string.profile_help_support_faq),
                        subtitle = stringResource(R.string.profile_help_support_faq_subtitle),
                        icon = Icons.AutoMirrored.Filled.Help,
                        iconTint = SakhiUIColors.HELP_ICON_BLUE.toComposeColor(),
                        iconBackground = SakhiUIColors.HELP_ICON_BLUE.toComposeColor().copy(alpha = 0.10f),
                        titleFontWeight = FontWeight.Bold,
                        titleFontSize = 15.sp,
                        iconGlyphSize = 16.dp,
                        trailingIconSize = 12.dp,
                        onClick = { openPage = ContentPageId.FAQ },
                    ),
                    SettingsSectionRow(
                        title = stringResource(R.string.profile_help_support_safety_guidelines),
                        subtitle = stringResource(R.string.profile_help_support_safety_guidelines_subtitle),
                        icon = Icons.Filled.Shield,
                        iconTint = SakhiUIColors.ACT_MEDICATION.toComposeColor(),
                        iconBackground = SakhiUIColors.ACT_MEDICATION.toComposeColor().copy(alpha = 0.10f),
                        titleFontWeight = FontWeight.Bold,
                        titleFontSize = 15.sp,
                        iconGlyphSize = 16.dp,
                        trailingIconSize = 12.dp,
                        onClick = { openPage = ContentPageId.SAFETY_GUIDELINES },
                    ),
                ),
            )
        }
    }
}
