// app/src/main/java/com/mathsnew/evidencecapture/presentation/main/MainScreen.kt
// 修改文件 - Kotlin（浅色简约风格重设计）

package com.mathsnew.evidencecapture.presentation.main

import android.app.Activity
import android.app.DatePickerDialog
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.mathsnew.evidencecapture.R
import com.mathsnew.evidencecapture.domain.model.Evidence
import com.mathsnew.evidencecapture.domain.model.MediaType
import com.mathsnew.evidencecapture.util.LocaleHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// ── 浅色简约配色 ──────────────────────────────────────────────
private val BgPage       = Color(0xFFF8F9FA)   // 页面背景
private val BgCard       = Color.White          // 卡片背景
private val BgBar        = Color.White          // 顶部栏背景
private val TextPrimary  = Color(0xFF1A1A2E)    // 主文字
private val TextSecond   = Color(0xFF6B7280)    // 次要文字
private val TextHint     = Color(0xFFB0B8C4)    // 提示文字
private val DividerColor = Color(0xFFF0F1F3)    // 分割线
private val BorderColor  = Color(0xFFE5E7EB)    // 边框色
private val AccentMain   = Color(0xFF4F6EF7)    // 主强调色（蓝紫）
private val AccentLight  = Color(0xFFEEF1FF)    // 浅强调背景

// 四种取证类型的配色（浅色风格下用柔和色）
private val ColorPhoto   = Color(0xFF4F6EF7)    // 蓝紫
private val ColorVideo   = Color(0xFFEF5350)    // 红
private val ColorAudio   = Color(0xFF9C27B0)    // 紫
private val ColorText    = Color(0xFF009688)    // 青绿

private fun mediaColor(type: MediaType) = when (type) {
    MediaType.PHOTO -> Color(0xFF4F6EF7)
    MediaType.VIDEO -> Color(0xFFEF5350)
    MediaType.AUDIO -> Color(0xFF9C27B0)
    MediaType.TEXT  -> Color(0xFF009688)
}

private fun mediaColorLight(type: MediaType) = when (type) {
    MediaType.PHOTO -> Color(0xFFEEF1FF)
    MediaType.VIDEO -> Color(0xFFFFEBEE)
    MediaType.AUDIO -> Color(0xFFF3E5F5)
    MediaType.TEXT  -> Color(0xFFE0F2F1)
}

// ── 视图模式 ──────────────────────────────────────────────────
private enum class ViewMode { GRID, LIST, DENSE }
private fun ViewMode.next() = when (this) {
    ViewMode.GRID  -> ViewMode.LIST
    ViewMode.LIST  -> ViewMode.DENSE
    ViewMode.DENSE -> ViewMode.GRID
}

private val dateGroupFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
private val timeFmt      = SimpleDateFormat("HH:mm", Locale.getDefault())

