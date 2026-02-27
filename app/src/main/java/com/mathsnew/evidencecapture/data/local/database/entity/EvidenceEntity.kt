// app/src/main/java/com/mathsnew/evidencecapture/data/local/database/entity/EvidenceEntity.kt
// Kotlin - 数据层，Room 证据主表实体

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
    val sha256Hash: String,
    val createdAt: Long,
    val isUploaded: Boolean,
    val snapshotId: String
) {
    fun toDomain(): Evidence = Evidence(
        id = id,
        mediaType = MediaType.valueOf(mediaType),
        caseId = caseId,
        mediaPath = mediaPath,
        voiceNotePath = voiceNotePath,
        textContent = textContent,
        tag = tag,
        title = title,
        sha256Hash = sha256Hash,
        createdAt = createdAt,
        isUploaded = isUploaded,
        snapshotId = snapshotId
    )

    companion object {
        fun fromDomain(e: Evidence): EvidenceEntity = EvidenceEntity(
            id = e.id,
            mediaType = e.mediaType.name,
            caseId = e.caseId,
            mediaPath = e.mediaPath,
            voiceNotePath = e.voiceNotePath,
            textContent = e.textContent,
            tag = e.tag,
            title = e.title,
            sha256Hash = e.sha256Hash,
            createdAt = e.createdAt,
            isUploaded = e.isUploaded,
            snapshotId = e.snapshotId
        )
    }
}