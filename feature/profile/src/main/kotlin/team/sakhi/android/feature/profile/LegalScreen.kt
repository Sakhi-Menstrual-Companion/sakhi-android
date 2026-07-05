package team.sakhi.android.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.ui.SheetSurface

/** Ports iOS `LegalView.swift`: documents list + data-rights list + a privacy note. */
@Composable
fun LegalScreen(onBack: () -> Unit) {
    var openPage by remember { mutableStateOf<ContentPageId?>(null) }

    openPage?.let { id ->
        ContentPageScreen(page = ContentLibrary.page(for_ = id), onBack = { openPage = null })
        return
    }

    SheetSurface(showDragHandle = true) {
        DetailHeader(title = "Legal & Compliance", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(SakhiSpacing.space5),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space5),
        ) {
            SettingsSectionCard(
                label = "DOCUMENTS",
                rows = listOf(
                    "Privacy Policy" to { openPage = ContentPageId.PRIVACY_POLICY },
                    "Terms of Service" to { openPage = ContentPageId.TERMS_OF_SERVICE },
                    "Code of Conduct" to { openPage = ContentPageId.CODE_OF_CONDUCT },
                ),
            )
            SettingsSectionCard(
                label = "YOUR DATA RIGHTS",
                rows = listOf(
                    "Cookie Policy" to { openPage = ContentPageId.COOKIE_POLICY },
                    "Data Usage Policy" to { openPage = ContentPageId.DATA_USAGE },
                    "GDPR Rights" to { openPage = ContentPageId.GDPR_RIGHTS },
                ),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(
                        text = "Your data stays private",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = "Sakhi stores your health data securely and we never sell or share your personal data with third parties.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun DetailHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SakhiSpacing.space2, vertical = SakhiSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        )
    }
    HorizontalDivider()
}

@Composable
internal fun SettingsSectionCard(label: String, rows: List<Pair<String, () -> Unit>>) {
    Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            shape = RoundedCornerShape(SakhiRadius.xl),
            tonalElevation = SakhiSpacing.space1,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                rows.forEachIndexed { index, (title, onClick) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onClick)
                            .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (index != rows.lastIndex) HorizontalDivider()
                }
            }
        }
    }
}
