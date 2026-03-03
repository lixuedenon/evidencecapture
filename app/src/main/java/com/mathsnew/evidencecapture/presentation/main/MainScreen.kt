// app/src/main/java/com/mathsnew/evidencecapture/presentation/main/MainScreen.kt
// Kotlin - 表现层，主页，鲜明色彩+立体卡片风格

package com.mathsnew.evidencecapture.presentation.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mathsnew.evidencecapture.domain.model.Evidence
import com.mathsnew.evidencecapture.domain.model.MediaType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── 主题色定义 ──────────────────────────────────────────────
private val NavyDeep   = Color(0xFF1A237E)
private val NavyMid    = Color(0xFF283593)
private val AccentBlue = Color(0xFF42A5F5)
private val AccentCyan = Color(0xFF00E5FF)

// 四个操作按钮各自的主色
private val ColorPhoto  = Color(0xFF5C6BC0)   // 蓝紫
private val ColorVideo  = Color(0xFFE53935)   // 红
private val ColorAudio  = Color(0xFF8E24AA)   // 紫
private val ColorText   = Color(0xFF00897B)   // 青绿

// 证据卡片图标颜色（按类型）
private fun mediaIconColor(type: MediaType) = when (type) {
    MediaType.PHOTO -> Color(0xFF42A5F5)
    MediaType.VIDEO -> Color(0xFFEF5350)
    MediaType.AUDIO -> Color(0xFFAB47BC)
    MediaType.TEXT  -> Color(0xFF26A69A)
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
    val evidenceList  by viewModel.evidenceList.collectAsState()
    val searchQuery   by viewModel.searchQuery.collectAsState()
    val filterType    by viewModel.filterType.collectAsState()
    val sortOrder     by viewModel.sortOrder.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedIds   by viewModel.selectedIds.collectAsState()

    var showDeleteConfirm    by remember { mutableStateOf(false) }
    var pendingDeleteEvidence by remember { mutableStateOf<Evidence?>(null) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    // ── 删除确认弹窗 ─────────────────────────────────────────
    if (showDeleteConfirm && pendingDeleteEvidence != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false; pendingDeleteEvidence = null },
            title = { Text("删除证据") },
            text  = { Text("确定删除这条证据？相关文件将一并删除，此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteEvidence?.let { viewModel.deleteEvidence(it) }
                    showDeleteConfirm = false; pendingDeleteEvidence = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false; pendingDeleteEvidence = null }) {
                    Text("取消")
                }
            }
        )
    }
    if (showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text("批量删除") },
            text  = { Text("确定删除已选中的 ${selectedIds.size} 条证据？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteSelected(); showBatchDeleteConfirm = false }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(listOf(NavyDeep, NavyMid))
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                if (isSelectionMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.exitSelectionMode() }) {
                            Icon(Icons.Default.Close, contentDescription = "退出多选", tint = Color.White)
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
                            Icon(Icons.Default.Delete, contentDescription = "批量删除", tint = Color.White)
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
        containerColor = Color(0xFFF0F4FF)   // 页面浅蓝底色
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
                    placeholder = { Text("搜索", color = Color(0xFFAAAAAA)) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = NavyMid)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "清除", tint = Color.Gray)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
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

            // ── 四个操作按钮（立体圆角胶囊风格）──────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Triple(Icons.Default.CameraAlt, "拍照",  ColorPhoto  to onNavigateCapturePhoto),
                    Triple(Icons.Default.Videocam,  "录像",  ColorVideo  to onNavigateRecordVideo),
                    Triple(Icons.Default.Mic,       "录音",  ColorAudio  to onNavigateRecordAudio),
                    Triple(Icons.Default.Edit,      "文字",  ColorText   to onNavigateTextNote)
                ).forEach { (icon, label, colorAction) ->
                    val (color, action) = colorAction
                    ActionButton(
                        icon   = icon,
                        label  = label,
                        color  = color,
                        onClick = action,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ── 证据列表 ──────────────────────────────────────
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
                            text = if (searchQuery.isNotEmpty() || filterType != FilterType.ALL)
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
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items = evidenceList, key = { it.id }) { evidence ->
                        val isSelected = selectedIds.contains(evidence.id)
                        if (isSelectionMode) {
                            EvidenceCard(
                                evidence   = evidence,
                                isSelected = isSelected,
                                modifier   = Modifier.combinedClickable(
                                    onClick = { viewModel.toggleSelection(evidence.id) }
                                )
                            )
                        } else {
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        pendingDeleteEvidence = evidence
                                        showDeleteConfirm = true
                                    }
                                    false
                                }
                            )
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                backgroundContent = {
                                    val color by animateColorAsState(
                                        targetValue = if (dismissState.dismissDirection ==
                                            SwipeToDismissBoxValue.EndToStart)
                                            Color(0xFFFFCDD2) else Color.Transparent,
                                        label = "swipe_bg"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(color)
                                            .padding(end = 20.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "删除",
                                            tint = Color(0xFFE53935)
                                        )
                                    }
                                }
                            ) {
                                EvidenceCard(
                                    evidence   = evidence,
                                    isSelected = false,
                                    modifier   = Modifier.combinedClickable(
                                        onClick    = { onNavigateDetail(evidence.id) },
                                        onLongClick = { viewModel.enterSelectionMode(evidence.id) }
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── 操作按钮：立体彩色胶囊 ────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActionButton(
    icon:    ImageVector,
    label:   String,
    color:   Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lightColor = color.copy(alpha = 0.18f)
    Box(
        modifier = modifier
            .shadow(6.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(color.copy(alpha = 0.92f), color)
                )
            )
            .combinedClickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        // 顶部高光
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
                Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── 证据卡片：立体白卡 ────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EvidenceCard(
    evidence:   Evidence,
    isSelected: Boolean,
    modifier:   Modifier = Modifier
) {
    val iconColor = mediaIconColor(evidence.mediaType)
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFFE8EDFF) else Color.White,
        label = "card_bg"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isSelected) 2.dp else 5.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = iconColor.copy(alpha = 0.15f),
                spotColor = iconColor.copy(alpha = 0.2f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .then(
                if (isSelected)
                    Modifier.border(2.dp, iconColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 13.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标容器（彩色圆形）
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
                    Text(
                        text = evidence.tag,
                        fontSize = 11.sp,
                        color = iconColor,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                        .format(Date(evidence.createdAt)),
                    fontSize = 11.sp,
                    color = Color(0xFFAAB4CC)
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFFCCD2E8),
                modifier = Modifier.size(20.dp)
            )
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