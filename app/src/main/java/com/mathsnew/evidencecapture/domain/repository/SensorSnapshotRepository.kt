// app/src/main/java/com/mathsnew/evidencecapture/domain/repository/SensorSnapshotRepository.kt
// Kotlin - 领域层接口，传感器快照仓库

package com.mathsnew.evidencecapture.domain.repository

import com.mathsnew.evidencecapture.domain.model.SensorSnapshot
import kotlinx.coroutines.flow.Flow

interface SensorSnapshotRepository {
    suspend fun insert(snapshot: SensorSnapshot)
    suspend fun getByEvidenceId(evidenceId: String): SensorSnapshot?

    /**
     * 以 Flow 持续观察指定证据的快照，天气/地址异步回填后自动发出新值
     * 供详情页 ViewModel 使用，避免天气数据写入后 UI 不刷新的问题
     */
    fun observeByEvidenceId(evidenceId: String): Flow<SensorSnapshot?>

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