// app/src/main/java/com/mathsnew/evidencecapture/di/AppModule.kt
// Kotlin - DI 模块，提供数据库与 DAO 全局单例

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
        .fallbackToDestructiveMigration()
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