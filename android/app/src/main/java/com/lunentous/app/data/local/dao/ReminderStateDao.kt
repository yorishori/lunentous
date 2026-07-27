package com.lunentous.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lunentous.app.data.local.entity.ReminderStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderStateDao {
    @Query("SELECT * FROM reminder_states WHERE plantLocalId = :plantLocalId")
    fun observeByPlant(plantLocalId: Long): Flow<List<ReminderStateEntity>>

    @Query("SELECT * FROM reminder_states")
    fun observeAll(): Flow<List<ReminderStateEntity>>

    @Query("SELECT * FROM reminder_states WHERE plantLocalId = :plantLocalId AND reminderTypeLocalId = :reminderTypeLocalId")
    suspend fun getByPlantAndType(plantLocalId: Long, reminderTypeLocalId: Long): ReminderStateEntity?

    @Upsert
    suspend fun upsert(state: ReminderStateEntity): Long

    @Query("DELETE FROM reminder_states WHERE plantLocalId = :plantLocalId AND reminderTypeLocalId = :reminderTypeLocalId")
    suspend fun deleteByPlantAndType(plantLocalId: Long, reminderTypeLocalId: Long)

    @Query("DELETE FROM reminder_states WHERE serverId = :serverId")
    suspend fun deleteByServerId(serverId: Long)
}
