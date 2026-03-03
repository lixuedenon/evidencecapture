// app/src/main/java/com/mathsnew/evidencecapture/presentation/audio/RecordAudioScreen.kt
// Kotlin - 表现层，录音取证界面，含伪装模式切换入口

package com.mathsnew.evidencecapture.presentation.audio

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mathsnew.evidencecapture.domain.model.DisguiseMode
import com.mathsnew.evidencecapture.presentation.capture.SnapshotCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordAudioScreen(
    onSaved: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: RecordAudioViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val disguiseMode by viewModel.disguiseMode.collectAsState()
    val snapshot by viewModel.snapshot.collectAsState()
    val durationSeconds by viewModel.durationSeconds.collectAsState()

    var tag by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var showDisguisePicker by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        if (uiState is AudioUiState.Saved) {
            onSaved((uiState as AudioUiState.Saved).evidenceId)
        }
    }

    if (disguiseMode != DisguiseMode.NONE && uiState is AudioUiState.Recording) {
        DisguiseScreen(
            mode = disguiseMode,
            onReveal = { viewModel.setDisguiseMode(DisguiseMode.NONE) }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("录音取证") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showDisguisePicker = true },
                        enabled = uiState is AudioUiState.Recording
                    ) {
                        Icon(Icons.Default.VisibilityOff, contentDescription = "伪装界面")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            RecordingStatusArea(uiState = uiState, durationSeconds = durationSeconds)

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("标题（可选）") },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState is AudioUiState.Idle,
                singleLine = true
            )

            when (uiState) {
                is AudioUiState.Idle -> {
                    Button(
                        onClick = { viewModel.startRecording() },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("开始录音")
                    }
                }
                is AudioUiState.Recording -> {
                    Button(
                        onClick = { viewModel.stopAndSave(title = title) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("停止并保存")
                    }
                }
                is AudioUiState.Saving -> {
                    CircularProgressIndicator()
                    Text("正在保存...")
                }
                else -> {}
            }

            if (uiState is AudioUiState.Error) {
                Text(
                    text = (uiState as AudioUiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            snapshot?.let { SnapshotCard(snapshot = it) }
        }
    }

    if (showDisguisePicker) {
        DisguisePickerDialog(
            onSelect = { mode ->
                viewModel.setDisguiseMode(mode)
                showDisguisePicker = false
            },
            onDismiss = { showDisguisePicker = false }
        )
    }
}

@Composable
private fun RecordingStatusArea(uiState: AudioUiState, durationSeconds: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when (uiState) {
            is AudioUiState.Idle -> {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Text(
                    text = "点击开始录音",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is AudioUiState.Recording -> {
                Icon(
                    imageVector = Icons.Default.FiberManualRecord,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                val min = durationSeconds / 60
                val sec = durationSeconds % 60
                Text(
                    text = "%02d:%02d".format(min, sec),
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "录音中",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            else -> {}
        }
    }
}

@Composable
private fun DisguisePickerDialog(onSelect: (DisguiseMode) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择伪装界面") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DisguiseMode.entries
                    .filter { it != DisguiseMode.NONE }
                    .forEach { mode ->
                        TextButton(
                            onClick = { onSelect(mode) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(mode.displayName()) }
                    }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun DisguiseMode.displayName() = when (this) {
    DisguiseMode.MUSIC -> "音乐播放器"
    DisguiseMode.CALCULATOR -> "计算器"
    DisguiseMode.CALL -> "通话界面"
    DisguiseMode.NEWS -> "新闻阅读"
    DisguiseMode.NONE -> ""
}