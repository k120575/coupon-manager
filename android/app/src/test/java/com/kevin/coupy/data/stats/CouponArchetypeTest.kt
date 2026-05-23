package com.kevin.coupy.data.stats

import com.kevin.coupy.data.dao.CategoryTicketCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Archetype 比對純函式測試。覆蓋：
 * - 門檻（< 5 張不顯示）
 * - 每個 archetype 觸發路徑
 * - 信號強度優先序（高 threshold / 組合 > 單一中比 > fallback）
 * - 邊界（剛好觸發 vs 差 1%）
 */
class CouponArchetypeTest {

    private fun dist(vararg pairs: Pair<String, Int>): List<CategoryTicketCount> =
        pairs.map { (cat, n) -> CategoryTicketCount(category = cat, total = n) }

    // ===== 門檻 =====

    @Test
    fun `累計 0 張回 NotEnoughData`() {
        val result = matchArchetype(emptyList())
        assertTrue(result is ArchetypeState.NotEnoughData)
        val state = result as ArchetypeState.NotEnoughData
        assertEquals(0, state.currentUsed)
        assertEquals(5, state.required)
    }

    @Test
    fun `累計 4 張回 NotEnoughData`() {
        val result = matchArchetype(dist("dining" to 4))
        assertTrue(result is ArchetypeState.NotEnoughData)
        assertEquals(4, (result as ArchetypeState.NotEnoughData).currentUsed)
    }

    @Test
    fun `累計剛好 5 張開始判定`() {
        val result = matchArchetype(dist("dining" to 5))
        assertTrue(result is ArchetypeState.Identified)
        assertEquals(CouponArchetype.FOODIE, (result as ArchetypeState.Identified).archetype)
    }

    // ===== 每個 archetype 都有對應路徑 =====

    @Test
    fun `dining 100 percent 觸發 FOODIE`() {
        val result = matchArchetype(dist("dining" to 10))
        assertArchetype(result, CouponArchetype.FOODIE, total = 10, percent = 100)
    }

    @Test
    fun `coffee 100 percent 觸發 COFFEE_WARRIOR 而非 FOODIE`() {
        // 設計選擇：COFFEE_WARRIOR 順序在 FOODIE 之前，純喝咖啡的人是 ☕ 不是廣義 🍱
        val result = matchArchetype(dist("coffee" to 10))
        assertArchetype(result, CouponArchetype.COFFEE_WARRIOR, total = 10, percent = 100)
    }

    @Test
    fun `dining 40 加 coffee 40 觸發 FOODIE 不是 COFFEE_WARRIOR`() {
        // coffee 40% < 50% → 不觸發 COFFEE_WARRIOR
        // dining+coffee 80% > 60% → 觸發 FOODIE
        val result = matchArchetype(dist("dining" to 4, "coffee" to 4, "shopping" to 2))
        assertArchetype(result, CouponArchetype.FOODIE, total = 10, percent = 80)
    }

    @Test
    fun `lodging 加 transport 觸發 TRAVELER`() {
        val result = matchArchetype(dist("lodging" to 3, "transport" to 3, "dining" to 4))
        assertArchetype(result, CouponArchetype.TRAVELER, total = 10, percent = 60)
    }

    @Test
    fun `beauty 加 massage 觸發 BEAUTY`() {
        val result = matchArchetype(dist("beauty" to 4, "massage" to 3, "other" to 3))
        assertArchetype(result, CouponArchetype.BEAUTY, total = 10, percent = 70)
    }

    @Test
    fun `fitness 加 medical 觸發 WELLNESS`() {
        val result = matchArchetype(dist("fitness" to 4, "medical" to 2, "shopping" to 4))
        assertArchetype(result, CouponArchetype.WELLNESS, total = 10, percent = 60)
    }

    @Test
    fun `shopping 加 tech 觸發 THRIFTY`() {
        val result = matchArchetype(dist("shopping" to 4, "tech" to 2, "other" to 4))
        assertArchetype(result, CouponArchetype.THRIFTY, total = 10, percent = 60)
    }

    @Test
    fun `movie 40 percent 觸發 ENTERTAINMENT`() {
        val result = matchArchetype(dist("movie" to 4, "shopping" to 3, "dining" to 3))
        assertArchetype(result, CouponArchetype.ENTERTAINMENT, total = 10, percent = 40)
    }

    @Test
    fun `pet 40 percent 觸發 PET_PARENT`() {
        val result = matchArchetype(dist("pet" to 4, "other" to 6))
        assertArchetype(result, CouponArchetype.PET_PARENT, total = 10, percent = 40)
    }

