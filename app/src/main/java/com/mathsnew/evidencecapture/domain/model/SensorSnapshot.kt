// app/src/main/java/com/mathsnew/evidencecapture/domain/model/SensorSnapshot.kt
// Kotlin - 领域模型，证据采集瞬间的环境传感器快照

package com.mathsnew.evidencecapture.domain.model

/**
 * 环境传感器快照，与证据一对一关联
 * 所有传感器字段在不可用时使用零值默认值，不阻断取证流程
 *
 * @param id          快照 ID，与关联证据 ID 相同
 * @param evidenceId  关联证据 ID
 * @param capturedAt  本地设备时间戳（毫秒）
 * @param networkTime 网络校准时间戳（毫秒，0 表示获取失败）
 * @param latitude    GPS 纬度
 * @param longitude   GPS 经度
 * @param altitude    海拔高度（米）
 * @param address     逆地理编码地址（异步回填，初始为空）
 * @param azimuth     手机方位角（0~360°，北为 0）
 * @param lightLux    环境光照强度（lux）
 * @param decibel     环境分贝（dB）
 * @param pressureHpa 大气压（hPa）
 * @param wifiSsid    当前 Wi-Fi SSID（未连接时为空）
 * @param operator    移动运营商名称
 * @param weatherDesc 天气描述（异步回填）
 * @param temperature 气温（℃，异步回填）
 * @param humidity    湿度（%，异步回填）
 * @param windSpeed   风速（m/s，异步回填）
 */
data class SensorSnapshot(
    val id: String,
    val evidenceId: String,
    val capturedAt: Long,
    val networkTime: Long = 0L,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val address: String = "",
    val azimuth: Float = 0f,
    val lightLux: Float = 0f,
    val decibel: Float = 0f,
    val pressureHpa: Float = 0f,
    val wifiSsid: String = "",
    val operator: String = "",
    val weatherDesc: String = "",
    val temperature: Float = 0f,
    val humidity: Float = 0f,
    val windSpeed: Float = 0f
)