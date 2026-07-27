package com.lunentous.app.data.repository

import com.google.gson.Gson
import com.lunentous.app.data.auth.SessionStore
import com.lunentous.app.data.local.dao.PhotoDao
import com.lunentous.app.data.local.dao.PlantDao
import com.lunentous.app.data.local.dao.ReminderTypeDao
import com.lunentous.app.data.local.dao.TimelineEventDao
import com.lunentous.app.data.local.entity.OutboxEntityType
import com.lunentous.app.data.local.entity.OutboxOpType
import com.lunentous.app.data.local.entity.OutboxOperationEntity
import com.lunentous.app.data.local.entity.PhotoEntity
import com.lunentous.app.data.local.entity.TimelineEventEntity
import com.lunentous.app.data.remote.LunentousApi
import com.lunentous.app.data.remote.dto.PhotoDto
import com.lunentous.app.data.remote.dto.TimelineEventDto
import com.lunentous.app.data.remote.dto.UpdateTimelineEventRequest
import com.lunentous.app.data.sync.dates.ProvisionalDueDateCalculator
import com.lunentous.app.data.sync.outbox.OutboxHandler
import com.lunentous.app.data.sync.outbox.OutboxRepository
import com.lunentous.app.data.sync.outbox.OutboxResult
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

data class TimelineEventWithPhotos(val event: TimelineEventEntity, val photos: List<PhotoEntity>)

private data class TimelinePayload(val eventDate: String, val reminderTypeLocalId: Long?, val text: String?)

