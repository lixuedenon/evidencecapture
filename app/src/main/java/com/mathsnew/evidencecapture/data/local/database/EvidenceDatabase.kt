// app/src/main/java/com/mathsnew/evidencecapture/data/local/database/EvidenceDatabase.kt
// Kotlin - 数据层，Room 数据库定义

package com.mathsnew.evidencecapture.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mathsnew.evidencecapture.data.local.database.dao.CaseDao
import com.mathsnew.evidencecapture.data.local.database.dao.EvidenceDao
import com.mathsnew.evidencecapture.data.local.database.dao.KeywordDao
import com.mathsnew.evidencecapture.data.local.database.dao.SensorSnapshotDao
import com.mathsnew.evidencecapture.data.local.database.entity.CaseEntity
import com.mathsnew.evidencecapture.data.local.database.entity.EvidenceEntity
import com.mathsnew.evidencecapture.data.local.database.entity.KeywordEntity
import com.mathsnew.evidencecapture.data.local.database.entity.SensorSnapshotEntity

/**
 * Room 数据库，version = 3
 * Migration 2→3：evidence 表新增 notes 列（TEXT，默认空字符串）
 */
@Database(
    entities = [
        EvidenceEntity::class,
        SensorSnapshotEntity::class,
        CaseEntity::class,
        KeywordEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class EvidenceDatabase : RoomDatabase() {
    abstract fun evidenceDao(): EvidenceDao
    abstract fun sensorSnapshotDao(): SensorSnapshotDao
    abstract fun caseDao(): CaseDao
    abstract fun keywordDao(): KeywordDao

    companion object {
        const val DATABASE_NAME = "evidence_capture.db"

        /**
         * v2 → v3：evidence 表新增 notes 列
         * 存量数据默认为空字符串，不影响已有证据
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE evidence ADD COLUMN notes TEXT NOT NULL DEFAULT ''"
                )
            }
        }
    }
}