// app/src/main/java/com/mathsnew/evidencecapture/presentation/audio/DisguiseScreen.kt
// Kotlin - 表现层，四种录音伪装界面实现

package com.mathsnew.evidencecapture.presentation.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mathsnew.evidencecapture.domain.model.DisguiseMode

/**
 * 伪装界面容器
 * 长按屏幕右下角 2 秒可恢复真实录音界面（onReveal 回调）
 */
@Composable
fun DisguiseScreen(
    mode: DisguiseMode,
    onReveal: () -> Unit
) {
    var holdProgress by remember { mutableStateOf(0f) }

    Box(modifier = Modifier.fillMaxSize()) {
        // 各伪装界面内容
        when (mode) {
            DisguiseMode.MUSIC -> MusicDisguise()
            DisguiseMode.CALCULATOR -> CalculatorDisguise()
            DisguiseMode.CALL -> CallDisguise()
            DisguiseMode.NEWS -> NewsDisguise()
            DisguiseMode.NONE -> {}
        }

        // 右下角长按区域（2 秒触发 onReveal）
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(64.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { onReveal() }
                    )
                }
        )
    }
}

/** 音乐播放器伪装 */
@Composable
private fun MusicDisguise() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(180.dp)
                .background(Color(0xFF16213E), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = Color.White
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text("未知歌曲", color = Color.White, fontSize = 22.sp)
        Text("未知艺术家", color = Color.Gray, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(32.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(40.dp))
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Pause, null, tint = Color.Black, modifier = Modifier.size(32.dp))
            }
            Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(40.dp))
        }
    }
}

/** 计算器伪装 */
@Composable
private fun CalculatorDisguise() {
    var display by remember { mutableStateOf("0") }
    val buttons = listOf(
        listOf("C", "±", "%", "÷"),
        listOf("7", "8", "9", "×"),
        listOf("4", "5", "6", "−"),
        listOf("1", "2", "3", "+"),
        listOf("0", ".", "=")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(8.dp)
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = display,
            color = Color.White,
            fontSize = 48.sp,
            textAlign = TextAlign.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
        buttons.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { label ->
                    val isWide = label == "0"
                    Button(
                        onClick = {
                            display = if (label == "C") "0"
                            else if (display == "0") label
                            else display + label
                        },
                        modifier = Modifier
                            .weight(if (isWide) 2f else 1f)
                            .height(72.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when (label) {
                                "÷", "×", "−", "+", "=" -> Color(0xFFFF9F0A)
                                "C", "±", "%" -> Color(0xFF505050)
                                else -> Color(0xFF333333)
                            }
                        ),
                        shape = CircleShape
                    ) {
                        Text(label, fontSize = 24.sp, color = Color.White)
                    }
                }
            }
        }
    }
}

/** 通话界面伪装 */
@Composable
private fun CallDisguise() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2C2C2E))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(80.dp))
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(Color(0xFF48484A), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(56.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("未知号码", color = Color.White, fontSize = 28.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text("通话中 00:00", color = Color.Gray, fontSize = 16.sp)
        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color(0xFF48484A), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.MicOff, null, tint = Color.White)
                }
                Text("静音", color = Color.White, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color(0xFFFF3B30), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CallEnd, null, tint = Color.White)
                }
                Text("挂断", color = Color.White, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color(0xFF48484A), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.VolumeUp, null, tint = Color.White)
                }
                Text("扬声器", color = Color.White, fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.height(48.dp))
    }
}

/** 新闻阅读伪装 */
@Composable
private fun NewsDisguise() {
    val articles = listOf(
        "国内经济稳步增长，多项指标创新高" to "据悉，今年以来国内生产总值持续保持稳定增长态势，各项经济指标均有所提升...",
        "科技创新驱动发展，新能源产业蓬勃兴起" to "新能源汽车销量再创历史新高，相关配套产业链持续完善，多个城市加快布局充电基础设施...",
        "城市建设加快推进，居民生活品质提升" to "多个城市宣布启动新一轮城市更新改造计划，重点聚焦老旧小区改造、公共空间优化等领域..."
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "今日头条",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(16.dp)
            )
        }
        articles.forEach { (headline, content) ->
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = headline, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}