// app/src/main/java/com/mathsnew/evidencecapture/presentation/textnote/TextNoteScreen.kt
// Kotlin - 表现层，文字记录界面，支持键盘输入与语音转文字输入

package com.mathsnew.evidencecapture.presentation.textnote

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

private val TAG_OPTIONS = listOf("租房纠纷", "交通事故", "职场纠纷", "消费欺诈", "人身安全", "家庭纠纷", "其他")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class,
    ExperimentalLayoutApi::class)
@Composable
fun TextNoteScreen(
    onSaved: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: TextNoteViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val voicePartial by viewModel.voicePartialResult.collectAsState()

    var content by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf("") }

    val audioPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    val displayContent = if (isListening && voicePartial.isNotEmpty())
        content + voicePartial else content

    LaunchedEffect(isListening) {
        if (!isListening && voicePartial.isNotEmpty()) {
            content = content + voicePartial
            viewModel.clearVoiceResult()
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is TextNoteUiState.Saved) {
            onSaved((uiState as TextNoteUiState.Saved).evidenceId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("文字记录") },
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
                // 取消：直接回主页
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    enabled = uiState !is TextNoteUiState.Saving
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("取消")
                }
                // 保存：保存记录，回主页
                Button(
                    onClick = { viewModel.saveNote(content, selectedTag, title) },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    enabled = uiState !is TextNoteUiState.Saving && content.isNotBlank()
                ) {
                    if (uiState is TextNoteUiState.Saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("保存记录")
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
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("标题（可选）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = displayContent,
                onValueChange = { content = it },
                label = { Text("记录内容") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp),
                maxLines = 12,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (!audioPermission.status.isGranted) {
                                audioPermission.launchPermissionRequest()
                                return@IconButton
                            }
                            if (isListening) {
                                viewModel.stopVoiceInput()
                            } else {
                                viewModel.startVoiceInput()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.Stop
                            else Icons.Default.Mic,
                            contentDescription = if (isListening) "停止语音" else "语音输入",
                            tint = if (isListening) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                },
                supportingText = if (isListening) {
                    { Text("正在聆听...", color = MaterialTheme.colorScheme.error) }
                } else null
            )

            Text(
                text = "场景标签",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TAG_OPTIONS.forEach { tag ->
                    FilterChip(
                        selected = selectedTag == tag,
                        onClick = { selectedTag = if (selectedTag == tag) "" else tag },
                        label = { Text(tag) }
                    )
                }
            }

            if (uiState is TextNoteUiState.Error) {
                Text(
                    text = (uiState as TextNoteUiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}