package com.lunentous.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.lunentous.app.data.local.entity.PlantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlantDao {
    @Query("SELECT * FROM plants WHERE deleted = 0 AND archived = :archived ORDER BY name")
    fun observeByArchived(archived: Boolean): Flow<List<PlantEntity>>

    @Query("SELECT * FROM plants WHERE deleted = 0 ORDER BY name")
    fun observeAll(): Flow<List<PlantEntity>>

    @Query("SELECT * FROM plants WHERE localId = :localId")
    fun observeByLocalId(localId: Long): Flow<PlantEntity?>

    @Query("SELECT * FROM plants WHERE localId = :localId")
    suspend fun getByLocalId(localId: Long): PlantEntity?

    @Query("SELECT * FROM plants WHERE serverId = :serverId")
    suspend fun getByServerId(serverId: Long): PlantEntity?

    @Query("SELECT * FROM plants WHERE deleted = 0 AND serverId IS NULL")
    suspend fun getLocalOnly(): List<PlantEntity>

    @Query("SELECT serverId FROM plants WHERE serverId IS NOT NULL")
    suspend fun getSyncedServerIds(): List<Long>

    @Upsert
    suspend fun upsert(plant: PlantEntity): Long

    @Query("DELETE FROM plants WHERE serverId = :serverId")
    suspend fun deleteByServerId(serverId: Long)

    @Delete
    suspend fun delete(plant: PlantEntity)
}
