// app/src/main/java/com/mathsnew/evidencecapture/presentation/capture/CapturePhotoScreen.kt
// 修改文件 - Kotlin

package com.mathsnew.evidencecapture.presentation.capture

import android.Manifest
import android.content.Context
import android.util.Log
import android.hardware.camera2.CaptureRequest
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.*
import androidx.camera.core.Camera
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.mathsnew.evidencecapture.R
import com.mathsnew.evidencecapture.util.FileHelper
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

// ── 闪光灯模式 ────────────────────────────────────────────────
private enum class FlashMode { OFF, ON, AUTO, TORCH }
private fun FlashMode.next() = when (this) {
    FlashMode.OFF -> FlashMode.ON; FlashMode.ON -> FlashMode.AUTO
    FlashMode.AUTO -> FlashMode.TORCH; FlashMode.TORCH -> FlashMode.OFF
}
private fun FlashMode.icon(): ImageVector = when (this) {
    FlashMode.OFF -> Icons.Default.FlashOff; FlashMode.ON -> Icons.Default.FlashOn
    FlashMode.AUTO -> Icons.Default.FlashAuto; FlashMode.TORCH -> Icons.Default.Highlight
}
private fun FlashMode.label() = when (this) {
    FlashMode.OFF -> "闪光:关"; FlashMode.ON -> "闪光:开"
    FlashMode.AUTO -> "闪光:自动"; FlashMode.TORCH -> "手电筒"
}
private fun FlashMode.toImageCaptureFlash() = when (this) {
    FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
    FlashMode.ON -> ImageCapture.FLASH_MODE_ON
    FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
    FlashMode.TORCH -> ImageCapture.FLASH_MODE_ON
}

// ── 白平衡模式 ────────────────────────────────────────────────
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

