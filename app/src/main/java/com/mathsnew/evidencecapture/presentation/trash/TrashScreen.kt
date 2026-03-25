// app/src/main/java/com/mathsnew/evidencecapture/presentation/trash/TrashScreen.kt
// 新建文件 - Kotlin

package com.mathsnew.evidencecapture.presentation.trash

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.mathsnew.evidencecapture.R
import com.mathsnew.evidencecapture.domain.model.Evidence
import com.mathsnew.evidencecapture.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val NavyDeep = Color(0xFF1A237E)
private val NavyMid  = Color(0xFF283593)

private fun mediaIconColor(type: MediaType) = when (type) {
    MediaType.PHOTO -> Color(0xFF42A5F5)
    MediaType.VIDEO -> Color(0xFFEF5350)
    MediaType.AUDIO -> Color(0xFFAB47BC)
    MediaType.TEXT  -> Color(0xFF26A69A)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TrashScreen(
    onBack:    () -> Unit,
    viewModel: TrashViewModel = hiltViewModel()
) {
    val trashList       by viewModel.trashList.collectAsState()
    val selectedIds     by viewModel.selectedIds.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()

    var showClearConfirm  by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var pendingEvidence   by remember { mutableStateOf<Evidence?>(null) }

    // 清空回收站确认
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空回收站") },
            text  = { Text("将永久删除回收站中的全部 ${trashList.size} 条证据，此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAll(); showClearConfirm = false }) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            }
        )
    }

    // 单条永久删除确认
    if (showDeleteConfirm && pendingEvidence != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false; pendingEvidence = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text  = { Text("将永久删除这条证据，此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingEvidence?.let { viewModel.deletePermanently(it) }
                    showDeleteConfirm = false; pendingEvidence = null
                }) {
                    Text(stringResource(R.string.delete_confirm),
                        color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false; pendingEvidence = null }) {
                    Text(stringResource(R.string.delete_cancel))
                }
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
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                if (isSelectionMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.exitSelectionMode() }) {
                            Icon(Icons.Default.Close, null, tint = Color.White)
                        }
                        Text("已选 ${selectedIds.size} 条",
                            color = Color.White, fontWeight = FontWeight.Bold,
                            fontSize = 18.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.selectAll() }) {
                            Text("全选", color = Color(0xFF00E5FF))
                        }
                        // 批量恢复
                        IconButton(onClick = { viewModel.restoreSelected() }) {
                            Icon(Icons.Default.RestoreFromTrash, null, tint = Color.White)
                        }
                        // 批量永久删除
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.DeleteForever, null, tint = Color.White)
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                        }
                        Text("回收站",
                            color = Color.White, fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp, modifier = Modifier.weight(1f))
                        if (trashList.isNotEmpty()) {
                            TextButton(onClick = { showClearConfirm = true }) {
                                Text("清空", color = Color(0xFFEF9A9A))
                            }
                        }
                    }
                }
            }
        },
        containerColor = Color(0xFFF0F4FF)
    ) { padding ->
        if (trashList.isEmpty()) {
            // 空状态
            Box(
                modifier         = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.DeleteOutline, null,
                        modifier = Modifier.size(80.dp), tint = Color(0xFFBBC5E0))
                    Spacer(Modifier.height(12.dp))
                    Text("回收站是空的", color = Color(0xFFAAB4CC),
                        style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(6.dp))
                    Text("删除的证据会在这里保留 30 天",
                        color = Color(0xFFCCD2E0),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            LazyColumn(
                modifier       = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 提示文字
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors   = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFF8E1)
                        )
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, null,
                                tint = Color(0xFFF57F17), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("回收站中的证据将在 30 天后自动永久删除",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF5D4037))
                        }
                    }
                }

                items(trashList, key = { it.id }) { evidence ->
                    TrashCard(
                        evidence        = evidence,
                        isSelected      = selectedIds.contains(evidence.id),
                        remainingDays   = viewModel.remainingDays(evidence.deletedAt),
                        onClick         = {
                            if (isSelectionMode) viewModel.toggleSelection(evidence.id)
                        },
                        onLongClick     = { viewModel.enterSelectionMode(evidence.id) },
                        onRestore       = { viewModel.restore(evidence.id) },
                        onDeleteForever = {
                            pendingEvidence   = evidence
                            showDeleteConfirm = true
                        }
                    )
                }
            }
        }
    }
}

// ── 回收站卡片 ────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrashCard(
    evidence:        Evidence,
    isSelected:      Boolean,
    remainingDays:   Int,
    onClick:         () -> Unit,
    onLongClick:     () -> Unit,
    onRestore:       () -> Unit,
    onDeleteForever: () -> Unit
) {
    val iconColor = mediaIconColor(evidence.mediaType)
    val dateFmt   = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    // 剩余天数颜色：≤3天红色警告，≤7天橙色，其余灰色
    val daysColor = when {
        remainingDays <= 3  -> Color(0xFFE53935)
        remainingDays <= 7  -> Color(0xFFF57C00)
        else                -> Color(0xFF888888)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .then(if (isSelected) Modifier.border(2.dp, iconColor, RoundedCornerShape(12.dp))
                else Modifier),
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFE8EDFF) else Color.White
        ),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Row(
            modifier          = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 缩略图
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                TrashThumbnail(evidence, iconColor)
                // 多选勾
                if (isSelected) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Check, null, tint = Color.White,
                            modifier = Modifier.size(24.dp))
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            // 信息区
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (evidence.title.isNotEmpty()) evidence.title else evidence.id,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = Color(0xFF333355)
                )
                if (evidence.tag.isNotEmpty()) {
                    Text(evidence.tag, fontSize = 11.sp, color = iconColor)
                }
                Spacer(Modifier.height(2.dp))
                // 原创建时间
                Text("创建：${dateFmt.format(Date(evidence.createdAt))}",
                    fontSize = 10.sp, color = Color(0xFFAAAAAA))
                // 删除时间
                evidence.deletedAt?.let {
                    Text("删除：${dateFmt.format(Date(it))}",
                        fontSize = 10.sp, color = Color(0xFFAAAAAA))
                }
                // 剩余天数
                Text(
                    text = if (remainingDays == 0) "今天将被永久删除"
                        else "还有 $remainingDays 天永久删除",
                    fontSize = 10.sp,
                    color    = daysColor,
                    fontWeight = if (remainingDays <= 3) FontWeight.Bold else FontWeight.Normal
                )
            }

            // 操作按钮
            if (!isSelected) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // 恢复
                    IconButton(onClick = onRestore, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.RestoreFromTrash, null,
                            tint = Color(0xFF1A237E), modifier = Modifier.size(20.dp))
                    }
                    // 永久删除
                    IconButton(onClick = onDeleteForever, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.DeleteForever, null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

// ── 缩略图 ────────────────────────────────────────────────────
@Composable
private fun TrashThumbnail(evidence: Evidence, iconColor: Color) {
    when (evidence.mediaType) {
        MediaType.PHOTO -> AsyncImage(
            model = evidence.mediaPath, contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        MediaType.VIDEO -> VideoThumb(evidence.mediaPath)
        MediaType.AUDIO -> Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(listOf(Color(0xFFCE93D8), Color(0xFF7B1FA2)))),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Mic, null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        MediaType.TEXT  -> Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(listOf(Color(0xFF80CBC4), Color(0xFF00695C)))),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Edit, null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun VideoThumb(path: String) {
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
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    } else {
        Box(Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Color(0xFFEF9A9A), Color(0xFFB71C1C)))),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Videocam, null, tint = Color.White,
                modifier = Modifier.size(28.dp))
        }
    }
}