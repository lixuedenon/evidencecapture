// app/src/main/java/com/mathsnew/evidencecapture/presentation/capture/SnapshotCard.kt
// Kotlin - 表现层，环境数据展示卡片，0值也显示，仅空字符串和null不显示

package com.mathsnew.evidencecapture.presentation.capture

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mathsnew.evidencecapture.domain.model.SensorSnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SnapshotCard(
    snapshot: SensorSnapshot,
    modifier: Modifier = Modifier
) {
    val fullFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "环境数据",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 设备时间（始终显示）
            SnapshotRow(
                icon = Icons.Default.AccessTime,
                label = "时间",
                value = fullFormatter.format(Date(snapshot.capturedAt))
            )

            // 网络校准时间
            if (snapshot.networkTime > 0) {
                val netTimeStr = fullFormatter.format(Date(snapshot.networkTime))
                val diffMs = snapshot.networkTime - snapshot.capturedAt
                val diffStr = when {
                    Math.abs(diffMs) < 1000 -> "误差<1秒"
                    diffMs > 0 -> "设备慢${Math.abs(diffMs) / 1000}秒"
                    else -> "设备快${Math.abs(diffMs) / 1000}秒"
                }
                SnapshotRow(
                    icon = Icons.Default.CloudDone,
                    label = "网络校时",
                    value = "$netTimeStr（$diffStr）"
                )
            }

            // GPS 坐标（有坐标才显示）
            if (snapshot.latitude != 0.0 || snapshot.longitude != 0.0) {
                SnapshotRow(
                    icon = Icons.Default.LocationOn,
                    label = "坐标",
                    value = "%.6f, %.6f".format(snapshot.latitude, snapshot.longitude)
                )
            }

            // 地址
            if (snapshot.address.isNotEmpty()) {
                SnapshotRow(
                    icon = Icons.Default.Place,
                    label = "地址",
                    value = snapshot.address
                )
            }

            // 海拔
            if (snapshot.altitude != 0.0) {
                SnapshotRow(
                    icon = Icons.Default.Terrain,
                    label = "海拔",
                    value = "%.1f m".format(snapshot.altitude)
                )
            }

            // 方位角（始终显示，0度也有意义）
            SnapshotRow(
                icon = Icons.Default.Explore,
                label = "方位角",
                value = "%.1f°".format(snapshot.azimuth)
            )

            // 光照（始终显示，0也显示）
            SnapshotRow(
                icon = Icons.Default.LightMode,
                label = "光照",
                value = "%.0f lux".format(snapshot.lightLux)
            )

            // 分贝（始终显示，0也显示）
            SnapshotRow(
                icon = Icons.Default.VolumeUp,
                label = "分贝",
                value = "%.1f dB".format(snapshot.decibel)
            )

            // 气压（始终显示）
            if (snapshot.pressureHpa != 0f) {
                SnapshotRow(
                    icon = Icons.Default.Speed,
                    label = "气压",
                    value = "%.1f hPa".format(snapshot.pressureHpa)
                )
            }

            // Wi-Fi
            if (snapshot.wifiSsid.isNotEmpty()) {
                SnapshotRow(
                    icon = Icons.Default.Wifi,
                    label = "Wi-Fi",
                    value = snapshot.wifiSsid
                )
            }

            // 运营商
            if (snapshot.operator.isNotEmpty()) {
                SnapshotRow(
                    icon = Icons.Default.SignalCellularAlt,
                    label = "运营商",
                    value = snapshot.operator
                )
            }

            // 天气（有数据才显示）
            if (snapshot.weatherDesc.isNotEmpty()) {
                SnapshotRow(
                    icon = Icons.Default.WbCloudy,
                    label = "天气",
                    value = "${snapshot.weatherDesc}  " +
                            "${snapshot.temperature}℃  " +
                            "湿度${snapshot.humidity}%  " +
                            "风速${snapshot.windSpeed}m/s"
                )
            }
        }
    }
}

@Composable
private fun SnapshotRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$label：",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}