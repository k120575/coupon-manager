package com.kevin.coupy.data.ocr

import android.content.Context
import android.net.Uri
import com.kevin.coupy.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 透過 Cloudflare Worker (/ocr) 呼叫 Gemini Vision 的真實 OCR 實作。
 *
 * 設定方式：
 * 1. 在 `android/local.properties` 加：
 *      coupy.workerBaseUrl=https://your-worker.workers.dev
 *      coupy.ocrToken=YOUR_RANDOM_TOKEN
 * 2. Worker 端對應地設 secret：
 *      npx wrangler secret put OCR_CLIENT_SECRET
 *      （貼跟 coupy.ocrToken 同一個值）
 */
@Singleton
class RealOcrService @Inject constructor(
    @ApplicationContext private val context: Context
) : OcrService {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS) // Gemini Vision 慢，給足時間
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun recognizeTicket(imageUri: Uri): OcrResult = withContext(Dispatchers.IO) {
        if (BuildConfig.WORKER_BASE_URL.isBlank()) {
            throw IllegalStateException(
                "WORKER_BASE_URL 未設定。請在 android/local.properties 加入 coupy.workerBaseUrl"
            )
        }
        if (BuildConfig.OCR_CLIENT_TOKEN.isBlank()) {
            throw IllegalStateException(
                "OCR_CLIENT_TOKEN 未設定。請在 android/local.properties 加入 coupy.ocrToken"
            )
        }

        // 讀圖片成 bytes（FileProvider Uri）
        val imageBytes: ByteArray = context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
            ?: throw IOException("無法讀取圖片")

        if (imageBytes.isEmpty()) throw IOException("圖片為空")

        val body = imageBytes.toRequestBody("image/jpeg".toMediaType())
        val request = Request.Builder()
            .url("${BuildConfig.WORKER_BASE_URL.trimEnd('/')}/ocr")
            .header("X-Coupy-Token", BuildConfig.OCR_CLIENT_TOKEN)
            .post(body)
            .build()

        val response = httpClient.newCall(request).execute()
        response.use {
            val responseBody = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                throw IOException("OCR 服務回傳 HTTP ${it.code}：${responseBody.take(200)}")
            }
            parseResponse(responseBody)
        }
    }

    private fun parseResponse(json: String): OcrResult {
        val obj = JSONObject(json)
        val name = obj.optStringOrNull("name")
        val dateStr = obj.optStringOrNull("expireDate")
        val categoryId = obj.optStringOrNull("categoryId")

        val date: LocalDate? = dateStr?.let {
            try {
                LocalDate.parse(it)
            } catch (e: Exception) {
                null
            }
        }

        return OcrResult(
            name = name,
            expireDate = date,
            categoryId = categoryId,
            quantity = null // OCR 永遠不知道張數
        )
    }
}

/** JSONObject 的 optString 回 "null" 字串很雷，自己包一層 */
private fun JSONObject.optStringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val s = optString(key, "").trim()
    return s.takeIf { it.isNotEmpty() && it != "null" }
}
