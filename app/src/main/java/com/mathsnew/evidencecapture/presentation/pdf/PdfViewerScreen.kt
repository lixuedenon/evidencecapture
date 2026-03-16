// app/src/main/java/com/mathsnew/evidencecapture/presentation/pdf/PdfViewerScreen.kt
// Kotlin - 表现层，App 内 PDF 查看页，使用 Android 原生 PdfRenderer 逐页渲染

package com.mathsnew.evidencecapture.presentation.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * App 内 PDF 查看页
 *
 * @param pdfPath   PDF 文件绝对路径
 * @param title     顶部栏标题
 * @param onBack    返回回调
 * @param onShare   分享回调，点击顶部分享按钮时触发
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    pdfPath: String,
    title: String,
    onBack: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current

    // 渲染后的页面 Bitmap 列表，加载完成前为空
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf("") }
    var isSharing by remember { mutableStateOf(false) }

    // 在 IO 线程用 PdfRenderer 逐页渲染为 Bitmap
    LaunchedEffect(pdfPath) {
        isLoading = true
        errorMsg = ""
        val rendered = withContext(Dispatchers.IO) {
            renderPdfPages(context, pdfPath)
        }
        if (rendered.isEmpty()) {
            errorMsg = "PDF 加载失败，请重新导出"
        } else {
            pages = rendered
        }
        isLoading = false
    }

    // 离开页面时释放 Bitmap 内存
    DisposableEffect(pdfPath) {
        onDispose {
            pages.forEach { it.recycle() }
        }
    }

    val listState = rememberLazyListState()
    // 当前可见页码（从 1 开始）
    val currentPage by remember {
        derivedStateOf { listState.firstVisibleItemIndex + 1 }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 分享按钮：把 PDF 发送到第三方
                    IconButton(
                        onClick = {
                            if (!isSharing) {
                                isSharing = true
                                onShare()
                                isSharing = false
                            }
                        }
                    ) {
                        if (isSharing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "分享 PDF",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        // 底部页码指示条
        bottomBar = {
            if (pages.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 2.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "第 $currentPage 页 / 共 ${pages.size} 页",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFEEEEEE))
        ) {
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            "正在生成报告...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                errorMsg.isNotEmpty() -> {
                    Text(
                        text = errorMsg,
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.error
                    )
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = 12.dp,
                            vertical = 12.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(pages) { _, bitmap ->
                            // 每页 PDF 渲染为白色卡片
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 使用 Android PdfRenderer 将 PDF 文件逐页渲染为 Bitmap 列表
 * 渲染宽度固定为 1080px，高度按页面比例自动计算
 * 必须在 IO 线程调用
 */
private fun renderPdfPages(context: Context, pdfPath: String): List<Bitmap> {
    val file = File(pdfPath)
    if (!file.exists()) return emptyList()

    return try {
        val fileDescriptor = ParcelFileDescriptor.open(
            file, ParcelFileDescriptor.MODE_READ_ONLY
        )
        val renderer = PdfRenderer(fileDescriptor)
        val renderWidth = 1080 // 固定渲染宽度，高度按比例

        val bitmaps = (0 until renderer.pageCount).map { i ->
            val page = renderer.openPage(i)
            val scale  = renderWidth.toFloat() / page.width
            val height = (page.height * scale).toInt()
            val bitmap = Bitmap.createBitmap(renderWidth, height, Bitmap.Config.ARGB_8888)
            // 白色背景（PDF 默认透明背景在 Bitmap 上会变黑）
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            bitmap
        }

        renderer.close()
        fileDescriptor.close()
        bitmaps
    } catch (e: Exception) {
        android.util.Log.e("PdfViewerScreen", "renderPdfPages failed: ${e.message}")
        emptyList()
    }
}