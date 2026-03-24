// app/src/main/java/com/mathsnew/evidencecapture/presentation/main/MainScreen.kt
// 修改文件 - Kotlin

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// ── 主题色 ────────────────────────────────────────────────────
private val NavyDeep   = Color(0xFF1A237E)
private val NavyMid    = Color(0xFF283593)
private val AccentBlue = Color(0xFF42A5F5)
private val AccentCyan = Color(0xFF00E5FF)
private val ColorPhoto = Color(0xFF5C6BC0)
private val ColorVideo = Color(0xFFE53935)
private val ColorAudio = Color(0xFF8E24AA)
private val ColorText  = Color(0xFF00897B)

private fun mediaIconColor(type: MediaType) = when (type) {
    MediaType.PHOTO -> Color(0xFF42A5F5)
    MediaType.VIDEO -> Color(0xFFEF5350)
    MediaType.AUDIO -> Color(0xFFAB47BC)
    MediaType.TEXT  -> Color(0xFF26A69A)
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
    val searchInNotes   by viewModel.searchInNotes.collectAsState()
    val customStart     by viewModel.customDateStart.collectAsState()
    val customEnd       by viewModel.customDateEnd.collectAsState()

    var showDeleteConfirm      by remember { mutableStateOf(false) }
    var pendingDeleteEvidence  by remember { mutableStateOf<Evidence?>(null) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var showEditDialog         by remember { mutableStateOf(false) }
    var editingEvidence        by remember { mutableStateOf<Evidence?>(null) }
    var showLanguagePicker     by remember { mutableStateOf(false) }
    var showDateFilter         by remember { mutableStateOf(false) }
    var isGridView             by remember { mutableStateOf(true) }  // 九宫格/列表切换

    val presetTags = listOf(
        stringResource(R.string.tag_rent),   stringResource(R.string.tag_traffic),
        stringResource(R.string.tag_workplace), stringResource(R.string.tag_family),
        stringResource(R.string.tag_fraud),  stringResource(R.string.tag_safety),
        stringResource(R.string.tag_property), stringResource(R.string.tag_other)
    )
    val groupedList = remember(evidenceList) { groupByDate(evidenceList) }

    // ── 删除确认弹窗 ──────────────────────────────────────────
    if (showDeleteConfirm && pendingDeleteEvidence != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false; pendingDeleteEvidence = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text  = { Text(stringResource(R.string.delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteEvidence?.let { viewModel.deleteEvidence(it) }
                    showDeleteConfirm = false; pendingDeleteEvidence = null
                }) { Text(stringResource(R.string.delete_confirm),
                    color = MaterialTheme.colorScheme.error) }
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

    // ── 日期筛选弹窗 ──────────────────────────────────────────
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
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(NavyDeep, NavyMid)))
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                if (isSelectionMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.exitSelectionMode() }) {
                            Icon(Icons.Default.Close, null, tint = Color.White)
                        }
                        Text(stringResource(R.string.main_selected_count, selectedIds.size),
                            color = Color.White, fontWeight = FontWeight.Bold,
                            fontSize = 18.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.selectAll() }) {
                            Text(stringResource(R.string.main_select_all), color = AccentCyan)
                        }
                        IconButton(onClick = { showBatchDeleteConfirm = true },
                            enabled = selectedIds.isNotEmpty()) {
                            Icon(Icons.Default.Delete, null, tint = Color.White)
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.main_title),
                            color = Color.White, fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp, modifier = Modifier.weight(1f))
                        // 九宫格/列表视图切换
                        IconButton(onClick = { isGridView = !isGridView }) {
                            Icon(
                                imageVector = if (isGridView) Icons.Default.ViewList
                                    else Icons.Default.GridView,
                                contentDescription = null, tint = Color.White
                            )
                        }
                        // 日期筛选（有筛选时图标变色提示）
                        IconButton(onClick = { showDateFilter = true }) {
                            Icon(Icons.Default.DateRange,
                                contentDescription = null,
                                tint = if (dateRange != DateRange.ALL) AccentCyan
                                    else Color.White)
                        }
                        IconButton(onClick = { viewModel.toggleSortOrder() }) {
                            Icon(
                                imageVector = if (sortOrder == SortOrder.NEWEST)
                                    Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                contentDescription = null, tint = Color.White
                            )
                        }
                        IconButton(onClick = { showLanguagePicker = true }) {
                            Icon(Icons.Default.Language, null, tint = Color.White)
                        }
                    }
                }
            }
        },
        containerColor = Color(0xFFF0F4FF)
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── 搜索框 ────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .shadow(4.dp, RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White)
            ) {
                Column {
                    OutlinedTextField(
                        value         = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder   = { Text(stringResource(R.string.main_search_hint),
                            color = Color(0xFFAAAAAA)) },
                        leadingIcon   = {
                            Icon(Icons.Default.Search, null, tint = NavyMid)
                        },
                        trailingIcon  = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, null, tint = Color.Gray)
                                }
                            }
                        },
                        modifier   = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors     = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor      = Color.Transparent,
                            unfocusedBorderColor    = Color.Transparent,
                            focusedContainerColor   = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                    // 搜索选项：是否包含备注
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("包含备注", style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF888888))
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(
                            checked         = searchInNotes,
                            onCheckedChange = { viewModel.toggleSearchInNotes() },
                            modifier        = Modifier.height(20.dp)
                        )
                        // 当前日期筛选状态提示
                        if (dateRange != DateRange.ALL) {
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text  = dateRange.label(customStart, customEnd),
                                style = MaterialTheme.typography.labelSmall,
                                color = NavyMid
                            )
                            IconButton(
                                onClick  = {
                                    viewModel.setDateRange(DateRange.ALL)
                                    viewModel.setCustomDateStart(null)
                                    viewModel.setCustomDateEnd(null)
                                },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Default.Close, null,
                                    modifier = Modifier.size(14.dp), tint = NavyMid)
                            }
                        }
                    }
                }
            }

            // ── 类型筛选标签 ──────────────────────────────────
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(FilterType.values().toList()) { type ->
                    val selected = filterType == type
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (selected)
                                    Brush.horizontalGradient(listOf(AccentBlue, AccentCyan))
                                else
                                    Brush.horizontalGradient(listOf(Color.White, Color.White))
                            )
                            .border(1.dp,
                                if (selected) Color.Transparent else Color(0xFFDDE3F0),
                                RoundedCornerShape(20.dp))
                            .combinedClickable(onClick = { viewModel.setFilterType(type) })
                            .padding(horizontal = 18.dp, vertical = 7.dp)
                    ) {
                        Text(type.displayName(),
                            color = if (selected) Color.White else Color(0xFF555577),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── 四个操作按钮 ──────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                    ActionButton(icon = icon, label = label, color = color,
                        onClick = action, modifier = Modifier.weight(1f))
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ── 证据列表（九宫格 or 滑动列表）────────────────
            if (evidenceList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f),
                    contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FolderOpen, null,
                            modifier = Modifier.size(64.dp), tint = Color(0xFFBBC5E0))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty() ||
                                filterType != FilterType.ALL ||
                                dateRange != DateRange.ALL)
                                stringResource(R.string.main_empty_filtered)
                            else stringResource(R.string.main_empty),
                            color = Color(0xFFAAB4CC)
                        )
                    }
                }
            } else {
                if (isGridView) {
                    // ── 九宫格视图 ────────────────────────────
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        groupedList.forEach { (dateLabel, items) ->
                            stickyHeader(key = "header_$dateLabel") {
                                Box(
                                    modifier = Modifier.fillMaxWidth()
                                        .background(Color(0xFFF0F4FF))
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(dateLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold, color = NavyMid)
                                }
                            }
                            val rows = items.chunked(3)
                            items(rows, key = { row -> "row_${row.first().id}" }) { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                                            onLongClick = {
                                                viewModel.enterSelectionMode(evidence.id)
                                            },
                                            onEdit   = { editingEvidence = evidence; showEditDialog = true },
                                            onDelete = { pendingDeleteEvidence = evidence; showDeleteConfirm = true }
                                        )
                                    }
                                    repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                } else {
                    // ── 滑动列表视图 ──────────────────────────
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        contentPadding = PaddingValues(
                            horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        groupedList.forEach { (dateLabel, items) ->
                            stickyHeader(key = "header_list_$dateLabel") {
                                Box(
                                    modifier = Modifier.fillMaxWidth()
                                        .background(Color(0xFFF0F4FF))
                                        .padding(horizontal = 4.dp, vertical = 6.dp)
                                ) {
                                    Text(dateLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold, color = NavyMid)
                                }
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
            }
        }
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
    val context = LocalContext.current
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
                        modifier          = Modifier
                            .fillMaxWidth()
                            .combinedClickable(onClick = { onRangeSelected(range) })
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentRange == range,
                            onClick  = { onRangeSelected(range) }
                        )
                        Text(label, modifier = Modifier.weight(1f))
                    }
                }

                // 自定义范围选择器
                if (currentRange == DateRange.CUSTOM) {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(4.dp))
                    // 开始日期
                    Row(
                        modifier          = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("开始：",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(48.dp))
                        OutlinedButton(
                            onClick = {
                                val cal = Calendar.getInstance()
                                customStart?.let { cal.timeInMillis = it }
                                DatePickerDialog(context,
                                    { _, y, m, d ->
                                        val c = Calendar.getInstance()
                                        c.set(y, m, d, 0, 0, 0)
                                        onCustomStart(c.timeInMillis)
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
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
                    // 结束日期
                    Row(
                        modifier          = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("结束：",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(48.dp))
                        OutlinedButton(
                            onClick = {
                                val cal = Calendar.getInstance()
                                customEnd?.let { cal.timeInMillis = it }
                                DatePickerDialog(context,
                                    { _, y, m, d ->
                                        val c = Calendar.getInstance()
                                        c.set(y, m, d, 23, 59, 59)
                                        onCustomEnd(c.timeInMillis)
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
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
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("确定") }
        }
    )
}

// ── 语言选择对话框 ────────────────────────────────────────────
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
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) NavyDeep
                                else MaterialTheme.colorScheme.onSurface)
                        if (isSelected) {
                            Icon(Icons.Default.Check, null,
                                tint = NavyDeep, modifier = Modifier.size(18.dp))
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

// ── 九宫格单元格 ──────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EvidenceGridCell(
    evidence: Evidence, isSelected: Boolean, modifier: Modifier = Modifier,
    onClick: () -> Unit, onLongClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit
) {
    val iconColor = mediaIconColor(evidence.mediaType)
    var showMenu  by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.aspectRatio(1f).padding(2.dp)
            .clip(RoundedCornerShape(10.dp)).background(Color.White)
            .then(if (isSelected) Modifier.border(2.5.dp, iconColor, RoundedCornerShape(10.dp))
                else Modifier)
            .combinedClickable(
                onClick     = onClick,
                onLongClick = { if (!isSelected) showMenu = true; onLongClick() }
            )
    ) {
        EvidenceThumbnail(evidence, iconColor, Modifier.fillMaxSize())
        Box(modifier = Modifier.align(Alignment.TopStart).padding(4.dp).size(22.dp)
            .clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center) {
            Icon(evidence.mediaType.icon(), null, tint = Color.White,
                modifier = Modifier.size(13.dp))
        }
        if (isSelected) {
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp)
                .clip(CircleShape).background(iconColor),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 5.dp, vertical = 3.dp)) {
            Column {
                if (evidence.title.isNotEmpty()) {
                    Text(evidence.title, color = Color.White, fontSize = 10.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                }
                Text(timeFmt.format(Date(evidence.createdAt)),
                    color = Color.White.copy(alpha = 0.8f), fontSize = 9.sp)
            }
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
    val buttonAreaWidth   = 152.dp
    val buttonAreaWidthPx = with(LocalDensity.current) { buttonAreaWidth.toPx() }
    val offsetX           = remember { Animatable(0f) }
    val scope             = rememberCoroutineScope()
    val isOpen by remember { derivedStateOf { offsetX.value < -buttonAreaWidthPx * 0.5f } }

    Box(modifier = Modifier.fillMaxWidth()) {
        // 右侧编辑/删除按钮
        Row(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(72.dp).height(72.dp)
                .clip(RoundedCornerShape(16.dp)).background(Color(0xFF1976D2))
                .combinedClickable(onClick = {
                    scope.launch { offsetX.animateTo(0f, spring()) }
                    onEdit()
                }), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Edit, null, tint = Color.White,
                        modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.detail_notes_edit),
                        color = Color.White, fontSize = 11.sp)
                }
            }
            Box(modifier = Modifier.width(72.dp).height(72.dp)
                .clip(RoundedCornerShape(16.dp)).background(Color(0xFFE53935))
                .combinedClickable(onClick = {
                    scope.launch { offsetX.animateTo(0f, spring()) }
                    onDelete()
                }), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Delete, null, tint = Color.White,
                        modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.delete_confirm),
                        color = Color.White, fontSize = 11.sp)
                }
            }
        }

        // 可滑动的卡片主体
        EvidenceListCard(
            evidence   = evidence,
            isSelected = isSelected,
            modifier   = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state       = rememberDraggableState { delta ->
                        scope.launch {
                            val newOffset = (offsetX.value + delta).coerceIn(-buttonAreaWidthPx, 0f)
                            offsetX.snapTo(newOffset)
                        }
                    },
                    onDragStopped = {
                        scope.launch {
                            if (offsetX.value < -buttonAreaWidthPx * 0.4f)
                                offsetX.animateTo(-buttonAreaWidthPx, spring())
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

// ── 列表卡片样式 ──────────────────────────────────────────────
@Composable
private fun EvidenceListCard(
    evidence: Evidence, isSelected: Boolean, modifier: Modifier = Modifier
) {
    val iconColor = mediaIconColor(evidence.mediaType)
    val bgColor   = if (isSelected) Color(0xFFE8EDFF) else Color.White

    Box(modifier = modifier.fillMaxWidth()
        .shadow(if (isSelected) 2.dp else 5.dp, RoundedCornerShape(16.dp),
            ambientColor = iconColor.copy(alpha = 0.15f),
            spotColor    = iconColor.copy(alpha = 0.2f))
        .clip(RoundedCornerShape(16.dp)).background(bgColor)
        .then(if (isSelected) Modifier.border(2.dp, iconColor.copy(alpha = 0.5f),
            RoundedCornerShape(16.dp)) else Modifier)
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(46.dp)
                .shadow(4.dp, CircleShape, spotColor = iconColor.copy(alpha = 0.3f))
                .clip(CircleShape)
                .background(Brush.radialGradient(
                    listOf(iconColor.copy(alpha = 0.25f), iconColor.copy(alpha = 0.08f))))
                .border(1.5.dp, iconColor.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center) {
                if (isSelected)
                    Icon(Icons.Default.CheckCircle, null, tint = iconColor, modifier = Modifier.size(26.dp))
                else
                    Icon(evidence.mediaType.icon(), null, tint = iconColor, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(if (evidence.title.isNotEmpty()) evidence.title else evidence.id,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, color = Color(0xFF1A237E))
                if (evidence.tag.isNotEmpty())
                    Text(evidence.tag, fontSize = 11.sp, color = iconColor, fontWeight = FontWeight.Medium)
                if (evidence.notes.isNotEmpty())
                    Text(evidence.notes, fontSize = 11.sp, color = Color(0xFF888888),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(evidence.createdAt)),
                    fontSize = 11.sp, color = Color(0xFFAAB4CC))
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color(0xFFCCD2E8), modifier = Modifier.size(20.dp))
        }
    }
}

// ── 缩略图 ────────────────────────────────────────────────────
@Composable
private fun EvidenceThumbnail(evidence: Evidence, iconColor: Color, modifier: Modifier = Modifier) {
    when (evidence.mediaType) {
        MediaType.PHOTO -> AsyncImage(evidence.mediaPath, null, contentScale = ContentScale.Crop, modifier = modifier)
        MediaType.VIDEO -> VideoThumbnail(evidence.mediaPath, modifier)
        MediaType.AUDIO -> Box(modifier = modifier.background(
            Brush.radialGradient(listOf(Color(0xFFCE93D8), Color(0xFF7B1FA2)))),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Mic, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(36.dp))
        }
        MediaType.TEXT  -> Box(modifier = modifier.background(
            Brush.radialGradient(listOf(Color(0xFF80CBC4), Color(0xFF00695C)))),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Edit, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(36.dp))
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
        Image(bitmap!!.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = modifier)
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.PlayCircle, null, tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(32.dp))
        }
    } else {
        Box(modifier = modifier.background(Brush.radialGradient(listOf(Color(0xFFEF9A9A), Color(0xFFB71C1C)))),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Videocam, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(36.dp))
        }
    }
}

