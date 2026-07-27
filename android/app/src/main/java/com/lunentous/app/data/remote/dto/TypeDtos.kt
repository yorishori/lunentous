package com.lunentous.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Mirrors /api/reminder-types. */
data class ReminderTypeDto(
    val id: Long,
    val name: String,
    val icon: String?,
    val color: String?,
    val archived: Int,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("usage_count") val usageCount: Int? = null,
)

/** Mirrors /api/phase-types -- same shape as ReminderTypeDto minus icon. */
data class PhaseTypeDto(
    val id: Long,
    val name: String,
    val color: String?,
    val archived: Int,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("usage_count") val usageCount: Int? = null,
)

data class CreateReminderTypeRequest(
    val name: String,
    val icon: String? = null,
    val color: String? = null,
)

data class CreatePhaseTypeRequest(
    val name: String,
    val color: String? = null,
)
