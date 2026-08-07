package team.sakhi.android.feature.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.toComposeColor
import team.sakhi.android.ui.DetailSheetScaffold
import team.sakhi.android.ui.ProfileSectionLabel
import team.sakhi.android.ui.SakhiNavDirection
import team.sakhi.android.ui.SakhiScreenTransition
import team.sakhi.design.DesignTokens
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiTertiaryLabel

/** Ports iOS `LegalView.swift`: documents list + data-rights list + a privacy note. */
@Composable
fun LegalScreen(onBack: () -> Unit) {
    var openPage by remember { mutableStateOf<ContentPageId?>(null) }
    val legalIconBackground = DesignTokens.COLOR_LIGHT_PINK.toComposeColor()

    BackHandler(enabled = openPage != null) { openPage = null }

    // Opening a content page pushes it in from the right; closing pops it back --
    // the same app-wide slide every other screen swap uses, instead of an instant cut.
    SakhiScreenTransition(
        targetState = openPage,
        directionFor = { _, target ->
            if (target != null) SakhiNavDirection.Forward else SakhiNavDirection.Backward
        },
        label = "legal_page_transition",
    ) { page ->
    if (page != null) {
        ContentPageScreen(pageId = page, onBack = { openPage = null })
        return@SakhiScreenTransition
    }

    DetailSheetScaffold(
        title = stringResource(R.string.profile_legal_title),
        subtitle = stringResource(R.string.profile_legal_header_subtitle),
        headerIcon = Icons.Filled.Description,
        onBack = onBack,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space5)) {
            SettingsSectionCard(
                label = stringResource(R.string.profile_legal_section_documents),
                rows = listOf(
                    SettingsSectionRow(
                        title = stringResource(R.string.profile_legal_privacy_policy),
                        icon = Icons.Filled.Lock,
                        iconTint = MaterialTheme.colorScheme.primary,
                        iconBackground = legalIconBackground,
                        circularIconBadge = true,
                        iconBadgeSize = 36.dp,
                        iconGlyphSize = LegalRowGlyphSize,
                        titleFontSize = LegalRowTitleSize,
                        trailingIconSize = LegalTrailingIconSize,
                        onClick = { openPage = ContentPageId.PRIVACY_POLICY },
                    ),
                    SettingsSectionRow(
                        title = stringResource(R.string.profile_legal_terms_of_service),
                        icon = Icons.Filled.Description,
                        iconTint = MaterialTheme.colorScheme.primary,
                        iconBackground = legalIconBackground,
                        circularIconBadge = true,
                        iconBadgeSize = 36.dp,
                        iconGlyphSize = LegalRowGlyphSize,
                        titleFontSize = LegalRowTitleSize,
                        trailingIconSize = LegalTrailingIconSize,
                        onClick = { openPage = ContentPageId.TERMS_OF_SERVICE },
                    ),
                    SettingsSectionRow(
                        title = stringResource(R.string.profile_legal_code_of_conduct),
                        icon = Icons.Filled.Shield,
                        iconTint = MaterialTheme.colorScheme.primary,
                        iconBackground = legalIconBackground,
                        circularIconBadge = true,
                        iconBadgeSize = 36.dp,
                        iconGlyphSize = LegalRowGlyphSize,
                        titleFontSize = LegalRowTitleSize,
                        trailingIconSize = LegalTrailingIconSize,
                        onClick = { openPage = ContentPageId.CODE_OF_CONDUCT },
                    ),
                ),
            )
            SettingsSectionCard(
                label = stringResource(R.string.profile_legal_section_data_rights),
                rows = listOf(
                    SettingsSectionRow(
                        title = stringResource(R.string.profile_legal_cookie_policy),
                        icon = Icons.Filled.Language,
                        iconTint = MaterialTheme.colorScheme.primary,
                        iconBackground = legalIconBackground,
                        circularIconBadge = true,
                        iconBadgeSize = 36.dp,
                        iconGlyphSize = LegalRowGlyphSize,
                        titleFontSize = LegalRowTitleSize,
                        trailingIconSize = LegalTrailingIconSize,
                        onClick = { openPage = ContentPageId.COOKIE_POLICY },
                    ),
                    SettingsSectionRow(
                        title = stringResource(R.string.profile_legal_data_usage_policy),
                        icon = Icons.Filled.BarChart,
                        iconTint = MaterialTheme.colorScheme.primary,
                        iconBackground = legalIconBackground,
                        circularIconBadge = true,
                        iconBadgeSize = 36.dp,
                        iconGlyphSize = LegalRowGlyphSize,
                        titleFontSize = LegalRowTitleSize,
                        trailingIconSize = LegalTrailingIconSize,
                        onClick = { openPage = ContentPageId.DATA_USAGE },
                    ),
                    SettingsSectionRow(
                        title = stringResource(R.string.profile_legal_gdpr_rights),
                        icon = Icons.Filled.Gavel,
                        iconTint = MaterialTheme.colorScheme.primary,
                        iconBackground = legalIconBackground,
                        circularIconBadge = true,
                        iconBadgeSize = 36.dp,
                        iconGlyphSize = LegalRowGlyphSize,
                        titleFontSize = LegalRowTitleSize,
                        trailingIconSize = LegalTrailingIconSize,
                        onClick = { openPage = ContentPageId.GDPR_RIGHTS },
                    ),
                ),
            )

            Surface(
                shape = RoundedCornerShape(SakhiRadius.xl),
                tonalElevation = SakhiSpacing.space1,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(SakhiSpacing.space4),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(LegalPrivacyNoteIconSize),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space1)) {
                        Text(
                            text = stringResource(R.string.profile_legal_privacy_note_title),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = LegalPrivacyNoteTitleSize,
                            ),
                        )
                        Text(
                            text = stringResource(R.string.profile_legal_privacy_note_body),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = LegalPrivacyNoteBodySize),
                            color = sakhiSecondaryLabel(),
                        )
                    }
                }
            }
        }
    }
    }
}

