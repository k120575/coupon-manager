package com.kevin.coupy.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 備份用的照片封存介面（只抽出備份會用到的兩個動作，方便測試替身）。
 *
 * 之所以拆介面：[BackupRepository] 的 unit test 手動 new，不想拖進需要 Android Context
 * 的真實實作；測試可注入 [NoopPhotoArchiver] 或自訂替身。
 */
interface PhotoArchiver {
    /** 讀本機照片檔轉成 Base64（給備份匯出）；path 為 null / 檔案不存在 → null */
    fun encodeToBase64(path: String?): String?

    /** 把 Base64 寫成新照片檔，回傳新路徑（給備份還原）；解碼或寫檔失敗 → null */
    suspend fun saveFromBase64(base64: String): String?
}

/** 不做事的封存器，給不關心照片的測試 / 非 Hilt 呼叫端當預設值用 */
object NoopPhotoArchiver : PhotoArchiver {
    override fun encodeToBase64(path: String?): String? = null
    override suspend fun saveFromBase64(base64: String): String? = null
}

/**
 * 票券照片的本機儲存。
 *
 * 照片存在 App 私有目錄 filesDir/coupon_images/，純本機、不上傳、不進相簿。
 * 用途是「使用時出示給店員掃」，所以存檔時就降到最長邊 [MAX_DIMENSION] px、
 * JPEG 品質 [JPEG_QUALITY]、並把 EXIF 旋轉烤進像素（存出來就是正放的標準 JPEG）。
 * 這樣顯示快、備份體積也可控（單張約數百 KB）。
 *
 * 刪除一律限制在 coupon_images/ 目錄內，避免誤刪到別的檔案。
 */
@Singleton
class CouponPhotoStore @Inject constructor(
    @ApplicationContext private val context: Context
) : PhotoArchiver {

    private fun imagesDir(): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    private fun newImageFile(): File =
        File(imagesDir(), "img_${UUID.randomUUID()}.jpg")

    /**
     * 從來源 uri（相機暫存檔 / 相簿挑選 / file uri）讀圖、轉正、降析度後存成新檔。
     * @return 新檔絕對路徑；解碼或寫檔失敗 → null
     */
    suspend fun saveFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        val bitmap = decodeUprightBitmap(context, uri, MAX_DIMENSION) ?: return@withContext null
        val file = newImageFile()
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            file.delete()
            null
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * 刪除一張照片檔。只允許刪 coupon_images/ 底下的檔，path 空 / 不在該目錄一律忽略。
     */
    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching {
            val file = File(path)
            if (file.exists() && file.parentFile?.name == DIR_NAME) {
                file.delete()
            }
        }
    }

    // ===== 備份封存（PhotoArchiver）=====

    override fun encodeToBase64(path: String?): String? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        return runCatching {
            Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        }.getOrNull()
    }

    override suspend fun saveFromBase64(base64: String): String? = withContext(Dispatchers.IO) {
        val bytes = runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrNull()
            ?: return@withContext null
        val file = newImageFile()
        runCatching {
            file.writeBytes(bytes)
            file.absolutePath
        }.getOrNull()
    }

    companion object {
        private const val DIR_NAME = "coupon_images"
        private const val MAX_DIMENSION = 1600
        private const val JPEG_QUALITY = 85
    }
}
