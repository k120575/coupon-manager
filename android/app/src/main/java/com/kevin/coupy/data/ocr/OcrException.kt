package com.kevin.coupy.data.ocr

/**
 * OCR 流程所有可能的失敗模式。每個都帶一條使用者語言的訊息（無技術術語）。
 *
 * UI 端直接顯示 [userMessage]，不要再加「辨識失敗：」之類的前綴——訊息本身已經完整。
 *
 * 對應 worker/src/ocr-api.ts 的 OcrErrorCode：使用 [fromWorkerErrorCode] 翻譯。
 */
sealed class OcrException(val userMessage: String) : Exception(userMessage) {

    // ===== 圖片問題 =====
    object ImageReadFailed : OcrException("無法讀取這張圖片，請換一張再試") {
        private fun readResolve(): Any = ImageReadFailed
    }
    object ImageEmpty : OcrException("圖片是空的，請重新選擇") {
        private fun readResolve(): Any = ImageEmpty
    }
    object ImageTooLarge : OcrException("圖片太大，請拍小一點或裁剪後再試") {
        private fun readResolve(): Any = ImageTooLarge
    }
    object ImageInvalid : OcrException("這張圖片無法辨識，請換一張清晰的票券照片") {
        private fun readResolve(): Any = ImageInvalid
    }

    // ===== 網路問題 =====
    object NoNetwork : OcrException("沒有網路連線，請檢查網路後再試") {
        private fun readResolve(): Any = NoNetwork
    }
    object Timeout : OcrException("辨識逾時，請檢查網路或換張清晰一點的圖") {
        private fun readResolve(): Any = Timeout
    }
    object NetworkError : OcrException("網路連線不穩定，請稍後再試") {
        private fun readResolve(): Any = NetworkError
    }

    // ===== 辨識服務問題 =====
    object AiBusy : OcrException("辨識服務目前太忙，過一下再試一次") {
        private fun readResolve(): Any = AiBusy
    }
    object AiUnavailable : OcrException("辨識服務暫時無法使用，先手動輸入或稍後再試") {
        private fun readResolve(): Any = AiUnavailable
    }
    object AiBlocked : OcrException("這張圖片無法辨識，請換一張票券照片") {
        private fun readResolve(): Any = AiBlocked
    }
    object AiFailed : OcrException("這次辨識沒成功，再試一次或手動輸入") {
        private fun readResolve(): Any = AiFailed
    }

    // ===== 設定 / 兜底 =====
    object NotConfigured : OcrException("辨識功能還沒準備好，請稍後再試") {
        private fun readResolve(): Any = NotConfigured
    }
    object Unauthorized : OcrException("辨識功能暫時無法使用，請稍後再試") {
        private fun readResolve(): Any = Unauthorized
    }
    object Unknown : OcrException("這次辨識沒成功，再試一次或手動輸入") {
        private fun readResolve(): Any = Unknown
    }

    companion object {
        /**
         * 把 worker/src/ocr-api.ts 回的 error code 翻譯成 OcrException。
         * 未知 code 一律當 [AiFailed] 處理。
         */
        fun fromWorkerErrorCode(code: String?): OcrException = when (code) {
            "ai_busy" -> AiBusy
            "ai_unavailable" -> AiUnavailable
            "ai_timeout" -> Timeout
            "ai_blocked" -> AiBlocked
            "ai_failed" -> AiFailed
            "network_error" -> NetworkError
            "image_invalid" -> ImageInvalid
            "image_too_large" -> ImageTooLarge
            "unauthorized" -> Unauthorized
            "bad_request" -> AiFailed       // App 送錯東西——使用者改不了，給通用訊息
            "internal_error" -> AiFailed
            else -> AiFailed
        }
    }
}