private fun groupByDate(list: List<Evidence>): List<Pair<String, List<Evidence>>> =
    list.groupBy { dateGroupFmt.format(Date(it.createdAt)) }
        .entries.map { it.key to it.value }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    onNavigateCapturePhoto: () -> Unit,
    onNavigateRecordVideo:  () -> Unit,
    onNavigateRecordAudio:  () -> Unit,
    onNavigateTextNote:     () -> Unit,
    onNavigateDetail:       (String) -> Unit,
    onNavigateTrash:        () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel()
) {
    val context         = LocalContext.current
    val activity        = context as? Activity
    val evidenceList    by viewModel.evidenceList.collectAsState()
    val searchQuery     by viewModel.searchQuery.collectAsState()
    val filterType      by viewModel.filterType.collectAsState()
    val sortOrder       by viewModel.sortOrder.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedIds     by viewModel.selectedIds.collectAsState()
    val dateRange       by viewModel.dateRange.collectAsState()
    val customStart     by viewModel.customDateStart.collectAsState()
    val customEnd       by viewModel.customDateEnd.collectAsState()

    var showDeleteConfirm      by remember { mutableStateOf(false) }
    var pendingDeleteEvidence  by remember { mutableStateOf<Evidence?>(null) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var showEditDialog         by remember { mutableStateOf(false) }
    var editingEvidence        by remember { mutableStateOf<Evidence?>(null) }
    var showLanguagePicker     by remember { mutableStateOf(false) }
    var showDateFilter         by remember { mutableStateOf(false) }
    var viewMode               by remember { mutableStateOf(ViewMode.GRID) }

    val presetTags = listOf(
        stringResource(R.string.tag_rent),      stringResource(R.string.tag_traffic),
        stringResource(R.string.tag_workplace), stringResource(R.string.tag_family),
        stringResource(R.string.tag_fraud),     stringResource(R.string.tag_safety),
        stringResource(R.string.tag_property),  stringResource(R.string.tag_other)
    )
    val groupedList = remember(evidenceList) { groupByDate(evidenceList) }

    // ── 弹窗 ──────────────────────────────────────────────────
    if (showDeleteConfirm && pendingDeleteEvidence != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false; pendingDeleteEvidence = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text  = { Text(stringResource(R.string.delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteEvidence?.let { viewModel.deleteEvidence(it) }
                    showDeleteConfirm = false; pendingDeleteEvidence = null
                }) { Text(stringResource(R.string.delete_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false; pendingDeleteEvidence = null }) {
                    Text(stringResource(R.string.delete_cancel))
                }
            }
        )
    }
    if (showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text(stringResource(R.string.batch_delete_title)) },
            text  = { Text(stringResource(R.string.batch_delete_message, selectedIds.size)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteSelected(); showBatchDeleteConfirm = false }) {
                    Text(stringResource(R.string.delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text(stringResource(R.string.delete_cancel))
                }
            }
        )
    }
    if (showEditDialog && editingEvidence != null) {
        EditEvidenceDialog(
            evidence   = editingEvidence!!,
            presetTags = presetTags,
            onDismiss  = { showEditDialog = false; editingEvidence = null },
            onConfirm  = { title, tag, notes ->
                viewModel.updateMeta(editingEvidence!!.id, title, tag, notes)
                showEditDialog = false; editingEvidence = null
            }
        )
    }
    if (showLanguagePicker) {
        LanguagePickerDialog(
            currentCode = LocaleHelper.getSavedLanguage(context),
            onSelect    = { item ->
                showLanguagePicker = false
                LocaleHelper.setLocale(context, item)
                activity?.recreate()
            },
            onDismiss = { showLanguagePicker = false }
        )
    }
    if (showDateFilter) {
        DateFilterDialog(
            currentRange    = dateRange,
            customStart     = customStart,
            customEnd       = customEnd,
            onRangeSelected = { viewModel.setDateRange(it) },
            onCustomStart   = { viewModel.setCustomDateStart(it) },
            onCustomEnd     = { viewModel.setCustomDateEnd(it) },
            onDismiss       = { showDateFilter = false }
        )
    }

    Scaffold(
        containerColor = BgPage,
        topBar = {
            // 浅色顶部栏：白底，细底线，无渐变
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgBar)
                    .statusBarsPadding()
                    .combinedClickable(onClick = {}, enabled = false)
            ) {
                if (isSelectionMode) {
                    // 多选模式：蓝紫色主题
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .background(AccentMain)
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.exitSelectionMode() }) {
                            Icon(Icons.Default.Close, null, tint = Color.White)
                        }
                        Text(
                            stringResource(R.string.main_selected_count, selectedIds.size),
                            color = Color.White, fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp, modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.selectAll() }) {
                            Text(stringResource(R.string.main_select_all),
                                color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                        }
                        IconButton(onClick = { showBatchDeleteConfirm = true },
                            enabled = selectedIds.isNotEmpty()) {
                            Icon(Icons.Default.Delete, null, tint = Color.White)
                        }
                    }
                } else {
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 14.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // App 标题
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text       = stringResource(R.string.main_title),
                                color      = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize   = 22.sp
                            )
                            Text(
                                text     = "${evidenceList.size} 条证据",
                                color    = TextHint,
                                fontSize = 12.sp
                            )
                        }
                        // 操作图标
                        IconButton(onClick = { viewMode = viewMode.next() }) {
                            Icon(
                                imageVector = when (viewMode) {
                                    ViewMode.GRID  -> Icons.Default.ViewList
                                    ViewMode.LIST  -> Icons.Default.GridView
                                    ViewMode.DENSE -> Icons.Default.Apps
                                },
                                contentDescription = null, tint = TextSecond
                            )
                        }
                        IconButton(onClick = { showDateFilter = true }) {
                            Icon(
                                Icons.Default.DateRange, null,
                                tint = if (dateRange != DateRange.ALL) AccentMain else TextSecond
                            )
                        }
                        IconButton(onClick = { viewModel.toggleSortOrder() }) {
                            Icon(
                                imageVector = if (sortOrder == SortOrder.NEWEST)
                                    Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                contentDescription = null, tint = TextSecond
                            )
                        }
                        IconButton(onClick = { showLanguagePicker = true }) {
                            Icon(Icons.Default.Language, null, tint = TextSecond)
                        }
                        IconButton(onClick = onNavigateTrash) {
                            Icon(Icons.Default.DeleteOutline, null, tint = TextSecond)
                        }
                    }
                }
                // 细分割线
                HorizontalDivider(color = DividerColor, thickness = 0.8.dp)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── 搜索框 ────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF1F3F8))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, null,
                    tint = TextHint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text(stringResource(R.string.main_search_hint),
                            color = TextHint, fontSize = 14.sp)
                    }
                    androidx.compose.foundation.text.BasicTextField(
                        value         = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        singleLine    = true,
                        textStyle     = androidx.compose.ui.text.TextStyle(
                            color    = TextPrimary,
                            fontSize = 14.sp
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") },
                        modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Clear, null,
                            tint = TextHint, modifier = Modifier.size(16.dp))
                    }
                }
                // 日期筛选标记
                if (dateRange != DateRange.ALL) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AccentLight)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(dateRange.label(customStart, customEnd),
                            fontSize = 11.sp, color = AccentMain)
                        Spacer(Modifier.width(2.dp))
                        Icon(Icons.Default.Close, null,
                            modifier = Modifier
                                .size(12.dp)
                                .combinedClickable(onClick = {
                                    viewModel.setDateRange(DateRange.ALL)
                                    viewModel.setCustomDateStart(null)
                                    viewModel.setCustomDateEnd(null)
                                }),
                            tint = AccentMain)
                    }
                }
            }

            // ── 类型筛选标签 ──────────────────────────────────
            LazyRow(
                modifier            = Modifier.fillMaxWidth(),
                contentPadding      = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(FilterType.values().toList()) { type ->
                    val selected = filterType == type
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selected) AccentMain else Color.Transparent)
                            .border(
                                width = 1.dp,
                                color = if (selected) AccentMain else BorderColor,
                                shape = RoundedCornerShape(20.dp)
                            )
                            .combinedClickable(onClick = { viewModel.setFilterType(type) })
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text       = type.displayName(),
                            color      = if (selected) Color.White else TextSecond,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize   = 13.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── 四个操作按钮 ──────────────────────────────────
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(
                    Triple(Icons.Default.CameraAlt, stringResource(R.string.main_action_photo),
                        ColorPhoto to onNavigateCapturePhoto),
                    Triple(Icons.Default.Videocam, stringResource(R.string.main_action_video),
                        ColorVideo to onNavigateRecordVideo),
                    Triple(Icons.Default.Mic, stringResource(R.string.main_action_audio),
                        ColorAudio to onNavigateRecordAudio),
                    Triple(Icons.Default.Edit, stringResource(R.string.main_action_text),
                        ColorText to onNavigateTextNote)
                ).forEach { (icon, label, colorAction) ->
                    val (color, action) = colorAction
                    ActionButton(
                        icon    = icon,
                        label   = label,
                        color   = color,
                        onClick = action,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── 证据列表 ──────────────────────────────────────
            if (evidenceList.isEmpty()) {
                Box(
                    modifier         = Modifier.fillMaxSize().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FolderOpen, null,
                            modifier = Modifier.size(56.dp), tint = TextHint)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty() ||
                                filterType != FilterType.ALL ||
                                dateRange != DateRange.ALL)
                                stringResource(R.string.main_empty_filtered)
                            else stringResource(R.string.main_empty),
                            color = TextHint, fontSize = 14.sp
                        )
                    }
                }
            } else {
                when (viewMode) {
                    ViewMode.GRID -> {
                        LazyColumn(
                            modifier       = Modifier.fillMaxSize().weight(1f),
                            contentPadding = PaddingValues(
                                start = 16.dp, end = 16.dp, bottom = 16.dp)
                        ) {
                            groupedList.forEach { (dateLabel, items) ->
                                stickyHeader(key = "hg_$dateLabel") {
                                    DateHeader(dateLabel)
                                }
                                val rows = items.chunked(3)
                                items(rows, key = { row -> "rg_${row.first().id}" }) { row ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        row.forEach { evidence ->
                                            EvidenceGridCell(
                                                evidence    = evidence,
                                                isSelected  = selectedIds.contains(evidence.id),
                                                modifier    = Modifier.weight(1f),
                                                onClick     = {
                                                    if (isSelectionMode)
                                                        viewModel.toggleSelection(evidence.id)
                                                    else onNavigateDetail(evidence.id)
                                                },
                                                onLongClick = { viewModel.enterSelectionMode(evidence.id) },
                                                onEdit      = { editingEvidence = evidence; showEditDialog = true },
                                                onDelete    = { pendingDeleteEvidence = evidence; showDeleteConfirm = true }
                                            )
                                        }
                                        repeat(3 - row.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                    ViewMode.LIST -> {
                        LazyColumn(
                            modifier       = Modifier.fillMaxSize().weight(1f),
                            contentPadding = PaddingValues(
                                horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            groupedList.forEach { (dateLabel, items) ->
                                stickyHeader(key = "hl_$dateLabel") {
                                    DateHeader(dateLabel)
                                }
                                items(items, key = { it.id }) { evidence ->
                                    SwipeableEvidenceCard(
                                        evidence         = evidence,
                                        isSelected       = selectedIds.contains(evidence.id),
                                        onEdit           = { editingEvidence = evidence; showEditDialog = true },
                                        onDelete         = { pendingDeleteEvidence = evidence; showDeleteConfirm = true },
                                        onNavigateDetail = { onNavigateDetail(evidence.id) },
                                        onLongClick      = { viewModel.enterSelectionMode(evidence.id) }
                                    )
                                }
                            }
                        }
                    }
                    ViewMode.DENSE -> {
                        DenseGridView(
                            groupedList     = groupedList,
                            selectedIds     = selectedIds,
                            isSelectionMode = isSelectionMode,
                            onItemClick     = { evidence ->
                                if (isSelectionMode)
                                    viewModel.toggleSelection(evidence.id)
                                else onNavigateDetail(evidence.id)
                            },
                            onItemLongClick = { evidence ->
                                viewModel.enterSelectionMode(evidence.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

// ── 日期分组标题 ──────────────────────────────────────────────
@Composable
private fun DateHeader(dateLabel: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgPage)
            .padding(vertical = 6.dp)
    ) {
        Text(
            text       = dateLabel,
            fontSize   = 12.sp,
            color      = TextSecond,
            fontWeight = FontWeight.Medium
        )
    }
}

// ── 日期范围标签文字 ──────────────────────────────────────────
private fun DateRange.label(customStart: Long?, customEnd: Long?): String {
    val fmt = SimpleDateFormat("MM/dd", Locale.getDefault())
    return when (this) {
        DateRange.ALL        -> ""
        DateRange.THREE_DAYS -> "3天内"
        DateRange.ONE_WEEK   -> "一周内"
        DateRange.ONE_MONTH  -> "一个月内"
        DateRange.THIS_YEAR  -> "今年"
        DateRange.CUSTOM     -> {
            val s = customStart?.let { fmt.format(Date(it)) } ?: "~"
            val e = customEnd?.let { fmt.format(Date(it)) } ?: "~"
            "$s ~ $e"
        }
    }
}

// ── 日期筛选弹窗 ──────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DateFilterDialog(
    currentRange:    DateRange,
    customStart:     Long?,
    customEnd:       Long?,
    onRangeSelected: (DateRange) -> Unit,
    onCustomStart:   (Long?) -> Unit,
    onCustomEnd:     (Long?) -> Unit,
    onDismiss:       () -> Unit
) {
    val context    = LocalContext.current
    val displayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("时间筛选") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    DateRange.ALL        to "不限",
                    DateRange.THREE_DAYS to "三天内",
                    DateRange.ONE_WEEK   to "一周内",
                    DateRange.ONE_MONTH  to "一个月内",
                    DateRange.THIS_YEAR  to "今年",
                    DateRange.CUSTOM     to "自定义范围"
                ).forEach { (range, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .combinedClickable(onClick = { onRangeSelected(range) })
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = currentRange == range,
                            onClick = { onRangeSelected(range) })
                        Text(label, modifier = Modifier.weight(1f))
                    }
                }
                if (currentRange == DateRange.CUSTOM) {
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("开始：", style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(48.dp))
                        OutlinedButton(onClick = {
                            val cal = Calendar.getInstance()
                            customStart?.let { cal.timeInMillis = it }
                            DatePickerDialog(context, { _, y, m, d ->
                                val c = Calendar.getInstance()
                                c.set(y, m, d, 0, 0, 0); onCustomStart(c.timeInMillis)
                            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)).show()
                        }, modifier = Modifier.weight(1f)) {
                            Text(customStart?.let { displayFmt.format(Date(it)) } ?: "选择日期",
                                fontSize = 12.sp)
                        }
                        if (customStart != null) {
                            IconButton(onClick = { onCustomStart(null) },
                                modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Clear, null, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("结束：", style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(48.dp))
                        OutlinedButton(onClick = {
                            val cal = Calendar.getInstance()
                            customEnd?.let { cal.timeInMillis = it }
                            DatePickerDialog(context, { _, y, m, d ->
                                val c = Calendar.getInstance()
                                c.set(y, m, d, 23, 59, 59); onCustomEnd(c.timeInMillis)
                            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)).show()
                        }, modifier = Modifier.weight(1f)) {
                            Text(customEnd?.let { displayFmt.format(Date(it)) } ?: "选择日期",
                                fontSize = 12.sp)
                        }
                        if (customEnd != null) {
                            IconButton(onClick = { onCustomEnd(null) },
                                modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Clear, null, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("确定") } }
    )
}

// ── 语言选择弹窗 ──────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LanguagePickerDialog(
    currentCode: String?,
    onSelect:    (LocaleHelper.LanguageItem) -> Unit,
    onDismiss:   () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language_select)) },
        text  = {
            LazyColumn {
                items(LocaleHelper.SUPPORTED_LANGUAGES) { item ->
                    val isSelected = item.code == currentCode
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .combinedClickable(onClick = { onSelect(item) })
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val nameResId = context.resources.getIdentifier(
                            item.nameKey, "string", context.packageName)
                        val displayName = if (nameResId != 0) context.getString(nameResId)
                            else item.language
                        Text(displayName, modifier = Modifier.weight(1f),
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) AccentMain
                                else MaterialTheme.colorScheme.onSurface)
                        if (isSelected) {
                            Icon(Icons.Default.Check, null,
                                tint = AccentMain, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.edit_cancel)) }
        }
    )
}

// ── 操作按钮（简约版）────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActionButton(
    icon:     ImageVector,
    label:    String,
    color:    Color,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier            = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier         = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, tint = color, modifier = Modifier.size(18.dp))
        }
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ── 九宫格单元格（简约版）────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EvidenceGridCell(
    evidence:   Evidence,
    isSelected: Boolean,
    modifier:   Modifier = Modifier,
    onClick:    () -> Unit,
    onLongClick: () -> Unit,
    onEdit:     () -> Unit,
    onDelete:   () -> Unit
) {
    val color    = mediaColor(evidence.mediaType)
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(
                width = if (isSelected) 2.dp else 0.8.dp,
                color = if (isSelected) color else BorderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .combinedClickable(
                onClick     = onClick,
                onLongClick = { if (!isSelected) showMenu = true; onLongClick() }
            )
    ) {
        EvidenceThumbnail(evidence, color, Modifier.fillMaxSize())

        // 媒体类型标记（左上角）
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(5.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(evidence.mediaType.icon(), null,
                tint = Color.White, modifier = Modifier.size(11.dp))
        }

        // 多选勾（右上角）
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, null,
                    tint = Color.White, modifier = Modifier.size(12.dp))
            }
        }

        // 底部时间条
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.38f))
                .padding(horizontal = 5.dp, vertical = 3.dp)
        ) {
            Text(
                text  = timeFmt.format(Date(evidence.createdAt)),
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 9.sp
            )
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text        = { Text(stringResource(R.string.detail_notes_edit)) },
                leadingIcon = { Icon(Icons.Default.Edit, null) },
                onClick     = { showMenu = false; onEdit() }
            )
            DropdownMenuItem(
                text        = { Text(stringResource(R.string.delete_confirm),
                    color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Default.Delete, null,
                    tint = MaterialTheme.colorScheme.error) },
                onClick     = { showMenu = false; onDelete() }
            )
        }
    }
}