    @Test
    fun `education 40 percent 觸發 LIFELONG_LEARNER`() {
        // 不能用 shopping 當填料——shopping+tech 50% 會搶先觸發 THRIFTY；用 other 隔離
        val result = matchArchetype(dist("education" to 5, "other" to 7))
        assertArchetype(result, CouponArchetype.LIFELONG_LEARNER, total = 12, percent = 41)
    }

    @Test
    fun `沒有任何 cluster 過閥值 觸發 OMNIVORE`() {
        // 每個都 < 40%，組合也都 < 50%
        val result = matchArchetype(
            dist(
                "dining" to 2,
                "movie" to 2,
                "shopping" to 2,
                "pet" to 2,
                "other" to 2
            )
        )
        assertArchetype(result, CouponArchetype.OMNIVORE, total = 10, percent = 0)
    }

    // ===== 信號強度優先序 =====

    @Test
    fun `組合 60 percent 優先於單一 40 percent`() {
        // dining+coffee = 60%, movie = 40% — 都觸發，但 FOODIE 該贏
        val result = matchArchetype(dist("dining" to 4, "coffee" to 2, "movie" to 4))
        assertArchetype(result, CouponArchetype.FOODIE, total = 10, percent = 60)
    }

    @Test
    fun `coffee 60 percent 觸發 COFFEE_WARRIOR 不是 FOODIE`() {
        // coffee=6/10=60%, dining+coffee=60% — 兩條都過閥值，但 COFFEE_WARRIOR 順序在前
        // 設計理由：純喝咖啡的人識別應該是 ☕ 而不是廣義 🍱
        val result = matchArchetype(dist("coffee" to 6, "other" to 4))
        assertArchetype(result, CouponArchetype.COFFEE_WARRIOR, total = 10, percent = 60)
    }

    @Test
    fun `pure coffee 70 percent 觸發 COFFEE_WARRIOR`() {
        val result = matchArchetype(dist("coffee" to 7, "shopping" to 3))
        assertArchetype(result, CouponArchetype.COFFEE_WARRIOR, total = 10, percent = 70)
    }

    // ===== 邊界 =====

    @Test
    fun `FOODIE 剛好 60 percent 觸發`() {
        val result = matchArchetype(dist("dining" to 3, "coffee" to 3, "shopping" to 4))
        assertArchetype(result, CouponArchetype.FOODIE, total = 10, percent = 60)
    }

    @Test
    fun `FOODIE 差 1 張 不觸發 退到 OMNIVORE`() {
        // dining+coffee = 50% (5/10) < 60%
        // 其他都不到單一 40%
        val result = matchArchetype(dist("dining" to 3, "coffee" to 2, "shopping" to 3, "movie" to 2))
        assertArchetype(result, CouponArchetype.OMNIVORE, total = 10, percent = 0)
    }

    @Test
    fun `pet 39 percent 不觸發 PET_PARENT`() {
        // 9/24 = 37.5% < 40%
        val result = matchArchetype(dist("pet" to 9, "other" to 15))
        // 其他類都沒過閥值
        assertArchetype(result, CouponArchetype.OMNIVORE, total = 24, percent = 0)
    }

    @Test
    fun `未知分類 名稱會被當作 other 計入分母不觸發 archetype`() {
        // "unknown_cat" 不對應任何 archetype，但仍計入總數
        val result = matchArchetype(dist("unknown_cat" to 8, "dining" to 2))
        // dining 20% 不夠任何 archetype
        assertArchetype(result, CouponArchetype.OMNIVORE, total = 10, percent = 0)
    }

    // ===== UI 文案 =====

    @Test
    fun `describe FOODIE 帶百分比`() {
        assertEquals("87% 都跟吃喝有關", CouponArchetype.FOODIE.describe(87))
    }

    @Test
    fun `describe OMNIVORE 忽略百分比`() {
        assertEquals("什麼類型都用一點", CouponArchetype.OMNIVORE.describe(0))
        assertEquals("什麼類型都用一點", CouponArchetype.OMNIVORE.describe(42))
    }

    // ===== 輔助 =====

    private fun assertArchetype(
        result: ArchetypeState,
        expected: CouponArchetype,
        total: Int,
        percent: Int
    ) {
        assertTrue("expected Identified, got $result", result is ArchetypeState.Identified)
        result as ArchetypeState.Identified
        assertEquals(expected, result.archetype)
        assertEquals(total, result.totalUsed)
        assertEquals(percent, result.concentrationPercent)
    }
}
