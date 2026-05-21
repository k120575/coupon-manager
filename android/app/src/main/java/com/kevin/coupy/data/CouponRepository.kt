package com.kevin.coupy.data

import com.kevin.coupy.data.dao.CouponDao
import com.kevin.coupy.data.entity.CouponEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 票券資料的單一入口。
 *
 * 封裝 DAO，提供業務語意層級的操作（例如「使用 N 張」會自動判斷是要扣張數還是標記用光）。
 * ViewModel 應該依賴這層，而非直接呼叫 DAO。
 */
@Singleton
class CouponRepository @Inject constructor(
    private val dao: CouponDao
) {

    // ===== 查詢 =====

    fun observeActive(): Flow<List<CouponEntity>> = dao.observeActive()

    suspend fun getById(id: Long): CouponEntity? = dao.getById(id)

    fun observeExpiringSoon(today: java.time.LocalDate): Flow<List<CouponEntity>> =
        dao.observeExpiringSoon(
            today = today.toString(),
            sevenDaysLater = today.plusDays(7).toString()
        )

    // ===== 寫入 =====

    suspend fun add(coupon: CouponEntity): Long = dao.insert(coupon)

    suspend fun update(coupon: CouponEntity) = dao.update(coupon)

    /**
     * 使用 N 張票券。
     *
     * @param count 要使用的張數，必須 >= 1 且 <= 目前剩餘
     * @return true 表示成功；false 表示票券不存在或張數不夠
     */
    suspend fun use(id: Long, count: Int = 1): Boolean {
        require(count >= 1) { "使用張數至少 1 張" }
        val coupon = dao.getById(id) ?: return false
        if (count > coupon.quantity) return false

        if (count >= coupon.quantity) {
            // 用完了 → 標記為 used
            dao.markAsUsed(id, Instant.now())
        } else {
            // 還有剩 → 只更新張數
            dao.updateQuantity(id, coupon.quantity - count)
        }
        return true
    }

    suspend fun delete(id: Long) = dao.softDelete(id)
}
