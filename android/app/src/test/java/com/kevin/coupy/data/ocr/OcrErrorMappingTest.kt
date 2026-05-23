package com.kevin.coupy.data.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

/**
 * 純函式測試——不需要 MockWebServer / instrumentation。
 * 覆蓋所有 OCR 錯誤分類路徑與 success body 解析路徑。
 */
class OcrErrorMappingTest {

    // ===== mapHttpFailureToException：優先用 Worker 回的 error code =====

    @Test
    fun `body 帶 ai_busy 一律對應 AiBusy 不管 status code`() {
        val body = """{"error":"ai_busy"}"""
        assertSame(OcrException.AiBusy, mapHttpFailureToException(503, body))
        assertSame(OcrException.AiBusy, mapHttpFailureToException(429, body))
        assertSame(OcrException.AiBusy, mapHttpFailureToException(500, body))
    }

    @Test
    fun `body 帶各 worker error code 都翻成對應的 OcrException`() {
        val cases = mapOf(
            "ai_busy" to OcrException.AiBusy,
            "ai_unavailable" to OcrException.AiUnavailable,
            "ai_timeout" to OcrException.Timeout,
            "ai_blocked" to OcrException.AiBlocked,
            "ai_failed" to OcrException.AiFailed,
            "network_error" to OcrException.NetworkError,
            "image_invalid" to OcrException.ImageInvalid,
            "image_too_large" to OcrException.ImageTooLarge,
            "unauthorized" to OcrException.Unauthorized,
            "bad_request" to OcrException.AiFailed,        // 對使用者就是「失敗」
            "internal_error" to OcrException.AiFailed
        )
        cases.forEach { (code, expected) ->
            val body = """{"error":"$code"}"""
            assertSame("code=$code", expected, mapHttpFailureToException(500, body))
        }
    }

    @Test
    fun `body 帶未知 error code 回 AiFailed`() {
        val body = """{"error":"who_knows"}"""
        assertSame(OcrException.AiFailed, mapHttpFailureToException(500, body))
    }

    // ===== mapHttpFailureToException：body 沒帶 error 時用 status code =====

    @Test
    fun `body 為空 fallback 到 status code`() {
        assertSame(OcrException.Unauthorized, mapHttpFailureToException(401, ""))
        assertSame(OcrException.ImageTooLarge, mapHttpFailureToException(413, ""))
        assertSame(OcrException.AiBusy, mapHttpFailureToException(429, ""))
        assertSame(OcrException.AiBusy, mapHttpFailureToException(503, ""))
        assertSame(OcrException.Timeout, mapHttpFailureToException(504, ""))
        assertSame(OcrException.AiFailed, mapHttpFailureToException(500, ""))
        assertSame(OcrException.AiFailed, mapHttpFailureToException(502, ""))
        assertSame(OcrException.AiFailed, mapHttpFailureToException(599, ""))
        assertSame(OcrException.AiFailed, mapHttpFailureToException(418, "")) // teapot
    }

    @Test
    fun `body 不是 JSON 也 fallback 到 status code`() {
        assertSame(OcrException.AiBusy, mapHttpFailureToException(503, "Service Unavailable"))
        assertSame(OcrException.AiFailed, mapHttpFailureToException(500, "<html>500</html>"))
    }

    @Test
    fun `body 是 JSON 但沒 error 欄位也 fallback 到 status code`() {
        val body = """{"message":"something"}"""
        assertSame(OcrException.AiFailed, mapHttpFailureToException(500, body))
    }

    @Test
    fun `body error 是空字串時 fallback 到 status code`() {
        val body = """{"error":""}"""
        assertSame(OcrException.AiBusy, mapHttpFailureToException(429, body))
    }

    // ===== fromWorkerErrorCode：null 與未知 code =====

    @Test
    fun `fromWorkerErrorCode null 回 AiFailed`() {
        assertSame(OcrException.AiFailed, OcrException.fromWorkerErrorCode(null))
    }

    @Test
    fun `fromWorkerErrorCode 未知字串回 AiFailed`() {
        assertSame(OcrException.AiFailed, OcrException.fromWorkerErrorCode("totally_made_up"))
    }

    // ===== parseSuccessResponse：完整與部分欄位 =====

    @Test
    fun `parseSuccessResponse 三欄位都有時都解析出來`() {
        val body = """
            {"name":"燒肉同話","expireDate":"2026-12-31","categoryId":"dining"}
        """.trimIndent()
        val result = parseSuccessResponse(body)
        assertEquals("燒肉同話", result.name)
        assertEquals(LocalDate.of(2026, 12, 31), result.expireDate)
        assertEquals("dining", result.categoryId)
        assertNull(result.quantity)
        assert(result.hasAnyData)
    }

    @Test
    fun `parseSuccessResponse 全 null 時 hasAnyData 為 false`() {
        val body = """{"name":null,"expireDate":null,"categoryId":null}"""
        val result = parseSuccessResponse(body)
        assertNull(result.name)
        assertNull(result.expireDate)
        assertNull(result.categoryId)
        assert(!result.hasAnyData)
    }

    @Test
    fun `parseSuccessResponse 缺欄位 時當 null 處理`() {
        val body = """{"name":"Only name"}"""
        val result = parseSuccessResponse(body)
        assertEquals("Only name", result.name)
        assertNull(result.expireDate)
        assertNull(result.categoryId)
    }

    @Test
    fun `parseSuccessResponse 欄位是字串 null 也當 null`() {
        val body = """{"name":"null","expireDate":"null","categoryId":"null"}"""
        val result = parseSuccessResponse(body)
        assertNull(result.name)
        assertNull(result.expireDate)
        assertNull(result.categoryId)
    }

    @Test
    fun `parseSuccessResponse 日期格式爛掉時 expireDate 為 null 但其他欄位保留`() {
        val body = """{"name":"X","expireDate":"not-a-date","categoryId":"dining"}"""
        val result = parseSuccessResponse(body)
        assertEquals("X", result.name)
        assertNull(result.expireDate)
        assertEquals("dining", result.categoryId)
    }

    @Test
    fun `parseSuccessResponse body 不是 JSON 時拋 AiFailed`() {
        assertThrows(OcrException.AiFailed::class.java) {
            parseSuccessResponse("not json at all")
        }
    }

    @Test
    fun `parseSuccessResponse 空字串拋 AiFailed`() {
        assertThrows(OcrException.AiFailed::class.java) {
            parseSuccessResponse("")
        }
    }

    @Test
    fun `parseSuccessResponse name 是空白字串時當 null`() {
        val body = """{"name":"   ","expireDate":"2026-12-31","categoryId":"dining"}"""
        val result = parseSuccessResponse(body)
        assertNull(result.name)
        assertEquals(LocalDate.of(2026, 12, 31), result.expireDate)
    }

    @Test
    fun `parseSuccessResponse 9999 to 12 to 31 視為無到期日哨兵 仍然 parse 成 LocalDate`() {
        val body = """{"name":"永久券","expireDate":"9999-12-31","categoryId":"other"}"""
        val result = parseSuccessResponse(body)
        assertEquals(LocalDate.of(9999, 12, 31), result.expireDate)
    }
}
