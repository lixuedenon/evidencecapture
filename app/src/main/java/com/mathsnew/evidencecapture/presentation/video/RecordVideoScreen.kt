// app/src/main/java/com/mathsnew/evidencecapture/presentation/video/RecordVideoScreen.kt
// Kotlin - 表现层，录视频取证界面

package com.mathsnew.evidencecapture.presentation.video

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.mathsnew.evidencecapture.util.EvidenceIdGenerator
import com.mathsnew.evidencecapture.util.FileHelper
import java.util.concurrent.Executor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun RecordVideoScreen(
    onSaved: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: RecordVideoViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val durationSeconds by viewModel.durationSeconds.collectAsState()

    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var currentEvidenceId by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("") }

    val permissions = rememberMultiplePermissionsState(
        listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    )

    LaunchedEffect(Unit) {
        if (!permissions.allPermissionsGranted) {
            permissions.launchMultiplePermissionRequest()
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is VideoUiState.Saved) {
            onSaved((uiState as VideoUiState.Saved).evidenceId)
        }
    }

    val isRecording = uiState is VideoUiState.Recording
    val isReadyToSave = uiState is VideoUiState.ReadyToSave

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("录视频取证") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState is VideoUiState.Idle) onBack()
                    }) {
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
            // 录完后显示保存/取消按钮
            if (isReadyToSave) {
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
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("取消")
                    }
                    Button(
                        onClick = { viewModel.saveRecording(tag = tag, title = title) },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("保存录像")
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!permissions.allPermissionsGranted) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("需要相机和麦克风权限才能录像")
                    Button(onClick = { permissions.launchMultiplePermissionRequest() }) {
                        Text("授予权限")
                    }
                }
            } else {
                // 相机预览（录完后隐藏，改为显示完成状态）
                if (!isReadyToSave) {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            bindVideoCamera(ctx, lifecycleOwner, previewView) { capture ->
                                videoCapture = capture
                            }
                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // 录完后显示完成提示
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "录像完成",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "请选择保存或取消",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("标题（可选）") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }

                // 录制中顶部时长状态栏
                if (isRecording) {
                    val min = durationSeconds / 60
                    val sec = durationSeconds % 60
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp),
                        color = Color.Black.copy(alpha = 0.55f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.FiberManualRecord,
                                contentDescription = null,
                                tint = Color.Red,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "%02d:%02d".format(min, sec),
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // 录制按钮（仅 Idle 和 Recording 时显示）
                if (!isReadyToSave && uiState !is VideoUiState.Saving) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 48.dp)
                    ) {
                        when (uiState) {
                            is VideoUiState.Idle -> {
                                FloatingActionButton(
                                    onClick = {
                                        val evidenceId = EvidenceIdGenerator.generate()
                                        currentEvidenceId = evidenceId
                                        startVideoRecording(
                                            context = context,
                                            videoCapture = videoCapture,
                                            evidenceId = evidenceId,
                                            onStarted = {
                                                viewModel.onRecordingStarted(evidenceId)
                                            },
                                            onRecording = { recording ->
                                                activeRecording = recording
                                            },
                                            onFinalized = { videoPath ->
                                                viewModel.onRecordingStopped(
                                                    evidenceId = currentEvidenceId,
                                                    videoPath = videoPath
                                                )
                                            },
                                            onError = { error ->
                                                Log.e("RecordVideoScreen", "Recording error: $error")
                                                viewModel.resetState()
                                            }
                                        )
                                    },
                                    containerColor = Color.Red
                                ) {
                                    Icon(
                                        Icons.Default.FiberManualRecord,
                                        contentDescription = "开始录制",
                                        modifier = Modifier.size(32.dp),
                                        tint = Color.White
                                    )
                                }
                            }
                            is VideoUiState.Recording -> {
                                FloatingActionButton(
                                    onClick = {
                                        activeRecording?.stop()
                                        activeRecording = null
                                    },
                                    containerColor = Color.White
                                ) {
                                    Icon(
                                        Icons.Default.Stop,
                                        contentDescription = "停止录制",
                                        modifier = Modifier.size(32.dp),
                                        tint = Color.Red
                                    )
                                }
                            }
                            else -> {}
                        }
                    }
                }

                if (uiState is VideoUiState.Saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (uiState is VideoUiState.Error) {
                    Snackbar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 120.dp, start = 16.dp, end = 16.dp)
                    ) {
                        Text((uiState as VideoUiState.Error).message)
                    }
                }
            }
        }
    }
}

private fun bindVideoCamera(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    onVideoCaptureReady: (VideoCapture<Recorder>) -> Unit
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD))
            .build()
        val videoCapture = VideoCapture.withOutput(recorder)
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                videoCapture
            )
            onVideoCaptureReady(videoCapture)
        } catch (e: Exception) {
            Log.e("RecordVideoScreen", "Video camera bind failed", e)
        }
    }, ContextCompat.getMainExecutor(context))
}

@androidx.annotation.OptIn(androidx.camera.video.ExperimentalPersistentRecording::class)
private fun startVideoRecording(
    context: Context,
    videoCapture: VideoCapture<Recorder>?,
    evidenceId: String,
    onStarted: () -> Unit,
    onRecording: (Recording) -> Unit,
    onFinalized: (String) -> Unit,
    onError: (String) -> Unit
) {
    if (videoCapture == null) {
        onError("Camera not ready")
        return
    }
    val outputFile = FileHelper.getVideoFile(context, evidenceId)
    val outputOptions = FileOutputOptions.Builder(outputFile).build()
    val executor: Executor = ContextCompat.getMainExecutor(context)

    val recording = videoCapture.output
        .prepareRecording(context, outputOptions)
        .apply {
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                withAudioEnabled()
            }
        }
        .start(executor) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    Log.i("RecordVideoScreen", "Recording started")
                    onStarted()
                }
                is VideoRecordEvent.Finalize -> {
                    if (event.hasError()) {
                        Log.e("RecordVideoScreen", "Recording finalize error: ${event.error}")
                        onError("录制错误: ${event.error}")
                    } else {
                        val savedPath = outputFile.absolutePath
                        Log.i("RecordVideoScreen", "Recording finalized: $savedPath")
                        onFinalized(savedPath)
                    }
                }
                else -> {}
            }
        }
    onRecording(recording)
}