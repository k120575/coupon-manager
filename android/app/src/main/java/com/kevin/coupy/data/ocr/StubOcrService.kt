package com.kevin.coupy.data.ocr

import android.net.Uri
import kotlinx.coroutines.delay
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 假的 OCR 實作——模擬 1.5 秒延遲後返回假資料，用來測試 App 端流程。
 *
 * Phase 2 會替換成真正呼叫 Worker → Gemini 的 RealOcrService。
 */
@Singleton
class StubOcrService @Inject constructor() : OcrService {

    override suspend fun recognizeTicket(imageUri: Uri): OcrResult {
        delay(1500) // 模擬網路延遲
        // 注意：張數永遠回傳 null（從圖片看不出來，要使用者自己決定）。
        // 分類可以給建議但要使用者確認。
        return OcrResult(
            name = "OCR 辨識的票券（測試）",
            expireDate = LocalDate.now().plusDays(60),
            categoryId = "dining", // Gemini 的「猜」，UI 會要使用者確認
            quantity = null
        )
    }
}
