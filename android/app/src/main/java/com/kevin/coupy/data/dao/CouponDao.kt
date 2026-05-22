package com.kevin.coupy.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.kevin.coupy.data.entity.CouponEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface CouponDao {

    // ===== 寫入 =====

    @Insert
    suspend fun insert(coupon: CouponEntity): Long

    @Insert
    suspend fun insertAll(coupons: List<CouponEntity>): List<Long>

    @Update
    suspend fun update(coupon: CouponEntity)

    // ===== 查詢 =====

    /** 主畫面：所有 active 票券，依到期日由近到遠排序 */
    @Query("SELECT * FROM coupons WHERE status = 'active' ORDER BY expire_date ASC, id ASC")
    fun observeActive(): Flow<List<CouponEntity>>

    /** 編輯畫面：依 id 取單筆 */
    @Query("SELECT * FROM coupons WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CouponEntity?

    /** 統計用：所有未刪除的 */
    @Query("SELECT COUNT(*) FROM coupons WHERE status != 'deleted'")
    suspend fun countAll(): Int

    /** 備份匯出用：所有未刪除的票券（active + used）*/
    @Query("SELECT * FROM coupons WHERE status != 'deleted' ORDER BY id ASC")
    suspend fun getAllForBackup(): List<CouponEntity>

    /**
     * 匯入去重檢查：以 name + expire_date + category 為自然鍵。
     * 同樣三欄組合視為同一張票券，避免重覆匯入。
     */
    @Query(
        """
        SELECT COUNT(*) FROM coupons
        WHERE name = :name
          AND expire_date = :expireDate
          AND category = :category
          AND status != 'deleted'
        """
    )
    suspend fun countMatching(name: String, expireDate: String, category: String): Int

    /** 7 天內到期的 active 票券（供 widget / 提醒用）*/
    @Query(
        """
        SELECT * FROM coupons
        WHERE status = 'active'
          AND expire_date BETWEEN :today AND :sevenDaysLater
        ORDER BY expire_date ASC
        """
    )
    fun observeExpiringSoon(today: String, sevenDaysLater: String): Flow<List<CouponEntity>>

    // ===== 統計頁專用查詢 =====
    // 注意：所有「張數」都是 SUM(quantity)，因為一筆 coupon 可能對應 N 張票。

    /** 目前持有的總張數（active 票券 SUM(quantity)）*/
    @Query("SELECT COALESCE(SUM(quantity), 0) FROM coupons WHERE status = 'active'")
    fun observeActiveTicketCount(): Flow<Int>

    /**
     * 本月用完的張數。
     *
     * 注意 used_at 是 epoch millis（Converters 設定），傳入區間用 Long。
     * 採近似法：只算「用完最後幾張」當下的張數，部分使用不會計入。
     */
    @Query(
        """
        SELECT COALESCE(SUM(quantity), 0) FROM coupons
        WHERE status = 'used'
          AND used_at >= :startMillis
          AND used_at < :endMillis
        """
    )
    fun observeUsedTicketsInRange(startMillis: Long, endMillis: Long): Flow<Int>

    /** 累計用完總張數（歷史所有 used）*/
    @Query("SELECT COALESCE(SUM(quantity), 0) FROM coupons WHERE status = 'used'")
    fun observeUsedTicketsAllTime(): Flow<Int>

    /** N 天內到期的總張數（排除無期限的 9999-12-31）*/
    @Query(
        """
        SELECT COALESCE(SUM(quantity), 0) FROM coupons
        WHERE status = 'active'
          AND expire_date BETWEEN :today AND :endDate
          AND expire_date < '9999-12-31'
        """
    )
    fun observeExpiringTicketCount(today: String, endDate: String): Flow<Int>

    /** 無期限票券總張數（9999-12-31 sentinel）*/
    @Query(
        """
        SELECT COALESCE(SUM(quantity), 0) FROM coupons
        WHERE status = 'active'
          AND expire_date = '9999-12-31'
        """
    )
    fun observeForeverTicketCount(): Flow<Int>

    /** 分類分佈：active 票券按 category 分組，回傳各分類總張數 */
    @Query(
        """
        SELECT category, SUM(quantity) AS total FROM coupons
        WHERE status = 'active'
        GROUP BY category
        ORDER BY total DESC
        """
    )
    fun observeCategoryDistribution(): Flow<List<CategoryTicketCount>>

    // ===== 狀態變更 =====

    /** 標記為已使用（用完所有張數時呼叫）*/
    @Query("UPDATE coupons SET status = 'used', used_at = :usedAt WHERE id = :id")
    suspend fun markAsUsed(id: Long, usedAt: Instant)

    /** 部分使用：更新剩餘張數 */
    @Query("UPDATE coupons SET quantity = :newQuantity WHERE id = :id")
    suspend fun updateQuantity(id: Long, newQuantity: Int)

    /** 軟刪除（資料留在 DB，僅標記 deleted）*/
    @Query("UPDATE coupons SET status = 'deleted' WHERE id = :id")
    suspend fun softDelete(id: Long)

    /** 取回已刪除（給未來「資源回收筒」用，現在 UI 不暴露）*/
    @Query("SELECT * FROM coupons WHERE status = 'deleted' ORDER BY id DESC")
    fun observeDeleted(): Flow<List<CouponEntity>>
}
