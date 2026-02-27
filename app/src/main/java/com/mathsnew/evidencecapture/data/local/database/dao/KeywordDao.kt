// app/src/main/java/com/mathsnew/evidencecapture/data/local/database/dao/KeywordDao.kt
// Kotlin - 数据层，Room 关键词表 DAO（预留，当前版本不使用）

package com.mathsnew.evidencecapture.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mathsnew.evidencecapture.data.local.database.entity.KeywordEntity

@Dao
interface KeywordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: KeywordEntity)

    @Query("SELECT * FROM keywords WHERE evidenceId = :evidenceId")
    suspend fun getByEvidenceId(evidenceId: String): List<KeywordEntity>

    @Query("DELETE FROM keywords WHERE evidenceId = :evidenceId")
    suspend fun deleteByEvidenceId(evidenceId: String)
}