// app/src/main/java/com/mathsnew/evidencecapture/domain/model/Evidence.kt
// Kotlin - 领域模型，证据主体

package com.mathsnew.evidencecapture.domain.model

/**
 * 证据领域模型
 *
 * @param id            唯一证据 ID，格式 EV20240220-143052-001
 * @param mediaType     媒体类型，见 MediaType
 * @param caseId        案件 ID（预留字段，当前为空字符串）
 * @param mediaPath     媒体文件本地绝对路径，TEXT 类型时为空
 * @param voiceNotePath 语音备注文件路径（拍照取证可选附加）
 * @param textContent   文字内容（TEXT 类型时有效）
 * @param tag           场景标签（如"租房纠纷"、"交通事故"）
 * @param title         用户自定义标题
 * @param notes         用户备注，可随时编辑的自由文本
 * @param sha256Hash    媒体文件 SHA-256 哈希，用于防篡改验证
 * @param createdAt     创建时间戳（毫秒）
 * @param isUploaded    是否已上传云端（预留字段）
 * @param snapshotId    关联传感器快照 ID
 */
data class Evidence(
    val id: String,
    val mediaType: MediaType,
    val caseId: String = "",
    val mediaPath: String = "",
    val voiceNotePath: String = "",
    val textContent: String = "",
    val tag: String = "",
    val title: String = "",
    val notes: String = "",
    val sha256Hash: String = "",
    val createdAt: Long,
    val isUploaded: Boolean = false,
    val snapshotId: String = ""
)