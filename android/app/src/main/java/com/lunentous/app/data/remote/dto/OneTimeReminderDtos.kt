package com.lunentous.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Mirrors /api/plants/:plantId/one-time-reminders, /api/one-time-reminders,
 * and /api/one-time-reminders/:id. */
data class OneTimeReminderDto(
    val id: Long,
    @SerializedName("plant_id") val plantId: Long,
    @SerializedName("due_date") val dueDate: String,
    val text: String,
    @SerializedName("completed_at") val completedAt: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("plant_name") val plantName: String? = null,
)

data class CreateOneTimeReminderRequest(
    @SerializedName("due_date") val dueDate: String,
    val text: String,
)

/** PATCH replaces all three fields wholesale -- send completedAt = null to
 * un-complete, or a timestamp to complete. */
data class UpdateOneTimeReminderRequest(
    @SerializedName("due_date") val dueDate: String,
    val text: String,
    @SerializedName("completed_at") val completedAt: String?,
)
