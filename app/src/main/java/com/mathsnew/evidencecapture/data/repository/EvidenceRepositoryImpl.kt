// app/src/main/java/com/mathsnew/evidencecapture/data/repository/EvidenceRepositoryImpl.kt
// 修改文件 - Kotlin

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

    override suspend fun getById(id: String): Evidence? =
        evidenceDao.getById(id)?.toDomain()

    override fun getAll(): Flow<List<Evidence>> =
        evidenceDao.getAllFlow().map { list -> list.map { it.toDomain() } }

    override fun getTrash(): Flow<List<Evidence>> =
        evidenceDao.getTrashFlow().map { list -> list.map { it.toDomain() } }

    override suspend fun moveToTrash(id: String) {
        evidenceDao.softDelete(id, System.currentTimeMillis())
    }

    override suspend fun restore(id: String) {
        evidenceDao.restore(id)
    }

    override suspend fun delete(id: String) {
        evidenceDao.deleteById(id)
    }

    override suspend fun purgeExpired(): List<Evidence> {
        // 30天前的时间戳
        val expireTime = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        val expired = evidenceDao.getExpired(expireTime).map { it.toDomain() }
        evidenceDao.purgeExpired(expireTime)
        return expired
    }

    override suspend fun updateMeta(id: String, title: String, tag: String, notes: String) {
        evidenceDao.updateMeta(id, title, tag, notes)
    }

    override suspend fun markAsUploaded(id: String, uploadUrl: String) {
        evidenceDao.markAsUploaded(id)
    }
}