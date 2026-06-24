package com.kevin.coupy.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri

/**
 * 把任意來源圖片（相機暫存檔 / 相簿 content uri / 本機 file uri）解碼成
 * 「已套用 EXIF 旋轉、且最長邊不超過 maxDimensionPx」的 Bitmap。
 *
 * 同時給兩處用：
 * - [CouponPhotoStore.saveFromUri] 存檔前的降析度與轉正
 * - UI 預覽 / 全螢幕看圖（ui/components/CouponPhoto.kt）
 *
 * 因為存檔時就轉正、降析度、去掉 EXIF，存進來的檔案是「正放」的標準 JPEG；
 * 但「剛拍 / 剛選還沒存」的預覽來源仍可能帶 EXIF 旋轉，所以這裡一律讀 EXIF 補正。
 *
 * 解碼失敗（檔案不存在、非圖片、OOM 等）一律回 null，由呼叫端決定 fallback。
 */
fun decodeUprightBitmap(context: Context, uri: Uri, maxDimensionPx: Int): Bitmap? {
    val resolver = context.contentResolver

    // 1. 先只讀尺寸，算 inSampleSize（2 的次方降取樣，省記憶體）
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching {
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    }.getOrNull()
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimensionPx)
    }
    val decoded = runCatching {
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
    }.getOrNull() ?: return null

    // 2. 套 EXIF 旋轉
    val orientation = runCatching {
        resolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }
    }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
    val rotated = applyExifOrientation(decoded, orientation)

    // 3. inSampleSize 只能降到 2 的次方，可能仍大於上限 → 再精準縮一次
    return scaleToMaxDimension(rotated, maxDimensionPx)
}

private fun calculateInSampleSize(width: Int, height: Int, maxDimensionPx: Int): Int {
    var sample = 1
    var w = width
    var h = height
    while (w / 2 >= maxDimensionPx && h / 2 >= maxDimensionPx) {
        w /= 2
        h /= 2
        sample *= 2
    }
    return sample
}

private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f); matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f); matrix.postScale(-1f, 1f)
        }
        else -> return bitmap
    }
    return runCatching {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            .also { if (it != bitmap) bitmap.recycle() }
    }.getOrDefault(bitmap)
}

private fun scaleToMaxDimension(bitmap: Bitmap, maxDimensionPx: Int): Bitmap {
    val longest = maxOf(bitmap.width, bitmap.height)
    if (longest <= maxDimensionPx) return bitmap
    val ratio = maxDimensionPx.toFloat() / longest
    val targetW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
    val targetH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
    return runCatching {
        Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            .also { if (it != bitmap) bitmap.recycle() }
    }.getOrDefault(bitmap)
}
