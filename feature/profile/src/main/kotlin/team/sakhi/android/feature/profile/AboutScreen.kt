package team.sakhi.android.feature.profile

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.koin.compose.koinInject
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.HapticImpact
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.ui.SheetSurface

private const val WEBSITE_URL = "https://sakhi.rachna.co"
private const val INSTAGRAM_URL = "https://instagram.com/sakhi.app"
private const val FEEDBACK_EMAIL = "hello@getswipe.in"
private const val PLAY_STORE_URL = "https://play.google.com/store/apps"

/** Ports iOS `AboutView.swift`: story/team/licenses navigation, connect links, share, app info. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    var openPage by remember { mutableStateOf<ContentPageId?>(null) }
    val context = LocalContext.current
    val hapticManager = koinInject<AndroidHapticManager>()

    openPage?.let { id ->
        ContentPageScreen(page = ContentLibrary.page(for_ = id), onBack = { openPage = null })
        return
    }

    SheetSurface(showDragHandle = true) {
        DetailHeader(title = "About Sakhi", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(SakhiSpacing.space5),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space5),
        ) {
            SettingsSectionCard(
                label = "OUR STORY",
                rows = listOf(
                    "The Sakhi Story" to { openPage = ContentPageId.ABOUT_US },
                    "Meet the Team" to { openPage = ContentPageId.CREDITS },
                    "Open Source Licenses" to { openPage = ContentPageId.OPEN_SOURCE_LICENSES },
                ),
            )

            SettingsSectionCard(
                label = "CONNECT",
                rows = listOf(
                    "Website" to { openUrl(context, WEBSITE_URL) },
                    "Instagram" to { openUrl(context, INSTAGRAM_URL) },
                    "Send Feedback" to { openUrl(context, "mailto:$FEEDBACK_EMAIL?subject=Sakhi%20Feedback") },
                    "Rate on Play Store" to { openUrl(context, PLAY_STORE_URL) },
                ),
            )

            SettingsSectionCard(
                label = "SHARE",
                rows = listOf(
                    "Share Sakhi" to {
                        hapticManager.impact(HapticImpact.LIGHT)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Check out Sakhi, a women's health app. $WEBSITE_URL")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Sakhi").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    },
                ),
            )

            Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
                Text(text = "Version ${appVersionName(context)}")
                Text(text = "Made with love in India")
                Text(text = "Team Sakhi")
            }
        }
    }
}

private fun appVersionName(context: android.content.Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
}.getOrDefault("1.0")

private fun openUrl(context: android.content.Context, url: String) {
    val intent = if (url.startsWith("mailto:")) {
        Intent(Intent.ACTION_SENDTO, android.net.Uri.parse(url))
    } else {
        Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
