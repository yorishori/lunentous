package com.lunentous.app.data.remote

import com.lunentous.app.data.remote.dto.ApiKeyDto
import com.lunentous.app.data.remote.dto.CreateApiKeyRequest
import com.lunentous.app.data.remote.dto.CreatePhaseTypeRequest
import com.lunentous.app.data.remote.dto.CreatePhaseWindowRequest
import com.lunentous.app.data.remote.dto.CreatePlantRequest
import com.lunentous.app.data.remote.dto.CreateReminderRuleRequest
import com.lunentous.app.data.remote.dto.CreateReminderTypeRequest
import com.lunentous.app.data.remote.dto.CreatedApiKeyDto
import com.lunentous.app.data.remote.dto.CreateOneTimeReminderRequest
import com.lunentous.app.data.remote.dto.OneTimeReminderDto
import com.lunentous.app.data.remote.dto.PhaseTypeDto
import com.lunentous.app.data.remote.dto.PhaseWindowDto
import com.lunentous.app.data.remote.dto.PlantDetailDto
import com.lunentous.app.data.remote.dto.PlantDto
import com.lunentous.app.data.remote.dto.ReminderRuleDto
import com.lunentous.app.data.remote.dto.ReminderStateDto
import com.lunentous.app.data.remote.dto.ReminderTypeDto
import com.lunentous.app.data.remote.dto.TimelineEventDto
import com.lunentous.app.data.remote.dto.UpdateOneTimeReminderRequest
import com.lunentous.app.data.remote.dto.UpdateReminderRuleRequest
import com.lunentous.app.data.remote.dto.UpdateTimelineEventRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/** 1:1 with the server routes documented in ARCHITECTURE.md's REST API
 * reference. Base URL is rewritten per-request by DynamicBaseUrlInterceptor
 * (see NetworkModule) since it's user-configurable at runtime. */
interface LunentousApi {
    @GET("api/health")
    suspend fun health(): Response<Unit>

    // ---------- Plants ----------

    @GET("api/plants")
    suspend fun getPlants(@Query("archived") archived: Boolean? = null): List<PlantDto>

    @POST("api/plants")
    suspend fun createPlant(@Body body: CreatePlantRequest): PlantDto

    @GET("api/plants/{id}")
    suspend fun getPlant(@Path("id") id: Long): PlantDetailDto

    @PATCH("api/plants/{id}")
    suspend fun updatePlant(@Path("id") id: Long, @Body body: CreatePlantRequest): PlantDto

    @POST("api/plants/{id}/archive")
    suspend fun archivePlant(@Path("id") id: Long): PlantDto

    @POST("api/plants/{id}/unarchive")
    suspend fun unarchivePlant(@Path("id") id: Long): PlantDto

    @Multipart
    @POST("api/plants/{id}/avatar")
    suspend fun uploadAvatar(@Path("id") id: Long, @Part file: MultipartBody.Part): PlantDto

    // ---------- Reminder types ----------

    @GET("api/reminder-types")
    suspend fun getReminderTypes(@Query("archived") archived: Boolean? = null): List<ReminderTypeDto>

    @POST("api/reminder-types")
    suspend fun createReminderType(@Body body: CreateReminderTypeRequest): ReminderTypeDto

    @PATCH("api/reminder-types/{id}")
    suspend fun updateReminderType(@Path("id") id: Long, @Body body: CreateReminderTypeRequest): ReminderTypeDto

    @POST("api/reminder-types/{id}/archive")
    suspend fun archiveReminderType(@Path("id") id: Long): ReminderTypeDto

    @POST("api/reminder-types/{id}/unarchive")
    suspend fun unarchiveReminderType(@Path("id") id: Long): ReminderTypeDto

    // ---------- Phase types ----------

    @GET("api/phase-types")
    suspend fun getPhaseTypes(@Query("archived") archived: Boolean? = null): List<PhaseTypeDto>

    @POST("api/phase-types")
    suspend fun createPhaseType(@Body body: CreatePhaseTypeRequest): PhaseTypeDto

    @PATCH("api/phase-types/{id}")
    suspend fun updatePhaseType(@Path("id") id: Long, @Body body: CreatePhaseTypeRequest): PhaseTypeDto

    @POST("api/phase-types/{id}/archive")
    suspend fun archivePhaseType(@Path("id") id: Long): PhaseTypeDto

    @POST("api/phase-types/{id}/unarchive")
    suspend fun unarchivePhaseType(@Path("id") id: Long): PhaseTypeDto

    // ---------- Reminder rules ----------

    @GET("api/plants/{plantId}/reminder-rules")
    suspend fun getReminderRules(@Path("plantId") plantId: Long): List<ReminderRuleDto>

