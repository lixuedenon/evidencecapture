// app/src/main/java/com/mathsnew/evidencecapture/data/repository/SensorSnapshotRepositoryImpl.kt
// Kotlin - 数据层，传感器快照仓库实现

package com.mathsnew.evidencecapture.data.repository

import com.mathsnew.evidencecapture.data.local.database.dao.SensorSnapshotDao
import com.mathsnew.evidencecapture.data.local.database.entity.SensorSnapshotEntity
import com.mathsnew.evidencecapture.domain.model.SensorSnapshot
import com.mathsnew.evidencecapture.domain.repository.SensorSnapshotRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SensorSnapshotRepositoryImpl @Inject constructor(
    private val snapshotDao: SensorSnapshotDao
) : SensorSnapshotRepository {

    override suspend fun insert(snapshot: SensorSnapshot) {
        snapshotDao.insert(SensorSnapshotEntity.fromDomain(snapshot))
    }

    override suspend fun getByEvidenceId(evidenceId: String): SensorSnapshot? {
        return snapshotDao.getByEvidenceId(evidenceId)?.toDomain()
    }

    /**
     * 将 Room Flow<Entity?> 映射为 Flow<SensorSnapshot?>
     * 数据库中 weatherDesc/address 等字段更新后，Flow 自动发出新值
     */
    override fun observeByEvidenceId(evidenceId: String): Flow<SensorSnapshot?> {
        return snapshotDao.observeByEvidenceId(evidenceId).map { it?.toDomain() }
    }

    override suspend fun updateWeather(
        evidenceId: String,
        desc: String,
        temperature: Float,
        humidity: Float,
        windSpeed: Float
    ) {
        snapshotDao.updateWeather(evidenceId, desc, temperature, humidity, windSpeed)
    }

    override suspend fun updateAddress(evidenceId: String, address: String) {
        snapshotDao.updateAddress(evidenceId, address)
    }

    override suspend fun updateNetworkTime(evidenceId: String, networkTime: Long) {
        snapshotDao.updateNetworkTime(evidenceId, networkTime)
    }
}