// ── 滑动列表卡片 ──────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeableEvidenceCard(
    evidence:         Evidence,
    isSelected:       Boolean,
    onEdit:           () -> Unit,
    onDelete:         () -> Unit,
    onNavigateDetail: (String) -> Unit,
    onLongClick:      () -> Unit
) {
    val bw   = 148.dp
    val bwPx = with(LocalDensity.current) { bw.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope   = rememberCoroutineScope()
    val isOpen  by remember { derivedStateOf { offsetX.value < -bwPx * 0.5f } }

    Box(modifier = Modifier.fillMaxWidth()) {
        // 右侧操作按钮
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.width(70.dp).height(68.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF4F6EF7).copy(alpha = 0.1f))
                    .border(1.dp, Color(0xFF4F6EF7).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .combinedClickable(onClick = {
                        scope.launch { offsetX.animateTo(0f, spring()) }; onEdit()
                    }),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Edit, null,
                        tint = Color(0xFF4F6EF7), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.height(2.dp))
                    Text(stringResource(R.string.detail_notes_edit),
                        color = Color(0xFF4F6EF7), fontSize = 11.sp)
                }
            }
            Box(
                modifier = Modifier.width(70.dp).height(68.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFEF5350).copy(alpha = 0.08f))
                    .border(1.dp, Color(0xFFEF5350).copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                    .combinedClickable(onClick = {
                        scope.launch { offsetX.animateTo(0f, spring()) }; onDelete()
                    }),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Delete, null,
                        tint = Color(0xFFEF5350), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.height(2.dp))
                    Text(stringResource(R.string.delete_confirm),
                        color = Color(0xFFEF5350), fontSize = 11.sp)
                }
            }
        }

        // 卡片主体
        EvidenceListCard(
            evidence   = evidence,
            isSelected = isSelected,
            modifier   = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state       = rememberDraggableState { delta ->
                        scope.launch {
                            offsetX.snapTo((offsetX.value + delta).coerceIn(-bwPx, 0f))
                        }
                    },
                    onDragStopped = {
                        scope.launch {
                            if (offsetX.value < -bwPx * 0.4f)
                                offsetX.animateTo(-bwPx, spring())
                            else offsetX.animateTo(0f, spring())
                        }
                    }
                )
                .combinedClickable(
                    onClick     = {
                        if (isOpen) scope.launch { offsetX.animateTo(0f, spring()) }
                        else onNavigateDetail(evidence.id)
                    },
                    onLongClick = onLongClick
                )
        )
    }
}

