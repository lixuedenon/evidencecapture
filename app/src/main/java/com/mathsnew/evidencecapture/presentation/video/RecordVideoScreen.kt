// app/src/main/java/com/mathsnew/evidencecapture/presentation/video/RecordVideoScreen.kt
// 修改文件 - Kotlin

package com.mathsnew.evidencecapture.presentation.video

import android.Manifest
import android.content.Context
import android.util.Log
import android.hardware.camera2.CaptureRequest
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.mathsnew.evidencecapture.R
import com.mathsnew.evidencecapture.util.EvidenceIdGenerator
import com.mathsnew.evidencecapture.util.FileHelper
import java.util.concurrent.Executor

// ── 白平衡 ────────────────────────────────────────────────────
private enum class WbMode { AUTO, CLOUDY, FLUORESCENT, DAYLIGHT, INCANDESCENT }
private fun WbMode.next() = when (this) {
    WbMode.AUTO -> WbMode.CLOUDY; WbMode.CLOUDY -> WbMode.FLUORESCENT
    WbMode.FLUORESCENT -> WbMode.DAYLIGHT; WbMode.DAYLIGHT -> WbMode.INCANDESCENT
    WbMode.INCANDESCENT -> WbMode.AUTO
}
private fun WbMode.label() = when (this) {
    WbMode.AUTO -> "白平衡:自动"; WbMode.CLOUDY -> "阴天"
    WbMode.FLUORESCENT -> "荧光灯"; WbMode.DAYLIGHT -> "日光"
    WbMode.INCANDESCENT -> "白炽灯"
}
private fun WbMode.toAwbMode() = when (this) {
    WbMode.AUTO -> CaptureRequest.CONTROL_AWB_MODE_AUTO
    WbMode.CLOUDY -> CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
    WbMode.FLUORESCENT -> CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT
    WbMode.DAYLIGHT -> CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT
    WbMode.INCANDESCENT -> CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT
}

// ── 视频画质 ──────────────────────────────────────────────────
private enum class VideoQuality { FHD, HD, SD }
private fun VideoQuality.next() = when (this) {
    VideoQuality.FHD -> VideoQuality.HD
    VideoQuality.HD  -> VideoQuality.SD
    VideoQuality.SD  -> VideoQuality.FHD
}
private fun VideoQuality.label() = when (this) {
    VideoQuality.FHD -> "1080p"; VideoQuality.HD -> "720p"; VideoQuality.SD -> "480p"
}
private fun VideoQuality.toQuality() = when (this) {
    VideoQuality.FHD -> Quality.FHD; VideoQuality.HD -> Quality.HD; VideoQuality.SD -> Quality.SD
}

