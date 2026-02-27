// app/src/main/java/com/mathsnew/evidencecapture/domain/model/MediaType.kt
// Kotlin - 领域模型，证据媒体类型枚举

package com.mathsnew.evidencecapture.domain.model

/**
 * 证据媒体类型
 * PHOTO - 拍照取证
 * VIDEO - 录视频取证
 * AUDIO - 录音取证
 * TEXT  - 文字记录（含语音转文字输入）
 */
enum class MediaType {
    PHOTO,
    VIDEO,
    AUDIO,
    TEXT
}