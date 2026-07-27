package com.lunentous.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Mirrors /api/reminder-states (global, joined with plant/type
 * name+icon+color) and /api/plants/:plantId/reminder-states (per-plant,
 * no plant_name). Both shapes fit here since the extras are all nullable. */
data class ReminderStateDto(
    val id: Long,
    @SerializedName("plant_id") val plantId: Long,
    @SerializedName("reminder_type_id") val reminderTypeId: Long,
    @SerializedName("due_date") val dueDate: String?,
    val notified: Int,
    @SerializedName("days_overdue") val daysOverdue: Int?,
    @SerializedName("plant_name") val plantName: String? = null,
    @SerializedName("reminder_type_name") val reminderTypeName: String? = null,
    @SerializedName("reminder_type_icon") val reminderTypeIcon: String? = null,
    @SerializedName("reminder_type_color") val reminderTypeColor: String? = null,
)