// ── 帧率 ──────────────────────────────────────────────────────
private enum class FrameRate { FPS_30, FPS_60 }
private fun FrameRate.next() = if (this == FrameRate.FPS_30) FrameRate.FPS_60 else FrameRate.FPS_30
private fun FrameRate.label() = if (this == FrameRate.FPS_30) "30fps" else "60fps"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun RecordVideoScreen(
    onSaved: (String) -> Unit,
    onBack:  () -> Unit,
    viewModel: RecordVideoViewModel = hiltViewModel()
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState           by viewModel.uiState.collectAsState()
    val durationSeconds   by viewModel.durationSeconds.collectAsState()
    val segmentIndex      by viewModel.segmentIndex.collectAsState()
    val savedSegmentCount by viewModel.savedSegmentCount.collectAsState()

    var videoCapture      by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var camera            by remember { mutableStateOf<Camera?>(null) }
    var previewViewRef    by remember { mutableStateOf<PreviewView?>(null) }
    var activeRecording   by remember { mutableStateOf<Recording?>(null) }
    var currentEvidenceId by remember { mutableStateOf("") }
    var isAutoSegment     by remember { mutableStateOf(false) }
    var title             by remember { mutableStateOf("") }
    var tag               by remember { mutableStateOf("") }

    // ── 相机控制状态 ──────────────────────────────────────────
    var isFrontCamera by remember { mutableStateOf(false) }
    var torchOn       by remember { mutableStateOf(false) }
    var wbMode        by remember { mutableStateOf(WbMode.AUTO) }
    var videoQuality  by remember { mutableStateOf(VideoQuality.HD) }
    var frameRate     by remember { mutableStateOf(FrameRate.FPS_30) }
    var zoomRatio     by remember { mutableStateOf(1f) }
    var exposureIndex by remember { mutableStateOf(0) }
    var showGrid      by remember { mutableStateOf(false) }
    var showControls  by remember { mutableStateOf(false) }

    val permissions = rememberMultiplePermissionsState(
        listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    )

    fun rebindCamera() {
        val pv = previewViewRef ?: return
        bindVideoCameraFull(
            context        = context,
            lifecycleOwner = lifecycleOwner,
            previewView    = pv,
            isFront        = isFrontCamera,
            quality        = videoQuality,
            wbMode         = wbMode,
            onReady        = { cap, cam ->
                videoCapture = cap
                camera       = cam
                cam.cameraControl.setZoomRatio(zoomRatio)
                cam.cameraControl.setExposureCompensationIndex(exposureIndex)
                cam.cameraControl.enableTorch(torchOn)
            }
        )
    }

    LaunchedEffect(Unit) {
        if (!permissions.allPermissionsGranted) permissions.launchMultiplePermissionRequest()
    }
    // 画质/帧率/前后摄变化时重新绑定（录制中禁止）
    LaunchedEffect(isFrontCamera, videoQuality, frameRate) {
        if (uiState !is VideoUiState.Recording) rebindCamera()
    }
    LaunchedEffect(torchOn) {
        camera?.cameraControl?.enableTorch(torchOn)
    }
    LaunchedEffect(wbMode) {
        try {
            val cam = camera ?: return@LaunchedEffect
            val c2ctrl = Camera2CameraControl.from(cam.cameraControl)
            val opts = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, wbMode.toAwbMode())
                .build()
            c2ctrl.captureRequestOptions = opts
        } catch (e: Exception) { Log.w("RecordVideoScreen", "WB not supported: ${e.message}") }
    }

    DisposableEffect(Unit) {
        viewModel.registerRecordingCallbacks(
            stopFn  = { isAutoSegment = true; activeRecording?.stop(); activeRecording = null },
            startFn = { nextId ->
                currentEvidenceId = nextId
                startVideoRecording(context, videoCapture, nextId,
                    onStarted   = { viewModel.onRecordingStarted(nextId) },
                    onRecording = { rec -> activeRecording = rec },
                    onFinalized = { path ->
                        val auto = isAutoSegment; isAutoSegment = false
                        viewModel.onRecordingStopped(currentEvidenceId, path, auto)
                    },
                    onError = { err -> Log.e("RecordVideoScreen", err); viewModel.resetState() }
                )
            }
        )
        onDispose { viewModel.clearRecordingCallbacks() }
    }

    LaunchedEffect(title, tag) { viewModel.updateSessionMeta(tag = tag, title = title) }
    LaunchedEffect(uiState) {
        if (uiState is VideoUiState.Saved) onSaved((uiState as VideoUiState.Saved).evidenceId)
    }

    val isRecording   = uiState is VideoUiState.Recording
    val isReadyToSave = uiState is VideoUiState.ReadyToSave
    val remainingSeconds = RecordVideoViewModel.SEGMENT_MAX_SECONDS - durationSeconds

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.video_title)) },
                navigationIcon = {
                    IconButton(onClick = { if (uiState is VideoUiState.Idle) onBack() }) {
                        Icon(Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.video_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick  = { showControls = !showControls },
                        enabled  = !isRecording
                    ) {
                        Icon(
                            imageVector = if (showControls) Icons.Default.ExpandLess
                                else Icons.Default.Tune,
                            contentDescription = "相机设置",
                            tint = if (isRecording)
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.onPrimary
                        )
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
            if (isReadyToSave) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { viewModel.cancelAndDelete(); onBack() },
                        modifier = Modifier.weight(1f).height(52.dp)) {
                        Icon(Icons.Default.Close, null); Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.video_cancel))
                    }
                    Button(onClick = { viewModel.saveRecording(tag = tag, title = title) },
                        modifier = Modifier.weight(1f).height(52.dp)) {
                        Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.video_save))
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (!permissions.allPermissionsGranted) {
                Column(Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.video_need_permission))
                    Button(onClick = { permissions.launchMultiplePermissionRequest() }) {
                        Text(stringResource(R.string.video_grant_permission))
                    }
                }
            } else {
                // ── 预览 ──────────────────────────────────────
                if (!isReadyToSave) {
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).also { pv ->
                                previewViewRef = pv
                                bindVideoCameraFull(ctx, lifecycleOwner, pv,
                                    isFrontCamera, videoQuality, wbMode
                                ) { cap, cam -> videoCapture = cap; camera = cam }
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, _, zoom, _ ->
                                    val cam    = camera ?: return@detectTransformGestures
                                    val zState = cam.cameraInfo.zoomState.value ?: return@detectTransformGestures
                                    val nz = (zoomRatio * zoom).coerceIn(zState.minZoomRatio, zState.maxZoomRatio)
                                    zoomRatio = nz; cam.cameraControl.setZoomRatio(nz)
                                }
                            }
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event  = awaitPointerEvent()
                                        val change = event.changes.firstOrNull() ?: continue
                                        if (change.pressed) {
                                            val pv = previewViewRef ?: continue
                                            val pt = pv.meteringPointFactory.createPoint(
                                                change.position.x, change.position.y)
                                            camera?.cameraControl?.startFocusAndMetering(
                                                FocusMeteringAction.Builder(pt).build())
                                            change.consume()
                                        }
                                    }
                                }
                            }
                    )
                } else {
                    // 录完提示
                    Column(Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.video_done_title),
                            style = MaterialTheme.typography.headlineSmall)
                        if (savedSegmentCount > 0) {
                            Spacer(Modifier.height(6.dp))
                            Text("已自动保存 $savedSegmentCount 段，当前为第 $segmentIndex 段",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.video_done_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(24.dp))
                        OutlinedTextField(value = title, onValueChange = { title = it },
                            label = { Text(stringResource(R.string.video_field_title)) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true)
                    }
                }

                if (showGrid && !isReadyToSave) GridOverlay(Modifier.fillMaxSize())

                // ── 控制栏 ────────────────────────────────────
                AnimatedVisibility(
                    visible  = showControls && !isRecording && !isReadyToSave,
                    enter    = fadeIn() + slideInVertically(),
                    exit     = fadeOut() + slideOutVertically(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    VideoControlBar(
                        torchOn          = torchOn,
                        onTorchToggle    = { torchOn = !torchOn },
                        isFront          = isFrontCamera,
                        onFlipCamera     = { isFrontCamera = !isFrontCamera },
                        showGrid         = showGrid,
                        onGridToggle     = { showGrid = !showGrid },
                        wbMode           = wbMode,
                        onWbToggle       = { wbMode = wbMode.next() },
                        quality          = videoQuality,
                        onQualityToggle  = { videoQuality = videoQuality.next() },
                        frameRate        = frameRate,
                        onFrameRateToggle = { frameRate = frameRate.next() },
                        zoomRatio        = zoomRatio,
                        minZoom          = camera?.cameraInfo?.zoomState?.value?.minZoomRatio ?: 1f,
                        maxZoom          = camera?.cameraInfo?.zoomState?.value?.maxZoomRatio ?: 8f,
                        onZoomChange     = { z -> zoomRatio = z; camera?.cameraControl?.setZoomRatio(z) },
                        exposureIndex    = exposureIndex,
                        minExposure      = camera?.cameraInfo?.exposureState?.exposureCompensationRange?.lower ?: -4,
                        maxExposure      = camera?.cameraInfo?.exposureState?.exposureCompensationRange?.upper ?: 4,
                        onExposureChange = { idx -> exposureIndex = idx; camera?.cameraControl?.setExposureCompensationIndex(idx) }
                    )
                }

                // ── 录制状态栏 ────────────────────────────────
                if (isRecording) {
                    val min = durationSeconds / 60; val sec = durationSeconds % 60
                    Surface(Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                        color = Color.Black.copy(alpha = 0.55f),
                        shape = MaterialTheme.shapes.small) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.FiberManualRecord, null,
                                tint = Color.Red, modifier = Modifier.size(12.dp))
                            Text("%02d:%02d".format(min, sec), color = Color.White)
                            Text("·", color = Color.White.copy(alpha = 0.5f))
                            Text("第 $segmentIndex 段", color = Color.White)
                            Text("·", color = Color.White.copy(alpha = 0.5f))
                            Text("剩 ${remainingSeconds}s",
                                color = if (remainingSeconds <= 10) Color.Red else Color.White,
                                fontSize = if (remainingSeconds <= 10) 14.sp else 12.sp)
                        }
                    }
                    if (savedSegmentCount > 0) {
                        Surface(Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp),
                            color = Color.Black.copy(alpha = 0.45f),
                            shape = MaterialTheme.shapes.small) {
                            Text("已自动保存 $savedSegmentCount 段", color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                        }
                    }
                }

                // ── 录制按钮 ──────────────────────────────────
                if (!isReadyToSave && uiState !is VideoUiState.Saving) {
                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp)) {
                        when (uiState) {
                            is VideoUiState.Idle -> FloatingActionButton(
                                onClick = {
                                    val evidenceId = EvidenceIdGenerator.generate()
                                    currentEvidenceId = evidenceId; isAutoSegment = false
                                    startVideoRecording(context, videoCapture, evidenceId,
                                        onStarted   = { viewModel.onRecordingStarted(evidenceId) },
                                        onRecording = { rec -> activeRecording = rec },
                                        onFinalized = { path ->
                                            val auto = isAutoSegment; isAutoSegment = false
                                            viewModel.onRecordingStopped(currentEvidenceId, path, auto)
                                        },
                                        onError = { err -> Log.e("RecordVideoScreen", err); viewModel.resetState() }
                                    )
                                },
                                containerColor = Color.Red
                            ) {
                                Icon(Icons.Default.FiberManualRecord,
                                    stringResource(R.string.video_start),
                                    Modifier.size(32.dp), Color.White)
                            }
                            is VideoUiState.Recording -> FloatingActionButton(
                                onClick        = { isAutoSegment = false; activeRecording?.stop(); activeRecording = null },
                                containerColor = Color.White
                            ) {
                                Icon(Icons.Default.Stop, stringResource(R.string.video_stop),
                                    Modifier.size(32.dp), Color.Red)
                            }
                            else -> {}
                        }
                    }
                }

                if (uiState is VideoUiState.Saving)
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                if (uiState is VideoUiState.Error)
                    Snackbar(Modifier.align(Alignment.BottomCenter)
                        .padding(bottom = 120.dp, start = 16.dp, end = 16.dp)) {
                        Text((uiState as VideoUiState.Error).message)
                    }
            }
        }
    }
}

