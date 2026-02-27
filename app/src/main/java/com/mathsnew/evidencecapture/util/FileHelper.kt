// app/src/main/java/com/mathsnew/evidencecapture/util/FileHelper.kt
// Kotlin - 工具类，证据媒体文件路径管理（存储于应用私有目录，无需存储权限）

package com.mathsnew.evidencecapture.util

import android.content.Context
import java.io.File

object FileHelper {

    /**
     * 获取或创建证据专属目录
     * 路径：/data/data/com.mathsnew.evidencecapture/files/evidence/{evidenceId}/
     */
    fun getEvidenceDir(context: Context, evidenceId: String): File {
        val dir = File(context.filesDir, "evidence/$evidenceId")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getPhotoFile(context: Context, evidenceId: String): File =
        File(getEvidenceDir(context, evidenceId), "photo.jpg")

    fun getVideoFile(context: Context, evidenceId: String): File =
        File(getEvidenceDir(context, evidenceId), "video.mp4")

    fun getAudioFile(context: Context, evidenceId: String): File =
        File(getEvidenceDir(context, evidenceId), "audio.m4a")

    fun getVoiceNoteFile(context: Context, evidenceId: String): File =
        File(getEvidenceDir(context, evidenceId), "voice_note.m4a")

    /** PDF 报告路径（预留，iText 实现时使用） */
    fun getReportFile(context: Context, evidenceId: String): File =
        File(getEvidenceDir(context, evidenceId), "report.pdf")

    /** 删除证据目录及其下所有文件，删除证据记录时调用 */
    fun deleteEvidenceDir(context: Context, evidenceId: String) {
        getEvidenceDir(context, evidenceId).deleteRecursively()
    }
}