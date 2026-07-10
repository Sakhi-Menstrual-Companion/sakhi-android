package team.sakhi.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import team.sakhi.android.designsystem.SakhiSpacing

/**
 * Shared profile/detail sheet shell: drag handle, back header, divider, and a
 * padded scroll body. This matches the reusable profile settings wrappers used
 * on iOS and reduces repeated screen-root boilerplate on Android.
 */
@Composable
fun DetailSheetScaffold(
    title: String? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    showDragHandle: Boolean = true,
    scrollable: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(SakhiSpacing.space5),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(SakhiSpacing.space5),
    trailingHeaderContent: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    SheetSurface(
        modifier = modifier,
        showDragHandle = showDragHandle,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space2, vertical = SakhiSpacing.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onClick = onBack)
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                )
            } else {
                Row(modifier = Modifier.weight(1f)) {}
            }
            trailingHeaderContent()
        }
        HorizontalDivider()

        val bodyModifier = Modifier
            .fillMaxSize()
            .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(contentPadding)

        Column(
            modifier = bodyModifier,
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}