// ── 录像控制栏 ────────────────────────────────────────────────
@Composable
private fun VideoControlBar(
    torchOn: Boolean, onTorchToggle: () -> Unit,
    isFront: Boolean, onFlipCamera: () -> Unit,
    showGrid: Boolean, onGridToggle: () -> Unit,
    wbMode: WbMode, onWbToggle: () -> Unit,
    quality: VideoQuality, onQualityToggle: () -> Unit,
    frameRate: FrameRate, onFrameRateToggle: () -> Unit,
    zoomRatio: Float, minZoom: Float, maxZoom: Float, onZoomChange: (Float) -> Unit,
    exposureIndex: Int, minExposure: Int, maxExposure: Int, onExposureChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 第一排：手电筒 / 前后摄 / 网格 / 帧率
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
            VCamBtn(if (torchOn) Icons.Default.Highlight else Icons.Default.FlashOff,
                if (torchOn) "手电:开" else "手电:关",
                tint = if (torchOn) Color(0xFFFFD54F) else Color.White, onClick = onTorchToggle)
            VCamBtn(if (isFront) Icons.Default.CameraFront else Icons.Default.CameraRear,
                if (isFront) "前置" else "后置", onClick = onFlipCamera)
            VCamBtn(Icons.Default.GridOn, if (showGrid) "网格:开" else "网格:关",
                tint = if (showGrid) Color(0xFF80CBC4) else Color.White, onClick = onGridToggle)
            VCamBtn(Icons.Default.Speed, frameRate.label(),
                tint = if (frameRate == FrameRate.FPS_60) Color(0xFF80CBC4) else Color.White,
                onClick = onFrameRateToggle)
        }
        // 第二排：白平衡 / 画质
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
            VCamBtn(Icons.Default.WbAuto, wbMode.label(),
                tint = if (wbMode != WbMode.AUTO) Color(0xFF80CBC4) else Color.White,
                onClick = onWbToggle)
            VCamBtn(Icons.Default.HighQuality, quality.label(), onClick = onQualityToggle)
        }
        // 变焦滑块
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ZoomOut, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Slider(value = zoomRatio, onValueChange = onZoomChange,
                valueRange = minZoom..maxZoom.coerceAtLeast(minZoom + 0.1f),
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White))
            Icon(Icons.Default.ZoomIn, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text("${"%.1f".format(zoomRatio)}×", color = Color.White,
                fontSize = 12.sp, modifier = Modifier.width(36.dp))
        }
        // 曝光补偿滑块
        if (minExposure < maxExposure) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BrightnessLow, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Slider(value = exposureIndex.toFloat(), onValueChange = { onExposureChange(it.toInt()) },
                    valueRange = minExposure.toFloat()..maxExposure.toFloat(),
                    steps = (maxExposure - minExposure - 1).coerceAtLeast(0),
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                    colors = SliderDefaults.colors(thumbColor = Color(0xFFFFD54F), activeTrackColor = Color(0xFFFFD54F)))
                Icon(Icons.Default.BrightnessHigh, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text(if (exposureIndex >= 0) "+$exposureIndex" else "$exposureIndex",
                    color = Color(0xFFFFD54F), fontSize = 12.sp, modifier = Modifier.width(28.dp))
            }
        }
    }
}

