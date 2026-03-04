// app/src/main/java/com/mathsnew/evidencecapture/presentation/detail/EvidenceDetailScreen.kt
// Kotlin - 表现层，证据详情页，含内置视频播放器、音频播放、备注编辑、导出、分享功能

package com.mathsnew.evidencecapture.presentation.detail

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.mathsnew.evidencecapture.domain.model.MediaType
import com.mathsnew.evidencecapture.presentation.capture.SnapshotCard
import com.mathsnew.evidencecapture.util.ExportHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvidenceDetailScreen(
    evidenceId: String,
    onBack: () -> Unit,
    viewModel: EvidenceDetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    LaunchedEffect(evidenceId) {
        viewModel.loadEvidence(evidenceId)
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopAudio() }
    }

    val uiState       by viewModel.uiState.collectAsState()
    val audioPlayState by viewModel.audioPlayState.collectAsState()

    var showExportMenu by remember { mutableStateOf(false) }
    var isExporting    by remember { mutableStateOf(false) }

    // 备注编辑弹窗状态
    var showNotesDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("证据详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (uiState is DetailUiState.Success) {
                        val state = uiState as DetailUiState.Success

                        // 备注编辑按钮
                        IconButton(onClick = { showNotesDialog = true }) {
                            Icon(
                                Icons.Default.EditNote,
                                contentDescription = "编辑备注",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }

                        // 分享按钮
                        IconButton(onClick = {
                            val intent = ExportHelper.getShareIntent(context, state.evidence)
                            if (intent != null) {
                                context.startActivity(
                                    android.content.Intent.createChooser(intent, "分享证据")
                                )
                            }
                        }) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "分享",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }

                        // 导出按钮
                        Box {
                            IconButton(onClick = { showExportMenu = true }) {
                                if (isExporting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.FileDownload,
                                        contentDescription = "导出",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = showExportMenu,
                                onDismissRequest = { showExportMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("导出为 ZIP 压缩包") },
                                    leadingIcon = {
                                        Icon(Icons.Default.FolderZip, contentDescription = null)
                                    },
                                    onClick = {
                                        showExportMenu = false
                                        isExporting = true
                                        val zipFile = ExportHelper.exportToZip(
                                            context,
                                            state.evidence,
                                            state.snapshot
                                        )
                                        isExporting = false
                                        if (zipFile != null) {
                                            val intent = ExportHelper.getZipShareIntent(
                                                context, zipFile
                                            )
                                            context.startActivity(
                                                android.content.Intent.createChooser(
                                                    intent, "导出证据包"
                                                )
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->

        // ── 备注编辑弹窗 ────────────────────────────────────
        if (showNotesDialog && uiState is DetailUiState.Success) {
            val state = uiState as DetailUiState.Success
            NotesEditDialog(
                currentNotes = state.evidence.notes,
                onDismiss = { showNotesDialog = false },
                onConfirm = { newNotes ->
                    viewModel.updateMeta(
                        id = state.evidence.id,
                        title = state.evidence.title,
                        tag = state.evidence.tag,
                        notes = newNotes
                    )
                    showNotesDialog = false
                }
            )
        }

        when (val state = uiState) {
            is DetailUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
            is DetailUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) { Text(state.message, color = MaterialTheme.colorScheme.error) }
            }
            is DetailUiState.Success -> {
                val evidence = state.evidence
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 证据基本信息
                    Text(
                        text = evidence.id,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            .format(Date(evidence.createdAt)),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (evidence.title.isNotEmpty()) {
                        Text(
                            text = evidence.title,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    if (evidence.tag.isNotEmpty()) {
                        AssistChip(onClick = {}, label = { Text(evidence.tag) })
                    }

                    // 媒体内容区域
                    when (evidence.mediaType) {
                        MediaType.PHOTO -> {
                            AsyncImage(
                                model = evidence.mediaPath,
                                contentDescription = "证据照片",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(280.dp)
                            )
                            if (evidence.voiceNotePath.isNotEmpty() &&
                                File(evidence.voiceNotePath).exists()
                            ) {
                                AudioPlayerCard(
                                    label = "语音备注",
                                    path = evidence.voiceNotePath,
                                    playState = audioPlayState,
                                    onTogglePlay = {
                                        viewModel.togglePlay(evidence.voiceNotePath, "语音备注")
                                    },
                                    onStop = { viewModel.stopAudio() },
                                    onSeek = { viewModel.seekTo(it) }
                                )
                            }
                        }
                        MediaType.AUDIO -> {
                            AudioPlayerCard(
                                label = "录音",
                                path = evidence.mediaPath,
                                playState = audioPlayState,
                                onTogglePlay = {
                                    viewModel.togglePlay(evidence.mediaPath, "录音")
                                },
                                onStop = { viewModel.stopAudio() },
                                onSeek = { viewModel.seekTo(it) }
                            )
                        }
                        MediaType.VIDEO -> {
                            VideoPlayerCard(videoPath = evidence.mediaPath)
                        }
                        MediaType.TEXT -> {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = evidence.textContent,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }

                    // 备注卡片（有备注显示内容，无备注显示引导提示）
                    NotesCard(
                        notes = evidence.notes,
                        onEditClick = { showNotesDialog = true }
                    )

                    // 哈希完整性验证卡片
                    if (evidence.sha256Hash.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (state.hashVerified)
                                    MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (state.hashVerified)
                                        Icons.Default.VerifiedUser else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (state.hashVerified)
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    else MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = if (state.hashVerified) "文件完整性验证通过"
                                        else "警告：文件可能已被篡改",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "SHA-256: ${evidence.sha256Hash.take(16)}...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // 环境数据快照卡片
                    state.snapshot?.let { SnapshotCard(snapshot = it) }
                }
            }
        }
    }
}

/**
 * 备注展示卡片
 * 有备注时显示内容 + 编辑按钮
 * 无备注时显示灰色引导文字 + 添加按钮
 */
@Composable
private fun NotesCard(
    notes: String,
    onEditClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.StickyNote2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "备注",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (notes.isNotEmpty()) {
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = "点击右侧按钮添加备注",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(
                onClick = onEditClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (notes.isEmpty()) Icons.Default.Add
                    else Icons.Default.Edit,
                    contentDescription = if (notes.isEmpty()) "添加备注" else "编辑备注",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * 备注编辑弹窗
 * 只编辑备注文字，多行输入
 */
@Composable
private fun NotesEditDialog(
    currentNotes: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var notes by remember { mutableStateOf(currentNotes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑备注") },
        text = {
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("备注内容") },
                placeholder = { Text("记录事件经过、补充说明等信息") },
                minLines = 4,
                maxLines = 8,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(notes) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 内置视频播放器卡片
 * 未播放时显示第一帧缩略图 + 居中播放按钮
 * 播放时显示 ExoPlayer 内嵌播放器，含进度条控件
 * 右上角全屏按钮切换横屏/竖屏
 */
@Composable
private fun VideoPlayerCard(videoPath: String) {
    val context  = LocalContext.current
    val activity = context as? Activity

    var isPlaying    by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    var thumbnail    by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(videoPath) {
        thumbnail = withContext(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(videoPath)
                val bmp = retriever.getFrameAtTime(
                    0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                retriever.release()
                bmp
            } catch (e: Exception) { null }
        }
    }

    val exoPlayer = remember(isPlaying) {
        if (isPlaying) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(videoPath))
                prepare()
                playWhenReady = true
            }
        } else null
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer?.release() }
    }

    LaunchedEffect(isFullscreen) {
        activity?.requestedOrientation = if (isFullscreen)
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        else
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    Card(modifier = if (isFullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
        Box(
            modifier = if (isFullscreen)
                Modifier.fillMaxSize().background(Color.Black)
            else
                Modifier.fillMaxWidth()
        ) {
            if (!isPlaying) {
                // 缩略图 + 播放按钮
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (thumbnail != null) {
                        Image(
                            bitmap = thumbnail!!.asImageBitmap(),
                            contentDescription = "视频缩略图",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                        )
                    }
                    IconButton(
                        onClick = { isPlaying = true },
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = "播放视频",
                            modifier = Modifier.size(72.dp),
                            tint = Color.White
                        )
                    }
                }
                // 视频标签（左下角）
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Videocam,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "视频证据",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            } else {
                // ExoPlayer 内嵌播放器
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                        }
                    },
                    modifier = if (isFullscreen) Modifier.fillMaxSize()
                    else Modifier.fillMaxWidth().height(220.dp)
                )
                // 全屏切换按钮（右上角）
                IconButton(
                    onClick = { isFullscreen = !isFullscreen },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit
                        else Icons.Default.Fullscreen,
                        contentDescription = if (isFullscreen) "退出全屏" else "全屏",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

/**
 * 音频播放卡片
 * 含播放/暂停/停止、进度条、时长显示
 * 被"录音取证"和"拍照语音备注"复用
 */
@Composable
private fun AudioPlayerCard(
    label: String,
    path: String,
    playState: AudioPlayState,
    onTogglePlay: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Int) -> Unit
) {
    val isThisPlaying = playState is AudioPlayState.Playing &&
            (playState as AudioPlayState.Playing).path == path
    val isThisPaused  = playState is AudioPlayState.Paused &&
            (playState as AudioPlayState.Paused).path == path

    val currentMs = when {
        isThisPlaying -> (playState as AudioPlayState.Playing).currentMs
        isThisPaused  -> (playState as AudioPlayState.Paused).currentMs
        else -> 0
    }
    val totalMs = when {
        isThisPlaying -> (playState as AudioPlayState.Playing).totalMs
        isThisPaused  -> (playState as AudioPlayState.Paused).totalMs
        else -> 0
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                if (isThisPlaying || isThisPaused) {
                    IconButton(onClick = onStop) {
                        Icon(Icons.Default.Stop, contentDescription = "停止",
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
                IconButton(onClick = onTogglePlay) {
                    Icon(
                        imageVector = if (isThisPlaying) Icons.Default.Pause
                        else Icons.Default.PlayArrow,
                        contentDescription = if (isThisPlaying) "暂停" else "播放",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            if (totalMs > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = if (totalMs > 0) currentMs.toFloat() / totalMs else 0f,
                    onValueChange = { onSeek((it * totalMs).toInt()) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatMs(currentMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatMs(totalMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatMs(ms: Int): String {
    val sec = ms / 1000
    val min = sec / 60
    val remSec = sec % 60
    return "%d:%02d".format(min, remSec)
}