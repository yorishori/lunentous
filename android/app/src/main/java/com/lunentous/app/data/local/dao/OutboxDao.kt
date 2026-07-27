package com.lunentous.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.lunentous.app.data.local.entity.OutboxEntityType
import com.lunentous.app.data.local.entity.OutboxOpType
import com.lunentous.app.data.local.entity.OutboxOperationEntity
import com.lunentous.app.data.local.entity.OutboxStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {
    @Insert
    suspend fun insert(op: OutboxOperationEntity): Long

    /** Strictly FIFO by id -- see the entity doc on why processing order
     * must match insertion order. */
    @Query("SELECT * FROM outbox_operations WHERE status IN ('PENDING', 'IN_FLIGHT') ORDER BY id ASC LIMIT 1")
    suspend fun nextPending(): OutboxOperationEntity?

    @Query("SELECT * FROM outbox_operations WHERE entityType = :entityType AND entityLocalId = :entityLocalId AND opType IN (:opTypes) AND status != 'FAILED' ORDER BY id DESC LIMIT 1")
    suspend fun findPendingForEntity(entityType: OutboxEntityType, entityLocalId: Long, opTypes: List<OutboxOpType>): OutboxOperationEntity?

    @Query("SELECT * FROM outbox_operations WHERE entityType = :entityType AND entityLocalId = :entityLocalId AND opType = 'UPDATE' AND status != 'FAILED'")
    suspend fun findPendingUpdateForEntity(entityType: OutboxEntityType, entityLocalId: Long): OutboxOperationEntity?

    @Query("UPDATE outbox_operations SET payloadJson = :payloadJson WHERE id = :id")
    suspend fun updatePayload(id: Long, payloadJson: String)

    @Query("UPDATE outbox_operations SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: OutboxStatus)

    @Query("UPDATE outbox_operations SET status = 'FAILED', lastError = :error, attemptCount = attemptCount + 1 WHERE id = :id")
    suspend fun markFailed(id: Long, error: String?)

    @Query("UPDATE outbox_operations SET status = 'PENDING', lastError = NULL WHERE id = :id")
    suspend fun markPendingRetry(id: Long)

    @Query("DELETE FROM outbox_operations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM outbox_operations WHERE status IN ('PENDING', 'IN_FLIGHT') ORDER BY id ASC")
    fun observePending(): Flow<List<OutboxOperationEntity>>

    @Query("SELECT * FROM outbox_operations WHERE status = 'FAILED' ORDER BY id ASC")
    fun observeFailed(): Flow<List<OutboxOperationEntity>>

    @Query("SELECT COUNT(*) FROM outbox_operations WHERE status IN ('PENDING', 'IN_FLIGHT')")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM outbox_operations WHERE status = 'FAILED'")
    fun observeFailedCount(): Flow<Int>
}