// ── 画质选择 ──────────────────────────────────────────────────
private enum class PhotoQuality { HIGH, MEDIUM, LOW }
private fun PhotoQuality.next() = when (this) {
    PhotoQuality.HIGH -> PhotoQuality.MEDIUM
    PhotoQuality.MEDIUM -> PhotoQuality.LOW
    PhotoQuality.LOW -> PhotoQuality.HIGH
}
private fun PhotoQuality.label() = when (this) {
    PhotoQuality.HIGH -> "画质:高"; PhotoQuality.MEDIUM -> "画质:中"
    PhotoQuality.LOW -> "画质:低"
}
private fun PhotoQuality.toJpegQuality() = when (this) {
    PhotoQuality.HIGH -> 95; PhotoQuality.MEDIUM -> 75; PhotoQuality.LOW -> 50
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun CapturePhotoScreen(
    onPhotoCaptured: (evidenceId: String, photoPath: String) -> Unit,
    onBack: () -> Unit,
    viewModel: CaptureViewModel = hiltViewModel()
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState        by viewModel.uiState.collectAsState()

    // ── 相机控制状态 ──────────────────────────────────────────
    var imageCapture   by remember { mutableStateOf<ImageCapture?>(null) }
    var camera         by remember { mutableStateOf<Camera?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var isFrontCamera  by remember { mutableStateOf(false) }
    var flashMode      by remember { mutableStateOf(FlashMode.OFF) }
    var wbMode         by remember { mutableStateOf(WbMode.AUTO) }
    var photoQuality   by remember { mutableStateOf(PhotoQuality.HIGH) }
    var hdrEnabled     by remember { mutableStateOf(false) }
    var zoomRatio      by remember { mutableStateOf(1f) }
    var exposureIndex  by remember { mutableStateOf(0) }
    var showGrid       by remember { mutableStateOf(false) }
    var showControls   by remember { mutableStateOf(false) }
    var timerSeconds   by remember { mutableStateOf(0) }
    var countdown      by remember { mutableStateOf(0) }

    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    fun rebindCamera() {
        val pv = previewViewRef ?: return
        bindCameraFull(
            context        = context,
            lifecycleOwner = lifecycleOwner,
            previewView    = pv,
            isFront        = isFrontCamera,
            flashMode      = flashMode,
            wbMode         = wbMode,
            quality        = photoQuality,
            hdr            = hdrEnabled,
            onReady        = { cap, cam ->
                imageCapture = cap
                camera       = cam
                cam.cameraControl.setZoomRatio(zoomRatio)
                cam.cameraControl.setExposureCompensationIndex(exposureIndex)
                cam.cameraControl.enableTorch(flashMode == FlashMode.TORCH)
            }
        )
    }

    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) cameraPermission.launchPermissionRequest()
    }
    LaunchedEffect(uiState) {
        val state = uiState
        if (state is CaptureUiState.PhotoTaken && state.evidenceId.isNotEmpty())
            onPhotoCaptured(state.evidenceId, state.photoPath)
    }
    // 任何相机参数变化时重新绑定
    LaunchedEffect(isFrontCamera, hdrEnabled, photoQuality) { rebindCamera() }
    LaunchedEffect(flashMode) {
        imageCapture?.flashMode = flashMode.toImageCaptureFlash()
        camera?.cameraControl?.enableTorch(flashMode == FlashMode.TORCH)
    }
    LaunchedEffect(wbMode) {
        camera?.cameraControl?.let { ctrl ->
            ctrl.startFocusAndMetering(
                FocusMeteringAction.Builder(
                    previewViewRef?.meteringPointFactory?.createPoint(0.5f, 0.5f)
                        ?: return@let
                ).build()
            )
        }
    }

    fun doTakePhoto() {
        if (uiState !is CaptureUiState.Idle) return
        val evidenceId = viewModel.onShutterPressed()
        takePhoto(context, imageCapture, evidenceId,
            onSuccess = { path -> viewModel.onPhotoSaved(evidenceId, path) },
            onError   = { err -> Log.e("CapturePhotoScreen", err); viewModel.resetState() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.capture_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.capture_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showControls = !showControls }) {
                        Icon(
                            imageVector = if (showControls) Icons.Default.ExpandLess
                                else Icons.Default.Tune,
                            contentDescription = "相机设置",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor             = MaterialTheme.colorScheme.primary,
                    titleContentColor          = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!cameraPermission.status.isGranted) {
                Column(
                    modifier            = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(stringResource(R.string.capture_need_permission))
                    Button(onClick = { cameraPermission.launchPermissionRequest() }) {
                        Text(stringResource(R.string.capture_grant_permission))
                    }
                }
            } else {
                // ── 预览 ──────────────────────────────────────
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also { pv ->
                            previewViewRef = pv
                            bindCameraFull(ctx, lifecycleOwner, pv,
                                isFrontCamera, flashMode, wbMode, photoQuality, hdrEnabled
                            ) { cap, cam -> imageCapture = cap; camera = cam }
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, _, zoom, _ ->
                                val cam    = camera ?: return@detectTransformGestures
                                val zState = cam.cameraInfo.zoomState.value ?: return@detectTransformGestures
                                val nz = (zoomRatio * zoom).coerceIn(zState.minZoomRatio, zState.maxZoomRatio)
                                zoomRatio = nz
                                cam.cameraControl.setZoomRatio(nz)
                            }
                        }
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event  = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: continue
                                    if (change.pressed) {
                                        val pv    = previewViewRef ?: continue
                                        val pt    = pv.meteringPointFactory.createPoint(
                                            change.position.x, change.position.y)
                                        camera?.cameraControl?.startFocusAndMetering(
                                            FocusMeteringAction.Builder(pt).build())
                                        change.consume()
                                    }
                                }
                            }
                        }
                )

                if (showGrid) GridOverlay(Modifier.fillMaxSize())

                // ── 控制栏 ────────────────────────────────────
                AnimatedVisibility(
                    visible  = showControls,
                    enter    = fadeIn() + slideInVertically(),
                    exit     = fadeOut() + slideOutVertically(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    PhotoControlBar(
                        flashMode        = flashMode,
                        onFlashToggle    = { flashMode = flashMode.next() },
                        isFront          = isFrontCamera,
                        onFlipCamera     = { isFrontCamera = !isFrontCamera },
                        showGrid         = showGrid,
                        onGridToggle     = { showGrid = !showGrid },
                        timerSeconds     = timerSeconds,
                        onTimerToggle    = { timerSeconds = when(timerSeconds) { 0->3; 3->5; 5->10; else->0 } },
                        hdrEnabled       = hdrEnabled,
                        onHdrToggle      = { hdrEnabled = !hdrEnabled },
                        wbMode           = wbMode,
                        onWbToggle       = { wbMode = wbMode.next() },
                        quality          = photoQuality,
                        onQualityToggle  = { photoQuality = photoQuality.next() },
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

                if (countdown > 0) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text(countdown.toString(), style = MaterialTheme.typography.displayLarge,
                            color = Color.White)
                    }
                }

                // ── 快门 ──────────────────────────────────────
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp)) {
                    if (uiState is CaptureUiState.Capturing || uiState is CaptureUiState.Saving || countdown > 0) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text  = if (countdown > 0) "准备拍摄..."
                                    else stringResource(R.string.capture_capturing),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White
                            )
                        }
                    } else {
                        FloatingActionButton(
                            onClick        = {
                                if (uiState is CaptureUiState.Idle) {
                                    if (timerSeconds == 0) { doTakePhoto() }
                                    else MainScope().launch {
                                        countdown = timerSeconds
                                        while (countdown > 0) { delay(1000); countdown-- }
                                        doTakePhoto()
                                    }
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(Icons.Default.Camera,
                                contentDescription = stringResource(R.string.capture_title),
                                modifier = Modifier.size(32.dp))
                        }
                    }
                }

                if (uiState is CaptureUiState.Error) {
                    Snackbar(modifier = Modifier.align(Alignment.BottomCenter)
                        .padding(bottom = 120.dp, start = 16.dp, end = 16.dp)) {
                        Text((uiState as CaptureUiState.Error).message)
                    }
                }
            }
        }
    }
}