// ── 列表卡片样式（简约版）────────────────────────────────────
@Composable
private fun EvidenceListCard(
    evidence: Evidence, isSelected: Boolean, modifier: Modifier = Modifier
) {
    val color = mediaColor(evidence.mediaType)
    val light = mediaColorLight(evidence.mediaType)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) light else BgCard)
            .border(
                width = if (isSelected) 1.5.dp else 0.8.dp,
                color = if (isSelected) color.copy(alpha = 0.4f) else BorderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 媒体类型图标
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(light),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected)
                Icon(Icons.Default.CheckCircle, null,
                    tint = color, modifier = Modifier.size(22.dp))
            else
                Icon(evidence.mediaType.icon(), null,
                    tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = if (evidence.title.isNotEmpty()) evidence.title else evidence.id,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
                color      = TextPrimary
            )
            if (evidence.tag.isNotEmpty()) {
                Spacer(Modifier.height(1.dp))
                Text(evidence.tag, fontSize = 11.sp,
                    color = color, fontWeight = FontWeight.Medium)
            }
            if (evidence.notes.isNotEmpty()) {
                Text(evidence.notes, fontSize = 11.sp, color = TextHint,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                    .format(Date(evidence.createdAt)),
                fontSize = 11.sp, color = TextHint
            )
        }
        Icon(Icons.Default.ChevronRight, null,
            tint = BorderColor, modifier = Modifier.size(18.dp))
    }
}

