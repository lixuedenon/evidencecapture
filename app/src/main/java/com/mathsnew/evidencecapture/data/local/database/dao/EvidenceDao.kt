// app/src/main/java/com/mathsnew/evidencecapture/data/local/database/dao/EvidenceDao.kt
// Kotlin - 数据层，Room 证据表 DAO

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

    @Query("SELECT * FROM evidence ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<EvidenceEntity>>

    @Query("DELETE FROM evidence WHERE id = :id")
    suspend fun deleteById(id: String)

    /** 标记已上传（预留，云端功能实现时调用） */
    @Query("UPDATE evidence SET isUploaded = 1 WHERE id = :id")
    suspend fun markAsUploaded(id: String)
}