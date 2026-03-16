// app/src/main/java/com/mathsnew/evidencecapture/presentation/main/MainScreen.kt
// 修改文件 - Kotlin

package com.mathsnew.evidencecapture.presentation.main

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.mathsnew.evidencecapture.domain.model.Evidence
import com.mathsnew.evidencecapture.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// ── 主题色 ───────────────────────────────────────────────────
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

private val presetTags = listOf(
    "租房纠纷", "交通事故", "职场纠纷", "家庭纠纷",
    "消费欺诈", "人身安全", "财产纠纷", "其他"
)

// 日期分组格式
private val dateGroupFmt = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
private val timeFmt      = SimpleDateFormat("HH:mm", Locale.getDefault())

// 将证据列表按日期分组，返回有序的 (日期字符串 -> 证据列表) 列表
private fun groupByDate(list: List<Evidence>): List<Pair<String, List<Evidence>>> {
    return list
        .groupBy { dateGroupFmt.format(Date(it.createdAt)) }
        .entries
        .map { it.key to it.value }
}

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
    val evidenceList    by viewModel.evidenceList.collectAsState()
    val searchQuery     by viewModel.searchQuery.collectAsState()
    val filterType      by viewModel.filterType.collectAsState()
    val sortOrder       by viewModel.sortOrder.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedIds     by viewModel.selectedIds.collectAsState()

    var showDeleteConfirm      by remember { mutableStateOf(false) }
    var pendingDeleteEvidence  by remember { mutableStateOf<Evidence?>(null) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var showEditDialog         by remember { mutableStateOf(false) }
    var editingEvidence        by remember { mutableStateOf<Evidence?>(null) }

    // 按日期分组后的列表
    val groupedList = remember(evidenceList) { groupByDate(evidenceList) }

    if (showDeleteConfirm && pendingDeleteEvidence != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirm = false
                pendingDeleteEvidence = null
            },
            title = { Text("删除证据") },
            text  = { Text("确定删除这条证据？相关文件将一并删除，此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteEvidence?.let { viewModel.deleteEvidence(it) }
                    showDeleteConfirm = false
                    pendingDeleteEvidence = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    pendingDeleteEvidence = null
                }) { Text("取消") }
            }
        )
    }

    if (showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text("批量删除") },
            text  = { Text("确定删除已选中的 ${selectedIds.size} 条证据？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSelected()
                    showBatchDeleteConfirm = false
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) { Text("取消") }
            }
        )
    }

    if (showEditDialog && editingEvidence != null) {
        EditEvidenceDialog(
            evidence  = editingEvidence!!,
            onDismiss = {
                showEditDialog = false
                editingEvidence = null
            },
            onConfirm = { title, tag, notes ->
                viewModel.updateMeta(editingEvidence!!.id, title, tag, notes)
                showEditDialog = false
                editingEvidence = null
            }
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
                            Icon(Icons.Default.Close, contentDescription = "退出多选",
                                tint = Color.White)
                        }
                        Text(
                            "已选 ${selectedIds.size} 条",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.selectAll() }) {
                            Text("全选", color = AccentCyan)
                        }
                        IconButton(
                            onClick = { showBatchDeleteConfirm = true },
                            enabled = selectedIds.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "批量删除",
                                tint = Color.White)
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "取证记录",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.toggleSortOrder() }) {
                            Icon(
                                imageVector = if (sortOrder == SortOrder.NEWEST)
                                    Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                contentDescription = "排序",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        },
        containerColor = Color(0xFFF0F4FF)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── 搜索框 ───────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .shadow(4.dp, RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("搜索标题或标签", color = Color(0xFFAAAAAA)) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = NavyMid)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "清除",
                                    tint = Color.Gray)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = Color.Transparent,
                        unfocusedBorderColor    = Color.Transparent,
                        focusedContainerColor   = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )
            }

            // ── 类型筛选标签 ──────────────────────────────────
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
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
                            .border(
                                width = 1.dp,
                                color = if (selected) Color.Transparent else Color(0xFFDDE3F0),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .combinedClickable(onClick = { viewModel.setFilterType(type) })
                            .padding(horizontal = 18.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = type.displayName(),
                            color = if (selected) Color.White else Color(0xFF555577),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── 四个操作按钮 ──────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Triple(Icons.Default.CameraAlt, "拍照", ColorPhoto to onNavigateCapturePhoto),
                    Triple(Icons.Default.Videocam,  "录像", ColorVideo to onNavigateRecordVideo),
                    Triple(Icons.Default.Mic,       "录音", ColorAudio to onNavigateRecordAudio),
                    Triple(Icons.Default.Edit,      "文字", ColorText  to onNavigateTextNote)
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

            Spacer(modifier = Modifier.height(4.dp))

            // ── 证据九宫格（按日期分组）─────────────────────────
            if (evidenceList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color(0xFFBBC5E0)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty() ||
                                filterType != FilterType.ALL)
                                "没有符合条件的证据" else "暂无取证记录",
                            color = Color(0xFFAAB4CC)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    groupedList.forEach { (dateLabel, items) ->
                        // 日期分组标题
                        stickyHeader(key = "header_$dateLabel") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF0F4FF))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = dateLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyMid
                                )
                            }
                        }

                        // 将当天证据按每行3个分组
                        val rows = items.chunked(3)
                        items(
                            items = rows,
                            key   = { row -> "row_${row.first().id}" }
                        ) { row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                row.forEach { evidence ->
                                    val isSelected = selectedIds.contains(evidence.id)
                                    EvidenceGridCell(
                                        evidence  = evidence,
                                        isSelected = isSelected,
                                        modifier  = Modifier.weight(1f),
                                        onClick   = {
                                            if (isSelectionMode)
                                                viewModel.toggleSelection(evidence.id)
                                            else
                                                onNavigateDetail(evidence.id)
                                        },
                                        onLongClick = {
                                            viewModel.enterSelectionMode(evidence.id)
                                        },
                                        onEdit = {
                                            editingEvidence = evidence
                                            showEditDialog = true
                                        },
                                        onDelete = {
                                            pendingDeleteEvidence = evidence
                                            showDeleteConfirm = true
                                        }
                                    )
                                }
                                // 末行不足3个时补空格占位，保持对齐
                                repeat(3 - row.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}

// ── 九宫格单元格 ──────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EvidenceGridCell(
    evidence:    Evidence,
    isSelected:  Boolean,
    modifier:    Modifier = Modifier,
    onClick:     () -> Unit,
    onLongClick: () -> Unit,
    onEdit:      () -> Unit,
    onDelete:    () -> Unit
) {
    val iconColor = mediaIconColor(evidence.mediaType)
    var showMenu  by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .aspectRatio(1f)               // 正方形
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .then(
                if (isSelected)
                    Modifier.border(2.5.dp, iconColor, RoundedCornerShape(10.dp))
                else Modifier
            )
            .combinedClickable(
                onClick     = onClick,
                onLongClick = {
                    if (!isSelected) showMenu = true
                    onLongClick()
                }
            )
    ) {
        // 缩略图区域
        EvidenceThumbnail(
            evidence  = evidence,
            iconColor = iconColor,
            modifier  = Modifier.fillMaxSize()
        )

        // 左上角类型图标角标
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = evidence.mediaType.icon(),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(13.dp)
            )
        }

        // 多选时右上角勾选圆圈
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(iconColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // 底部时间 + 标题栏
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 5.dp, vertical = 3.dp)
        ) {
            Column {
                if (evidence.title.isNotEmpty()) {
                    Text(
                        text = evidence.title,
                        color = Color.White,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = timeFmt.format(Date(evidence.createdAt)),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 9.sp
                )
            }
        }

        // 长按右键菜单（编辑 / 删除）
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("编辑") },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = {
                    showMenu = false
                    onEdit()
                }
            )
            DropdownMenuItem(
                text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(Icons.Default.Delete, contentDescription = null,
                        tint = MaterialTheme.colorScheme.error)
                },
                onClick = {
                    showMenu = false
                    onDelete()
                }
            )
        }
    }
}

