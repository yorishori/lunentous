package com.lunentous.app.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.lunentous.app.LunentousApplication
import com.lunentous.app.MainActivity
import com.lunentous.app.data.widget.WidgetReminderItem
import com.lunentous.app.data.widget.loadWidgetReminders
import com.lunentous.app.ui.nav.EXTRA_PLANT_LOCAL_ID
import java.time.LocalDate

private val plantIdParam = ActionParameters.Key<Long>(EXTRA_PLANT_LOCAL_ID)
private val reminderTypeIdParam = ActionParameters.Key<Long>("reminderTypeLocalId")

private val backgroundColor = Color(0xFF1E1E2E) // Catppuccin Mocha base, matching the app's dark theme
private val surfaceColor = Color(0xFF313244)
private val textColor = Color(0xFFCDD6F4)
private val mutedColor = Color(0xFFA6ADC8)
private val accentColor = Color(0xFFA6E3A1)
private val overdueColor = Color(0xFFF38BA8)

/**
 * Overdue count + next few reminders, one-tap mark-done -- per the Android
 * plan's native-additions list. No GlanceStateDefinition: each
 * provideGlance call just re-reads Room directly (see
 * data/widget/WidgetReminderLoader.kt), since that's already fast/local
 * and avoids a second persisted-state copy of the same data.
 */
class LunentousWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as LunentousApplication).container
        val model = loadWidgetReminders(container)

        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(backgroundColor)
                    .cornerRadius(16.dp)
                    .padding(12.dp),
            ) {
                Text(
                    text = if (model.overdueCount > 0) "${model.overdueCount} overdue" else "All caught up",
                    style = TextStyle(color = ColorProvider(if (model.overdueCount > 0) overdueColor else accentColor), fontWeight = FontWeight.Bold),
                )
                Spacer(modifier = GlanceModifier.height(8.dp))

                if (model.items.isEmpty()) {
                    Text("Nothing due soon", style = TextStyle(color = ColorProvider(mutedColor)))
                } else {
                    model.items.forEach { item -> ReminderRow(item) }
                }
            }
        }
    }
}

@Composable
private fun ReminderRow(item: WidgetReminderItem) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(surfaceColor)
            .cornerRadius(10.dp)
            .padding(8.dp)
            .clickable(actionStartActivity<MainActivity>(actionParametersOf(plantIdParam to item.plantLocalId))),
    ) {
        Text(item.plantName, style = TextStyle(color = ColorProvider(textColor), fontWeight = FontWeight.Bold))
        Text(
            "${item.reminderTypeName} — ${describeDue(item.daysOverdue)}",
            style = TextStyle(color = ColorProvider(if (item.daysOverdue >= 0) overdueColor else mutedColor)),
        )
        Spacer(modifier = GlanceModifier.height(6.dp))
        Text(
            "Mark done",
            style = TextStyle(color = ColorProvider(accentColor), fontWeight = FontWeight.Bold),
            modifier = GlanceModifier
                .background(backgroundColor)
                .cornerRadius(8.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .clickable(actionRunCallback<MarkDoneAction>(actionParametersOf(plantIdParam to item.plantLocalId, reminderTypeIdParam to item.reminderTypeLocalId))),
        )
    }
}

private fun describeDue(daysOverdue: Int): String = when {
    daysOverdue > 0 -> "$daysOverdue day${if (daysOverdue == 1) "" else "s"} overdue"
    daysOverdue == 0 -> "due today"
    else -> "in ${-daysOverdue} day${if (daysOverdue == -1) "" else "s"}"
}

class LunentousWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LunentousWidget()
}

class MarkDoneAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val plantLocalId = parameters[plantIdParam] ?: return
        val reminderTypeLocalId = parameters[reminderTypeIdParam] ?: return
        val container = (context.applicationContext as LunentousApplication).container

        container.timelineRepository.createEvent(
            plantLocalId = plantLocalId,
            eventDate = LocalDate.now().toString(),
            reminderTypeLocalId = reminderTypeLocalId,
            text = null,
        )
        container.reminderStateRepository.pullSyncForPlant(plantLocalId)
        LunentousWidget().updateAll(context)
    }
}

/** Called after anything that could change what the widget shows -- see
 * PullSyncWorker and the in-app mark-done flows. A no-op when no widget
 * instance is currently placed. */
suspend fun refreshLunentousWidget(context: Context) {
    LunentousWidget().updateAll(context)
}
