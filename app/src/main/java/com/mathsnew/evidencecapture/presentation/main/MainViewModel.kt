// app/src/main/java/com/mathsnew/evidencecapture/presentation/main/MainViewModel.kt
// Kotlin - 表现层，主页 ViewModel，含搜索/筛选/排序/删除/批量删除/编辑元数据逻辑

package com.mathsnew.evidencecapture.presentation.main

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mathsnew.evidencecapture.domain.model.Evidence
import com.mathsnew.evidencecapture.domain.model.MediaType
import com.mathsnew.evidencecapture.domain.repository.EvidenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class SortOrder { NEWEST, OLDEST }
enum class FilterType { ALL, PHOTO, VIDEO, AUDIO, TEXT }

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val evidenceRepository: EvidenceRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _filterType = MutableStateFlow(FilterType.ALL)
    val filterType: StateFlow<FilterType> = _filterType

    private val _sortOrder = MutableStateFlow(SortOrder.NEWEST)
    val sortOrder: StateFlow<SortOrder> = _sortOrder

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds

    private val _filterParams = combine(
        _searchQuery,
        _filterType,
        _sortOrder
    ) { query, filter, sort ->
        Triple(query, filter, sort)
    }

    val evidenceList: StateFlow<List<Evidence>> = combine(
        evidenceRepository.getAll(),
        _filterParams
    ) { list, (query, filter, sort) ->
        list
            .filter { evidence ->
                when (filter) {
                    FilterType.ALL   -> true
                    FilterType.PHOTO -> evidence.mediaType == MediaType.PHOTO
                    FilterType.VIDEO -> evidence.mediaType == MediaType.VIDEO
                    FilterType.AUDIO -> evidence.mediaType == MediaType.AUDIO
                    FilterType.TEXT  -> evidence.mediaType == MediaType.TEXT
                }
            }
            .filter { evidence ->
                if (query.isBlank()) true
                else evidence.title.contains(query, ignoreCase = true) ||
                        evidence.tag.contains(query, ignoreCase = true)
            }
            .let { filtered ->
                when (sort) {
                    SortOrder.NEWEST -> filtered.sortedByDescending { it.createdAt }
                    SortOrder.OLDEST -> filtered.sortedBy { it.createdAt }
                }
            }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setSearchQuery(query: String) { _searchQuery.value = query }

    fun setFilterType(type: FilterType) { _filterType.value = type }

    fun toggleSortOrder() {
        _sortOrder.value = if (_sortOrder.value == SortOrder.NEWEST)
            SortOrder.OLDEST else SortOrder.NEWEST
    }

    fun enterSelectionMode(evidenceId: String) {
        _isSelectionMode.value = true
        _selectedIds.value = setOf(evidenceId)
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedIds.value = emptySet()
    }

    fun toggleSelection(evidenceId: String) {
        val current = _selectedIds.value.toMutableSet()
        if (current.contains(evidenceId)) current.remove(evidenceId)
        else current.add(evidenceId)
        _selectedIds.value = current
        if (current.isEmpty()) exitSelectionMode()
    }

    fun selectAll() {
        _selectedIds.value = evidenceList.value.map { it.id }.toSet()
    }

    fun deleteEvidence(evidence: Evidence) {
        viewModelScope.launch {
            try {
                deleteFiles(evidence)
                evidenceRepository.delete(evidence.id)
                Log.i(TAG, "Deleted: ${evidence.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Delete failed: ${e.message}")
            }
        }
    }

    fun deleteSelected() {
        val ids = _selectedIds.value.toSet()
        viewModelScope.launch {
            try {
                val list = evidenceList.value.filter { it.id in ids }
                list.forEach { evidence ->
                    deleteFiles(evidence)
                    evidenceRepository.delete(evidence.id)
                }
                Log.i(TAG, "Batch deleted: ${ids.size} items")
                exitSelectionMode()
            } catch (e: Exception) {
                Log.e(TAG, "Batch delete failed: ${e.message}")
            }
        }
    }

    /**
     * 更新证据的可编辑元数据：标题、标签、备注
     * 不修改媒体路径、哈希、时间戳等取证核心字段
     */
    fun updateMeta(id: String, title: String, tag: String, notes: String) {
        viewModelScope.launch {
            try {
                evidenceRepository.updateMeta(id, title, tag, notes)
                Log.i(TAG, "Meta updated: $id title=$title tag=$tag")
            } catch (e: Exception) {
                Log.e(TAG, "Update meta failed: ${e.message}")
            }
        }
    }

    private fun deleteFiles(evidence: Evidence) {
        if (evidence.mediaPath.isNotEmpty()) {
            File(evidence.mediaPath).takeIf { it.exists() }?.delete()
        }
        if (evidence.voiceNotePath.isNotEmpty()) {
            File(evidence.voiceNotePath).takeIf { it.exists() }?.delete()
        }
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
}