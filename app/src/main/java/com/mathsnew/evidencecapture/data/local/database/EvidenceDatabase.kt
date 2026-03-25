// app/src/main/java/com/mathsnew/evidencecapture/data/local/database/EvidenceDatabase.kt
// 修改文件 - Kotlin

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
 * Room 数据库，version = 4
 * Migration 2→3：evidence 表新增 notes 列
 * Migration 3→4：evidence 表新增 deletedAt 列（回收站软删除时间戳）
 */
@Database(
    entities = [
        EvidenceEntity::class,
        SensorSnapshotEntity::class,
        CaseEntity::class,
        KeywordEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class EvidenceDatabase : RoomDatabase() {
    abstract fun evidenceDao(): EvidenceDao
    abstract fun sensorSnapshotDao(): SensorSnapshotDao
    abstract fun caseDao(): CaseDao
    abstract fun keywordDao(): KeywordDao

    companion object {
        const val DATABASE_NAME = "evidence_capture.db"

        // v2 → v3：新增 notes 列
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE evidence ADD COLUMN notes TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        // v3 → v4：新增 deletedAt 列（NULL = 正常证据，非 NULL = 回收站）
        // 存量数据默认 NULL，即全部归为正常证据，不影响已有数据
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE evidence ADD COLUMN deletedAt INTEGER DEFAULT NULL"
                )
            }
        }
    }
}