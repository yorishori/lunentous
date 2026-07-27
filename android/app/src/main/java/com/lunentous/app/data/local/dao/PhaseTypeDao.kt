package com.lunentous.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lunentous.app.data.local.entity.PhaseTypeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PhaseTypeDao {
    @Query("SELECT * FROM phase_types WHERE deleted = 0 AND archived = :archived ORDER BY name")
    fun observeByArchived(archived: Boolean): Flow<List<PhaseTypeEntity>>

    @Query("SELECT * FROM phase_types WHERE deleted = 0 ORDER BY name")
    fun observeAll(): Flow<List<PhaseTypeEntity>>

    @Query("SELECT * FROM phase_types WHERE localId = :localId")
    suspend fun getByLocalId(localId: Long): PhaseTypeEntity?

    @Query("SELECT * FROM phase_types WHERE serverId = :serverId")
    suspend fun getByServerId(serverId: Long): PhaseTypeEntity?

    @Query("SELECT serverId FROM phase_types WHERE serverId IS NOT NULL")
    suspend fun getSyncedServerIds(): List<Long>

    @Upsert
    suspend fun upsert(type: PhaseTypeEntity): Long

    @Query("DELETE FROM phase_types WHERE serverId = :serverId")
    suspend fun deleteByServerId(serverId: Long)
}
