package com.kevin.coupy.data.backup

import com.kevin.coupy.data.CouponStatus
import com.kevin.coupy.data.CouponType
import com.kevin.coupy.data.dao.CouponDao
import com.kevin.coupy.data.entity.CouponEntity
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 票券備份的序列化 / 反序列化。
 *
 * 設計原則來自 [[feedback-respectful-freemium]]：免費版要有「使用者可以帶資料走人」的退路。
 * 格式刻意做成可讀的 JSON，使用者可以用任何文字編輯器打開檢視、甚至手動編輯。
 *
 * 匯出範圍：所有未刪除的票券（active + used），不含軟刪除資料。
 * 匯入策略：以 name + expire_date + category 為自然鍵去重，已存在則跳過。
 */
@Singleton
class BackupRepository @Inject constructor(
    private val dao: CouponDao
) {

    // ===== 匯出 =====

    /**
     * 把目前資料庫序列化成 JSON 字串。
     */
    suspend fun exportToJson(now: Instant = Instant.now()): String {
        val coupons = dao.getAllForBackup()
        return buildJson(coupons, now).toString(JSON_INDENT)
    }

    // ===== 匯入 =====

    /**
     * 解析 JSON 字串並寫入資料庫。已存在的票券（natural key 相同）會被跳過。
     *
     * @throws BackupParseException 格式不合法 / 版本不支援
     */
    suspend fun importFromJson(json: String): ImportResult {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw BackupParseException("無法解析 JSON 檔案：${e.message}", e)
        }

        val version = root.optInt(KEY_VERSION, -1)
        if (version <= 0 || version > BACKUP_FORMAT_VERSION) {
            throw BackupParseException("不支援的備份版本：$version（本 App 支援到 $BACKUP_FORMAT_VERSION）")
        }

        val array = root.optJSONArray(KEY_COUPONS)
            ?: throw BackupParseException("備份檔缺少 \"$KEY_COUPONS\" 欄位")

        var imported = 0
        var skippedDup = 0
        var skippedInvalid = 0
        val toInsert = mutableListOf<CouponEntity>()

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i)
            if (obj == null) {
                skippedInvalid++
                continue
            }
            val entity = parseCoupon(obj)
            if (entity == null) {
                skippedInvalid++
                continue
            }
            val existing = dao.countMatching(
                name = entity.name,
                expireDate = entity.expireDate.toString(),
                category = entity.category
            )
            if (existing > 0) {
                skippedDup++
            } else {
                toInsert.add(entity)
                imported++
            }
        }

        if (toInsert.isNotEmpty()) {
            dao.insertAll(toInsert)
        }

        return ImportResult(
            imported = imported,
            skippedDuplicates = skippedDup,
            skippedInvalid = skippedInvalid
        )
    }

    // ===== private =====

    private fun buildJson(coupons: List<CouponEntity>, now: Instant): JSONObject {
        val array = JSONArray()
        coupons.forEach { array.put(toJson(it)) }
        return JSONObject().apply {
            put(KEY_VERSION, BACKUP_FORMAT_VERSION)
            put(KEY_EXPORTED_AT, now.toString())
            put(KEY_COUNT, coupons.size)
            put(KEY_COUPONS, array)
        }
    }

    private fun toJson(c: CouponEntity): JSONObject = JSONObject().apply {
        put("name", c.name)
        put("expire_date", c.expireDate.toString())
        put("category", c.category)
        put("quantity", c.quantity)
        put("status", c.status.dbValue)
        put("type", c.type.dbValue)
        put("created_at", c.createdAt.toString())
        if (c.usedAt != null) put("used_at", c.usedAt.toString())
        if (!c.note.isNullOrBlank()) put("note", c.note)
    }

    private fun parseCoupon(obj: JSONObject): CouponEntity? {
        val name = obj.optString("name").takeIf { it.isNotBlank() } ?: return null
        val expireStr = obj.optString("expire_date").takeIf { it.isNotBlank() } ?: return null
        val category = obj.optString("category").takeIf { it.isNotBlank() } ?: return null
        val quantity = obj.optInt("quantity", 1).coerceAtLeast(1)
        val statusStr = obj.optString("status", "active")
        val typeStr = obj.optString("type", CouponType.PHYSICAL.dbValue)
        val createdAtStr = obj.optString("created_at").takeIf { it.isNotBlank() }
        val usedAtStr = obj.optString("used_at").takeIf { it.isNotBlank() }
        val note = obj.optString("note").takeIf { it.isNotBlank() }

        val expireDate = runCatching { LocalDate.parse(expireStr) }.getOrNull() ?: return null
        val createdAt = createdAtStr?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?: Instant.now()
        val usedAt = usedAtStr?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val status = CouponStatus.fromDbValue(statusStr)
            .let { if (it == CouponStatus.DELETED) CouponStatus.ACTIVE else it }
        val type = CouponType.fromDbValue(typeStr)

        return CouponEntity(
            name = name,
            expireDate = expireDate,
            category = category,
            quantity = quantity,
            status = status,
            type = type,
            createdAt = createdAt,
            usedAt = usedAt,
            note = note
        )
    }

    companion object {
        private const val JSON_INDENT = 2
        private const val KEY_VERSION = "version"
        private const val KEY_EXPORTED_AT = "exported_at"
        private const val KEY_COUNT = "count"
        private const val KEY_COUPONS = "coupons"
    }
}

class BackupParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
