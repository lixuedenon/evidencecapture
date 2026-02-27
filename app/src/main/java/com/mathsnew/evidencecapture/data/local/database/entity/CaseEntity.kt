// app/src/main/java/com/mathsnew/evidencecapture/data/local/database/entity/CaseEntity.kt
// Kotlin - 数据层，Room 案件表实体（预留，当前版本不使用）

package com.mathsnew.evidencecapture.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 案件表（预留）
 * 将来实现案件管理时，多条 Evidence 通过 caseId 归入同一案件
 */
@Entity(tableName = "cases")
data class CaseEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val createdAt: Long,
    val updatedAt: Long
)