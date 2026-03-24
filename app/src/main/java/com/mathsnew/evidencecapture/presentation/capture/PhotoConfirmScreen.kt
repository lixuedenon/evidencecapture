// app/src/main/java/com/mathsnew/evidencecapture/presentation/capture/PhotoConfirmScreen.kt
// 修改文件 - Kotlin

package com.mathsnew.evidencecapture.presentation.capture

import android.Manifest
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.mathsnew.evidencecapture.R
import com.mathsnew.evidencecapture.util.FileHelper
import java.io.File
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun PhotoConfirmScreen(
    evidenceId: String,
    photoPath:  String,
    onSaved:    (String) -> Unit,
    onBack:     () -> Unit,
    viewModel:  CaptureViewModel = hiltViewModel()
) {
    val context  = LocalContext.current
    val uiState  by viewModel.uiState.collectAsState()
    val snapshot by viewModel.snapshot.collectAsState()

    var selectedTag      by remember { mutableStateOf("") }
    var title            by remember { mutableStateOf("") }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var voiceNotePath    by remember { mutableStateOf("") }
    var voiceRecorder    by remember { mutableStateOf<MediaRecorder?>(null) }

    val audioPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    val tagOptions = listOf(
        stringResource(R.string.tag_rent),
        stringResource(R.string.tag_traffic),
        stringResource(R.string.tag_workplace),
        stringResource(R.string.tag_fraud),
        stringResource(R.string.tag_safety),
        stringResource(R.string.tag_family),
        stringResource(R.string.tag_other)
    )

    LaunchedEffect(uiState) {
        if (uiState is CaptureUiState.Saved)
            onSaved((uiState as CaptureUiState.Saved).evidenceId)
    }

    DisposableEffect(Unit) {
        onDispose { voiceRecorder?.release(); voiceRecorder = null }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.confirm_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.confirm_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor             = MaterialTheme.colorScheme.primary,
                    titleContentColor          = MaterialTheme.colorScheme.onPrimary,
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
                OutlinedButton(
                    onClick = {
                        voiceRecorder?.release(); voiceRecorder = null
                        File(photoPath).delete()
                        if (voiceNotePath.isNotEmpty()) File(voiceNotePath).delete()
                        viewModel.resetState(); onBack()
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                    enabled  = uiState !is CaptureUiState.Saving
                ) {
                    Icon(Icons.Default.Close, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.confirm_cancel))
                }
                Button(
                    onClick = {
                        // 如果忘记点停止录音，直接保存时先执行停止，保证录音内容完整
                        if (isRecordingVoice) {
                            val path = FileHelper.getVoiceNoteFile(context, evidenceId).absolutePath
                            stopVoiceNote(voiceRecorder, path) { savedPath ->
                                voiceNotePath = savedPath
                            }
                            voiceRecorder    = null
                            isRecordingVoice = false
                        }
                        viewModel.saveEvidence(
                            evidenceId    = evidenceId,
                            photoPath     = photoPath,
                            voiceNotePath = voiceNotePath,
                            tag           = selectedTag,
                            title         = title
                        )
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                    enabled  = uiState !is CaptureUiState.Saving
                ) {
                    if (uiState is CaptureUiState.Saving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Save, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.confirm_save))
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
                model              = photoPath,
                contentDescription = stringResource(R.string.filter_photo),
                contentScale       = ContentScale.Fit,
                modifier           = Modifier.fillMaxWidth().height(240.dp)
            )

            OutlinedTextField(
                value         = title,
                onValueChange = { title = it },
                label         = { Text(stringResource(R.string.confirm_field_title)) },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true
            )

            Text(stringResource(R.string.confirm_tag_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TagSelector(options = tagOptions, selected = selectedTag,
                onSelect = { selectedTag = if (selectedTag == it) "" else it })

            // 语音备注按钮行
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier          = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (voiceNotePath.isEmpty())
                        stringResource(R.string.confirm_voice_add)
                    else stringResource(R.string.confirm_voice_done),
                    style    = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        if (!audioPermission.status.isGranted) {
                            audioPermission.launchPermissionRequest(); return@IconButton
                        }
                        if (isRecordingVoice) {
                            val path = FileHelper.getVoiceNoteFile(context, evidenceId).absolutePath
                            stopVoiceNote(voiceRecorder, path) { voiceNotePath = it }
                            voiceRecorder = null; isRecordingVoice = false
                        } else {
                            val path = FileHelper.getVoiceNoteFile(context, evidenceId).absolutePath
                            voiceRecorder = startVoiceNote(context, path)
                            isRecordingVoice = true
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (isRecordingVoice) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isRecordingVoice)
                            stringResource(R.string.confirm_voice_stop)
                        else stringResource(R.string.confirm_voice_start),
                        tint = if (isRecordingVoice) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 录音中显示山形频谱动画
            if (isRecordingVoice) {
                VoiceSpectrumBar(recorder = voiceRecorder)
            }

            snapshot?.let { SnapshotCard(snapshot = it) }

            if (uiState is CaptureUiState.Error) {
                Text((uiState as CaptureUiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TagSelector(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement   = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { tag ->
            FilterChip(selected = selected == tag, onClick = { onSelect(tag) },
                label = { Text(tag) })
        }
    }
}

private fun startVoiceNote(context: Context, outputPath: String): MediaRecorder? {
    return try {
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            MediaRecorder(context)
        else @Suppress("DEPRECATION") MediaRecorder()
        recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(outputPath)
            prepare(); start()
        }
    } catch (e: Exception) {
        Log.e("PhotoConfirmScreen", "Voice note start failed", e); null
    }
}

private fun stopVoiceNote(recorder: MediaRecorder?, path: String, onPath: (String) -> Unit) {
    recorder ?: return
    try { recorder.stop(); onPath(path) }
    catch (e: Exception) { Log.w("PhotoConfirmScreen", "Voice note stop: ${e.message}") }
    finally { recorder.release() }
}

/**
 * 录音频谱动画：中间高两边低的山形分布
 * 每根柱子基础高度按高斯曲线预设，叠加实时音量驱动的整体振幅。
 * 有声音时整体拔高，保持山形；安静时收缩至底部微弱轮廓。
 */
@Composable
private fun VoiceSpectrumBar(
    recorder: MediaRecorder?,
    barColor: androidx.compose.ui.graphics.Color =
        androidx.compose.ui.graphics.Color(0xFFEF5350)
) {
    val barCount = 12

    // 山形权重：中间≈1.0，边缘≈0.15（高斯曲线）
    val mountainWeights = remember {
        FloatArray(barCount) { i ->
            val center = (barCount - 1) / 2f
            val dist   = Math.abs(i - center) / center
            Math.exp(-4.0 * dist * dist).toFloat()
        }
    }

    val barHeights = remember {
        Array(barCount) { i -> Animatable(mountainWeights[i] * 0.12f) }
    }

    LaunchedEffect(recorder) {
        while (true) {
            delay(80)
            val amplitude  = try { recorder?.maxAmplitude ?: 0 } catch (e: Exception) { 0 }
            val normalized = (amplitude / 32767f).coerceIn(0f, 1f)

            for (i in 0 until barCount) {
                val base   = mountainWeights[i]
                val target = if (normalized > 0.02f)
                    (base * normalized * 0.85f + base * 0.15f).coerceIn(0.06f, 1f)
                else
                    base * 0.15f + 0.04f
                barHeights[i].animateTo(target,
                    animationSpec = tween(durationMillis = 75, easing = LinearEasing))
            }
        }
    }

    val heights = barHeights.map { it.value }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 8.dp)
    ) {
        val totalWidth  = size.width
        val totalHeight = size.height
        val gap         = totalWidth * 0.025f
        val barWidth    = (totalWidth - gap * (barCount - 1)) / barCount
        val cornerR     = barWidth / 2f

        heights.forEachIndexed { i, heightFraction ->
            val barH = (totalHeight * heightFraction).coerceAtLeast(cornerR * 2)
            val left = i * (barWidth + gap)
            val top  = totalHeight - barH  // 从底部向上生长
            drawRoundRect(
                color        = barColor.copy(alpha = 0.88f),
                topLeft      = Offset(left, top),
                size         = Size(barWidth, barH),
                cornerRadius = CornerRadius(cornerR, cornerR)
            )
        }
    }
}