package com.kevin.coupy.data.backup

import com.kevin.coupy.data.CouponStatus
import com.kevin.coupy.data.CouponType
import com.kevin.coupy.data.dao.CategoryTicketCount
import com.kevin.coupy.data.dao.CouponDao
import com.kevin.coupy.data.entity.CouponEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class BackupRepositoryTest {

    @Test
    fun `export then import round-trips all coupons`() = runTest {
        val source = listOf(
            coupon("星巴克買一送一", "2026-08-31", "drink", 2),
            coupon("電影票", "2026-12-31", "entertainment", 1),
            coupon("禮品卡", "9999-12-31", "lifestyle", 5)
        )
        val srcDao = FakeCouponDao(source)
        val srcRepo = BackupRepository(srcDao)

        val json = srcRepo.exportToJson(Instant.parse("2026-05-22T10:00:00Z"))

        val obj = JSONObject(json)
        assertEquals(BACKUP_FORMAT_VERSION, obj.getInt("version"))
        assertEquals(3, obj.getInt("count"))
        assertEquals(3, obj.getJSONArray("coupons").length())

        val dstDao = FakeCouponDao(emptyList())
        val dstRepo = BackupRepository(dstDao)
        val result = dstRepo.importFromJson(json)

        assertEquals(3, result.imported)
        assertEquals(0, result.skippedDuplicates)
        assertEquals(0, result.skippedInvalid)
        assertEquals(3, dstDao.inserted.size)

        val restored = dstDao.inserted.sortedBy { it.name }
        val original = source.sortedBy { it.name }
        original.zip(restored).forEach { (a, b) ->
            assertEquals(a.name, b.name)
            assertEquals(a.expireDate, b.expireDate)
            assertEquals(a.category, b.category)
            assertEquals(a.quantity, b.quantity)
            assertEquals(a.status, b.status)
        }
    }

    @Test
    fun `import skips duplicates by name plus expire_date plus category`() = runTest {
        val existing = listOf(coupon("星巴克", "2026-08-31", "drink", 2))
        val dao = FakeCouponDao(existing)
        val repo = BackupRepository(dao)

        val incomingJson = """
            {
              "version": 1,
              "exported_at": "2026-05-22T10:00:00Z",
              "coupons": [
                {"name": "星巴克", "expire_date": "2026-08-31", "category": "drink", "quantity": 5, "status": "active", "created_at": "2026-01-01T00:00:00Z"},
                {"name": "電影票", "expire_date": "2026-12-31", "category": "entertainment", "quantity": 1, "status": "active", "created_at": "2026-01-01T00:00:00Z"}
              ]
            }
        """.trimIndent()

        val result = repo.importFromJson(incomingJson)
        assertEquals(1, result.imported)
        assertEquals(1, result.skippedDuplicates)
        assertEquals(1, dao.inserted.size)
        assertEquals("電影票", dao.inserted[0].name)
    }

    @Test
    fun `import counts unparseable rows as invalid but continues`() = runTest {
        val dao = FakeCouponDao(emptyList())
        val repo = BackupRepository(dao)

        val json = """
            {
              "version": 1,
              "coupons": [
                {"name": "", "expire_date": "2026-08-31", "category": "drink"},
                {"name": "Missing date", "category": "drink"},
                {"name": "OK 票", "expire_date": "2026-09-30", "category": "drink", "quantity": 1}
              ]
            }
        """.trimIndent()

        val result = repo.importFromJson(json)
        assertEquals(1, result.imported)
        assertEquals(2, result.skippedInvalid)
        assertEquals(0, result.skippedDuplicates)
    }

    @Test
    fun `import rejects unsupported version`() = runTest {
        val repo = BackupRepository(FakeCouponDao(emptyList()))
        val futureJson = """{"version": 99, "coupons": []}"""
        assertThrows(BackupParseException::class.java) {
            kotlinx.coroutines.runBlocking { repo.importFromJson(futureJson) }
        }
    }

    @Test
    fun `import rejects malformed json`() = runTest {
        val repo = BackupRepository(FakeCouponDao(emptyList()))
        assertThrows(BackupParseException::class.java) {
            kotlinx.coroutines.runBlocking { repo.importFromJson("{not json") }
        }
    }

    @Test
    fun `exported json contains expected top-level keys`() = runTest {
        val dao = FakeCouponDao(listOf(coupon("X", "2026-08-31", "drink", 1)))
        val repo = BackupRepository(dao)
        val json = repo.exportToJson(Instant.parse("2026-05-22T10:00:00Z"))
        val obj = JSONObject(json)
        assertTrue(obj.has("version"))
        assertTrue(obj.has("exported_at"))
        assertTrue(obj.has("count"))
        assertTrue(obj.has("coupons"))
        assertEquals("2026-05-22T10:00:00Z", obj.getString("exported_at"))
    }

    @Test
    fun `imported deleted status is normalized to active`() = runTest {
        val dao = FakeCouponDao(emptyList())
        val repo = BackupRepository(dao)
        val json = """
            {"version": 1, "coupons": [
              {"name": "回收票", "expire_date": "2026-08-31", "category": "drink", "status": "deleted"}
            ]}
        """.trimIndent()
        val result = repo.importFromJson(json)
        assertEquals(1, result.imported)
        assertEquals(CouponStatus.ACTIVE, dao.inserted[0].status)
    }

    @Test
    fun `round-trip preserves type and note v3 fields`() = runTest {
        val source = listOf(
            CouponEntity(
                name = "紙本喜餅券",
                expireDate = LocalDate.parse("2026-12-31"),
                category = "dining",
                quantity = 3,
                type = CouponType.PHYSICAL,
                note = "周一不可用、出示會員卡",
                createdAt = Instant.parse("2026-01-01T00:00:00Z")
            ),
            CouponEntity(
                name = "全家數位券",
                expireDate = LocalDate.parse("2026-08-31"),
                category = "shopping",
                quantity = 1,
                type = CouponType.DIGITAL,
                note = null,
                createdAt = Instant.parse("2026-01-01T00:00:00Z")
            )
        )
        val srcDao = FakeCouponDao(source)
        val srcRepo = BackupRepository(srcDao)
        val json = srcRepo.exportToJson(Instant.parse("2026-05-23T10:00:00Z"))

        val dstDao = FakeCouponDao(emptyList())
        val dstRepo = BackupRepository(dstDao)
        dstRepo.importFromJson(json)

        val restored = dstDao.inserted.associateBy { it.name }
        assertEquals(CouponType.PHYSICAL, restored["紙本喜餅券"]!!.type)
        assertEquals("周一不可用、出示會員卡", restored["紙本喜餅券"]!!.note)
        assertEquals(CouponType.DIGITAL, restored["全家數位券"]!!.type)
        assertEquals(null, restored["全家數位券"]!!.note)
    }

    @Test
    fun `old v1 backup without type or note restores with safe defaults`() = runTest {
        // 模擬從 v2 階段做的 backup（沒有 type / note 欄位）
        val oldJson = """
            {"version": 1, "exported_at": "2026-05-22T10:00:00Z", "coupons": [
              {"name": "舊備份票券", "expire_date": "2026-08-31", "category": "drink",
               "quantity": 1, "status": "active", "created_at": "2026-01-01T00:00:00Z"}
            ]}
        """.trimIndent()
        val dao = FakeCouponDao(emptyList())
        val repo = BackupRepository(dao)
        repo.importFromJson(oldJson)

        val inserted = dao.inserted.single()
        assertEquals(CouponType.PHYSICAL, inserted.type)   // 沒帶 type → PHYSICAL fallback
        assertEquals(null, inserted.note)                   // 沒帶 note → null
    }

    @Test
    fun `unknown type value falls back to PHYSICAL`() = runTest {
        val json = """
            {"version": 1, "coupons": [
              {"name": "怪 type", "expire_date": "2026-08-31", "category": "drink",
               "quantity": 1, "type": "alien", "status": "active"}
            ]}
        """.trimIndent()
        val dao = FakeCouponDao(emptyList())
        val repo = BackupRepository(dao)
        repo.importFromJson(json)
        assertEquals(CouponType.PHYSICAL, dao.inserted.single().type)
    }

    @Test
    fun `used_at is preserved when present`() = runTest {
        val dao = FakeCouponDao(emptyList())
        val repo = BackupRepository(dao)
        val usedAt = "2026-04-01T12:00:00Z"
        val json = """
            {"version": 1, "coupons": [
              {"name": "X", "expire_date": "2026-08-31", "category": "drink",
               "status": "used", "quantity": 1,
               "created_at": "2026-01-01T00:00:00Z", "used_at": "$usedAt"}
            ]}
        """.trimIndent()
        repo.importFromJson(json)
        val inserted = dao.inserted.single()
        assertNotNull(inserted.usedAt)
        assertEquals(Instant.parse(usedAt), inserted.usedAt)
    }

    // ===== helpers =====

    private fun coupon(
        name: String,
        expire: String,
        category: String,
        quantity: Int,
        status: CouponStatus = CouponStatus.ACTIVE
    ) = CouponEntity(
        name = name,
        expireDate = LocalDate.parse(expire),
        category = category,
        quantity = quantity,
        status = status,
        createdAt = Instant.parse("2026-01-01T00:00:00Z")
    )

    private class FakeCouponDao(initial: List<CouponEntity>) : CouponDao {
        val stored = initial.toMutableList()
        val inserted = mutableListOf<CouponEntity>()

        override suspend fun insert(coupon: CouponEntity): Long {
            inserted.add(coupon)
            stored.add(coupon)
            return stored.size.toLong()
        }

        override suspend fun insertAll(coupons: List<CouponEntity>): List<Long> {
            inserted.addAll(coupons)
            stored.addAll(coupons)
            return coupons.indices.map { it.toLong() }
        }

        override suspend fun update(coupon: CouponEntity) {}

        override fun observeActive(): Flow<List<CouponEntity>> = flowOf(stored.toList())

        override suspend fun getById(id: Long): CouponEntity? = null

        override suspend fun countAll(): Int =
            stored.count { it.status != CouponStatus.DELETED }

        override suspend fun getAllForBackup(): List<CouponEntity> =
            stored.filter { it.status != CouponStatus.DELETED }

        override suspend fun countMatching(name: String, expireDate: String, category: String): Int =
            stored.count {
                it.name == name &&
                    it.expireDate.toString() == expireDate &&
                    it.category == category &&
                    it.status != CouponStatus.DELETED
            }

        override fun observeExpiringSoon(today: String, sevenDaysLater: String): Flow<List<CouponEntity>> =
            flowOf(emptyList())

        override fun observeActiveTicketCount(): Flow<Int> = flowOf(0)
        override fun observeUsedTicketsInRange(startMillis: Long, endMillis: Long): Flow<Int> = flowOf(0)
        override fun observeUsedTicketsAllTime(): Flow<Int> = flowOf(0)
        override fun observeExpiringTicketCount(today: String, endDate: String): Flow<Int> = flowOf(0)
        override fun observeForeverTicketCount(): Flow<Int> = flowOf(0)
        override fun observeCategoryDistribution(): Flow<List<CategoryTicketCount>> = flowOf(emptyList())

        override suspend fun markAsUsed(id: Long, usedAt: Instant) {}

        override suspend fun updateQuantity(id: Long, newQuantity: Int) {}

        override suspend fun softDelete(id: Long) {}

        override fun observeDeleted(): Flow<List<CouponEntity>> = flowOf(emptyList())
    }
}