private val LegalRowGlyphSize = 14.dp
private val LegalTrailingIconSize = 12.dp
private val LegalRowTitleSize = 15.sp
private val LegalPrivacyNoteIconSize = 16.dp
private val LegalPrivacyNoteTitleSize = 15.sp
private val LegalPrivacyNoteBodySize = 13.sp

internal data class SettingsSectionRow(
    val title: String,
    val subtitle: String? = null,
    val icon: ImageVector? = null,
    val iconTint: Color = Color.Unspecified,
    val iconBackground: Color = Color.Unspecified,
    val circularIconBadge: Boolean = false,
    val iconBadgeSize: Dp = 40.dp,
    val iconGlyphSize: Dp = 24.dp,
    val titleFontWeight: FontWeight? = null,
    val titleFontSize: TextUnit = TextUnit.Unspecified,
    val trailingIconSize: Dp = 24.dp,
    val onClick: () -> Unit,
)

@Composable
internal fun SettingsSectionCard(label: String?, rows: List<SettingsSectionRow>) {
    Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
        label?.let { labelText ->
            ProfileSectionLabel(text = labelText)
        }
        Surface(
            shape = RoundedCornerShape(SakhiRadius.xl),
            tonalElevation = SakhiSpacing.space1,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                rows.forEachIndexed { index, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = row.onClick)
                            .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        row.icon?.let { icon ->
                            val iconTint = if (row.iconTint == Color.Unspecified) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                row.iconTint
                            }
                            val iconBackground = if (row.iconBackground == Color.Unspecified) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                            } else {
                                row.iconBackground
                            }
                            Box(
                                modifier = Modifier
                                    .size(row.iconBadgeSize)
                                    .background(
                                        color = iconBackground,
                                        shape = if (row.circularIconBadge) CircleShape else RoundedCornerShape(10.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = iconTint,
                                    modifier = Modifier.size(row.iconGlyphSize),
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space1),
                        ) {
                            val titleStyle = MaterialTheme.typography.bodyLarge.let { base ->
                                if (row.titleFontWeight != null || row.titleFontSize != TextUnit.Unspecified) {
                                    base.copy(
                                        fontWeight = row.titleFontWeight ?: base.fontWeight,
                                        fontSize = if (row.titleFontSize != TextUnit.Unspecified) row.titleFontSize else base.fontSize,
                                    )
                                } else {
                                    base
                                }
                            }
                            Text(
                                text = row.title,
                                style = titleStyle,
                            )
                            row.subtitle?.let { subtitle ->
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = sakhiSecondaryLabel(),
                                )
                            }
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = sakhiTertiaryLabel(),
                            modifier = Modifier.size(row.trailingIconSize),
                        )
                    }
                    if (index != rows.lastIndex) {
                        val dividerStart = if (row.icon != null) {
                            SakhiSpacing.space4 + row.iconBadgeSize + SakhiSpacing.space3
                        } else {
                            SakhiSpacing.space4
                        }
                        HorizontalDivider(modifier = Modifier.padding(start = dividerStart))
                    }
                }
            }
        }
    }
}
