// app/src/main/java/com/mathsnew/evidencecapture/data/local/database/entity/SensorSnapshotEntity.kt
// Kotlin - 数据层，Room 传感器快照表实体

package com.mathsnew.evidencecapture.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mathsnew.evidencecapture.domain.model.SensorSnapshot

@Entity(
    tableName = "sensor_snapshot",
    foreignKeys = [
        ForeignKey(
            entity = EvidenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["evidenceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["evidenceId"])]
)
data class SensorSnapshotEntity(
    @PrimaryKey val id: String,
    val evidenceId: String,
    val capturedAt: Long,
    val networkTime: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val address: String,
    val azimuth: Float,
    val lightLux: Float,
    val decibel: Float,
    val pressureHpa: Float,
    val wifiSsid: String,
    val operator: String,
    val weatherDesc: String,
    val temperature: Float,
    val humidity: Float,
    val windSpeed: Float
) {
    fun toDomain(): SensorSnapshot = SensorSnapshot(
        id = id,
        evidenceId = evidenceId,
        capturedAt = capturedAt,
        networkTime = networkTime,
        latitude = latitude,
        longitude = longitude,
        altitude = altitude,
        address = address,
        azimuth = azimuth,
        lightLux = lightLux,
        decibel = decibel,
        pressureHpa = pressureHpa,
        wifiSsid = wifiSsid,
        operator = operator,
        weatherDesc = weatherDesc,
        temperature = temperature,
        humidity = humidity,
        windSpeed = windSpeed
    )

    companion object {
        fun fromDomain(s: SensorSnapshot): SensorSnapshotEntity = SensorSnapshotEntity(
            id = s.id,
            evidenceId = s.evidenceId,
            capturedAt = s.capturedAt,
            networkTime = s.networkTime,
            latitude = s.latitude,
            longitude = s.longitude,
            altitude = s.altitude,
            address = s.address,
            azimuth = s.azimuth,
            lightLux = s.lightLux,
            decibel = s.decibel,
            pressureHpa = s.pressureHpa,
            wifiSsid = s.wifiSsid,
            operator = s.operator,
            weatherDesc = s.weatherDesc,
            temperature = s.temperature,
            humidity = s.humidity,
            windSpeed = s.windSpeed
        )
    }
}