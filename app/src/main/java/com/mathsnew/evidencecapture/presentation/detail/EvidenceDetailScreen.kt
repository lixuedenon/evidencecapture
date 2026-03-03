// app/src/main/java/com/mathsnew/evidencecapture/presentation/detail/EvidenceDetailScreen.kt
// Kotlin - 表现层，证据详情页，含音频播放、导出、分享功能

package com.mathsnew.evidencecapture.presentation.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.mathsnew.evidencecapture.domain.model.MediaType
import com.mathsnew.evidencecapture.presentation.capture.SnapshotCard
import com.mathsnew.evidencecapture.util.ExportHelper
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

    val uiState by viewModel.uiState.collectAsState()
    val audioPlayState by viewModel.audioPlayState.collectAsState()

    var showExportMenu by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }

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

                    // 媒体内容
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
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.VideoFile,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "视频文件",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    // 调起系统视频播放器
                                    IconButton(onClick = {
                                        val file = java.io.File(evidence.mediaPath)
                                        val uri = androidx.core.content.FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        val intent = android.content.Intent(
                                            android.content.Intent.ACTION_VIEW
                                        ).apply {
                                            setDataAndType(uri, "video/*")
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(
                                            android.content.Intent.createChooser(intent, "播放视频")
                                        )
                                    }) {
                                        Icon(
                                            Icons.Default.PlayCircle,
                                            contentDescription = "播放视频",
                                            modifier = Modifier.size(36.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
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

                    // 哈希验证
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

                    // 环境数据
                    state.snapshot?.let { SnapshotCard(snapshot = it) }
                }
            }
        }
    }
}

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
    val isThisPaused = playState is AudioPlayState.Paused &&
            (playState as AudioPlayState.Paused).path == path

    val currentMs = when {
        isThisPlaying -> (playState as AudioPlayState.Playing).currentMs
        isThisPaused -> (playState as AudioPlayState.Paused).currentMs
        else -> 0
    }
    val totalMs = when {
        isThisPlaying -> (playState as AudioPlayState.Playing).totalMs
        isThisPaused -> (playState as AudioPlayState.Paused).totalMs
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
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "停止",
                            tint = MaterialTheme.colorScheme.error
                        )
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