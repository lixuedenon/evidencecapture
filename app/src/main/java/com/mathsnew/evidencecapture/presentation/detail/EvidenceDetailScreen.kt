// app/src/main/java/com/mathsnew/evidencecapture/presentation/detail/EvidenceDetailScreen.kt
// 修改文件 - Kotlin

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.mathsnew.evidencecapture.R
import com.mathsnew.evidencecapture.domain.model.MediaType
import com.mathsnew.evidencecapture.presentation.capture.SnapshotCard
import com.mathsnew.evidencecapture.util.ExportHelper
import com.mathsnew.evidencecapture.util.PdfReportBuilder
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    onNavigatePdfViewer: (pdfPath: String, title: String) -> Unit = { _, _ -> },
    viewModel: EvidenceDetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    LaunchedEffect(evidenceId) { viewModel.loadEvidence(evidenceId) }
    DisposableEffect(Unit) { onDispose { viewModel.stopAudio() } }

    val uiState        by viewModel.uiState.collectAsState()
    val audioPlayState by viewModel.audioPlayState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var showExportMenu    by remember { mutableStateOf(false) }
    var isExporting       by remember { mutableStateOf(false) }
    var isSharing         by remember { mutableStateOf(false) }
    var isExportingPdf    by remember { mutableStateOf(false) }
    var showNotesDialog   by remember { mutableStateOf(false) }
    var showEditDialog    by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // ── 编辑证据信息弹窗 ──────────────────────────────────────
    if (showEditDialog && uiState is DetailUiState.Success) {
        val state = uiState as DetailUiState.Success
        EditMetaDialog(
            title     = state.evidence.title,
            tag       = state.evidence.tag,
            onDismiss = { showEditDialog = false },
            onConfirm = { newTitle, newTag ->
                viewModel.updateMeta(
                    id    = state.evidence.id,
                    title = newTitle,
                    tag   = newTag,
                    notes = state.evidence.notes
                )
                showEditDialog = false
            }
        )
    }

    // ── 删除确认弹窗 ──────────────────────────────────────────
    if (showDeleteConfirm && uiState is DetailUiState.Success) {
        val state = uiState as DetailUiState.Success
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_title)) },
            text  = { Text(stringResource(R.string.delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteEvidence(state.evidence.id)
                    showDeleteConfirm = false
                    onBack()
                }) {
                    Text(stringResource(R.string.delete_confirm),
                        color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.delete_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.detail_back))
                    }
                },
                actions = {
                    if (uiState is DetailUiState.Success) {
                        val state = uiState as DetailUiState.Success

                        // 编辑标题/标签
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(Icons.Default.Edit,
                                contentDescription = stringResource(R.string.edit_title),
                                tint = MaterialTheme.colorScheme.onPrimary)
                        }

                        // 编辑备注
                        IconButton(onClick = { showNotesDialog = true }) {
                            Icon(Icons.Default.EditNote,
                                contentDescription = stringResource(R.string.detail_edit_notes),
                                tint = MaterialTheme.colorScheme.onPrimary)
                        }

                        // 删除证据
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete_title),
                                tint = MaterialTheme.colorScheme.onPrimary)
                        }

                        // 分享
                        IconButton(
                            onClick = {
                                if (!isSharing) {
                                    coroutineScope.launch {
                                        isSharing = true
                                        val htmlFile = withContext(Dispatchers.IO) {
                                            ExportHelper.exportToHtml(
                                                context, state.evidence, state.snapshot)
                                        }
                                        isSharing = false
                                        if (htmlFile != null) {
                                            context.startActivity(
                                                android.content.Intent.createChooser(
                                                    ExportHelper.getHtmlShareIntent(context, htmlFile),
                                                    context.getString(R.string.detail_share_chooser)
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        ) {
                            if (isSharing) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(Icons.Default.Share,
                                    contentDescription = stringResource(R.string.detail_share),
                                    tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }

                        // 导出（ZIP / PDF）
                        Box {
                            IconButton(onClick = { showExportMenu = true }) {
                                if (isExporting || isExportingPdf) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary)
                                } else {
                                    Icon(Icons.Default.FileDownload,
                                        contentDescription = stringResource(R.string.detail_export),
                                        tint = MaterialTheme.colorScheme.onPrimary)
                                }
                            }
                            DropdownMenu(expanded = showExportMenu,
                                onDismissRequest = { showExportMenu = false }) {
                                DropdownMenuItem(
                                    text        = { Text(stringResource(R.string.detail_export_zip)) },
                                    leadingIcon = { Icon(Icons.Default.FolderZip, null) },
                                    onClick     = {
                                        showExportMenu = false; isExporting = true
                                        val zipFile = ExportHelper.exportToZip(
                                            context, state.evidence, state.snapshot)
                                        isExporting = false
                                        if (zipFile != null) {
                                            context.startActivity(
                                                android.content.Intent.createChooser(
                                                    ExportHelper.getZipShareIntent(context, zipFile),
                                                    context.getString(R.string.detail_export_chooser)
                                                )
                                            )
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text        = { Text(stringResource(R.string.detail_export_pdf)) },
                                    leadingIcon = { Icon(Icons.Default.PictureAsPdf, null) },
                                    onClick     = {
                                        showExportMenu = false
                                        coroutineScope.launch {
                                            isExportingPdf = true
                                            val pdfFile = withContext(Dispatchers.IO) {
                                                PdfReportBuilder.build(
                                                    context, state.evidence, state.snapshot)
                                            }
                                            isExportingPdf = false
                                            if (pdfFile != null) {
                                                onNavigatePdfViewer(
                                                    pdfFile.absolutePath,
                                                    state.evidence.title.ifEmpty { state.evidence.id }
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor             = MaterialTheme.colorScheme.primary,
                    titleContentColor          = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->

        if (showNotesDialog && uiState is DetailUiState.Success) {
            val state = uiState as DetailUiState.Success
            NotesEditDialog(
                currentNotes = state.evidence.notes,
                onDismiss    = { showNotesDialog = false },
                onConfirm    = { newNotes ->
                    viewModel.updateMeta(state.evidence.id, state.evidence.title,
                        state.evidence.tag, newNotes)
                    showNotesDialog = false
                }
            )
        }

        when (val state = uiState) {
            is DetailUiState.Loading -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is DetailUiState.Error -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
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
                    Text(evidence.id, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        .format(Date(evidence.createdAt)),
                        style = MaterialTheme.typography.bodySmall)
                    if (evidence.title.isNotEmpty())
                        Text(evidence.title, style = MaterialTheme.typography.titleMedium)
                    if (evidence.tag.isNotEmpty())
                        AssistChip(onClick = {}, label = { Text(evidence.tag) })

                    when (evidence.mediaType) {
                        MediaType.PHOTO -> {
                            AsyncImage(
                                model              = evidence.mediaPath,
                                contentDescription = stringResource(R.string.filter_photo),
                                contentScale       = ContentScale.Fit,
                                modifier           = Modifier.fillMaxWidth().height(280.dp)
                            )
                            if (evidence.voiceNotePath.isNotEmpty() &&
                                File(evidence.voiceNotePath).exists()) {
                                AudioPlayerCard(
                                    label        = stringResource(R.string.detail_voice_note_label),
                                    path         = evidence.voiceNotePath,
                                    playState    = audioPlayState,
                                    onTogglePlay = { viewModel.togglePlay(evidence.voiceNotePath, "voice") },
                                    onStop       = { viewModel.stopAudio() },
                                    onSeek       = { viewModel.seekTo(it) }
                                )
                            }
                        }
                        MediaType.AUDIO -> {
                            AudioPlayerCard(
                                label        = stringResource(R.string.detail_audio_label),
                                path         = evidence.mediaPath,
                                playState    = audioPlayState,
                                onTogglePlay = { viewModel.togglePlay(evidence.mediaPath, "audio") },
                                onStop       = { viewModel.stopAudio() },
                                onSeek       = { viewModel.seekTo(it) }
                            )
                        }
                        MediaType.VIDEO -> VideoPlayerCard(videoPath = evidence.mediaPath)
                        MediaType.TEXT  -> {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Text(evidence.textContent,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(16.dp))
                            }
                        }
                    }

                    NotesCard(notes = evidence.notes, onEditClick = { showNotesDialog = true })

                    if (evidence.sha256Hash.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors   = CardDefaults.cardColors(
                                containerColor = if (state.hashVerified)
                                    MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (state.hashVerified)
                                        Icons.Default.VerifiedUser else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (state.hashVerified)
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    else MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text  = if (state.hashVerified)
                                            stringResource(R.string.detail_hash_verified)
                                        else stringResource(R.string.detail_hash_tampered),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text  = stringResource(R.string.detail_hash_prefix) +
                                                evidence.sha256Hash.take(16) + "...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    state.snapshot?.let { SnapshotCard(snapshot = it) }
                }
            }
        }
    }
}

// ── 编辑标题/标签弹窗 ─────────────────────────────────────────
@Composable
private fun EditMetaDialog(
    title:     String,
    tag:       String,
    onDismiss: () -> Unit,
    onConfirm: (title: String, tag: String) -> Unit
) {
    var newTitle by remember { mutableStateOf(title) }
    var newTag   by remember { mutableStateOf(tag) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_title)) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value         = newTitle,
                    onValueChange = { newTitle = it },
                    label         = { Text(stringResource(R.string.edit_field_title)) },
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier      = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value         = newTag,
                    onValueChange = { newTag = it },
                    label         = { Text(stringResource(R.string.edit_field_tag)) },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(newTitle, newTag) }) {
                Text(stringResource(R.string.edit_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.edit_cancel)) }
        }
    )
}

// ── 备注卡片 ──────────────────────────────────────────────────
@Composable
private fun NotesCard(notes: String, onEditClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.StickyNote2, null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.detail_notes_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                if (notes.isNotEmpty()) {
                    Text(notes, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(stringResource(R.string.detail_notes_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onEditClick, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (notes.isEmpty()) Icons.Default.Add else Icons.Default.Edit,
                    contentDescription = if (notes.isEmpty())
                        stringResource(R.string.detail_notes_add)
                    else stringResource(R.string.detail_notes_edit),
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ── 备注编辑弹窗 ──────────────────────────────────────────────
@Composable
private fun NotesEditDialog(
    currentNotes: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit
) {
    var notes by remember { mutableStateOf(currentNotes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notes_dialog_title)) },
        text  = {
            OutlinedTextField(
                value           = notes,
                onValueChange   = { notes = it },
                label           = { Text(stringResource(R.string.notes_dialog_label)) },
                placeholder     = { Text(stringResource(R.string.notes_dialog_hint)) },
                minLines        = 4,
                maxLines        = 8,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                modifier        = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(notes) }) {
                Text(stringResource(R.string.notes_dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.notes_dialog_cancel)) }
        }
    )
}

// ── 视频播放器 ────────────────────────────────────────────────
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
                val r = MediaMetadataRetriever()
                r.setDataSource(videoPath)
                val bmp = r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                r.release(); bmp
            } catch (e: Exception) { null }
        }
    }

    val exoPlayer = remember(videoPath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoPath)); prepare(); playWhenReady = false
        }
    }
    LaunchedEffect(isPlaying) { exoPlayer.playWhenReady = isPlaying }
    DisposableEffect(videoPath) {
        onDispose {
            exoPlayer.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    if (isFullscreen) {
        Dialog(
            onDismissRequest = {
                isFullscreen = false
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            },
            properties = DialogProperties(usePlatformDefaultWidth = false,
                dismissOnBackPress = true, dismissOnClickOutside = false)
        ) {
            LaunchedEffect(Unit) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AndroidView(
                    factory  = { ctx -> PlayerView(ctx).apply { player = exoPlayer; useController = true } },
                    update   = { it.player = exoPlayer },
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(onClick = {
                    isFullscreen = false
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Icon(Icons.Default.FullscreenExit, null, tint = Color.White)
                }
            }
        }
    }

    if (!isFullscreen) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth()) {
                if (!isPlaying) {
                    Box(Modifier.fillMaxWidth().height(220.dp), Alignment.Center) {
                        if (thumbnail != null) {
                            Image(thumbnail!!.asImageBitmap(), null,
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
                        } else {
                            Box(Modifier.fillMaxSize().background(Color.Black))
                        }
                        IconButton(onClick = { isPlaying = true }, modifier = Modifier.size(72.dp)) {
                            Icon(Icons.Default.PlayCircle, null,
                                modifier = Modifier.size(72.dp), tint = Color.White)
                        }
                    }
                    Surface(Modifier.align(Alignment.BottomStart).padding(8.dp),
                        color = Color.Black.copy(alpha = 0.55f), shape = MaterialTheme.shapes.small) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Videocam, null, tint = Color.White,
                                modifier = Modifier.size(14.dp))
                            Text(stringResource(R.string.detail_video_label),
                                color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                } else {
                    AndroidView(
                        factory  = { ctx -> PlayerView(ctx).apply { player = exoPlayer; useController = true } },
                        update   = { it.player = exoPlayer },
                        modifier = Modifier.fillMaxWidth().height(220.dp)
                    )
                    IconButton(onClick = { isFullscreen = true },
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                        Icon(Icons.Default.Fullscreen, null, tint = Color.White)
                    }
                }
            }
        }
    }
}

// ── 音频播放器 ────────────────────────────────────────────────
@Composable
private fun AudioPlayerCard(
    label: String, path: String, playState: AudioPlayState,
    onTogglePlay: () -> Unit, onStop: () -> Unit, onSeek: (Int) -> Unit
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
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Mic, null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                if (isThisPlaying || isThisPaused) {
                    IconButton(onClick = onStop) {
                        Icon(Icons.Default.Stop, null, tint = MaterialTheme.colorScheme.error)
                    }
                }
                IconButton(onClick = onTogglePlay) {
                    Icon(
                        imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            if (totalMs > 0) {
                Spacer(Modifier.height(8.dp))
                Slider(value = if (totalMs > 0) currentMs.toFloat() / totalMs else 0f,
                    onValueChange = { onSeek((it * totalMs).toInt()) },
                    modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatMs(currentMs), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatMs(totalMs), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun formatMs(ms: Int): String {
    val sec = ms / 1000; val min = sec / 60; val rem = sec % 60
    return "%d:%02d".format(min, rem)
}