package com.lunentous.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class OverridePeriodDto(
    val id: Long? = null,
    @SerializedName("start_month") val startMonth: Int,
    @SerializedName("start_day") val startDay: Int,
    @SerializedName("end_month") val endMonth: Int,
    @SerializedName("end_day") val endDay: Int,
    @SerializedName("interval_days") val intervalDays: Int?,
)

/** Mirrors /api/plants/:plantId/reminder-rules and /api/reminder-rules/:id.
 * annual_month/annual_day are mutually exclusive with
 * default_interval_days/override_periods -- see reminderRules.ts's
 * validateRuleMode -- always send all four together (nulling/emptying
 * whichever mode is inactive) so the server never has to guess intent
 * from a partial update. */
data class ReminderRuleDto(
    val id: Long,
    @SerializedName("plant_id") val plantId: Long,
    @SerializedName("reminder_type_id") val reminderTypeId: Long,
    @SerializedName("default_interval_days") val defaultIntervalDays: Int?,
    @SerializedName("annual_month") val annualMonth: Int?,
    @SerializedName("annual_day") val annualDay: Int?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("override_periods") val overridePeriods: List<OverridePeriodDto>,
)

data class CreateReminderRuleRequest(
    @SerializedName("reminder_type_id") val reminderTypeId: Long,
    @SerializedName("default_interval_days") val defaultIntervalDays: Int?,
    @SerializedName("annual_month") val annualMonth: Int?,
    @SerializedName("annual_day") val annualDay: Int?,
    @SerializedName("override_periods") val overridePeriods: List<OverridePeriodDto>,
)

/** PATCH replaces all of these fields wholesale -- always send them all,
 * mirroring the web's own PATCH behavior. */
data class UpdateReminderRuleRequest(
    @SerializedName("default_interval_days") val defaultIntervalDays: Int?,
    @SerializedName("annual_month") val annualMonth: Int?,
    @SerializedName("annual_day") val annualDay: Int?,
    @SerializedName("override_periods") val overridePeriods: List<OverridePeriodDto>,
)