// ── 缩略图加载 ────────────────────────────────────────────────
@Composable
private fun EvidenceThumbnail(
    evidence:  Evidence,
    iconColor: Color,
    modifier:  Modifier = Modifier
) {
    when (evidence.mediaType) {
        MediaType.PHOTO -> {
            // 照片直接用 Coil 加载
            AsyncImage(
                model            = evidence.mediaPath,
                contentDescription = "照片缩略图",
                contentScale     = ContentScale.Crop,
                modifier         = modifier
            )
        }
        MediaType.VIDEO -> {
            // 视频提取第一帧作为缩略图
            VideoThumbnail(path = evidence.mediaPath, modifier = modifier)
        }
        MediaType.AUDIO -> {
            // 录音显示紫色背景 + 麦克风图标
            Box(
                modifier = modifier.background(
                    Brush.radialGradient(
                        listOf(Color(0xFFCE93D8), Color(0xFF7B1FA2))
                    )
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        MediaType.TEXT -> {
            // 文字显示青绿色背景 + 文字图标
            Box(
                modifier = modifier.background(
                    Brush.radialGradient(
                        listOf(Color(0xFF80CBC4), Color(0xFF00695C))
                    )
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

// 视频第一帧缩略图，异步加载避免卡顿
@Composable
private fun VideoThumbnail(path: String, modifier: Modifier = Modifier) {
    var bitmap by remember(path) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(path) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(path)
                val bmp = retriever.getFrameAtTime(
                    0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                retriever.release()
                bmp
            } catch (e: Exception) { null }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap           = bitmap!!.asImageBitmap(),
            contentDescription = "视频缩略图",
            contentScale     = ContentScale.Crop,
            modifier         = modifier
        )
        // 视频播放图标水印
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.PlayCircle,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(32.dp)
            )
        }
    } else {
        Box(
            modifier = modifier.background(
                Brush.radialGradient(listOf(Color(0xFFEF9A9A), Color(0xFFB71C1C)))
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Videocam,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

// ── 可左滑展开编辑/删除（列表模式保留，目前九宫格改用长按菜单）──
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeableEvidenceCard(
    evidence: Evidence,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onNavigateDetail: (String) -> Unit,
    onLongClick: () -> Unit
) {
    val buttonAreaWidth = 152.dp
    val buttonAreaWidthPx = with(LocalDensity.current) { buttonAreaWidth.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val isOpen by remember { derivedStateOf { offsetX.value < -buttonAreaWidthPx * 0.5f } }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(72.dp).height(72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1976D2))
                    .combinedClickable(onClick = {
                        scope.launch { offsetX.animateTo(0f, spring()) }
                        onEdit()
                    }),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑",
                        tint = Color.White, modifier = Modifier.size(20.dp))
                    Text("编辑", color = Color.White, fontSize = 11.sp)
                }
            }
            Box(
                modifier = Modifier
                    .width(72.dp).height(72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFE53935))
                    .combinedClickable(onClick = {
                        scope.launch { offsetX.animateTo(0f, spring()) }
                        onDelete()
                    }),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Delete, contentDescription = "删除",
                        tint = Color.White, modifier = Modifier.size(20.dp))
                    Text("删除", color = Color.White, fontSize = 11.sp)
                }
            }
        }

        EvidenceCard(
            evidence   = evidence,
            isSelected = false,
            modifier   = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            val newOffset = (offsetX.value + delta)
                                .coerceIn(-buttonAreaWidthPx, 0f)
                            offsetX.snapTo(newOffset)
                        }
                    },
                    onDragStopped = {
                        scope.launch {
                            if (offsetX.value < -buttonAreaWidthPx * 0.4f)
                                offsetX.animateTo(-buttonAreaWidthPx, spring())
                            else
                                offsetX.animateTo(0f, spring())
                        }
                    }
                )
                .combinedClickable(
                    onClick = {
                        if (isOpen) scope.launch { offsetX.animateTo(0f, spring()) }
                        else onNavigateDetail(evidence.id)
                    },
                    onLongClick = onLongClick
                )
        )
    }
}

