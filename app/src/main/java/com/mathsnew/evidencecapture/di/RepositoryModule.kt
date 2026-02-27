// app/src/main/java/com/mathsnew/evidencecapture/di/RepositoryModule.kt
// Kotlin - DI 模块，绑定 Repository 接口与实现类

package com.mathsnew.evidencecapture.di

import com.mathsnew.evidencecapture.data.repository.EvidenceRepositoryImpl
import com.mathsnew.evidencecapture.data.repository.SensorSnapshotRepositoryImpl
import com.mathsnew.evidencecapture.domain.repository.EvidenceRepository
import com.mathsnew.evidencecapture.domain.repository.SensorSnapshotRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindEvidenceRepository(
        impl: EvidenceRepositoryImpl
    ): EvidenceRepository

    @Binds
    @Singleton
    abstract fun bindSensorSnapshotRepository(
        impl: SensorSnapshotRepositoryImpl
    ): SensorSnapshotRepository
}