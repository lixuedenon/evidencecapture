// app/src/main/java/com/mathsnew/evidencecapture/data/local/database/entity/KeywordEntity.kt
// Kotlin - 数据层，Room 关键词表实体（预留，当前版本不使用）

package com.mathsnew.evidencecapture.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 关键词表（预留）
 * 将来实现证据全文搜索时使用
 */
@Entity(tableName = "keywords")
data class KeywordEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val evidenceId: String,
    val keyword: String
)