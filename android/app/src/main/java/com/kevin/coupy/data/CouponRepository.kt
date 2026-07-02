package com.kevin.coupy.data

import com.kevin.coupy.data.dao.CategoryTicketCount
import com.kevin.coupy.data.dao.CouponDao
import com.kevin.coupy.data.dao.UsageEventDao
import com.kevin.coupy.data.dao.UsageHistoryRow
import com.kevin.coupy.data.entity.CouponEntity
import com.kevin.coupy.data.entity.UsageEventEntity
import com.kevin.coupy.data.photo.CouponPhotoStore
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
    private val dao: CouponDao,
    private val usageEventDao: UsageEventDao,
    private val photoStore: CouponPhotoStore
) {

    // ===== 查詢 =====

    fun observeActive(): Flow<List<CouponEntity>> = dao.observeActive()

    suspend fun getById(id: Long): CouponEntity? = dao.getById(id)

    fun observeExpiringSoon(today: java.time.LocalDate): Flow<List<CouponEntity>> =
        dao.observeExpiringSoon(
            today = today.toString(),
            sevenDaysLater = today.plusDays(7).toString()
        )

    // ===== 統計頁 =====

    fun observeActiveTicketCount(): Flow<Int> = dao.observeActiveTicketCount()

    // 統計改查 usage_events 表才能正確算分次使用
    fun observeUsedTicketsAllTime(): Flow<Int> = usageEventDao.observeUsageAllTime()

    fun observeUsedTicketsInRange(startMillis: Long, endMillis: Long): Flow<Int> =
        usageEventDao.observeUsageInRange(startMillis, endMillis)

    fun observeExpiringTicketCount(today: java.time.LocalDate, daysAhead: Long): Flow<Int> =
        dao.observeExpiringTicketCount(
            today = today.toString(),
            endDate = today.plusDays(daysAhead).toString()
        )

    fun observeForeverTicketCount(): Flow<Int> = dao.observeForeverTicketCount()

    fun observeCategoryDistribution(): Flow<List<CategoryTicketCount>> =
        dao.observeCategoryDistribution()

    /** 使用張數按分類分佈——身分標籤用 */
    fun observeUsageByCategory(): Flow<List<CategoryTicketCount>> =
        usageEventDao.observeUsageByCategory()

    /** 完整使用紀錄（最新在前）——使用紀錄頁 */
    fun observeUsageHistory(): Flow<List<UsageHistoryRow>> =
        usageEventDao.observeHistory()

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

        val now = Instant.now()
        // 寫事件——讓統計頁能正確算「本月使用」
        usageEventDao.insert(UsageEventEntity(couponId = id, count = count, usedAt = now))

        if (count >= coupon.quantity) {
            // 用完了 → 標記為 used，並刪掉照片檔（券已退場、出示用途結束、使用者也看不到它）
            dao.markAsUsed(id, now)
            photoStore.delete(coupon.imagePath)
        } else {
            // 還有剩 → 只更新張數，照片保留（之後還要出示）
            dao.updateQuantity(id, coupon.quantity - count)
        }
        return true
    }

    suspend fun delete(id: Long) {
        val coupon = dao.getById(id)
        dao.softDelete(id)
        photoStore.delete(coupon?.imagePath)
    }

    /** 一鍵清除所有已過期票券（軟刪除），連帶刪掉照片檔釋放空間 */
    suspend fun deleteAllExpired(today: java.time.LocalDate) {
        val paths = dao.getExpiredImagePaths(today.toString())
        dao.softDeleteExpired(today.toString())
        paths.forEach { photoStore.delete(it) }
    }
}
