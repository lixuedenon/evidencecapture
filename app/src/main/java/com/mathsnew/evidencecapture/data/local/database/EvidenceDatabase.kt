// app/src/main/java/com/mathsnew/evidencecapture/data/local/database/EvidenceDatabase.kt
// Kotlin - 数据层，Room 数据库定义

package com.mathsnew.evidencecapture.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mathsnew.evidencecapture.data.local.database.dao.CaseDao
import com.mathsnew.evidencecapture.data.local.database.dao.EvidenceDao
import com.mathsnew.evidencecapture.data.local.database.dao.KeywordDao
import com.mathsnew.evidencecapture.data.local.database.dao.SensorSnapshotDao
import com.mathsnew.evidencecapture.data.local.database.entity.CaseEntity
import com.mathsnew.evidencecapture.data.local.database.entity.EvidenceEntity
import com.mathsnew.evidencecapture.data.local.database.entity.KeywordEntity
import com.mathsnew.evidencecapture.data.local.database.entity.SensorSnapshotEntity

/**
 * Room 数据库，version = 2
 * 开发阶段使用 fallbackToDestructiveMigration，正式发布前必须替换为完整 Migration
 */
@Database(
    entities = [
        EvidenceEntity::class,
        SensorSnapshotEntity::class,
        CaseEntity::class,
        KeywordEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class EvidenceDatabase : RoomDatabase() {
    abstract fun evidenceDao(): EvidenceDao
    abstract fun sensorSnapshotDao(): SensorSnapshotDao
    abstract fun caseDao(): CaseDao
    abstract fun keywordDao(): KeywordDao

    companion object {
        const val DATABASE_NAME = "evidence_capture.db"
    }
}