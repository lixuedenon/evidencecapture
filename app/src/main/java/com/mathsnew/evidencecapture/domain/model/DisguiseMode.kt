// app/src/main/java/com/mathsnew/evidencecapture/domain/model/DisguiseMode.kt
// Kotlin - 领域模型，录音伪装界面模式枚举

package com.mathsnew.evidencecapture.domain.model

/**
 * 录音界面伪装模式，在不方便显露录音状态时切换
 * NONE       - 显示正常录音界面
 * MUSIC      - 伪装成音乐播放器
 * CALCULATOR - 伪装成计算器
 * CALL       - 伪装成通话界面
 * NEWS       - 伪装成新闻阅读
 */
enum class DisguiseMode {
    NONE,
    MUSIC,
    CALCULATOR,
    CALL,
    NEWS
}