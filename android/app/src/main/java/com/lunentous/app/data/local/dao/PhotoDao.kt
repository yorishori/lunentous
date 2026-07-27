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
}
