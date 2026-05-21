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
