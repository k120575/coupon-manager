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
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

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
 *
 * 所有失敗情境都會被分類成 [OcrException] 的其中一個 case，由呼叫端直接用 [OcrException.userMessage] 顯示。
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
        if (BuildConfig.WORKER_BASE_URL.isBlank() || BuildConfig.OCR_CLIENT_TOKEN.isBlank()) {
            throw OcrException.NotConfigured
        }

        val imageBytes = readImageBytes(imageUri)

        val request = Request.Builder()
            .url("${BuildConfig.WORKER_BASE_URL.trimEnd('/')}/ocr")
            .header("X-Coupy-Token", BuildConfig.OCR_CLIENT_TOKEN)
            .post(imageBytes.toRequestBody("image/jpeg".toMediaType()))
            .build()

        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: UnknownHostException) {
            throw OcrException.NoNetwork
        } catch (e: ConnectException) {
            throw OcrException.NoNetwork
        } catch (e: SocketTimeoutException) {
            throw OcrException.Timeout
        } catch (e: SSLException) {
            throw OcrException.NetworkError
        } catch (e: IOException) {
            throw OcrException.NetworkError
        }

        response.use {
            val responseBody = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                throw mapHttpFailureToException(it.code, responseBody)
            }
            parseSuccessResponse(responseBody)
        }
    }

    private fun readImageBytes(imageUri: Uri): ByteArray {
        val bytes: ByteArray = try {
            context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                ?: throw OcrException.ImageReadFailed
        } catch (e: OcrException) {
            throw e
        } catch (e: SecurityException) {
            throw OcrException.ImageReadFailed
        } catch (e: IOException) {
            throw OcrException.ImageReadFailed
        }
        if (bytes.isEmpty()) throw OcrException.ImageEmpty
        return bytes
    }
}

// ===== 純函式，方便單元測試 =====

/**
 * 從 HTTP 失敗 response 翻譯成 [OcrException]。
 * 優先看 body 裡的 `error` 欄位（Worker 會帶），其次 fallback 到 status code。
 */
internal fun mapHttpFailureToException(statusCode: Int, body: String): OcrException {
    val errorCode = runCatching {
        JSONObject(body).optString("error", "").takeIf { it.isNotBlank() }
    }.getOrNull()
    if (errorCode != null) {
        return OcrException.fromWorkerErrorCode(errorCode)
    }
    return when (statusCode) {
        401 -> OcrException.Unauthorized
        413 -> OcrException.ImageTooLarge
        429, 503 -> OcrException.AiBusy
        504 -> OcrException.Timeout
        in 500..599 -> OcrException.AiFailed
        else -> OcrException.AiFailed
    }
}

/**
 * Worker 回 200 的成功 body → [OcrResult]。
 * 任何欄位缺值都 null（UI 會自己決定要不要保留現有值）。
 * Body 不是 JSON 視為 AI 失敗。
 */
internal fun parseSuccessResponse(json: String): OcrResult {
    val obj = try {
        JSONObject(json)
    } catch (e: Exception) {
        throw OcrException.AiFailed
    }
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

/** JSONObject 的 optString 回 "null" 字串很雷，自己包一層 */
private fun JSONObject.optStringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val s = optString(key, "").trim()
    return s.takeIf { it.isNotEmpty() && it != "null" }
}
