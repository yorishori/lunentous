package com.lunentous.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lunentous.app.data.local.entity.ReminderRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderRuleDao {
    @Query("SELECT * FROM reminder_rules WHERE deleted = 0 AND plantLocalId = :plantLocalId")
    fun observeByPlant(plantLocalId: Long): Flow<List<ReminderRuleEntity>>

    @Query("SELECT reminderTypeLocalId AS typeLocalId, COUNT(*) AS count FROM reminder_rules WHERE deleted = 0 GROUP BY reminderTypeLocalId")
    fun observeUsageCounts(): Flow<List<TypeUsageCount>>

    /** Across every plant -- used by Calendar to project due dates without
     * one Flow per selected plant. */
    @Query("SELECT * FROM reminder_rules WHERE deleted = 0")
    fun observeAll(): Flow<List<ReminderRuleEntity>>

    @Query("SELECT * FROM reminder_rules WHERE localId = :localId")
    suspend fun getByLocalId(localId: Long): ReminderRuleEntity?

    @Query("SELECT * FROM reminder_rules WHERE serverId = :serverId")
    suspend fun getByServerId(serverId: Long): ReminderRuleEntity?

    @Query("SELECT * FROM reminder_rules WHERE plantLocalId = :plantLocalId AND reminderTypeLocalId = :reminderTypeLocalId")
    suspend fun getByPlantAndType(plantLocalId: Long, reminderTypeLocalId: Long): ReminderRuleEntity?

    @Query("SELECT * FROM reminder_rules WHERE deleted = 0 AND plantLocalId = :plantLocalId")
    suspend fun getByPlantOnce(plantLocalId: Long): List<ReminderRuleEntity>

    @Query("SELECT serverId FROM reminder_rules WHERE plantLocalId = :plantLocalId AND serverId IS NOT NULL")
    suspend fun getSyncedServerIdsForPlant(plantLocalId: Long): List<Long>

    @Upsert
    suspend fun upsert(rule: ReminderRuleEntity): Long

    @Query("DELETE FROM reminder_rules WHERE serverId = :serverId")
    suspend fun deleteByServerId(serverId: Long)

    @Query("DELETE FROM reminder_rules WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: Long)
}
