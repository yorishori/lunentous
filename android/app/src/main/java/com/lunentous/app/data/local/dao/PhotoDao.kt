package com.lunentous.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lunentous.app.data.local.entity.PhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos WHERE timelineEventLocalId = :eventLocalId")
    fun observeByTimelineEvent(eventLocalId: Long): Flow<List<PhotoEntity>>

    /** Feeds the plant photo gallery (see ui/plant/PlantGalleryScreen.kt) --
     * every photo ever logged for the plant, across all its timeline
     * events, newest first. */
    @Query("SELECT * FROM photos WHERE plantLocalId = :plantLocalId ORDER BY createdAt DESC")
    fun observeByPlant(plantLocalId: Long): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE timelineEventLocalId IN (:eventLocalIds)")
    suspend fun getByTimelineEvents(eventLocalIds: List<Long>): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE serverId = :serverId")
    suspend fun getByServerId(serverId: Long): PhotoEntity?

    @Upsert
    suspend fun upsert(photo: PhotoEntity): Long

    @Query("DELETE FROM photos WHERE serverId = :serverId")
    suspend fun deleteByServerId(serverId: Long)

    @Query("DELETE FROM photos WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: Long)

    @Query("DELETE FROM photos WHERE timelineEventLocalId = :eventLocalId")
    suspend fun deleteByTimelineEvent(eventLocalId: Long)

    /** Called right before reconciling a server response that returns an
     * event's complete authoritative photo list (a successful CREATE or
     * APPEND_PHOTOS outbox op) -- local-only rows (no serverId, so not yet
     * uploaded) can't be matched to the response's DTOs, so without this
     * they'd end up duplicated alongside the newly-inserted server-backed
     * rows. Never called from routine pull sync, which would otherwise
     * wipe out a still-pending capture's local preview. */
    @Query("DELETE FROM photos WHERE timelineEventLocalId = :eventLocalId AND serverId IS NULL")
    suspend fun deleteLocalOnlyForEvent(eventLocalId: Long)
}
