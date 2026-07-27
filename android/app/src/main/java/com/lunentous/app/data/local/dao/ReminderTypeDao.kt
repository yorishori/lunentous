package com.lunentous.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lunentous.app.data.local.entity.ReminderTypeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderTypeDao {
    @Query("SELECT * FROM reminder_types WHERE deleted = 0 AND archived = :archived ORDER BY name")
    fun observeByArchived(archived: Boolean): Flow<List<ReminderTypeEntity>>

    @Query("SELECT * FROM reminder_types WHERE deleted = 0 ORDER BY name")
    fun observeAll(): Flow<List<ReminderTypeEntity>>

    @Query("SELECT * FROM reminder_types WHERE localId = :localId")
    suspend fun getByLocalId(localId: Long): ReminderTypeEntity?

    @Query("SELECT * FROM reminder_types WHERE serverId = :serverId")
    suspend fun getByServerId(serverId: Long): ReminderTypeEntity?

    @Query("SELECT serverId FROM reminder_types WHERE serverId IS NOT NULL")
    suspend fun getSyncedServerIds(): List<Long>

    @Upsert
    suspend fun upsert(type: ReminderTypeEntity): Long

    @Query("DELETE FROM reminder_types WHERE serverId = :serverId")
    suspend fun deleteByServerId(serverId: Long)
}