    @POST("api/plants/{plantId}/reminder-rules")
    suspend fun createReminderRule(@Path("plantId") plantId: Long, @Body body: CreateReminderRuleRequest): ReminderRuleDto

    @PATCH("api/reminder-rules/{id}")
    suspend fun updateReminderRule(@Path("id") id: Long, @Body body: UpdateReminderRuleRequest): ReminderRuleDto

    @DELETE("api/reminder-rules/{id}")
    suspend fun deleteReminderRule(@Path("id") id: Long): Response<Unit>

    // ---------- Reminder states ----------

    @GET("api/reminder-states")
    suspend fun getReminderStates(
        @Query("due_before_or_on") dueBeforeOrOn: String? = null,
        @Query("notified") notified: Boolean? = null,
    ): List<ReminderStateDto>

    @GET("api/plants/{plantId}/reminder-states")
    suspend fun getReminderStatesForPlant(@Path("plantId") plantId: Long): List<ReminderStateDto>

    @POST("api/reminder-states/{id}/mark-notified")
    suspend fun markNotified(@Path("id") id: Long): ReminderStateDto

    // ---------- Phase windows ----------

    @GET("api/plants/{plantId}/phase-windows")
    suspend fun getPhaseWindows(@Path("plantId") plantId: Long): List<PhaseWindowDto>

    @POST("api/plants/{plantId}/phase-windows")
    suspend fun createPhaseWindow(@Path("plantId") plantId: Long, @Body body: CreatePhaseWindowRequest): PhaseWindowDto

    @PATCH("api/phase-windows/{id}")
    suspend fun updatePhaseWindow(@Path("id") id: Long, @Body body: CreatePhaseWindowRequest): PhaseWindowDto

    @DELETE("api/phase-windows/{id}")
    suspend fun deletePhaseWindow(@Path("id") id: Long): Response<Unit>

    // ---------- One-time reminders ----------

    @GET("api/plants/{plantId}/one-time-reminders")
    suspend fun getOneTimeReminders(@Path("plantId") plantId: Long): List<OneTimeReminderDto>

    /** Across every plant -- used by the Dashboard. */
    @GET("api/one-time-reminders")
    suspend fun getAllOneTimeReminders(): List<OneTimeReminderDto>

    @POST("api/plants/{plantId}/one-time-reminders")
    suspend fun createOneTimeReminder(@Path("plantId") plantId: Long, @Body body: CreateOneTimeReminderRequest): OneTimeReminderDto

    @PATCH("api/one-time-reminders/{id}")
    suspend fun updateOneTimeReminder(@Path("id") id: Long, @Body body: UpdateOneTimeReminderRequest): OneTimeReminderDto

    @DELETE("api/one-time-reminders/{id}")
    suspend fun deleteOneTimeReminder(@Path("id") id: Long): Response<Unit>

    // ---------- Timeline ----------

    @GET("api/plants/{plantId}/timeline")
    suspend fun getTimeline(
        @Path("plantId") plantId: Long,
        @Query("reminder_type_id") reminderTypeId: Long? = null,
        @Query("limit") limit: Int? = null,
        @Query("before") before: Long? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): List<TimelineEventDto>

    @Multipart
    @POST("api/plants/{plantId}/timeline")
    suspend fun createTimelineEvent(
        @Path("plantId") plantId: Long,
        @Part("event_date") eventDate: RequestBody,
        @Part("reminder_type_id") reminderTypeId: RequestBody?,
        @Part("text") text: RequestBody?,
        @Part photos: List<MultipartBody.Part>,
    ): TimelineEventDto

    @PATCH("api/timeline/{id}")
    suspend fun updateTimelineEvent(@Path("id") id: Long, @Body body: UpdateTimelineEventRequest): TimelineEventDto

    @Multipart
    @POST("api/timeline/{id}/photos")
    suspend fun appendTimelinePhotos(@Path("id") id: Long, @Part photos: List<MultipartBody.Part>): TimelineEventDto

    @DELETE("api/timeline/{id}")
    suspend fun deleteTimelineEvent(@Path("id") id: Long): Response<Unit>

    @DELETE("api/photos/{id}")
    suspend fun deletePhoto(@Path("id") id: Long): Response<Unit>

    // ---------- API keys ----------

    @GET("api/api-keys")
    suspend fun getApiKeys(): List<ApiKeyDto>

    @POST("api/api-keys")
    suspend fun createApiKey(@Body body: CreateApiKeyRequest): CreatedApiKeyDto

    @DELETE("api/api-keys/{id}")
    suspend fun deleteApiKey(@Path("id") id: Long): Response<Unit>

    // ---------- Export ----------

    @Streaming
    @GET("api/export")
    suspend fun export(): ResponseBody
}
