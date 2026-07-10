package team.sakhi.android.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.LocalContext
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * Android home-screen widget, matching iOS's real widget lane: countdown text
 * bottom-left plus a top-right "log today" affordance routed through the same
 * app deep-link intake path. Glance does not expose the same fully dynamic
 * per-phase gradient rendering iOS's WidgetKit view uses, so Android uses the
 * exact per-phase resolved background mid-tone plus the same primary/secondary/
 * accent text colors. The data and copy stay in lock-step with Home.
 */
class SakhiPeriodWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            androidx.compose.ui.unit.DpSize(120.dp, 120.dp),
            androidx.compose.ui.unit.DpSize(280.dp, 120.dp),
        ),
    )

    override suspend fun provideGlance(context: Context, id: androidx.glance.GlanceId) {
        provideContent {
            WidgetContent(
                snapshot = AndroidWidgetSnapshotManager.loadSnapshot(context)
                    ?: WidgetSnapshot.placeholder(context),
            )
        }
    }
}

class SakhiPeriodWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SakhiPeriodWidget()
}

@androidx.compose.runtime.Composable
private fun WidgetContent(snapshot: WidgetSnapshot) {
    val size = LocalSize.current
    val isMedium = size.width >= 180.dp
    val titleSize = if (isMedium) 34.sp else 28.sp
    val subtitleSize = if (isMedium) 15.sp else 13.sp

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(widgetColor(snapshot.backgroundLight, snapshot.backgroundDark))
            .padding(16.dp),
    ) {
        if (snapshot.showLogButton) {
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.TopEnd,
            ) {
                WidgetLogButton(snapshot = snapshot)
            }
        }

        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.BottomStart,
        ) {
            Column {
                if (snapshot.heading.isNotEmpty()) {
                    Text(
                        text = snapshot.heading,
                        style = TextStyle(
                            color = widgetColor(snapshot.primaryLight, snapshot.primaryDark),
                            fontWeight = FontWeight.Bold,
                            fontSize = titleSize,
                        ),
                        maxLines = 1,
                    )
                }
                Text(
                    text = snapshot.subtitle,
                    style = TextStyle(
                        color = widgetColor(snapshot.secondaryLight, snapshot.secondaryDark),
                        fontSize = subtitleSize,
                    ),
                    maxLines = 2,
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun WidgetLogButton(snapshot: WidgetSnapshot) {
    val context = LocalContext.current
    val label = if (snapshot.isTodayLogged) context.getString(R.string.widget_log_today_logged) else "+"
    val backgroundAlpha = if (snapshot.isTodayLogged) 0.24f else 0.16f
    Text(
        text = label,
        modifier = GlanceModifier
            .cornerRadius(14.dp)
            .background(
                widgetColor(
                    light = withAlpha(snapshot.accentLight, backgroundAlpha),
                    dark = withAlpha(snapshot.accentDark, backgroundAlpha),
                ),
            )
            .clickable(actionStartActivity(widgetLogIntent()))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        style = TextStyle(
            color = widgetColor(snapshot.accentLight, snapshot.accentDark),
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
        ),
    )
}

private fun widgetLogIntent(): Intent {
    return Intent(Intent.ACTION_VIEW, Uri.parse(AndroidWidgetSnapshotManager.WIDGET_LOG_TODAY_URL)).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
}

private fun widgetColor(light: String, dark: String): ColorProvider {
    return androidx.glance.color.ColorProvider(hexColor(light), hexColor(dark))
}

private fun hexColor(value: String): Color {
    return Color(android.graphics.Color.parseColor(value))
}

private fun withAlpha(hex: String, alpha: Float): String {
    val color = android.graphics.Color.parseColor(hex)
    val alphaChannel = (alpha.coerceIn(0f, 1f) * 255).toInt()
    val value = (color and 0x00FFFFFF) or (alphaChannel shl 24)
    return String.format("#%08X", value)
}
