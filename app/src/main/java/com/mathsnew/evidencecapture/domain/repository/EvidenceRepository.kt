// app/src/main/java/com/mathsnew/evidencecapture/domain/repository/EvidenceRepository.kt
// 修改文件 - Kotlin

package com.mathsnew.evidencecapture.domain.repository

import com.mathsnew.evidencecapture.domain.model.Evidence
import kotlinx.coroutines.flow.Flow

interface EvidenceRepository {

    /** 保存一条证据（insert or replace） */
    suspend fun save(evidence: Evidence)

    /** 根据 ID 查询单条证据 */
    suspend fun getById(id: String): Evidence?

    /** 获取全部未删除证据，按创建时间倒序，Flow 持续监听 */
    fun getAll(): Flow<List<Evidence>>

    /** 获取回收站中的证据（已软删除），按删除时间倒序 */
    fun getTrash(): Flow<List<Evidence>>

    /**
     * 软删除：将证据移入回收站，记录删除时间
     * 不删除媒体文件，30天后由清理任务永久删除
     */
    suspend fun moveToTrash(id: String)

    /** 从回收站恢复：清除删除时间，重新出现在主页 */
    suspend fun restore(id: String)

    /** 永久删除：从数据库彻底移除（媒体文件由调用方负责删除） */
    suspend fun delete(id: String)

    /** 清理超过 30 天的回收站记录，返回被清理的证据列表（用于删除文件） */
    suspend fun purgeExpired(): List<Evidence>

    /** 更新可编辑的元数据：标题、标签、备注 */
    suspend fun updateMeta(id: String, title: String, tag: String, notes: String)

    /** 标记为已上传（预留接口） */
    suspend fun markAsUploaded(id: String, uploadUrl: String)
}