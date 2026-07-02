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

    /**
     * 完整使用紀錄，最新在前——使用紀錄頁。
     *
     * JOIN coupons 取名稱（含 status='used' / 'deleted' 的票券，歷史不因退場消失）。
     * 同一天多筆時以 id 遞減當次要排序，維持插入順序穩定。
     */
    @Query(
        """
        SELECT e.used_at AS usedAt, c.name AS couponName, e.count AS count
        FROM usage_events e
        INNER JOIN coupons c ON e.coupon_id = c.id
        ORDER BY e.used_at DESC, e.id DESC
        """
    )
    fun observeHistory(): Flow<List<com.kevin.coupy.data.dao.UsageHistoryRow>>

    /**
     * 使用張數按 coupons.category 分組。
     *
     * 用於統計頁的「身分標籤」（archetype）——看的是使用者實際的「行為」分佈，
     * 而非持有票券分佈。JOIN coupons 取分類（包含 status='deleted' 的軟刪除，
     * 因為使用歷史不應該因刪除而消失）。
     */
    @Query(
        """
        SELECT c.category AS category, COALESCE(SUM(e.count), 0) AS total
        FROM usage_events e
        INNER JOIN coupons c ON e.coupon_id = c.id
        GROUP BY c.category
        """
    )
    fun observeUsageByCategory(): Flow<List<com.kevin.coupy.data.dao.CategoryTicketCount>>
}
