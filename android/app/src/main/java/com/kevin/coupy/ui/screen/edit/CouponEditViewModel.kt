package com.kevin.coupy.ui.screen.edit

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.coupy.data.CouponRepository
import com.kevin.coupy.data.CouponType
import com.kevin.coupy.data.category.Category
import com.kevin.coupy.data.category.CategoryRepository
import com.kevin.coupy.data.entity.CouponEntity
import com.kevin.coupy.data.ocr.MonthlyUsage
import com.kevin.coupy.data.ocr.OcrException
import com.kevin.coupy.data.ocr.OcrService
import com.kevin.coupy.data.ocr.OcrUsageTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * 新增 / 編輯 票券的 ViewModel。
 *
 * 模式判定：savedStateHandle["couponId"] 為 null → 新增；有值且 > 0 → 編輯該筆。
 * UI 透過 navigation 傳遞 couponId。
 */
@HiltViewModel
class CouponEditViewModel @Inject constructor(
    private val couponRepository: CouponRepository,
    categoryRepository: CategoryRepository,
    private val ocrService: OcrService,
    private val ocrUsageTracker: OcrUsageTracker,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val editingId: Long? =
        savedStateHandle.get<Long>(KEY_COUPON_ID)?.takeIf { it > 0L }

    val isEditMode: Boolean = editingId != null

    private val _formState = MutableStateFlow(CouponFormState.empty())
    val formState: StateFlow<CouponFormState> = _formState.asStateFlow()

    val categories: StateFlow<List<Category>> = categoryRepository.observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val ocrUsage: StateFlow<MonthlyUsage> = ocrUsageTracker.monthlyUsage
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MonthlyUsage(
                count = 0,
                limit = OcrUsageTracker.CURRENT_LIMIT,
                isDaily = OcrUsageTracker.IS_DAILY
            )
        )

    private val _isOcrRunning = MutableStateFlow(false)
    val isOcrRunning: StateFlow<Boolean> = _isOcrRunning.asStateFlow()

    private val _saveEvent = Channel<SaveEvent>(Channel.BUFFERED)
    val saveEvent = _saveEvent.receiveAsFlow()

    init {
        editingId?.let { loadForEdit(it) }
    }

    private fun loadForEdit(id: Long) {
        viewModelScope.launch {
            couponRepository.getById(id)?.let { coupon ->
                _formState.update {
                    it.copy(
                        name = coupon.name,
                        expireDate = coupon.expireDate,
                        categoryId = coupon.category,
                        quantity = coupon.quantity,
                        type = coupon.type,
                        note = coupon.note.orEmpty()
                    )
                }
            }
        }
    }

    // ===== 表單欄位更新 =====

    fun onNameChange(name: String) {
        _formState.update { it.copy(name = name.take(MAX_NAME_LENGTH)) }
    }

    fun onExpireDateChange(date: LocalDate) {
        _formState.update { it.copy(expireDate = date) }
    }

    fun onCategoryChange(categoryId: String) {
        _formState.update { it.copy(categoryId = categoryId) }
    }

    fun onQuantityChange(quantity: Int) {
        val clamped = quantity.coerceIn(1, MAX_QUANTITY)
        _formState.update { it.copy(quantity = clamped) }
    }

    fun onTypeChange(type: CouponType) {
        _formState.update { it.copy(type = type) }
    }

    fun onNoteChange(note: String) {
        _formState.update { it.copy(note = note.take(MAX_NOTE_LENGTH)) }
    }

    // ===== 儲存 =====

    fun save() {
        val state = _formState.value
        if (!state.isValid) return

        viewModelScope.launch {
            try {
                if (editingId == null) {
                    couponRepository.add(state.toNewEntity())
                } else {
                    val existing = couponRepository.getById(editingId)
                    if (existing != null) {
                        couponRepository.update(state.applyTo(existing))
                    }
                }
                _saveEvent.send(SaveEvent.Saved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _saveEvent.send(SaveEvent.Error(e.message ?: "儲存失敗"))
            }
        }
    }

    // ===== 使用 / 刪除（編輯模式才能呼叫）=====

    fun useTickets(count: Int) {
        val id = editingId ?: return
        require(count >= 1)
        viewModelScope.launch {
            try {
                val ok = couponRepository.use(id, count)
                if (ok) {
                    _saveEvent.send(SaveEvent.Used(count))
                } else {
                    _saveEvent.send(SaveEvent.Error("使用失敗（張數不足或票券不存在）"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _saveEvent.send(SaveEvent.Error(e.message ?: "使用失敗"))
            }
        }
    }

    fun deleteCoupon() {
        val id = editingId ?: return
        viewModelScope.launch {
            try {
                couponRepository.delete(id)
                _saveEvent.send(SaveEvent.Deleted)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _saveEvent.send(SaveEvent.Error(e.message ?: "刪除失敗"))
            }
        }
    }

    // ===== OCR =====

    /**
     * 拍照後呼叫：執行 OCR、把結果填入表單欄位、計次 +1。
     * 撞牆檢查由 UI 側負責——這個 method 假設呼叫前已通過配額檢查。
     *
     * 計次策略：
     * - 任何錯誤 → 不扣（recognizeTicket throw 後 increment 不會被呼叫）
     * - Gemini 成功但完全沒辨識到內容（hasAnyData = false）→ 不扣，提示使用者
     * - 至少抓到一個欄位 → 扣 1 次
     *
     * CancellationException 必須 rethrow，不然 viewModelScope 取消時會被當成錯誤吞掉。
     */
    fun runOcrOnImage(imageUri: Uri) {
        viewModelScope.launch {
            _isOcrRunning.value = true
            try {
                val result = ocrService.recognizeTicket(imageUri)
                if (result.hasAnyData) {
                    ocrUsageTracker.increment()
                    applyOcrResultToForm(result)
                } else {
                    _saveEvent.send(
                        SaveEvent.Error("沒辨識到票券資訊，請手動輸入或換張清晰的圖（這次不扣次數）")
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: OcrException) {
                _saveEvent.send(SaveEvent.Error(e.userMessage))
            } catch (e: Exception) {
                _saveEvent.send(SaveEvent.Error(OcrException.Unknown.userMessage))
            } finally {
                _isOcrRunning.value = false
            }
        }
    }

    private fun applyOcrResultToForm(result: com.kevin.coupy.data.ocr.OcrResult) {
        _formState.update { current ->
            current.copy(
                name = result.name?.takeIf { it.isNotBlank() } ?: current.name,
                expireDate = result.expireDate ?: current.expireDate,
                categoryId = result.categoryId?.takeIf { it.isNotBlank() } ?: current.categoryId,
                quantity = result.quantity?.takeIf { it >= 1 } ?: current.quantity
            )
        }
    }

    companion object {
        const val KEY_COUPON_ID = "couponId"
        const val MAX_NAME_LENGTH = 30
        const val MAX_QUANTITY = 999
        const val MAX_NOTE_LENGTH = 100
    }
}

// ===== Form State =====

data class CouponFormState(
    val name: String,
    val expireDate: LocalDate,
    val categoryId: String,
    val quantity: Int,
    val type: CouponType,
    /** 空字串 = 沒填備註，存進 DB 時轉成 null */
    val note: String
) {
    val isValid: Boolean
        get() = name.isNotBlank() &&
                categoryId.isNotBlank() &&
                quantity in 1..CouponEditViewModel.MAX_QUANTITY

    private val noteForDb: String? get() = note.trim().takeIf { it.isNotEmpty() }

    fun toNewEntity(): CouponEntity = CouponEntity(
        name = name.trim(),
        expireDate = expireDate,
        category = categoryId,
        quantity = quantity,
        type = type,
        note = noteForDb
    )

    fun applyTo(existing: CouponEntity): CouponEntity = existing.copy(
        name = name.trim(),
        expireDate = expireDate,
        category = categoryId,
        quantity = quantity,
        type = type,
        note = noteForDb
    )

    companion object {
        fun empty(): CouponFormState = CouponFormState(
            name = "",
            expireDate = LocalDate.now().plusDays(30),
            categoryId = "dining", // 預設「餐飲」（最常見類別）
            quantity = 1,
            type = CouponType.PHYSICAL,
            note = ""
        )
    }
}

// ===== One-shot events =====

sealed interface SaveEvent {
    data object Saved : SaveEvent
    data class Used(val count: Int) : SaveEvent
    data object Deleted : SaveEvent
    data class Error(val message: String) : SaveEvent
}
