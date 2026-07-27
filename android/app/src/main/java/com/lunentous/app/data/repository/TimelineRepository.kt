package com.lunentous.app.data.repository

import com.lunentous.app.data.auth.SessionStore
import com.lunentous.app.data.local.dao.PhotoDao
import com.lunentous.app.data.local.dao.PlantDao
import com.lunentous.app.data.local.dao.ReminderTypeDao
import com.lunentous.app.data.local.dao.TimelineEventDao
import com.lunentous.app.data.local.entity.PhotoEntity
import com.lunentous.app.data.local.entity.TimelineEventEntity
import com.lunentous.app.data.remote.LunentousApi
import com.lunentous.app.data.remote.dto.PhotoDto
import com.lunentous.app.data.remote.dto.TimelineEventDto
import com.lunentous.app.data.remote.dto.UpdateTimelineEventRequest
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

data class TimelineEventWithPhotos(val event: TimelineEventEntity, val photos: List<PhotoEntity>)

/**
 * Note: like reminder rules, an event tagged with a reminder type
 * (create/edit/delete) can trigger server-side recompute -- callers should
 * re-run ReminderStateRepository.pullSyncForPlant() afterward.
 *
 * plantDao/reminderTypeDao are held only to resolve local IDs to server
 * IDs for outgoing requests -- callers never look these up themselves.
 */
