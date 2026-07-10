package team.sakhi.android.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.ui.DetailSheetScaffold

/** Ports iOS `HelpSupportView.swift`: FAQ + Safety Guidelines navigation rows. */
@Composable
fun HelpSupportScreen(onBack: () -> Unit) {
    var openPage by remember { mutableStateOf<ContentPageId?>(null) }

    openPage?.let { id ->
        ContentPageScreen(pageId = id, onBack = { openPage = null })
        return
    }

    DetailSheetScaffold(title = "Help & Support", onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space5)) {
            SettingsSectionCard(
                label = "SUPPORT",
                rows = listOf(
                    "Frequently Asked Questions" to { openPage = ContentPageId.FAQ },
                    "Safety Guidelines" to { openPage = ContentPageId.SAFETY_GUIDELINES },
                ),
            )
        }
    }
}