@Composable
private fun VCamBtn(icon: ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(2.dp)) {
        IconButton(onClick = onClick,
            modifier = Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f))) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(20.dp))
        }
        Text(label, color = Color.White, fontSize = 9.sp)
    }
}

@Composable
private fun GridOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val (w, h) = size.width to size.height
        val c = Color.White.copy(alpha = 0.4f); val sw = 1.dp.toPx()
        drawLine(c, Offset(w/3f, 0f), Offset(w/3f, h), sw)
        drawLine(c, Offset(w*2f/3f, 0f), Offset(w*2f/3f, h), sw)
        drawLine(c, Offset(0f, h/3f), Offset(w, h/3f), sw)
        drawLine(c, Offset(0f, h*2f/3f), Offset(w, h*2f/3f), sw)
    }
}

private fun bindVideoCameraFull(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    isFront: Boolean,
    quality: VideoQuality,
    wbMode: WbMode,
    onReady: (VideoCapture<Recorder>, Camera) -> Unit
) {
    val executor = ContextCompat.getMainExecutor(context)
    ProcessCameraProvider.getInstance(context).addListener({
        val provider  = ProcessCameraProvider.getInstance(context).get()
        val preview   = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val recorder  = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(quality.toQuality())).build()
        val videoCapture = VideoCapture.withOutput(recorder)
        val selector  = if (isFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        try {
            provider.unbindAll()
            val cam = provider.bindToLifecycle(lifecycleOwner, selector, preview, videoCapture)
            // 白平衡
            try {
                val c2ctrl = Camera2CameraControl.from(cam.cameraControl)
                val opts = CaptureRequestOptions.Builder()
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, wbMode.toAwbMode()).build()
                c2ctrl.captureRequestOptions = opts
            } catch (e: Exception) { Log.w("RecordVideoScreen", "WB not supported: ${e.message}") }
            onReady(videoCapture, cam)
        } catch (e: Exception) { Log.e("RecordVideoScreen", "Video camera bind failed", e) }
    }, executor)
}

@androidx.annotation.OptIn(androidx.camera.video.ExperimentalPersistentRecording::class)
private fun startVideoRecording(
    context: Context, videoCapture: VideoCapture<Recorder>?,
    evidenceId: String, onStarted: () -> Unit, onRecording: (Recording) -> Unit,
    onFinalized: (String) -> Unit, onError: (String) -> Unit
) {
    if (videoCapture == null) { onError("Camera not ready"); return }
    val outputFile    = FileHelper.getVideoFile(context, evidenceId)
    val outputOptions = FileOutputOptions.Builder(outputFile).build()
    val executor: Executor = ContextCompat.getMainExecutor(context)
    val recording = videoCapture.output.prepareRecording(context, outputOptions).apply {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED) withAudioEnabled()
    }.start(executor) { event ->
        when (event) {
            is VideoRecordEvent.Start    -> onStarted()
            is VideoRecordEvent.Finalize -> if (event.hasError()) onError("录制错误: ${event.error}")
                else onFinalized(outputFile.absolutePath)
            else -> {}
        }
    }
    onRecording(recording)
}