// ── 密集全屏视图 ──────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DenseGridView(
    groupedList:     List<Pair<String, List<Evidence>>>,
    selectedIds:     Set<String>,
    isSelectionMode: Boolean,
    onItemClick:     (Evidence) -> Unit,
    onItemLongClick: (Evidence) -> Unit
) {
    data class DenseItem(
        val evidence:    Evidence,
        val isGroupHead: Boolean,
        val dateLabel:   String
    )
    val flatList = remember(groupedList) {
        groupedList.flatMap { (date, items) ->
            items.mapIndexed { idx, ev -> DenseItem(ev, idx == 0, date) }
        }
    }

    var bubbleEvidenceId by remember { mutableStateOf<String?>(null) }
    var bubbleDateLabel  by remember { mutableStateOf("") }

    LaunchedEffect(bubbleEvidenceId) {
        if (bubbleEvidenceId != null) { delay(1800); bubbleEvidenceId = null }
    }

    val rows = flatList.chunked(3)

    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        items(rows, key = { row -> "d_${row.first().evidence.id}" }) { row ->
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(1.5.dp)
            ) {
                row.forEach { item ->
                    val evidence   = item.evidence
                    val color      = mediaColor(evidence.mediaType)
                    val isSelected = selectedIds.contains(evidence.id)
                    val showBubble = bubbleEvidenceId == evidence.id

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .combinedClickable(
                                onClick     = { onItemClick(evidence) },
                                onLongClick = { onItemLongClick(evidence) }
                            )
                            .then(if (isSelected)
                                Modifier.border(2.dp, color) else Modifier)
                    ) {
                        EvidenceThumbnail(evidence, color, Modifier.fillMaxSize())

                        if (isSelected) {
                            Box(
                                modifier         = Modifier.fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Check, null,
                                    tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                        }

                        // 日期组标记（青色小圆点）
                        if (item.isGroupHead) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(4.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(AccentMain)
                                    .combinedClickable(onClick = {
                                        bubbleEvidenceId = evidence.id
                                        bubbleDateLabel  = item.dateLabel
                                    })
                            )
                            if (showBubble) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(start = 14.dp, top = 2.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.Black.copy(alpha = 0.7f))
                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Text(bubbleDateLabel, color = Color.White,
                                        fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }

                        // 非照片类型标记
                        if (evidence.mediaType != MediaType.PHOTO) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(3.dp)
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(evidence.mediaType.icon(), null,
                                    tint = Color.White, modifier = Modifier.size(9.dp))
                            }
                        }
                    }
                }
                repeat(3 - row.size) {
                    Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                }
            }
            Spacer(Modifier.height(1.5.dp))
        }
    }
}

