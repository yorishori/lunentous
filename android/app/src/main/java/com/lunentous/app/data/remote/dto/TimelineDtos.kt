package com.lunentous.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class PhotoDto(
    val id: Long,
    @SerializedName("plant_id") val plantId: Long,
    @SerializedName("timeline_event_id") val timelineEventId: Long?,
    @SerializedName("file_path") val filePath: String,
    @SerializedName("created_at") val createdAt: String,
)

/** Mirrors /api/plants/:plantId/timeline and /api/timeline/:id. */
data class TimelineEventDto(
    val id: Long,
    @SerializedName("plant_id") val plantId: Long,
    @SerializedName("reminder_type_id") val reminderTypeId: Long?,
    @SerializedName("event_date") val eventDate: String,
    val text: String?,
    @SerializedName("created_at") val createdAt: String,
    val photos: List<PhotoDto>,
)

/** JSON-only PATCH body -- photos are handled separately via
 * POST /api/timeline/:id/photos (multipart, see LunentousApi). */
data class UpdateTimelineEventRequest(
    @SerializedName("event_date") val eventDate: String?,
    @SerializedName("reminder_type_id") val reminderTypeId: Long?,
    val text: String?,
)
