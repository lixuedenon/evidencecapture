// app/src/main/java/com/mathsnew/evidencecapture/presentation/capture/PhotoConfirmScreen.kt
// Kotlin - 表现层，照片确认页，支持查看环境数据、添加语音备注、选择标签后保存

package com.mathsnew.evidencecapture.presentation.capture

import android.Manifest
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
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
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.mathsnew.evidencecapture.util.FileHelper
import java.io.File

private val TAG_OPTIONS = listOf("租房纠纷", "交通事故", "职场纠纷", "消费欺诈", "人身安全", "家庭纠纷", "其他")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun PhotoConfirmScreen(
    evidenceId: String,
    photoPath: String,
    onSaved: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: CaptureViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snapshot by viewModel.snapshot.collectAsState()

    var selectedTag by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var voiceNotePath by remember { mutableStateOf("") }
    var voiceRecorder by remember { mutableStateOf<MediaRecorder?>(null) }

    val audioPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    LaunchedEffect(uiState) {
        if (uiState is CaptureUiState.Saved) {
            onSaved((uiState as CaptureUiState.Saved).evidenceId)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceRecorder?.release()
            voiceRecorder = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("确认证据") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 取消：删除文件，回主页
                OutlinedButton(
                    onClick = {
                        voiceRecorder?.release()
                        voiceRecorder = null
                        File(photoPath).delete()
                        if (voiceNotePath.isNotEmpty()) File(voiceNotePath).delete()
                        viewModel.resetState()
                        onBack()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    enabled = uiState !is CaptureUiState.Saving
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("取消")
                }
                // 保存：保存记录，回主页
                Button(
                    onClick = {
                        viewModel.saveEvidence(
                            evidenceId = evidenceId,
                            photoPath = photoPath,
                            voiceNotePath = voiceNotePath,
                            tag = selectedTag,
                            title = title
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    enabled = uiState !is CaptureUiState.Saving
                ) {
                    if (uiState is CaptureUiState.Saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("保存证据")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AsyncImage(
                model = photoPath,
                contentDescription = "拍摄照片",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("标题（可选）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text(
                text = "场景标签",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TagSelector(
                options = TAG_OPTIONS,
                selected = selectedTag,
                onSelect = { selectedTag = if (selectedTag == it) "" else it }
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (voiceNotePath.isEmpty()) "添加语音备注（可选）"
                    else "语音备注已录制",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        if (!audioPermission.status.isGranted) {
                            audioPermission.launchPermissionRequest()
                            return@IconButton
                        }
                        if (isRecordingVoice) {
                            val path = FileHelper.getVoiceNoteFile(context, evidenceId).absolutePath
                            stopVoiceNote(voiceRecorder, path) { savedPath ->
                                voiceNotePath = savedPath
                            }
                            voiceRecorder = null
                            isRecordingVoice = false
                        } else {
                            val path = FileHelper.getVoiceNoteFile(context, evidenceId).absolutePath
                            voiceRecorder = startVoiceNote(context, path)
                            isRecordingVoice = true
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (isRecordingVoice) Icons.Default.Stop
                        else Icons.Default.Mic,
                        contentDescription = if (isRecordingVoice) "停止录音" else "开始录音",
                        tint = if (isRecordingVoice) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                }
            }

            snapshot?.let { SnapshotCard(snapshot = it) }

            if (uiState is CaptureUiState.Error) {
                Text(
                    text = (uiState as CaptureUiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TagSelector(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { tag ->
            FilterChip(
                selected = selected == tag,
                onClick = { onSelect(tag) },
                label = { Text(tag) }
            )
        }
    }
}

private fun startVoiceNote(context: Context, outputPath: String): MediaRecorder? {
    return try {
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(outputPath)
            prepare()
            start()
        }
    } catch (e: Exception) {
        Log.e("PhotoConfirmScreen", "Voice note start failed", e)
        null
    }
}

private fun stopVoiceNote(recorder: MediaRecorder?, path: String, onPath: (String) -> Unit) {
    recorder ?: return
    try {
        recorder.stop()
        onPath(path)
    } catch (e: Exception) {
        Log.w("PhotoConfirmScreen", "Voice note stop exception: ${e.message}")
    } finally {
        recorder.release()
    }
}