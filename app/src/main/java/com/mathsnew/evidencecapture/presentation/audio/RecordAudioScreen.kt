// app/src/main/java/com/mathsnew/evidencecapture/presentation/audio/RecordAudioScreen.kt
// 修改文件 - Kotlin

package com.mathsnew.evidencecapture.presentation.audio

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mathsnew.evidencecapture.R
import com.mathsnew.evidencecapture.domain.model.DisguiseMode
import com.mathsnew.evidencecapture.presentation.capture.SnapshotCard
import kotlinx.coroutines.delay
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordAudioScreen(
    onSaved: (String) -> Unit,
    onBack:  () -> Unit,
    viewModel: RecordAudioViewModel = hiltViewModel()
) {
    val uiState         by viewModel.uiState.collectAsState()
    val disguiseMode    by viewModel.disguiseMode.collectAsState()
    val snapshot        by viewModel.snapshot.collectAsState()
    val durationSeconds by viewModel.durationSeconds.collectAsState()

    var tag   by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var showDisguisePicker by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        if (uiState is AudioUiState.Saved) {
            onSaved((uiState as AudioUiState.Saved).evidenceId)
        }
    }

    if (disguiseMode != DisguiseMode.NONE && uiState is AudioUiState.Recording) {
        DisguiseScreen(
            mode     = disguiseMode,
            onReveal = { viewModel.setDisguiseMode(DisguiseMode.NONE) }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.audio_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState is AudioUiState.Idle) onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.audio_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick  = { showDisguisePicker = true },
                        enabled  = uiState is AudioUiState.Recording
                    ) {
                        Icon(Icons.Default.VisibilityOff,
                            contentDescription = stringResource(R.string.audio_disguise))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor             = MaterialTheme.colorScheme.primary,
                    titleContentColor          = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor     = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            if (uiState is AudioUiState.ReadyToSave) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick  = { viewModel.cancelAndDelete(); onBack() },
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.audio_cancel))
                    }
                    Button(
                        onClick  = { viewModel.saveRecording(tag = tag, title = title) },
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.audio_save))
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            RecordingStatusArea(
                uiState         = uiState,
                durationSeconds = durationSeconds
            )

            OutlinedTextField(
                value         = title,
                onValueChange = { title = it },
                label         = { Text(stringResource(R.string.audio_field_title)) },
                modifier      = Modifier.fillMaxWidth(),
                enabled       = uiState is AudioUiState.Idle ||
                        uiState is AudioUiState.ReadyToSave,
                singleLine    = true
            )

            when (uiState) {
                is AudioUiState.Idle -> {
                    Button(
                        onClick  = { viewModel.startRecording() },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.audio_start))
                    }
                }
                is AudioUiState.Recording -> {
                    Button(
                        onClick  = { viewModel.stopRecording() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.audio_stop))
                    }
                }
                is AudioUiState.Stopping -> {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.audio_processing))
                }
                is AudioUiState.Saving -> {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.audio_saving))
                }
                is AudioUiState.ReadyToSave -> {
                    Text(
                        text  = stringResource(R.string.audio_ready_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {}
            }

            if (uiState is AudioUiState.Error) {
                Text(
                    text  = (uiState as AudioUiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            snapshot?.let { SnapshotCard(snapshot = it) }
        }
    }

    if (showDisguisePicker) {
        DisguisePickerDialog(
            onSelect  = { mode ->
                viewModel.setDisguiseMode(mode)
                showDisguisePicker = false
            },
            onDismiss = { showDisguisePicker = false }
        )
    }
}

// ── 录音状态区域 ──────────────────────────────────────────────
@Composable
private fun RecordingStatusArea(uiState: AudioUiState, durationSeconds: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (uiState) {
            is AudioUiState.Idle -> {
                Icon(
                    imageVector        = Icons.Default.Mic,
                    contentDescription = null,
                    modifier           = Modifier.size(80.dp),
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant
                        .copy(alpha = 0.4f)
                )
                Text(
                    text  = stringResource(R.string.audio_status_idle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is AudioUiState.Recording -> {
                val min = durationSeconds / 60
                val sec = durationSeconds % 60
                Text(
                    text  = "%02d:%02d".format(min, sec),
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text  = stringResource(R.string.audio_status_recording),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                // 山形频谱动画（模拟振幅）
                AudioSpectrumBar(
                    isRecording = true,
                    barColor    = MaterialTheme.colorScheme.error
                )
            }
            is AudioUiState.ReadyToSave -> {
                Icon(
                    imageVector        = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier           = Modifier.size(80.dp),
                    tint               = MaterialTheme.colorScheme.primary
                )
                Text(
                    text  = stringResource(R.string.audio_status_done),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            else -> {}
        }
    }
}

/**
 * 山形频谱动画（模拟振幅）
 * 12根柱子，中间高两边低的高斯曲线分布
 * 每根柱子独立随机跳动，整体呈现真实录音频谱感
 */
@Composable
private fun AudioSpectrumBar(
    isRecording: Boolean,
    barColor: androidx.compose.ui.graphics.Color =
        androidx.compose.ui.graphics.Color(0xFFEF5350)
) {
    val barCount = 20

    // 山形基础权重：中间≈1.0，边缘≈0.15
    val mountainWeights = remember {
        FloatArray(barCount) { i ->
            val center = (barCount - 1) / 2f
            val dist   = Math.abs(i - center) / center
            Math.exp(-3.5 * dist * dist).toFloat()
        }
    }

    val barHeights = remember {
        Array(barCount) { i -> Animatable(mountainWeights[i] * 0.15f) }
    }

    LaunchedEffect(isRecording) {
        if (!isRecording) return@LaunchedEffect
        while (true) {
            delay(100)
            for (i in 0 until barCount) {
                val base   = mountainWeights[i]
                val random = Random.nextFloat()
                // 中间柱子振幅范围更大，边缘柱子更平稳
                val target = (base * (0.3f + random * 0.7f)).coerceIn(0.08f, 1f)
                barHeights[i].animateTo(
                    targetValue   = target,
                    animationSpec = tween(durationMillis = 90, easing = LinearEasing)
                )
            }
        }
    }

    val heights = barHeights.map { it.value }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(horizontal = 24.dp)
    ) {
        val totalWidth  = size.width
        val totalHeight = size.height
        val barWidth    = 2.5.dp.toPx()   // 细柱子
        val gap         = (totalWidth - barWidth * barCount) / (barCount - 1)

        heights.forEachIndexed { i, heightFraction ->
            val barH    = (totalHeight * heightFraction).coerceAtLeast(4.dp.toPx())
            val left    = i * (barWidth + gap)
            val top     = (totalHeight - barH) / 2f   // 居中生长（上下对称）
            val cornerR = barWidth / 2f
            drawRoundRect(
                color        = barColor.copy(alpha = 0.75f),
                topLeft      = Offset(left, top),
                size         = Size(barWidth, barH),
                cornerRadius = CornerRadius(cornerR, cornerR)
            )
        }
    }
}

@Composable
private fun DisguisePickerDialog(
    onSelect:  (DisguiseMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.disguise_picker_title)) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DisguiseMode.entries
                    .filter { it != DisguiseMode.NONE }
                    .forEach { mode ->
                        TextButton(
                            onClick  = { onSelect(mode) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(mode.displayName()) }
                    }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.disguise_cancel))
            }
        }
    )
}

@Composable
private fun DisguiseMode.displayName() = when (this) {
    DisguiseMode.MUSIC      -> stringResource(R.string.disguise_music)
    DisguiseMode.CALCULATOR -> stringResource(R.string.disguise_calculator)
    DisguiseMode.CALL       -> stringResource(R.string.disguise_call)
    DisguiseMode.NEWS       -> stringResource(R.string.disguise_news)
    DisguiseMode.NONE       -> ""
}