// app/src/main/java/com/mathsnew/evidencecapture/presentation/capture/CapturePhotoScreen.kt
// Kotlin - 表现层，CameraX 拍照界面
// 按下快门时同步启动传感器采集和 CameraX 拍照，两者并行

package com.mathsnew.evidencecapture.presentation.capture

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.io.File
import java.util.concurrent.Executor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun CapturePhotoScreen(
    onPhotoCaptured: (evidenceId: String, photoPath: String) -> Unit,
    onBack: () -> Unit,
    viewModel: CaptureViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) {
            cameraPermission.launchPermissionRequest()
        }
    }

    // PhotoTaken 且已有 evidenceId 时跳转确认页
    LaunchedEffect(uiState) {
        val state = uiState
        if (state is CaptureUiState.PhotoTaken && state.evidenceId.isNotEmpty()) {
            onPhotoCaptured(state.evidenceId, state.photoPath)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("拍照取证") },
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
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!cameraPermission.status.isGranted) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("需要相机权限才能拍照")
                    Button(onClick = { cameraPermission.launchPermissionRequest() }) {
                        Text("授予权限")
                    }
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        bindCamera(ctx, lifecycleOwner, previewView) { capture ->
                            imageCapture = capture
                        }
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 48.dp)
                ) {
                    if (uiState is CaptureUiState.Capturing ||
                        uiState is CaptureUiState.Saving) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "采集中...",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    } else {
                        FloatingActionButton(
                            onClick = {
                                if (uiState is CaptureUiState.Idle) {
                                    // 1. 通知 ViewModel 快门按下，同步启动传感器采集
                                    //    ViewModel 返回本次的 evidenceId
                                    val evidenceId = viewModel.onShutterPressed()

                                    // 2. 同时立刻触发 CameraX 拍照（与传感器采集并行）
                                    takePhoto(
                                        context = context,
                                        imageCapture = imageCapture,
                                        onSuccess = { path ->
                                            // 3. 拍照完成，通知 ViewModel 合并
                                            viewModel.onPhotoSaved(evidenceId, path)
                                        },
                                        onError = { error ->
                                            Log.e("CapturePhotoScreen",
                                                "Take photo failed: $error")
                                            viewModel.resetState()
                                        }
                                    )
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(
                                imageVector = Icons.Default.Camera,
                                contentDescription = "拍照",
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                if (uiState is CaptureUiState.Error) {
                    Snackbar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 120.dp, start = 16.dp, end = 16.dp)
                    ) {
                        Text((uiState as CaptureUiState.Error).message)
                    }
                }
            }
        }
    }
}

private fun bindCamera(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    onImageCaptureReady: (ImageCapture) -> Unit
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )
            onImageCaptureReady(imageCapture)
        } catch (e: Exception) {
            Log.e("CapturePhotoScreen", "Camera bind failed", e)
        }
    }, ContextCompat.getMainExecutor(context))
}

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture?,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    if (imageCapture == null) {
        onError("Camera not ready")
        return
    }
    val tempFile = File(context.cacheDir, "temp_photo_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()
    val executor: Executor = ContextCompat.getMainExecutor(context)
    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                onSuccess(tempFile.absolutePath)
            }
            override fun onError(exception: ImageCaptureException) {
                onError(exception.message ?: "拍照失败")
            }
        }
    )
}