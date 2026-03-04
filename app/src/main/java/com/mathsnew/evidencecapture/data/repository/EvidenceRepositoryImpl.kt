// app/src/main/java/com/mathsnew/evidencecapture/data/repository/EvidenceRepositoryImpl.kt
// Kotlin - 数据层，证据仓库实现

package com.mathsnew.evidencecapture.data.repository

import com.mathsnew.evidencecapture.data.local.database.dao.EvidenceDao
import com.mathsnew.evidencecapture.data.local.database.entity.EvidenceEntity
import com.mathsnew.evidencecapture.domain.model.Evidence
import com.mathsnew.evidencecapture.domain.repository.EvidenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class EvidenceRepositoryImpl @Inject constructor(
    private val evidenceDao: EvidenceDao
) : EvidenceRepository {

    override suspend fun save(evidence: Evidence) {
        evidenceDao.insert(EvidenceEntity.fromDomain(evidence))
    }

    override suspend fun getById(id: String): Evidence? {
        return evidenceDao.getById(id)?.toDomain()
    }

    override fun getAll(): Flow<List<Evidence>> {
        return evidenceDao.getAllFlow().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun delete(id: String) {
        evidenceDao.deleteById(id)
    }

    override suspend fun updateMeta(id: String, title: String, tag: String, notes: String) {
        evidenceDao.updateMeta(id, title, tag, notes)
    }

    override suspend fun markAsUploaded(id: String, uploadUrl: String) {
        evidenceDao.markAsUploaded(id)
    }
}