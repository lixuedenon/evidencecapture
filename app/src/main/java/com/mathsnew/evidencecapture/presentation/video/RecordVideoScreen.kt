// app/src/main/java/com/mathsnew/evidencecapture/presentation/video/RecordVideoScreen.kt
// Kotlin - 表现层，录像取证界面，支持 45 秒自动分段录制

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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
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
    val segmentIndex by viewModel.segmentIndex.collectAsState()
    val savedSegmentCount by viewModel.savedSegmentCount.collectAsState()

    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }

    // activeRecording 和 currentEvidenceId 需要在 Screen 层持有，
    // 因为 CameraX Recording 对象的生命周期由 Composable 管理
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var currentEvidenceId by remember { mutableStateOf("") }

    // 是否是自动分段触发的停止，用于区分 onRecordingStopped 的行为
    var isAutoSegment by remember { mutableStateOf(false) }

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

    // 向 ViewModel 注册录制控制回调，ViewModel 自动分段时通过回调操作 CameraX
    DisposableEffect(Unit) {
        viewModel.registerRecordingCallbacks(
            stopFn = {
                // ViewModel 触发自动分段停止
                isAutoSegment = true
                activeRecording?.stop()
                activeRecording = null
            },
            startFn = { nextEvidenceId ->
                // ViewModel 触发下一段开始
                currentEvidenceId = nextEvidenceId
                startVideoRecording(
                    context = context,
                    videoCapture = videoCapture,
                    evidenceId = nextEvidenceId,
                    onStarted = {
                        viewModel.onRecordingStarted(nextEvidenceId)
                    },
                    onRecording = { recording ->
                        activeRecording = recording
                    },
                    onFinalized = { videoPath ->
                        val autoSeg = isAutoSegment
                        isAutoSegment = false
                        viewModel.onRecordingStopped(
                            evidenceId = currentEvidenceId,
                            videoPath = videoPath,
                            isAutoSegment = autoSeg
                        )
                    },
                    onError = { error ->
                        Log.e("RecordVideoScreen", "Auto-segment recording error: $error")
                        viewModel.resetState()
                    }
                )
            }
        )
        onDispose {
            viewModel.clearRecordingCallbacks()
        }
    }

    // 标题或标签变化时同步到 ViewModel，自动分段保存时使用
    LaunchedEffect(title, tag) {
        viewModel.updateSessionMeta(tag = tag, title = title)
    }

    LaunchedEffect(uiState) {
        if (uiState is VideoUiState.Saved) {
            onSaved((uiState as VideoUiState.Saved).evidenceId)
        }
    }

    val isRecording = uiState is VideoUiState.Recording
    val isReadyToSave = uiState is VideoUiState.ReadyToSave
    val remainingSeconds = RecordVideoViewModel.SEGMENT_MAX_SECONDS - durationSeconds

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
            // 手动停止后显示保存/取消按钮
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
                    // 手动停止后显示完成提示
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
                        // 有自动保存的分段时告知用户
                        if (savedSegmentCount > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "已自动保存 $savedSegmentCount 段，当前为第 $segmentIndex 段",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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

                // 录制中顶部状态栏：显示段序号 + 已录时长 + 剩余倒计时
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
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // 录制指示红点
                            Icon(
                                Icons.Default.FiberManualRecord,
                                contentDescription = null,
                                tint = Color.Red,
                                modifier = Modifier.size(12.dp)
                            )
                            // 已录时长
                            Text(
                                text = "%02d:%02d".format(min, sec),
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "·",
                                color = Color.White.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            // 段序号
                            Text(
                                text = "第 $segmentIndex 段",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "·",
                                color = Color.White.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            // 剩余倒计时，临近结束时变红提醒
                            Text(
                                text = "剩 ${remainingSeconds}s",
                                color = if (remainingSeconds <= 10) Color.Red else Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = if (remainingSeconds <= 10) 14.sp else 12.sp
                            )
                        }
                    }

                    // 有自动保存记录时在底部显示提示
                    if (savedSegmentCount > 0) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 120.dp),
                            color = Color.Black.copy(alpha = 0.45f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "已自动保存 $savedSegmentCount 段",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
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
                                        isAutoSegment = false
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
                                                val autoSeg = isAutoSegment
                                                isAutoSegment = false
                                                viewModel.onRecordingStopped(
                                                    evidenceId = currentEvidenceId,
                                                    videoPath = videoPath,
                                                    isAutoSegment = autoSeg
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
                                        // 用户手动停止：标记非自动分段
                                        isAutoSegment = false
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
                    Log.i("RecordVideoScreen", "Recording started: $evidenceId")
                    onStarted()
                }
                is VideoRecordEvent.Finalize -> {
                    if (event.hasError()) {
                        Log.e("RecordVideoScreen", "Finalize error: ${event.error}")
                        onError("录制错误: ${event.error}")
                    } else {
                        val savedPath = outputFile.absolutePath
                        Log.i("RecordVideoScreen", "Finalized: $savedPath")
                        onFinalized(savedPath)
                    }
                }
                else -> {}
            }
        }
    onRecording(recording)
}