package com.lunentous.app.data.widget

import com.lunentous.app.di.AppContainer
import java.time.LocalDate
import kotlinx.coroutines.flow.first

data class WidgetReminderItem(
    val plantLocalId: Long,
    val plantName: String,
    val reminderTypeLocalId: Long,
    val reminderTypeName: String,
    val daysOverdue: Int,
)

data class WidgetUiModel(val overdueCount: Int, val items: List<WidgetReminderItem>)

private const val MAX_ITEMS = 5

/**
 * One-shot equivalent of DashboardViewModel's client-side join --
 * ReminderStateEntity doesn't carry plant/type names, so this repeats that
 * join, just as a plain suspend read instead of a combined Flow (see
 * ui/widget/LunentousWidget.kt: Glance recomposes on its own update cycle,
 * not by collecting a Flow). Overdue items sort soonest-overdue-first;
 * shown before any not-yet-due upcoming items, mirroring the dashboard.
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
            daysOverdue = (today - LocalDate.parse(dueDate).toEpochDay()).toInt(),
        )
    }.sortedByDescending { it.daysOverdue }

    return WidgetUiModel(
        overdueCount = tasks.count { it.daysOverdue >= 0 },
        items = tasks.take(MAX_ITEMS),
    )
}