// ── 编辑弹窗 ──────────────────────────────────────────────────
@Composable
private fun EditEvidenceDialog(
    evidence: Evidence, presetTags: List<String>,
    onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit
) {
    var title by remember { mutableStateOf(evidence.title.ifBlank { evidence.id }) }
    var tag   by remember { mutableStateOf(evidence.tag) }
    var notes by remember { mutableStateOf(evidence.notes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_title)) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
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
                    placeholder = { Text(stringResource(R.string.edit_field_title_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth())
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
                    placeholder = { Text(stringResource(R.string.edit_field_tag_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.edit_field_notes)) },
                    placeholder = { Text(stringResource(R.string.edit_field_notes_hint)) },
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

// ── 操作按钮 ──────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActionButton(
    icon: ImageVector, label: String, color: Color,
    onClick: () -> Unit, modifier: Modifier = Modifier
) {
    Box(modifier = modifier.shadow(6.dp, RoundedCornerShape(20.dp))
        .clip(RoundedCornerShape(20.dp))
        .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.92f), color)))
        .combinedClickable(onClick = onClick).padding(vertical = 14.dp),
        contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.fillMaxWidth(0.7f).height(4.dp).align(Alignment.TopCenter)
            .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
            .background(Color.White.copy(alpha = 0.35f)))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(38.dp).clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                Icon(icon, label, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
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