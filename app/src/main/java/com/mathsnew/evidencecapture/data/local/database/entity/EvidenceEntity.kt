// app/src/main/java/com/mathsnew/evidencecapture/data/local/database/entity/EvidenceEntity.kt
// 修改文件 - Kotlin

package com.mathsnew.evidencecapture.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mathsnew.evidencecapture.domain.model.Evidence
import com.mathsnew.evidencecapture.domain.model.MediaType

@Entity(tableName = "evidence")
data class EvidenceEntity(
    @PrimaryKey val id: String,
    val mediaType: String,
    val caseId: String,
    val mediaPath: String,
    val voiceNotePath: String,
    val textContent: String,
    val tag: String,
    val title: String,
    val notes: String,
    val sha256Hash: String,
    val createdAt: Long,
    val isUploaded: Boolean,
    val snapshotId: String,
    // 软删除时间戳：null = 正常证据，非 null = 在回收站中
    // Migration 3→4 新增，默认 null（存量数据不受影响）
    val deletedAt: Long? = null
) {
    fun toDomain(): Evidence = Evidence(
        id            = id,
        mediaType     = MediaType.valueOf(mediaType),
        caseId        = caseId,
        mediaPath     = mediaPath,
        voiceNotePath = voiceNotePath,
        textContent   = textContent,
        tag           = tag,
        title         = title,
        notes         = notes,
        sha256Hash    = sha256Hash,
        createdAt     = createdAt,
        isUploaded    = isUploaded,
        snapshotId    = snapshotId,
        deletedAt     = deletedAt
    )

    companion object {
        fun fromDomain(e: Evidence): EvidenceEntity = EvidenceEntity(
            id            = e.id,
            mediaType     = e.mediaType.name,
            caseId        = e.caseId,
            mediaPath     = e.mediaPath,
            voiceNotePath = e.voiceNotePath,
            textContent   = e.textContent,
            tag           = e.tag,
            title         = e.title,
            notes         = e.notes,
            sha256Hash    = e.sha256Hash,
            createdAt     = e.createdAt,
            isUploaded    = e.isUploaded,
            snapshotId    = e.snapshotId,
            deletedAt     = e.deletedAt
        )
    }
}