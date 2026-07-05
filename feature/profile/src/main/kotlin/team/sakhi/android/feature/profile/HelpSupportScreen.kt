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
import androidx.compose.ui.Modifier
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.ui.SheetSurface

/** Ports iOS `HelpSupportView.swift`: FAQ + Safety Guidelines navigation rows. */
@Composable
fun HelpSupportScreen(onBack: () -> Unit) {
    var openPage by remember { mutableStateOf<ContentPageId?>(null) }

    openPage?.let { id ->
        ContentPageScreen(page = ContentLibrary.page(for_ = id), onBack = { openPage = null })
        return
    }

    SheetSurface(showDragHandle = true) {
        DetailHeader(title = "Help & Support", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(SakhiSpacing.space5),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space5),
        ) {
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
