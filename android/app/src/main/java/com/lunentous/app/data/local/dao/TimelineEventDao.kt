package com.lunentous.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lunentous.app.data.local.entity.TimelineEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimelineEventDao {
    @Query(
        "SELECT * FROM timeline_events WHERE deleted = 0 AND plantLocalId = :plantLocalId " +
            "ORDER BY eventDate DESC, localId DESC LIMIT :limit"
    )
    fun observeRecentByPlant(plantLocalId: Long, limit: Int): Flow<List<TimelineEventEntity>>

    @Query(
        "SELECT * FROM timeline_events WHERE deleted = 0 AND plantLocalId = :plantLocalId " +
            "AND reminderTypeLocalId = :reminderTypeLocalId ORDER BY eventDate DESC, localId DESC LIMIT :limit"
    )
    fun observeRecentByPlantAndType(plantLocalId: Long, reminderTypeLocalId: Long, limit: Int): Flow<List<TimelineEventEntity>>

    @Query(
        "SELECT * FROM timeline_events WHERE deleted = 0 AND plantLocalId = :plantLocalId " +
            "AND eventDate >= :from AND eventDate <= :to"
    )
    suspend fun getByPlantInRange(plantLocalId: Long, from: String, to: String): List<TimelineEventEntity>

    @Query("SELECT * FROM timeline_events WHERE localId = :localId")
    suspend fun getByLocalId(localId: Long): TimelineEventEntity?

    @Query("SELECT * FROM timeline_events WHERE serverId = :serverId")
    suspend fun getByServerId(serverId: Long): TimelineEventEntity?

    @Query(
        "SELECT * FROM timeline_events WHERE deleted = 0 AND plantLocalId = :plantLocalId " +
            "AND reminderTypeLocalId = :reminderTypeLocalId ORDER BY eventDate DESC, localId DESC LIMIT 1"
    )
    suspend fun getMostRecentByPlantAndType(plantLocalId: Long, reminderTypeLocalId: Long): TimelineEventEntity?

    @Upsert
    suspend fun upsert(event: TimelineEventEntity): Long

    @Query("DELETE FROM timeline_events WHERE serverId = :serverId")
    suspend fun deleteByServerId(serverId: Long)

    @Query("DELETE FROM timeline_events WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: Long)
}