// ── 拍照控制栏 ────────────────────────────────────────────────
@Composable
private fun PhotoControlBar(
    flashMode: FlashMode, onFlashToggle: () -> Unit,
    isFront: Boolean, onFlipCamera: () -> Unit,
    showGrid: Boolean, onGridToggle: () -> Unit,
    timerSeconds: Int, onTimerToggle: () -> Unit,
    hdrEnabled: Boolean, onHdrToggle: () -> Unit,
    wbMode: WbMode, onWbToggle: () -> Unit,
    quality: PhotoQuality, onQualityToggle: () -> Unit,
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
        // 第一排：闪光灯 / 前后摄 / 网格 / 定时
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
            CamBtn(flashMode.icon(), flashMode.label(),
                tint = if (flashMode != FlashMode.OFF) Color(0xFFFFD54F) else Color.White,
                onClick = onFlashToggle)
            CamBtn(if (isFront) Icons.Default.CameraFront else Icons.Default.CameraRear,
                if (isFront) "前置" else "后置", onClick = onFlipCamera)
            CamBtn(Icons.Default.GridOn, if (showGrid) "网格:开" else "网格:关",
                tint = if (showGrid) Color(0xFF80CBC4) else Color.White, onClick = onGridToggle)
            CamBtn(Icons.Default.Timer,
                if (timerSeconds == 0) "定时:关" else "${timerSeconds}s",
                tint = if (timerSeconds > 0) Color(0xFF80CBC4) else Color.White,
                onClick = onTimerToggle)
        }

        // 第二排：HDR / 白平衡 / 画质
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
            CamBtn(Icons.Default.HdrOn, if (hdrEnabled) "HDR:开" else "HDR:关",
                tint = if (hdrEnabled) Color(0xFF80CBC4) else Color.White, onClick = onHdrToggle)
            CamBtn(Icons.Default.WbAuto, wbMode.label(),
                tint = if (wbMode != WbMode.AUTO) Color(0xFF80CBC4) else Color.White,
                onClick = onWbToggle)
            CamBtn(Icons.Default.HighQuality, quality.label(), onClick = onQualityToggle)
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

