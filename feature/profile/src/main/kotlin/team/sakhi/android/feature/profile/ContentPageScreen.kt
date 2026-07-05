package team.sakhi.android.feature.profile

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.ui.SheetSurface

/**
 * Generic renderer for any [ContentPage] -- shared by Legal, About, and Help &
 * Support's sub-pages, matching iOS's `InAppContentView`/`SanityLegalPageView`
 * layout (heading, subtitle, then sections, each either plain text items or a
 * grid of small cards).
 */
@Composable
fun ContentPageScreen(page: ContentPage, onBack: () -> Unit) {
    SheetSurface(showDragHandle = true) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
        }
        HorizontalDivider()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(SakhiSpacing.space5),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space5),
        ) {
            Text(
                text = page.heading,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = page.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            page.sections.forEach { section ->
                Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
                    Text(
                        text = section.title.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (section.style == ContentSectionStyle.CARDS) {
                        section.items.forEach { item ->
                            ContentCard(item)
                        }
                    } else {
                        section.items.forEach { item ->
                            Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space1)) {
                                if (item.title.isNotBlank()) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    )
                                }
                                Text(
                                    text = item.body,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentCard(item: ContentItem) {
    Surface(
        shape = RoundedCornerShape(SakhiRadius.lg),
        tonalElevation = SakhiSpacing.space1,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(SakhiSpacing.space4),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space1),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = item.body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
