package com.lunentous.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lunentous.app.data.local.entity.OneTimeReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OneTimeReminderDao {
    @Query("SELECT * FROM one_time_reminders WHERE deleted = 0 AND plantLocalId = :plantLocalId ORDER BY dueDate")
    fun observeByPlant(plantLocalId: Long): Flow<List<OneTimeReminderEntity>>

    /** Across every plant -- used by the Dashboard and Care Timeline. */
    @Query("SELECT * FROM one_time_reminders WHERE deleted = 0")
    fun observeAll(): Flow<List<OneTimeReminderEntity>>

    @Query("SELECT * FROM one_time_reminders WHERE localId = :localId")
    suspend fun getByLocalId(localId: Long): OneTimeReminderEntity?

    @Query("SELECT * FROM one_time_reminders WHERE serverId = :serverId")
    suspend fun getByServerId(serverId: Long): OneTimeReminderEntity?

    @Query("SELECT serverId FROM one_time_reminders WHERE plantLocalId = :plantLocalId AND serverId IS NOT NULL")
    suspend fun getSyncedServerIdsForPlant(plantLocalId: Long): List<Long>

    @Upsert
    suspend fun upsert(reminder: OneTimeReminderEntity): Long

    @Query("DELETE FROM one_time_reminders WHERE serverId = :serverId")
    suspend fun deleteByServerId(serverId: Long)

    @Query("DELETE FROM one_time_reminders WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: Long)
}
