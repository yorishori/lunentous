package com.lunentous.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lunentous.app.data.local.entity.PlantPhaseWindowEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PhaseWindowDao {
    @Query("SELECT * FROM plant_phase_windows WHERE deleted = 0 AND plantLocalId = :plantLocalId")
    fun observeByPlant(plantLocalId: Long): Flow<List<PlantPhaseWindowEntity>>

    @Query("SELECT * FROM plant_phase_windows WHERE deleted = 0 AND plantLocalId = :plantLocalId")
    suspend fun getByPlantOnce(plantLocalId: Long): List<PlantPhaseWindowEntity>

    @Query("SELECT * FROM plant_phase_windows WHERE localId = :localId")
    suspend fun getByLocalId(localId: Long): PlantPhaseWindowEntity?

    @Query("SELECT * FROM plant_phase_windows WHERE serverId = :serverId")
    suspend fun getByServerId(serverId: Long): PlantPhaseWindowEntity?

    @Query("SELECT serverId FROM plant_phase_windows WHERE plantLocalId = :plantLocalId AND serverId IS NOT NULL")
    suspend fun getSyncedServerIdsForPlant(plantLocalId: Long): List<Long>

    @Upsert
    suspend fun upsert(window: PlantPhaseWindowEntity): Long

    @Query("DELETE FROM plant_phase_windows WHERE serverId = :serverId")
    suspend fun deleteByServerId(serverId: Long)

    @Query("DELETE FROM plant_phase_windows WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: Long)
}
