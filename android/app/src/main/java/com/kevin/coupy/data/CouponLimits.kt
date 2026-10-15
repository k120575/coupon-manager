package com.kevin.coupy.data

/**
 * 票券欄位的長度／數量上限，單一來源。
 *
 * 所有會把字串寫進 Room 的入口都要套：編輯器（onNameChange）、備份匯入（BackupRepository）、
 * OCR 回填（CouponEditViewModel.applyOcrResultToForm）。少套一個入口，超長字串就會進資料庫，
 * 之後列表每次排版都要量測整串，造成卡頓。
 */
object CouponLimits {
    const val MAX_NAME_LENGTH = 30
    const val MAX_NOTE_LENGTH = 100
    const val MAX_QUANTITY = 999
}