// ── 证据列表卡片（多选模式保留）────────────────────────────────
@Composable
private fun EvidenceCard(
    evidence:   Evidence,
    isSelected: Boolean,
    modifier:   Modifier = Modifier
) {
    val iconColor = mediaIconColor(evidence.mediaType)
    val bgColor   = if (isSelected) Color(0xFFE8EDFF) else Color.White

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation     = if (isSelected) 2.dp else 5.dp,
                shape         = RoundedCornerShape(16.dp),
                ambientColor  = iconColor.copy(alpha = 0.15f),
                spotColor     = iconColor.copy(alpha = 0.2f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .then(
                if (isSelected)
                    Modifier.border(2.dp, iconColor.copy(alpha = 0.5f),
                        RoundedCornerShape(16.dp))
                else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 13.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .shadow(4.dp, CircleShape, spotColor = iconColor.copy(alpha = 0.3f))
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(iconColor.copy(alpha = 0.25f), iconColor.copy(alpha = 0.08f))
                        )
                    )
                    .border(1.5.dp, iconColor.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null,
                        tint = iconColor, modifier = Modifier.size(26.dp))
                } else {
                    Icon(evidence.mediaType.icon(), contentDescription = null,
                        tint = iconColor, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (evidence.title.isNotEmpty()) evidence.title else evidence.id,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(0xFF1A237E)
                )
                if (evidence.tag.isNotEmpty()) {
                    Text(text = evidence.tag, fontSize = 11.sp, color = iconColor,
                        fontWeight = FontWeight.Medium)
                }
                if (evidence.notes.isNotEmpty()) {
                    Text(text = evidence.notes, fontSize = 11.sp, color = Color(0xFF888888),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    text = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                        .format(Date(evidence.createdAt)),
                    fontSize = 11.sp, color = Color(0xFFAAB4CC)
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null,
                tint = Color(0xFFCCD2E8), modifier = Modifier.size(20.dp))
        }
    }
}

