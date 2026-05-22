package com.kevin.coupy.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.kevin.coupy.data.entity.UsageEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageEventDao {

    @Insert
    suspend fun insert(event: UsageEventEntity): Long

    /** 區間內使用的張數總和（startMillis 含；endMillis 不含）*/
    @Query(
        """
        SELECT COALESCE(SUM(count), 0) FROM usage_events
        WHERE used_at >= :startMillis AND used_at < :endMillis
        """
    )
    fun observeUsageInRange(startMillis: Long, endMillis: Long): Flow<Int>

    /** 歷史累計使用張數 */
    @Query("SELECT COALESCE(SUM(count), 0) FROM usage_events")
    fun observeUsageAllTime(): Flow<Int>
}
