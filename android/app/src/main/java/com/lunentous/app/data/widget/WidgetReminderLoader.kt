package com.lunentous.app.data.widget

import com.lunentous.app.di.AppContainer
import java.time.LocalDate
import kotlinx.coroutines.flow.first

data class WidgetReminderItem(
    val plantLocalId: Long,
    val plantName: String,
    val reminderTypeLocalId: Long,
    val reminderTypeName: String,
    val reminderTypeIcon: String?,
    val reminderTypeColor: String?,
    val daysOverdue: Int,
)

/** Mirrors the Dashboard's overdue/upcoming split, further dividing
 * "upcoming" into This Week vs Later so the widget's compact list still
 * gives a sense of urgency at a glance. */
data class WidgetUiModel(
    val overdue: List<WidgetReminderItem>,
    val thisWeek: List<WidgetReminderItem>,
    val later: List<WidgetReminderItem>,
) {
    val isEmpty: Boolean get() = overdue.isEmpty() && thisWeek.isEmpty() && later.isEmpty()
}

private const val MAX_PER_SECTION = 4
private const val THIS_WEEK_THRESHOLD = -7

/**
 * One-shot equivalent of DashboardViewModel's client-side join --
 * ReminderStateEntity doesn't carry plant/type names, so this repeats that
 * join, just as a plain suspend read instead of a combined Flow (see
 * ui/widget/LunentousWidget.kt: Glance recomposes on its own update cycle,
 * not by collecting a Flow). Overdue items sort soonest-overdue-first;
 * This Week and Later sort soonest-due-first.
 */
suspend fun loadWidgetReminders(container: AppContainer): WidgetUiModel {
    val plantsById = container.plantRepository.observeAll().first().associateBy { it.localId }
    val typesById = container.reminderTypeRepository.observeAll().first().associateBy { it.localId }
    val today = LocalDate.now().toEpochDay()

    val tasks = container.reminderStateRepository.getAllOnce().mapNotNull { state ->
        val dueDate = state.dueDate ?: return@mapNotNull null
        val plant = plantsById[state.plantLocalId] ?: return@mapNotNull null
        val type = typesById[state.reminderTypeLocalId] ?: return@mapNotNull null
        WidgetReminderItem(
            plantLocalId = plant.localId,
            plantName = plant.name,
            reminderTypeLocalId = type.localId,
            reminderTypeName = type.name,
            reminderTypeIcon = type.icon,
            reminderTypeColor = type.color,
            daysOverdue = (today - LocalDate.parse(dueDate).toEpochDay()).toInt(),
        )
    }

    return WidgetUiModel(
        overdue = tasks.filter { it.daysOverdue >= 0 }.sortedByDescending { it.daysOverdue }.take(MAX_PER_SECTION),
        thisWeek = tasks.filter { it.daysOverdue in THIS_WEEK_THRESHOLD until 0 }.sortedByDescending { it.daysOverdue }.take(MAX_PER_SECTION),
        later = tasks.filter { it.daysOverdue < THIS_WEEK_THRESHOLD }.sortedByDescending { it.daysOverdue }.take(MAX_PER_SECTION),
    )
}
