// app/src/main/java/com/mathsnew/evidencecapture/presentation/trash/TrashViewModel.kt
// 新建文件 - Kotlin

package com.mathsnew.evidencecapture.presentation.trash

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mathsnew.evidencecapture.domain.model.Evidence
import com.mathsnew.evidencecapture.domain.repository.EvidenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val evidenceRepository: EvidenceRepository
) : ViewModel() {

    // 回收站证据列表，实时监听数据库
    val trashList: StateFlow<List<Evidence>> = evidenceRepository.getTrash()
        .stateIn(
            scope          = viewModelScope,
            started        = SharingStarted.WhileSubscribed(5000),
            initialValue   = emptyList()
        )

    // 多选状态
    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode

    // 剩余天数计算：30天 - 已删除天数
    fun remainingDays(deletedAt: Long?): Int {
        if (deletedAt == null) return 30
        val elapsedMs  = System.currentTimeMillis() - deletedAt
        val elapsedDay = (elapsedMs / (24 * 60 * 60 * 1000)).toInt()
        return (30 - elapsedDay).coerceAtLeast(0)
    }

    /** 恢复单条证据 */
    fun restore(evidenceId: String) {
        viewModelScope.launch {
            try {
                evidenceRepository.restore(evidenceId)
                Log.i(TAG, "Restored: $evidenceId")
            } catch (e: Exception) {
                Log.e(TAG, "Restore failed: ${e.message}")
            }
        }
    }

    /** 永久删除单条证据（同时删除文件） */
    fun deletePermanently(evidence: Evidence) {
        viewModelScope.launch {
            try {
                deleteFiles(evidence)
                evidenceRepository.delete(evidence.id)
                Log.i(TAG, "Permanently deleted: ${evidence.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Permanent delete failed: ${e.message}")
            }
        }
    }

    /** 清空回收站（永久删除所有） */
    fun clearAll() {
        viewModelScope.launch {
            try {
                trashList.value.forEach { evidence ->
                    deleteFiles(evidence)
                    evidenceRepository.delete(evidence.id)
                }
                exitSelectionMode()
                Log.i(TAG, "Trash cleared")
            } catch (e: Exception) {
                Log.e(TAG, "Clear trash failed: ${e.message}")
            }
        }
    }

    /** 批量恢复选中项 */
    fun restoreSelected() {
        val ids = _selectedIds.value.toSet()
        viewModelScope.launch {
            ids.forEach { evidenceRepository.restore(it) }
            exitSelectionMode()
        }
    }

    /** 批量永久删除选中项 */
    fun deleteSelected() {
        val ids = _selectedIds.value.toSet()
        viewModelScope.launch {
            val list = trashList.value.filter { it.id in ids }
            list.forEach { evidence ->
                deleteFiles(evidence)
                evidenceRepository.delete(evidence.id)
            }
            exitSelectionMode()
        }
    }

    fun enterSelectionMode(id: String) {
        _isSelectionMode.value = true
        _selectedIds.value     = setOf(id)
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedIds.value     = emptySet()
    }

    fun toggleSelection(id: String) {
        val current = _selectedIds.value.toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        _selectedIds.value = current
        if (current.isEmpty()) exitSelectionMode()
    }

    fun selectAll() {
        _selectedIds.value = trashList.value.map { it.id }.toSet()
    }

    private fun deleteFiles(evidence: Evidence) {
        if (evidence.mediaPath.isNotEmpty())
            File(evidence.mediaPath).takeIf { it.exists() }?.delete()
        if (evidence.voiceNotePath.isNotEmpty())
            File(evidence.voiceNotePath).takeIf { it.exists() }?.delete()
    }

    companion object {
        private const val TAG = "TrashViewModel"
    }
}