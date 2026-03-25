// app/src/main/java/com/mathsnew/evidencecapture/presentation/main/MainViewModel.kt
// 修改文件 - Kotlin

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
import java.util.Calendar
import javax.inject.Inject

enum class SortOrder { NEWEST, OLDEST }
enum class FilterType { ALL, PHOTO, VIDEO, AUDIO, TEXT }

// 日期范围筛选
enum class DateRange {
    ALL,         // 不限
    THREE_DAYS,  // 三天内
    ONE_WEEK,    // 一周内
    ONE_MONTH,   // 一个月内
    THIS_YEAR,   // 今年
    CUSTOM       // 自定义范围
}

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

    // 日期范围筛选
    private val _dateRange = MutableStateFlow(DateRange.ALL)
    val dateRange: StateFlow<DateRange> = _dateRange

    // 自定义日期范围（毫秒时间戳，null 表示不限）
    private val _customDateStart = MutableStateFlow<Long?>(null)
    val customDateStart: StateFlow<Long?> = _customDateStart

    private val _customDateEnd = MutableStateFlow<Long?>(null)
    val customDateEnd: StateFlow<Long?> = _customDateEnd

// 供语言切换按钮判断录制状态
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    val evidenceList: StateFlow<List<Evidence>> = combine(
        evidenceRepository.getAll(),
        _searchQuery,
        _filterType,
        _sortOrder,
        _dateRange
    ) { list, query, filter, sort, dateRange ->
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
                else {
                    val inTitle = evidence.title.contains(query, ignoreCase = true)
                    val inTag   = evidence.tag.contains(query, ignoreCase = true)
                    val inNotes = evidence.notes.contains(query, ignoreCase = true)
                    inTitle || inTag || inNotes
                }
            }
            .filter { evidence ->
                // 日期范围筛选
                val startMs = dateRangeStartMs(dateRange)
                val endMs   = if (dateRange == DateRange.CUSTOM) _customDateEnd.value else null
                when {
                    dateRange == DateRange.ALL    -> true
                    dateRange == DateRange.CUSTOM -> {
                        val s = _customDateStart.value
                        val e = _customDateEnd.value
                        when {
                            s != null && e != null ->
                                evidence.createdAt in s..e
                            s != null -> evidence.createdAt >= s
                            e != null -> evidence.createdAt <= e
                            else      -> true
                        }
                    }
                    startMs != null -> evidence.createdAt >= startMs
                    else            -> true
                }
            }
            .let { filtered ->
                when (sort) {
                    SortOrder.NEWEST -> filtered.sortedByDescending { it.createdAt }
                    SortOrder.OLDEST -> filtered.sortedBy { it.createdAt }
                }
            }
    }.stateIn(
        scope          = viewModelScope,
        started        = SharingStarted.WhileSubscribed(5000),
        initialValue   = emptyList()
    )

    // 根据枚举计算开始时间戳
    private fun dateRangeStartMs(range: DateRange): Long? {
        val cal = Calendar.getInstance()
        return when (range) {
            DateRange.ALL, DateRange.CUSTOM -> null
            DateRange.THREE_DAYS -> {
                cal.add(Calendar.DAY_OF_YEAR, -3); cal.timeInMillis
            }
            DateRange.ONE_WEEK -> {
                cal.add(Calendar.DAY_OF_YEAR, -7); cal.timeInMillis
            }
            DateRange.ONE_MONTH -> {
                cal.add(Calendar.MONTH, -1); cal.timeInMillis
            }
            DateRange.THIS_YEAR -> {
                cal.set(Calendar.MONTH, 0)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.timeInMillis
            }
        }
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setFilterType(type: FilterType) { _filterType.value = type }
    fun setDateRange(range: DateRange) { _dateRange.value = range }
    fun setCustomDateStart(ms: Long?) { _customDateStart.value = ms }
    fun setCustomDateEnd(ms: Long?) { _customDateEnd.value = ms }
    fun toggleSortOrder() {
        _sortOrder.value = if (_sortOrder.value == SortOrder.NEWEST)
            SortOrder.OLDEST else SortOrder.NEWEST
    }

    fun enterSelectionMode(evidenceId: String) {
        _isSelectionMode.value = true
        _selectedIds.value     = setOf(evidenceId)
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedIds.value     = emptySet()
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
                // 软删除：移入回收站，不删除文件
                evidenceRepository.moveToTrash(evidence.id)
                Log.i(TAG, "Moved to trash: ${evidence.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Move to trash failed: ${e.message}")
            }
        }
    }

    fun deleteSelected() {
        val ids = _selectedIds.value.toSet()
        viewModelScope.launch {
            try {
                ids.forEach { evidenceRepository.moveToTrash(it) }
                Log.i(TAG, "Batch moved to trash: ${ids.size} items")
                exitSelectionMode()
            } catch (e: Exception) {
                Log.e(TAG, "Batch move to trash failed: ${e.message}")
            }
        }
    }

    fun updateMeta(id: String, title: String, tag: String, notes: String) {
        viewModelScope.launch {
            try {
                evidenceRepository.updateMeta(id, title, tag, notes)
                Log.i(TAG, "Meta updated: $id")
            } catch (e: Exception) {
                Log.e(TAG, "Update meta failed: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
}