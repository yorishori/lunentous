package com.lunentous.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Mirrors /api/plants/:plantId/phase-windows and /api/phase-windows/:id. */
data class PhaseWindowDto(
    val id: Long,
    @SerializedName("plant_id") val plantId: Long,
    @SerializedName("phase_type_id") val phaseTypeId: Long,
    @SerializedName("start_month") val startMonth: Int,
    @SerializedName("start_day") val startDay: Int,
    @SerializedName("end_month") val endMonth: Int,
    @SerializedName("end_day") val endDay: Int,
    val notes: String?,
    @SerializedName("phase_type_name") val phaseTypeName: String? = null,
    @SerializedName("phase_type_color") val phaseTypeColor: String? = null,
)

data class CreatePhaseWindowRequest(
    @SerializedName("phase_type_id") val phaseTypeId: Long,
    @SerializedName("start_month") val startMonth: Int,
    @SerializedName("start_day") val startDay: Int,
    @SerializedName("end_month") val endMonth: Int,
    @SerializedName("end_day") val endDay: Int,
    val notes: String? = null,
)
