package com.lunentous.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.lunentous.app.data.local.entity.OverridePeriodEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OverridePeriodDao {
    @Query("SELECT * FROM override_periods WHERE reminderRuleLocalId = :ruleLocalId")
    fun observeByRule(ruleLocalId: Long): Flow<List<OverridePeriodEntity>>

    @Query("SELECT * FROM override_periods WHERE reminderRuleLocalId = :ruleLocalId")
    suspend fun getByRuleOnce(ruleLocalId: Long): List<OverridePeriodEntity>

    @Insert
    suspend fun insertAll(periods: List<OverridePeriodEntity>)

    /** Full replace, matching the server's PATCH /reminder-rules/:id
     * delete-all-reinsert semantics for override_periods. */
    @Query("DELETE FROM override_periods WHERE reminderRuleLocalId = :ruleLocalId")
    suspend fun deleteByRule(ruleLocalId: Long)
}
