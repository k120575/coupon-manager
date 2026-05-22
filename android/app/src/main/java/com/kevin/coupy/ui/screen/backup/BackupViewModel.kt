package com.kevin.coupy.ui.screen.backup

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.coupy.data.backup.BackupParseException
import com.kevin.coupy.data.backup.BackupRepository
import com.kevin.coupy.data.backup.ImportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepository: BackupRepository
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _events = Channel<BackupEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun suggestedExportFilename(today: LocalDate = LocalDate.now()): String =
        "coupy-backup-$today.json"

    fun exportTo(uri: Uri, resolver: ContentResolver) {
        viewModelScope.launch {
            _busy.value = true
            try {
                val json = backupRepository.exportToJson()
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri, "wt")?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("無法寫入檔案")
                }
                _events.send(BackupEvent.ExportSuccess)
            } catch (e: Exception) {
                _events.send(BackupEvent.Error("備份失敗：${e.message ?: "未知錯誤"}"))
            } finally {
                _busy.value = false
            }
        }
    }

    fun importFrom(uri: Uri, resolver: ContentResolver) {
        viewModelScope.launch {
            _busy.value = true
            try {
                val json = withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.use { stream ->
                        stream.readBytes().toString(Charsets.UTF_8)
                    } ?: throw IllegalStateException("無法讀取檔案")
                }
                val result = backupRepository.importFromJson(json)
                _events.send(BackupEvent.ImportSuccess(result))
            } catch (e: BackupParseException) {
                _events.send(BackupEvent.Error(e.message ?: "備份檔格式錯誤"))
            } catch (e: Exception) {
                _events.send(BackupEvent.Error("還原失敗：${e.message ?: "未知錯誤"}"))
            } finally {
                _busy.value = false
            }
        }
    }
}

sealed interface BackupEvent {
    data object ExportSuccess : BackupEvent
    data class ImportSuccess(val result: ImportResult) : BackupEvent
    data class Error(val message: String) : BackupEvent
}
