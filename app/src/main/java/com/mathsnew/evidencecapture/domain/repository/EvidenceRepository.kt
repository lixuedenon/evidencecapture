// app/src/main/java/com/mathsnew/evidencecapture/domain/repository/EvidenceRepository.kt
// Kotlin - 领域层接口，证据仓库，不依赖任何框架实现

package com.mathsnew.evidencecapture.domain.repository

import com.mathsnew.evidencecapture.domain.model.Evidence
import kotlinx.coroutines.flow.Flow

interface EvidenceRepository {

    /** 保存一条证据（insert or replace） */
    suspend fun save(evidence: Evidence)

    /** 根据 ID 查询单条证据 */
    suspend fun getById(id: String): Evidence?

    /** 获取全部证据，按创建时间倒序，Flow 持续监听数据库变化 */
    fun getAll(): Flow<List<Evidence>>

    /** 删除指定证据（级联删除关联的传感器快照和媒体文件由调用方负责） */
    suspend fun delete(id: String)

    /**
     * 标记为已上传（预留接口，云端上传功能实现时调用）
     * @param id        证据 ID
     * @param uploadUrl 云端文件地址
     */
    suspend fun markAsUploaded(id: String, uploadUrl: String)
}