// ── 缩略图 ────────────────────────────────────────────────────
@Composable
private fun EvidenceThumbnail(
    evidence: Evidence, color: Color, modifier: Modifier = Modifier
) {
    val light = mediaColorLight(evidence.mediaType)
    when (evidence.mediaType) {
        MediaType.PHOTO -> AsyncImage(
            model = evidence.mediaPath, contentDescription = null,
            contentScale = ContentScale.Crop, modifier = modifier)
        MediaType.VIDEO -> VideoThumbnail(evidence.mediaPath, modifier)
        MediaType.AUDIO -> Box(modifier = modifier.background(light),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Mic, null, tint = color.copy(alpha = 0.7f),
                modifier = Modifier.size(32.dp))
        }
        MediaType.TEXT  -> Box(modifier = modifier.background(light),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Edit, null, tint = color.copy(alpha = 0.7f),
                modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun VideoThumbnail(path: String, modifier: Modifier = Modifier) {
    var bitmap by remember(path) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                val r = MediaMetadataRetriever()
                r.setDataSource(path)
                val bmp = r.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                r.release(); bmp
            } catch (e: Exception) { null }
        }
    }
    if (bitmap != null) {
        Image(bitmap!!.asImageBitmap(), null,
            contentScale = ContentScale.Crop, modifier = modifier)
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.PlayCircle, null,
                tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(28.dp))
        }
    } else {
        Box(modifier = modifier.background(mediaColorLight(MediaType.VIDEO)),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Videocam, null,
                tint = mediaColor(MediaType.VIDEO).copy(alpha = 0.6f),
                modifier = Modifier.size(32.dp))
        }
    }
}

