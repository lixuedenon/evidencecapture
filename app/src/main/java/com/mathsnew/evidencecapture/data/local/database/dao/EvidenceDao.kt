// app/src/main/java/com/mathsnew/evidencecapture/data/local/database/dao/EvidenceDao.kt
// 修改文件 - Kotlin

package com.mathsnew.evidencecapture.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mathsnew.evidencecapture.data.local.database.entity.EvidenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EvidenceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: EvidenceEntity)

    @Query("SELECT * FROM evidence WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): EvidenceEntity?

    // 主页：只返回未删除的证据
    @Query("SELECT * FROM evidence WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<EvidenceEntity>>

    // 回收站：返回已软删除的证据，按删除时间倒序
    @Query("SELECT * FROM evidence WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun getTrashFlow(): Flow<List<EvidenceEntity>>

    // 软删除：标记删除时间，移入回收站
    @Query("UPDATE evidence SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long)

    // 恢复：清除删除时间，移回主页
    @Query("UPDATE evidence SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: String)

    // 永久删除：从数据库彻底移除
    @Query("DELETE FROM evidence WHERE id = :id")
    suspend fun deleteById(id: String)

    // 清理超过 30 天的回收站记录（由后台任务定期调用）
    @Query("DELETE FROM evidence WHERE deletedAt IS NOT NULL AND deletedAt < :expireTime")
    suspend fun purgeExpired(expireTime: Long)

    // 获取所有已过期的回收站证据（用于清理前先删文件）
    @Query("SELECT * FROM evidence WHERE deletedAt IS NOT NULL AND deletedAt < :expireTime")
    suspend fun getExpired(expireTime: Long): List<EvidenceEntity>

    @Query("UPDATE evidence SET title = :title, tag = :tag, notes = :notes WHERE id = :id")
    suspend fun updateMeta(id: String, title: String, tag: String, notes: String)

    @Query("UPDATE evidence SET isUploaded = 1 WHERE id = :id")
    suspend fun markAsUploaded(id: String)
}