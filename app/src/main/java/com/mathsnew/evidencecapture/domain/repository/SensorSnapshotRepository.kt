// app/src/main/java/com/mathsnew/evidencecapture/domain/repository/SensorSnapshotRepository.kt
// Kotlin - 领域层接口，传感器快照仓库

package com.mathsnew.evidencecapture.domain.repository

import com.mathsnew.evidencecapture.domain.model.SensorSnapshot

interface SensorSnapshotRepository {
    suspend fun insert(snapshot: SensorSnapshot)
    suspend fun getByEvidenceId(evidenceId: String): SensorSnapshot?
    suspend fun updateAddress(evidenceId: String, address: String)
    suspend fun updateWeather(
        evidenceId: String,
        desc: String,
        temperature: Float,
        humidity: Float,
        windSpeed: Float
    )
    suspend fun updateNetworkTime(evidenceId: String, networkTime: Long)
}