// ── 通用图标按钮 ──────────────────────────────────────────────
@Composable
private fun CamBtn(
    icon: ImageVector, label: String,
    tint: Color = Color.White, onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(2.dp)) {
        IconButton(onClick = onClick,
            modifier = Modifier.size(42.dp).clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f))) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(20.dp))
        }
        Text(label, color = Color.White, fontSize = 9.sp)
    }
}

// ── 三分网格线 ────────────────────────────────────────────────
@Composable
private fun GridOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val (w, h) = size.width to size.height
        val c = Color.White.copy(alpha = 0.4f); val sw = 1.dp.toPx()
        drawLine(c, Offset(w/3f, 0f),      Offset(w/3f, h),      sw)
        drawLine(c, Offset(w*2f/3f, 0f),   Offset(w*2f/3f, h),   sw)
        drawLine(c, Offset(0f, h/3f),      Offset(w, h/3f),      sw)
        drawLine(c, Offset(0f, h*2f/3f),   Offset(w, h*2f/3f),   sw)
    }
}

// ── 相机绑定 ──────────────────────────────────────────────────
private fun bindCameraFull(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    isFront: Boolean,
    flashMode: FlashMode,
    wbMode: WbMode,
    quality: PhotoQuality,
    hdr: Boolean,
    onReady: (ImageCapture, Camera) -> Unit
) {
    val executor = ContextCompat.getMainExecutor(context)
    val future   = ProcessCameraProvider.getInstance(context)
    future.addListener({
        val provider = future.get()
        val selector = if (isFront) CameraSelector.DEFAULT_FRONT_CAMERA
            else CameraSelector.DEFAULT_BACK_CAMERA

        // HDR 通过 CameraX Extensions 实现，不支持时静默回退
        val extFuture = ExtensionsManager.getInstanceAsync(context, provider)
        extFuture.addListener({
            val extManager   = extFuture.get()
            val finalSelector = if (hdr && extManager.isExtensionAvailable(
                    selector, ExtensionMode.HDR)) {
                extManager.getExtensionEnabledCameraSelector(selector, ExtensionMode.HDR)
            } else selector

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(flashMode.toImageCaptureFlash())
                .setJpegQuality(quality.toJpegQuality())
                .build()
            try {
                provider.unbindAll()
                val cam = provider.bindToLifecycle(
                    lifecycleOwner, finalSelector, preview, imageCapture)
                cam.cameraControl.enableTorch(flashMode == FlashMode.TORCH)
                // 白平衡通过 Camera2CameraControl 设置
                try {
                    val c2ctrl = Camera2CameraControl.from(cam.cameraControl)
                    val reqOpts = CaptureRequestOptions.Builder()
                        .setCaptureRequestOption(
                            CaptureRequest.CONTROL_AWB_MODE, wbMode.toAwbMode())
                        .build()
                    c2ctrl.captureRequestOptions = reqOpts
                } catch (e: Exception) {
                    Log.w("CapturePhotoScreen", "WB not supported: ${e.message}")
                }
                onReady(imageCapture, cam)
            } catch (e: Exception) {
                Log.e("CapturePhotoScreen", "Camera bind failed", e)
            }
        }, executor)
    }, executor)
}

// ── 拍照 ──────────────────────────────────────────────────────
private fun takePhoto(
    context: Context, imageCapture: ImageCapture?,
    evidenceId: String, onSuccess: (String) -> Unit, onError: (String) -> Unit
) {
    if (imageCapture == null) { onError("Camera not ready"); return }
    val photoFile     = FileHelper.getPhotoFile(context, evidenceId)
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
    val executor: Executor = ContextCompat.getMainExecutor(context)
    imageCapture.takePicture(outputOptions, executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) = onSuccess(photoFile.absolutePath)
            override fun onError(exception: ImageCaptureException) = onError(exception.message ?: "拍照失败")
        }
    )
}