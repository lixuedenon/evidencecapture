// app/src/main/java/com/mathsnew/evidencecapture/data/local/database/dao/CaseDao.kt
// Kotlin - 数据层，Room 案件表 DAO（预留，当前版本不使用）
// 注意：所有参数名使用 entity 代替 case，避免与 Kotlin 保留字冲突

package com.mathsnew.evidencecapture.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mathsnew.evidencecapture.data.local.database.entity.CaseEntity

@Dao
interface CaseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CaseEntity)

    @Query("SELECT * FROM cases WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CaseEntity?

    @Query("SELECT * FROM cases ORDER BY createdAt DESC")
    suspend fun getAll(): List<CaseEntity>

    @Query("DELETE FROM cases WHERE id = :id")
    suspend fun deleteById(id: String)
}