// ── 编辑弹窗 ──────────────────────────────────────────────────
@Composable
private fun EditEvidenceDialog(
    evidence:  Evidence,
    presetTags: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var title by remember { mutableStateOf(evidence.title.ifBlank { evidence.id }) }
    var tag   by remember { mutableStateOf(evidence.tag) }
    var notes by remember { mutableStateOf(evidence.notes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_title)) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(stringResource(R.string.edit_label_id, evidence.id),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.edit_label_type, evidence.mediaType.name),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.edit_label_time,
                            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                .format(Date(evidence.createdAt))),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OutlinedTextField(value = title, onValueChange = { title = it },
                    label = { Text(stringResource(R.string.edit_field_title)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
                Text(stringResource(R.string.edit_field_tag_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(presetTags) { preset ->
                        val selected = tag == preset
                        FilterChip(selected = selected,
                            onClick = { tag = if (selected) "" else preset },
                            label = { Text(preset, fontSize = 12.sp) })
                    }
                }
                OutlinedTextField(value = tag, onValueChange = { tag = it },
                    label = { Text(stringResource(R.string.edit_field_tag)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
                OutlinedTextField(value = notes, onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.edit_field_notes)) },
                    minLines = 3, maxLines = 5, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title, tag, notes) }) {
                Text(stringResource(R.string.edit_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.edit_cancel)) }
        }
    )
}

@Composable
private fun FilterType.displayName() = when (this) {
    FilterType.ALL   -> stringResource(R.string.filter_all)
    FilterType.PHOTO -> stringResource(R.string.filter_photo)
    FilterType.VIDEO -> stringResource(R.string.filter_video)
    FilterType.AUDIO -> stringResource(R.string.filter_audio)
    FilterType.TEXT  -> stringResource(R.string.filter_text)
}

private fun MediaType.icon() = when (this) {
    MediaType.PHOTO -> Icons.Default.CameraAlt
    MediaType.VIDEO -> Icons.Default.Videocam
    MediaType.AUDIO -> Icons.Default.Mic
    MediaType.TEXT  -> Icons.Default.Edit
}