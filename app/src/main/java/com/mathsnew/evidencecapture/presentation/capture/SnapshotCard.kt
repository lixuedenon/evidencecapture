// app/src/main/java/com/mathsnew/evidencecapture/presentation/capture/SnapshotCard.kt
// 修改文件 - Kotlin

package com.mathsnew.evidencecapture.presentation.capture

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mathsnew.evidencecapture.R
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
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text  = stringResource(R.string.pdf_section_env),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 设备时间（始终显示）
            SnapshotRow(
                icon  = Icons.Default.AccessTime,
                label = stringResource(R.string.pdf_label_capture_time),
                value = fullFormatter.format(Date(snapshot.capturedAt))
            )

            // 网络校准时间
            if (snapshot.networkTime > 0) {
                val netTimeStr = fullFormatter.format(Date(snapshot.networkTime))
                val diffMs     = snapshot.networkTime - snapshot.capturedAt
                val diffStr    = when {
                    Math.abs(diffMs) < 1000 ->
                        stringResource(R.string.pdf_ntp_diff_less1s)
                    diffMs > 0 ->
                        stringResource(R.string.pdf_ntp_diff_slow,
                            (Math.abs(diffMs) / 1000).toInt())
                    else ->
                        stringResource(R.string.pdf_ntp_diff_fast,
                            (Math.abs(diffMs) / 1000).toInt())
                }
                SnapshotRow(
                    icon  = Icons.Default.CloudDone,
                    label = stringResource(R.string.pdf_label_ntp_time),
                    value = "$netTimeStr（$diffStr）"
                )
            }

            // GPS 坐标
            if (snapshot.latitude != 0.0 || snapshot.longitude != 0.0) {
                SnapshotRow(
                    icon  = Icons.Default.LocationOn,
                    label = stringResource(R.string.pdf_label_gps),
                    value = "%.6f, %.6f".format(snapshot.latitude, snapshot.longitude)
                )
            }

            // 地址
            if (snapshot.address.isNotEmpty()) {
                SnapshotRow(
                    icon  = Icons.Default.Place,
                    label = stringResource(R.string.pdf_label_address),
                    value = snapshot.address
                )
            }

            // 海拔
            if (snapshot.altitude != 0.0) {
                SnapshotRow(
                    icon  = Icons.Default.Terrain,
                    label = stringResource(R.string.pdf_label_altitude),
                    value = "%.1f m".format(snapshot.altitude)
                )
            }

            // 方位角
            SnapshotRow(
                icon  = Icons.Default.Explore,
                label = stringResource(R.string.pdf_label_azimuth),
                value = "%.1f°".format(snapshot.azimuth)
            )

            // 光照
            SnapshotRow(
                icon  = Icons.Default.LightMode,
                label = stringResource(R.string.pdf_label_light),
                value = "%.0f lux".format(snapshot.lightLux)
            )

            // 分贝
            SnapshotRow(
                icon  = Icons.Default.VolumeUp,
                label = stringResource(R.string.pdf_label_decibel),
                value = "%.1f dB".format(snapshot.decibel)
            )

            // 气压
            if (snapshot.pressureHpa != 0f) {
                SnapshotRow(
                    icon  = Icons.Default.Speed,
                    label = stringResource(R.string.pdf_label_pressure),
                    value = "%.1f hPa".format(snapshot.pressureHpa)
                )
            }

            // Wi-Fi
            if (snapshot.wifiSsid.isNotEmpty()) {
                SnapshotRow(
                    icon  = Icons.Default.Wifi,
                    label = stringResource(R.string.pdf_label_wifi),
                    value = snapshot.wifiSsid
                )
            }

            // 运营商
            if (snapshot.operator.isNotEmpty()) {
                SnapshotRow(
                    icon  = Icons.Default.SignalCellularAlt,
                    label = stringResource(R.string.pdf_label_operator),
                    value = snapshot.operator
                )
            }

            // 天气
            if (snapshot.weatherDesc.isNotEmpty()) {
                SnapshotRow(
                    icon  = Icons.Default.WbCloudy,
                    label = stringResource(R.string.pdf_label_weather),
                    value = "${snapshot.weatherDesc}  " +
                            "${snapshot.temperature}℃  " +
                            "${snapshot.humidity}%  " +
                            "${snapshot.windSpeed}m/s"
                )
            }
        }
    }
}

@Composable
private fun SnapshotRow(
    icon:  ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            modifier           = Modifier.size(16.dp),
            tint               = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text     = "$label：",
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp)
        )
        Text(
            text     = value,
            style    = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}