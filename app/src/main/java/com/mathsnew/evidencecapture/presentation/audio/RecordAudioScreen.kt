// app/src/main/java/com/mathsnew/evidencecapture/presentation/audio/RecordAudioScreen.kt
// 修改文件 - Kotlin

package com.mathsnew.evidencecapture.presentation.audio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mathsnew.evidencecapture.R
import com.mathsnew.evidencecapture.domain.model.DisguiseMode
import com.mathsnew.evidencecapture.presentation.capture.SnapshotCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordAudioScreen(
    onSaved: (String) -> Unit,
    onBack: () -> Unit,
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
                        onClick = {
                            viewModel.cancelAndDelete()
                            onBack()
                        },
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

            RecordingStatusArea(uiState = uiState, durationSeconds = durationSeconds)

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

@Composable
private fun RecordingStatusArea(uiState: AudioUiState, durationSeconds: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when (uiState) {
            is AudioUiState.Idle -> {
                Icon(
                    imageVector        = Icons.Default.Mic,
                    contentDescription = null,
                    modifier           = Modifier.size(80.dp),
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Text(
                    text  = stringResource(R.string.audio_status_idle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is AudioUiState.Recording -> {
                Icon(
                    imageVector        = Icons.Default.FiberManualRecord,
                    contentDescription = null,
                    modifier           = Modifier.size(80.dp),
                    tint               = MaterialTheme.colorScheme.error
                )
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

@Composable
private fun DisguisePickerDialog(
    onSelect:  (DisguiseMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.disguise_picker_title)) },
        text = {
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

// @Composable 扩展以便在 Composable 内调用 stringResource
@Composable
private fun DisguiseMode.displayName() = when (this) {
    DisguiseMode.MUSIC      -> stringResource(R.string.disguise_music)
    DisguiseMode.CALCULATOR -> stringResource(R.string.disguise_calculator)
    DisguiseMode.CALL       -> stringResource(R.string.disguise_call)
    DisguiseMode.NEWS       -> stringResource(R.string.disguise_news)
    DisguiseMode.NONE       -> ""
}