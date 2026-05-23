package com.kevin.coupy.data.stats

import com.kevin.coupy.data.dao.CategoryTicketCount

/**
 * 票券人格標籤——從使用紀錄的分類分佈推導出來的「使用者畫像」。
 *
 * 這不是嚴謹分類，是 Spotify Wrapped 風格的趣味身份標籤，
 * 目的是讓統計頁多一個情緒/分享亮點。
 *
 * 觸發條件見 [matchArchetype]。
 */
enum class CouponArchetype(
    val emoji: String,
    val displayName: String,
    /** 在「過去 N 張使用紀錄，X% [fragment]」這句話的後半段 */
    private val fragment: String
) {
    FOODIE("🍱", "美食探險家", "都跟吃喝有關"),
    COFFEE_WARRIOR("☕", "咖啡因戰士", "都是咖啡或飲料"),
    TRAVELER("✈️", "旅行控", "都跟旅行交通有關"),
    BEAUTY("💆", "美麗達人", "都跟美容保養有關"),
    WELLNESS("💪", "養生健身派", "都跟健身保健有關"),
    THRIFTY("🛒", "精打細算", "都是購物或 3C"),
    ENTERTAINMENT("🎬", "影視享樂派", "都是電影票"),
    PET_PARENT("🐾", "毛孩家長", "都是寵物相關"),
    LIFELONG_LEARNER("📚", "終身學習者", "都是課程學習"),
    OMNIVORE("🎲", "雜食派", "");

    /** UI 用：顯示「X% 都跟吃喝有關」或對 OMNIVORE 顯示「什麼類型都用一點」 */
    fun describe(percent: Int): String = when (this) {
        OMNIVORE -> "什麼類型都用一點"
        else -> "$percent% $fragment"
    }
}

sealed interface ArchetypeState {
    /** 累計使用 < 門檻時，不顯示標籤 */
    data class NotEnoughData(val currentUsed: Int, val required: Int) : ArchetypeState

    data class Identified(
        val archetype: CouponArchetype,
        val totalUsed: Int,
        /** 觸發此 archetype 的累計佔比 (0-100)；OMNIVORE 為 0（UI 不顯示） */
        val concentrationPercent: Int
    ) : ArchetypeState
}

/** 累計使用張數低於此值不顯示標籤——避免根據 1-2 張就貼身份 */
const val ARCHETYPE_USE_THRESHOLD = 5

/**
 * 從使用紀錄分類分佈推導身份標籤。
 *
 * 比對順序依「信號強度」由強到弱：
 * 1. 高 threshold 組合（≥ 60% / ≥ 50%）
 * 2. 單一類別中 threshold（≥ 40%）
 * 3. 都不觸發 → [CouponArchetype.OMNIVORE]
 *
 * 分母包含 `other` 類別——一堆 other 的人本來就應該是雜食派。
 */
fun matchArchetype(distribution: List<CategoryTicketCount>): ArchetypeState {
    val total = distribution.sumOf { it.total }
    if (total < ARCHETYPE_USE_THRESHOLD) {
        return ArchetypeState.NotEnoughData(currentUsed = total, required = ARCHETYPE_USE_THRESHOLD)
    }

    val byCat: Map<String, Int> = distribution.associate { it.category to it.total }
    fun pct(vararg cats: String): Int {
        val sum = cats.sumOf { byCat[it] ?: 0 }
        return (sum * 100) / total
    }

    // 順序：單一高 threshold > 組合高 threshold > 組合中 threshold > 單一中 threshold
    // COFFEE_WARRIOR（單一 ≥ 50%）放最前面，因為「純喝咖啡的人」識別優先於「廣義美食家」——
    // 否則 pure coffee 100% 會先撞到 FOODIE（dining+coffee ≥ 60%）變美食家，跟設計本意不符。
    val coffee = pct("coffee")
    if (coffee >= 50) return ArchetypeState.Identified(CouponArchetype.COFFEE_WARRIOR, total, coffee)

    val foodie = pct("dining", "coffee")
    if (foodie >= 60) return ArchetypeState.Identified(CouponArchetype.FOODIE, total, foodie)

    val traveler = pct("lodging", "transport")
    if (traveler >= 50) return ArchetypeState.Identified(CouponArchetype.TRAVELER, total, traveler)

    val beauty = pct("beauty", "massage")
    if (beauty >= 50) return ArchetypeState.Identified(CouponArchetype.BEAUTY, total, beauty)

    val wellness = pct("fitness", "medical")
    if (wellness >= 50) return ArchetypeState.Identified(CouponArchetype.WELLNESS, total, wellness)

    val thrifty = pct("shopping", "tech")
    if (thrifty >= 50) return ArchetypeState.Identified(CouponArchetype.THRIFTY, total, thrifty)

    val entertainment = pct("movie")
    if (entertainment >= 40) return ArchetypeState.Identified(CouponArchetype.ENTERTAINMENT, total, entertainment)

    val pet = pct("pet")
    if (pet >= 40) return ArchetypeState.Identified(CouponArchetype.PET_PARENT, total, pet)

    val learning = pct("education")
    if (learning >= 40) return ArchetypeState.Identified(CouponArchetype.LIFELONG_LEARNER, total, learning)

    return ArchetypeState.Identified(CouponArchetype.OMNIVORE, total, 0)
}
