package com.kevin.coupy.data.ocr

import android.net.Uri

/**
 * OCR 抽象介面，讓 ViewModel 不關心具體實作（stub / real）。
 *
 * Phase 1 用 [StubOcrService]——返回假資料模擬流程。
 * Phase 2 換成 RealOcrService（透過 Cloudflare Worker 呼叫 Gemini）。
 */
interface OcrService {
    suspend fun recognizeTicket(imageUri: Uri): OcrResult
}