class TimelineRepository(
    private val eventDao: TimelineEventDao,
    private val photoDao: PhotoDao,
    private val plantDao: PlantDao,
    private val reminderTypeDao: ReminderTypeDao,
    private val api: LunentousApi,
    private val sessionStore: SessionStore,
) {
    fun observeRecentByPlant(plantLocalId: Long, limit: Int = 60): Flow<List<TimelineEventWithPhotos>> =
        eventDao.observeRecentByPlant(plantLocalId, limit).map { events -> events.attachPhotos() }

    fun observeRecentByPlantAndType(plantLocalId: Long, reminderTypeLocalId: Long, limit: Int = 60): Flow<List<TimelineEventWithPhotos>> =
        eventDao.observeRecentByPlantAndType(plantLocalId, reminderTypeLocalId, limit).map { events -> events.attachPhotos() }

    suspend fun createEvent(
        plantLocalId: Long,
        eventDate: String,
        reminderTypeLocalId: Long?,
        text: String?,
        photoFiles: List<File> = emptyList(),
    ): Result<TimelineEventWithPhotos> = runCatching {
        val plantServerId = plantDao.getByLocalId(plantLocalId)?.serverId
        val reminderTypeServerId = reminderTypeLocalId?.let { reminderTypeDao.getByLocalId(it)?.serverId }

        if (sessionStore.hasSession() && plantServerId != null) {
            val dto = api.createTimelineEvent(
                plantServerId,
                eventDate.toRequestBody("text/plain".toMediaType()),
                reminderTypeServerId?.toString()?.toRequestBody("text/plain".toMediaType()),
                text?.toRequestBody("text/plain".toMediaType()),
                photoFiles.toMultipartParts(),
            )
            upsertFromDto(dto, plantLocalId, reminderTypeLocalId)
        } else {
            val event = TimelineEventEntity(
                plantLocalId = plantLocalId,
                reminderTypeLocalId = reminderTypeLocalId,
                eventDate = eventDate,
                text = text,
            )
            val eventLocalId = eventDao.upsert(event)
            val photos = photoFiles.map { file ->
                val photo = PhotoEntity(plantLocalId = plantLocalId, timelineEventLocalId = eventLocalId, localFileUri = file.absolutePath)
                photo.copy(localId = photoDao.upsert(photo))
            }
            TimelineEventWithPhotos(event.copy(localId = eventLocalId), photos)
        }
    }

    suspend fun updateEvent(
        eventLocalId: Long,
        eventDate: String,
        reminderTypeLocalId: Long?,
        text: String?,
    ): Result<TimelineEventWithPhotos> = runCatching {
        val existing = eventDao.getByLocalId(eventLocalId) ?: error("Timeline event $eventLocalId not found locally")
        val reminderTypeServerId = reminderTypeLocalId?.let { reminderTypeDao.getByLocalId(it)?.serverId }
        if (sessionStore.hasSession() && existing.serverId != null) {
            val dto = api.updateTimelineEvent(existing.serverId, UpdateTimelineEventRequest(eventDate, reminderTypeServerId, text))
            upsertFromDto(dto, existing.plantLocalId, reminderTypeLocalId, preserveLocalId = eventLocalId)
        } else {
            val updated = existing.copy(
                eventDate = eventDate,
                reminderTypeLocalId = reminderTypeLocalId,
                text = text,
                dirty = existing.serverId != null,
            )
            eventDao.upsert(updated)
            TimelineEventWithPhotos(updated, photoDao.getByTimelineEvents(listOf(eventLocalId)))
        }
    }

    suspend fun appendPhotos(eventLocalId: Long, photoFiles: List<File>): Result<TimelineEventWithPhotos> = runCatching {
        val existing = eventDao.getByLocalId(eventLocalId) ?: error("Timeline event $eventLocalId not found locally")
        if (sessionStore.hasSession() && existing.serverId != null) {
            val dto = api.appendTimelinePhotos(existing.serverId, photoFiles.toMultipartParts())
            upsertFromDto(dto, existing.plantLocalId, existing.reminderTypeLocalId, preserveLocalId = eventLocalId)
        } else {
            val newPhotos = photoFiles.map { file ->
                val photo = PhotoEntity(plantLocalId = existing.plantLocalId, timelineEventLocalId = eventLocalId, localFileUri = file.absolutePath)
                photo.copy(localId = photoDao.upsert(photo))
            }
            TimelineEventWithPhotos(existing, photoDao.getByTimelineEvents(listOf(eventLocalId)) + newPhotos)
        }
    }

    suspend fun deleteEvent(eventLocalId: Long): Result<Unit> = runCatching {
        val existing = eventDao.getByLocalId(eventLocalId) ?: return@runCatching
        if (sessionStore.hasSession() && existing.serverId != null) {
            api.deleteTimelineEvent(existing.serverId)
        }
        photoDao.deleteByTimelineEvent(eventLocalId)
        eventDao.deleteByLocalId(eventLocalId)
    }

    suspend fun deletePhoto(photoLocalId: Long, photoServerId: Long?): Result<Unit> = runCatching {
        if (sessionStore.hasSession() && photoServerId != null) {
            api.deletePhoto(photoServerId)
        }
        photoDao.deleteByLocalId(photoLocalId)
    }

    /** Range-based, never pruned -- unlike the other entities' full-list
     * pull sync, timeline history is unbounded, so this only ever adds to
     * the local cache (per the plan's Pull sync design). */
    suspend fun pullSyncForPlant(plantLocalId: Long, limit: Int = 60) {
        if (!sessionStore.hasSession()) return
        val plantServerId = plantDao.getByLocalId(plantLocalId)?.serverId ?: return
        val reminderTypeLocalIdByServerId = reminderTypeDao.getAllOnce()
            .mapNotNull { t -> t.serverId?.let { it to t.localId } }
            .toMap()

        val remote = api.getTimeline(plantServerId, limit = limit)
        remote.forEach { dto ->
            val reminderTypeLocalId = dto.reminderTypeId?.let { reminderTypeLocalIdByServerId[it] }
            upsertFromDto(dto, plantLocalId, reminderTypeLocalId)
        }
    }

    private suspend fun upsertFromDto(
        dto: TimelineEventDto,
        plantLocalId: Long,
        reminderTypeLocalId: Long?,
        preserveLocalId: Long? = null,
    ): TimelineEventWithPhotos {
        val existing = preserveLocalId?.let { eventDao.getByLocalId(it) } ?: eventDao.getByServerId(dto.id)
        val entity = TimelineEventEntity(
            localId = existing?.localId ?: 0,
            serverId = dto.id,
            plantLocalId = plantLocalId,
            reminderTypeLocalId = reminderTypeLocalId,
            eventDate = dto.eventDate,
            text = dto.text,
            createdAt = dto.createdAt,
        )
        val eventLocalId = if (existing != null) existing.localId else eventDao.upsert(entity)
        if (existing != null) eventDao.upsert(entity)

        val photos = dto.photos.map { p -> upsertPhotoFromDto(p, plantLocalId, eventLocalId) }
        return TimelineEventWithPhotos(entity.copy(localId = eventLocalId), photos)
    }

    private suspend fun upsertPhotoFromDto(dto: PhotoDto, plantLocalId: Long, eventLocalId: Long): PhotoEntity {
        val existing = photoDao.getByServerId(dto.id)
        val entity = PhotoEntity(
            localId = existing?.localId ?: 0,
            serverId = dto.id,
            plantLocalId = plantLocalId,
            timelineEventLocalId = eventLocalId,
            localFileUri = existing?.localFileUri,
            remoteFilePath = dto.filePath,
            createdAt = dto.createdAt,
        )
        val newLocalId = photoDao.upsert(entity)
        return if (existing != null) entity else entity.copy(localId = newLocalId)
    }

    private suspend fun List<TimelineEventEntity>.attachPhotos(): List<TimelineEventWithPhotos> {
        if (isEmpty()) return emptyList()
        val photosByEvent = photoDao.getByTimelineEvents(map { it.localId }).groupBy { it.timelineEventLocalId }
        return map { event -> TimelineEventWithPhotos(event, photosByEvent[event.localId].orEmpty()) }
    }

    private fun List<File>.toMultipartParts(): List<MultipartBody.Part> = map { file ->
        val body: RequestBody = file.asRequestBody("image/*".toMediaType())
        MultipartBody.Part.createFormData("photo", file.name, body)
    }
}
