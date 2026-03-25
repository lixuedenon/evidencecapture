// app/src/main/java/com/mathsnew/evidencecapture/di/AppModule.kt
// 修改文件 - Kotlin

package com.mathsnew.evidencecapture.di

import android.content.Context
import androidx.room.Room
import com.mathsnew.evidencecapture.data.local.database.EvidenceDatabase
import com.mathsnew.evidencecapture.data.local.database.dao.CaseDao
import com.mathsnew.evidencecapture.data.local.database.dao.EvidenceDao
import com.mathsnew.evidencecapture.data.local.database.dao.KeywordDao
import com.mathsnew.evidencecapture.data.local.database.dao.SensorSnapshotDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideEvidenceDatabase(
        @ApplicationContext context: Context
    ): EvidenceDatabase = Room.databaseBuilder(
        context,
        EvidenceDatabase::class.java,
        EvidenceDatabase.DATABASE_NAME
    )
        .addMigrations(
            EvidenceDatabase.MIGRATION_2_3,
            EvidenceDatabase.MIGRATION_3_4   // v3→v4：新增 deletedAt 列（回收站）
        )
        .build()

    @Provides
    @Singleton
    fun provideEvidenceDao(db: EvidenceDatabase): EvidenceDao = db.evidenceDao()

    @Provides
    @Singleton
    fun provideSensorSnapshotDao(db: EvidenceDatabase): SensorSnapshotDao = db.sensorSnapshotDao()

    @Provides
    @Singleton
    fun provideCaseDao(db: EvidenceDatabase): CaseDao = db.caseDao()

    @Provides
    @Singleton
    fun provideKeywordDao(db: EvidenceDatabase): KeywordDao = db.keywordDao()
}