// ── 编辑弹窗 ──────────────────────────────────────────────────
@Composable
private fun EditEvidenceDialog(
    evidence:  Evidence,
    onDismiss: () -> Unit,
    onConfirm: (title: String, tag: String, notes: String) -> Unit
) {
    var title by remember { mutableStateOf(evidence.title.ifBlank { evidence.id }) }
    var tag   by remember { mutableStateOf(evidence.tag) }
    var notes by remember { mutableStateOf(evidence.notes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑证据信息") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("ID：${evidence.id}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("类型：${evidence.mediaType.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                .format(Date(evidence.createdAt))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("标题") },
                    placeholder = { Text("为这条证据起个名字") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("场景标签", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(presetTags) { preset ->
                        val selected = tag == preset
                        FilterChip(
                            selected = selected,
                            onClick  = { tag = if (selected) "" else preset },
                            label    = { Text(preset, fontSize = 12.sp) }
                        )
                    }
                }
                OutlinedTextField(
                    value = tag, onValueChange = { tag = it },
                    label = { Text("自定义标签") },
                    placeholder = { Text("或手动输入标签") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("备注") },
                    placeholder = { Text("补充说明事件经过等信息") },
                    minLines = 3, maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title, tag, notes) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

// ── 操作按钮 ──────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActionButton(
    icon:     ImageVector,
    label:    String,
    color:    Color,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .shadow(6.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.92f), color)))
            .combinedClickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(4.dp)
                .align(Alignment.TopCenter)
                .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                .background(Color.White.copy(alpha = 0.35f))
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, tint = Color.White,
                    modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, color = Color.White, fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun FilterType.displayName() = when (this) {
    FilterType.ALL   -> "全部"
    FilterType.PHOTO -> "照片"
    FilterType.VIDEO -> "视频"
    FilterType.AUDIO -> "录音"
    FilterType.TEXT  -> "文字"
}

private fun MediaType.icon() = when (this) {
    MediaType.PHOTO -> Icons.Default.CameraAlt
    MediaType.VIDEO -> Icons.Default.Videocam
    MediaType.AUDIO -> Icons.Default.Mic
    MediaType.TEXT  -> Icons.Default.Edit
}