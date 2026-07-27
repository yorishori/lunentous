package com.lunentous.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Mirrors GET/POST /api/plants and PATCH /api/plants/:id response shape. */
data class PlantDto(
    val id: Long,
    val name: String,
    val species: String?,
    val location: String?,
    @SerializedName("acquired_date") val acquiredDate: String?,
    @SerializedName("avatar_photo_id") val avatarPhotoId: Long?,
    @SerializedName("avatar_photo_path") val avatarPhotoPath: String?,
    @SerializedName("general_notes") val generalNotes: String?,
    val archived: Int,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
)

/** GET /api/plants/:id -- same fields as PlantDto plus the joined extras. */
data class PlantDetailDto(
    val id: Long,
    val name: String,
    val species: String?,
    val location: String?,
    @SerializedName("acquired_date") val acquiredDate: String?,
    @SerializedName("avatar_photo_id") val avatarPhotoId: Long?,
    @SerializedName("avatar_photo_path") val avatarPhotoPath: String?,
    @SerializedName("general_notes") val generalNotes: String?,
    val archived: Int,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("active_phase_windows") val activePhaseWindows: List<PhaseWindowDto>,
    @SerializedName("reminder_states") val reminderStates: List<ReminderStateDto>,
)

data class CreatePlantRequest(
    val name: String,
    val species: String? = null,
    val location: String? = null,
    @SerializedName("acquired_date") val acquiredDate: String? = null,
    @SerializedName("general_notes") val generalNotes: String? = null,
)