/**
 * Note: like reminder rules, an event tagged with a reminder type
 * (create/edit/delete) can trigger server-side recompute -- the local-
 * provisional equivalent runs synchronously here via
 * ProvisionalDueDateCalculator, and the ViewModel layer follows up the
 * outbox op's eventual success with a targeted
 * ReminderStateRepository.pullSyncForPlant().
 *
 * Photos aren't part of the outbox yet -- offline photo capture is the
 * phase-6 camera work, which will need its own multipart-capable outbox
 * op type. createEvent's photoFiles param still writes local rows so a
 * future caller isn't blocked, but nothing currently calls it with files,
 * and the CREATE/UPDATE outbox payload never carries them.
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
    private val outboxRepository: OutboxRepository,
    private val gson: Gson,
    private val provisionalCalculator: ProvisionalDueDateCalculator,
) : OutboxHandler {
    override val entityType = OutboxEntityType.TIMELINE_EVENT

    fun observeRecentByPlant(plantLocalId: Long, limit: Int = 60): Flow<List<TimelineEventWithPhotos>> =
        eventDao.observeRecentByPlant(plantLocalId, limit).map { events -> events.attachPhotos() }

    fun observeRecentByPlantAndType(plantLocalId: Long, reminderTypeLocalId: Long, limit: Int = 60): Flow<List<TimelineEventWithPhotos>> =
        eventDao.observeRecentByPlantAndType(plantLocalId, reminderTypeLocalId, limit).map { events -> events.attachPhotos() }

    /** Across every plant, for a date range -- used by the Calendar screen
     * for the visible month. Only as complete as what's already been
     * pulled locally for that range (see pullSyncForPlant's recent-N
     * cursor); Calendar's refresh() pulls each plant before rendering. */
    fun observeAllInRange(from: String, to: String): Flow<List<TimelineEventWithPhotos>> =
        eventDao.observeAllInRange(from, to).map { events -> events.attachPhotos() }

    suspend fun createEvent(
        plantLocalId: Long,
        eventDate: String,
        reminderTypeLocalId: Long?,
        text: String?,
        photoFiles: List<File> = emptyList(),
    ): Result<TimelineEventWithPhotos> = runCatching {
        val event = TimelineEventEntity(plantLocalId = plantLocalId, reminderTypeLocalId = reminderTypeLocalId, eventDate = eventDate, text = text, pendingSync = true)
        val eventLocalId = eventDao.upsert(event)
        val photos = photoFiles.map { file ->
            val photo = PhotoEntity(plantLocalId = plantLocalId, timelineEventLocalId = eventLocalId, localFileUri = file.absolutePath)
            photo.copy(localId = photoDao.upsert(photo))
        }
        outboxRepository.enqueueCreate(entityType, eventLocalId, TimelinePayload(eventDate, reminderTypeLocalId, text))
        if (reminderTypeLocalId != null) provisionalCalculator.recompute(plantLocalId, reminderTypeLocalId)
        TimelineEventWithPhotos(event.copy(localId = eventLocalId), photos)
    }

    suspend fun updateEvent(
        eventLocalId: Long,
        eventDate: String,
        reminderTypeLocalId: Long?,
        text: String?,
    ): Result<TimelineEventWithPhotos> = runCatching {
        val existing = eventDao.getByLocalId(eventLocalId) ?: error("Timeline event $eventLocalId not found locally")
        val oldReminderTypeLocalId = existing.reminderTypeLocalId
        val updated = existing.copy(eventDate = eventDate, reminderTypeLocalId = reminderTypeLocalId, text = text, dirty = existing.serverId != null, pendingSync = true)
        eventDao.upsert(updated)
        outboxRepository.enqueueUpdate(entityType, eventLocalId, TimelinePayload(eventDate, reminderTypeLocalId, text))
        if (oldReminderTypeLocalId != null) provisionalCalculator.recompute(existing.plantLocalId, oldReminderTypeLocalId)
        if (reminderTypeLocalId != null && reminderTypeLocalId != oldReminderTypeLocalId) provisionalCalculator.recompute(existing.plantLocalId, reminderTypeLocalId)
        TimelineEventWithPhotos(updated, photoDao.getByTimelineEvents(listOf(eventLocalId)))
    }

    /** Still network-passthrough -- see the class doc on why photos aren't
     * queued through the outbox yet. */
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
        val localOnly = outboxRepository.enqueueDelete(entityType, eventLocalId)
        if (localOnly) {
            photoDao.deleteByTimelineEvent(eventLocalId)
            eventDao.deleteByLocalId(eventLocalId)
        } else {
            // Tombstone rather than hard-delete -- OutboxProcessor still
            // needs this row's serverId when the DELETE op actually runs.
            // Its photos stop showing immediately too, since attachPhotos
            // only ever runs against already deleted=0-filtered events.
            eventDao.upsert(existing.copy(deleted = true, pendingSync = true))
        }
        existing.reminderTypeLocalId?.let { provisionalCalculator.recompute(existing.plantLocalId, it) }
    }

    /** Still network-passthrough -- see the class doc on why photos aren't
     * queued through the outbox yet. */
    suspend fun deletePhoto(photoLocalId: Long, photoServerId: Long?): Result<Unit> = runCatching {
        if (sessionStore.hasSession() && photoServerId != null) {
            api.deletePhoto(photoServerId)
        }
        photoDao.deleteByLocalId(photoLocalId)
    }

    /** Range-based, never pruned -- unlike the other entities' full-list
     * pull sync, timeline history is unbounded, so this only ever adds to
     * the local cache (per the plan's Pull sync design). Skips rows with
     * unpushed local edits -- see PlantRepository.pullSync for why. */
    suspend fun pullSyncForPlant(plantLocalId: Long, limit: Int = 60) {
        if (!sessionStore.hasSession()) return
        val plantServerId = plantDao.getByLocalId(plantLocalId)?.serverId ?: return
        val reminderTypeLocalIdByServerId = reminderTypeDao.getAllOnce()
            .mapNotNull { t -> t.serverId?.let { it to t.localId } }
            .toMap()

        val remote = api.getTimeline(plantServerId, limit = limit)
        remote.forEach { dto ->
            val reminderTypeLocalId = dto.reminderTypeId?.let { reminderTypeLocalIdByServerId[it] }
            if (eventDao.getByServerId(dto.id)?.dirty != true) upsertFromDto(dto, plantLocalId, reminderTypeLocalId)
        }
    }

    override suspend fun process(op: OutboxOperationEntity): OutboxResult {
        val event = eventDao.getByLocalId(op.entityLocalId) ?: return OutboxResult.Success // already gone locally, nothing to do
        return when (op.opType) {
            OutboxOpType.CREATE -> {
                val payload = gson.fromJson(op.payloadJson, TimelinePayload::class.java)
                val plantServerId = plantDao.getByLocalId(event.plantLocalId)?.serverId ?: return OutboxResult.CascadeFailed
                val reminderTypeServerId = payload.reminderTypeLocalId?.let { localId ->
                    reminderTypeDao.getByLocalId(localId)?.serverId ?: return OutboxResult.CascadeFailed
                }
                val dto = api.createTimelineEvent(
                    plantServerId,
                    payload.eventDate.toRequestBody("text/plain".toMediaType()),
                    reminderTypeServerId?.toString()?.toRequestBody("text/plain".toMediaType()),
                    payload.text?.toRequestBody("text/plain".toMediaType()),
                    emptyList(),
                )
                upsertFromDto(dto, event.plantLocalId, payload.reminderTypeLocalId, preserveLocalId = op.entityLocalId)
                OutboxResult.Success
            }
            OutboxOpType.UPDATE -> {
                val serverId = event.serverId ?: return OutboxResult.CascadeFailed
                val payload = gson.fromJson(op.payloadJson, TimelinePayload::class.java)
                val reminderTypeServerId = payload.reminderTypeLocalId?.let { localId ->
                    reminderTypeDao.getByLocalId(localId)?.serverId ?: return OutboxResult.CascadeFailed
                }
                val dto = api.updateTimelineEvent(serverId, UpdateTimelineEventRequest(payload.eventDate, reminderTypeServerId, payload.text))
                upsertFromDto(dto, event.plantLocalId, payload.reminderTypeLocalId, preserveLocalId = op.entityLocalId)
                OutboxResult.Success
            }
            OutboxOpType.DELETE -> {
                event.serverId?.let { api.deleteTimelineEvent(it) }
                photoDao.deleteByTimelineEvent(op.entityLocalId)
                eventDao.deleteByLocalId(op.entityLocalId)
                OutboxResult.Success
            }
            else -> error("Timeline events only support CREATE/UPDATE/DELETE")
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
