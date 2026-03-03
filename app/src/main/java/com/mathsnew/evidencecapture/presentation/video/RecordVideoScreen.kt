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
    // 从独立 StateFlow 获取时长，不依赖 uiState 字段
    val durationSeconds by viewModel.durationSeconds.collectAsState()

    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    // 记录当次录制的 evidenceId，onRecordingStopped 时需要传入
    var currentEvidenceId by remember { mutableStateOf("") }

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
    var showExitDialog by remember { mutableStateOf(false) }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("正在录制中") },
            text = { Text("停止录制并放弃本次录像？") },
            confirmButton = {
                TextButton(onClick = {
                    activeRecording?.stop()
                    activeRecording = null
                    viewModel.resetState()
                    showExitDialog = false
                    onBack()
                }) { Text("放弃录制") }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("继续录制") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("录视频取证") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isRecording) showExitDialog = true else onBack()
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
                // 相机预览
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

                // 录制中顶部状态栏，使用 durationSeconds StateFlow
                if (uiState is VideoUiState.Recording) {
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

                // 底部录制按钮
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
                                            // 传入 evidenceId 和 videoPath，修复之前只传一个参数的 bug
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
                        is VideoUiState.Saving -> {
                            CircularProgressIndicator(color = Color.White)
                        }
                        else -> {}
                    }
                }

                // 错误提示
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