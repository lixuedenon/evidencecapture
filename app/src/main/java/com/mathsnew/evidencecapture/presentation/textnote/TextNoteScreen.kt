// app/src/main/java/com/mathsnew/evidencecapture/presentation/textnote/TextNoteScreen.kt
// 修改文件 - Kotlin

package com.mathsnew.evidencecapture.presentation.textnote

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.mathsnew.evidencecapture.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class,
    ExperimentalLayoutApi::class)
@Composable
fun TextNoteScreen(
    onSaved: (String) -> Unit,
    onBack:  () -> Unit,
    viewModel: TextNoteViewModel = hiltViewModel()
) {
    val uiState      by viewModel.uiState.collectAsState()
    val isListening  by viewModel.isListening.collectAsState()
    val voicePartial by viewModel.voicePartialResult.collectAsState()

    var content     by remember { mutableStateOf("") }
    var title       by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf("") }

    val audioPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    // 预设标签（多语言）
    val tagOptions = listOf(
        stringResource(R.string.tag_rent),
        stringResource(R.string.tag_traffic),
        stringResource(R.string.tag_workplace),
        stringResource(R.string.tag_fraud),
        stringResource(R.string.tag_safety),
        stringResource(R.string.tag_family),
        stringResource(R.string.tag_other)
    )

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
                title = { Text(stringResource(R.string.textnote_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.textnote_back))
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
                    onClick  = onBack,
                    modifier = Modifier.weight(1f).height(52.dp),
                    enabled  = uiState !is TextNoteUiState.Saving
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.textnote_cancel))
                }
                Button(
                    onClick  = { viewModel.saveNote(content, selectedTag, title) },
                    modifier = Modifier.weight(1f).height(52.dp),
                    enabled  = uiState !is TextNoteUiState.Saving && content.isNotBlank()
                ) {
                    if (uiState is TextNoteUiState.Saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.textnote_save))
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
                value         = title,
                onValueChange = { title = it },
                label         = { Text(stringResource(R.string.textnote_field_title)) },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true
            )

            OutlinedTextField(
                value         = displayContent,
                onValueChange = { content = it },
                label         = { Text(stringResource(R.string.textnote_field_content)) },
                modifier      = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                maxLines      = 12,
                trailingIcon  = {
                    IconButton(
                        onClick = {
                            if (!audioPermission.status.isGranted) {
                                audioPermission.launchPermissionRequest()
                                return@IconButton
                            }
                            if (isListening) viewModel.stopVoiceInput()
                            else viewModel.startVoiceInput()
                        }
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.Stop
                            else Icons.Default.Mic,
                            contentDescription = if (isListening)
                                stringResource(R.string.textnote_voice_stop)
                            else stringResource(R.string.textnote_voice_start),
                            tint = if (isListening) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                },
                supportingText = if (isListening) {
                    { Text(stringResource(R.string.textnote_listening),
                        color = MaterialTheme.colorScheme.error) }
                } else null
            )

            Text(
                text  = stringResource(R.string.textnote_tag_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement   = Arrangement.spacedBy(8.dp)
            ) {
                tagOptions.forEach { tag ->
                    FilterChip(
                        selected = selectedTag == tag,
                        onClick  = { selectedTag = if (selectedTag == tag) "" else tag },
                        label    = { Text(tag) }
                    )
                }
            }

            if (uiState is TextNoteUiState.Error) {
                Text(
                    text  = (uiState as TextNoteUiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}