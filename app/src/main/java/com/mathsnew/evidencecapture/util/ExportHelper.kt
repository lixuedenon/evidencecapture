// app/src/main/java/com/mathsnew/evidencecapture/util/ExportHelper.kt
// Kotlin - 工具层，证据导出打包为 zip，含媒体文件、传感器JSON、哈希校验文件

package com.mathsnew.evidencecapture.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.gson.GsonBuilder
import com.mathsnew.evidencecapture.domain.model.Evidence
import com.mathsnew.evidencecapture.domain.model.SensorSnapshot
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ExportHelper {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * 将单条证据打包为 zip 文件
     * 包含：媒体文件、语音备注、sensor_data.json、hash.txt
     * @return zip 文件，失败返回 null
     */
    fun exportToZip(
        context: Context,
        evidence: Evidence,
        snapshot: SensorSnapshot?
    ): File? {
        return try {
            val exportDir = File(context.cacheDir, "exports").also { it.mkdirs() }
            val zipName = "evidence_${evidence.id}.zip"
            val zipFile = File(exportDir, zipName)

            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->

                // 1. 媒体文件
                if (evidence.mediaPath.isNotEmpty()) {
                    val mediaFile = File(evidence.mediaPath)
                    if (mediaFile.exists()) {
                        addFileToZip(zos, mediaFile, "media/${mediaFile.name}")
                    }
                }

                // 2. 语音备注
                if (evidence.voiceNotePath.isNotEmpty()) {
                    val voiceFile = File(evidence.voiceNotePath)
                    if (voiceFile.exists()) {
                        addFileToZip(zos, voiceFile, "media/${voiceFile.name}")
                    }
                }

                // 3. 传感器数据 JSON
                val sensorJson = gson.toJson(
                    mapOf(
                        "evidenceId" to evidence.id,
                        "mediaType" to evidence.mediaType.name,
                        "title" to evidence.title,
                        "tag" to evidence.tag,
                        "createdAt" to SimpleDateFormat(
                            "yyyy-MM-dd HH:mm:ss", Locale.getDefault()
                        ).format(Date(evidence.createdAt)),
                        "sha256" to evidence.sha256Hash,
                        "sensor" to snapshot
                    )
                )
                addTextToZip(zos, sensorJson, "sensor_data.json")

                // 4. 哈希校验文件
                if (evidence.sha256Hash.isNotEmpty()) {
                    val mediaFile = File(evidence.mediaPath)
                    addTextToZip(
                        zos,
                        "${evidence.sha256Hash}  ${mediaFile.name}\n",
                        "hash.txt"
                    )
                }

                // 5. 说明文件
                val readme = buildString {
                    appendLine("EvidenceCapturer 取证记录")
                    appendLine("========================")
                    appendLine("证据ID: ${evidence.id}")
                    appendLine("类型: ${evidence.mediaType.name}")
                    appendLine("标题: ${evidence.title}")
                    appendLine("标签: ${evidence.tag}")
                    appendLine("时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                        Locale.getDefault()).format(Date(evidence.createdAt))}")
                    appendLine()
                    appendLine("文件说明:")
                    appendLine("- media/     媒体文件（照片/视频/录音）及语音备注")
                    appendLine("- sensor_data.json  完整环境数据（GPS/气压/光照等）")
                    appendLine("- hash.txt   SHA-256 哈希值，用于验证文件完整性")
                }
                addTextToZip(zos, readme, "README.txt")
            }

            zipFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 获取分享 Intent
     * 分享媒体文件本身（不打包）
     */
    fun getShareIntent(context: Context, evidence: Evidence): Intent? {
        val path = if (evidence.mediaPath.isNotEmpty()) evidence.mediaPath else return null
        val file = File(path).takeIf { it.exists() } ?: return null
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val mimeType = when {
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            path.endsWith(".png") -> "image/png"
            path.endsWith(".mp4") -> "video/mp4"
            path.endsWith(".m4a") || path.endsWith(".aac") -> "audio/mp4"
            else -> "*/*"
        }
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * 获取 zip 导出文件的分享 Intent
     */
    fun getZipShareIntent(context: Context, zipFile: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            zipFile
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun addFileToZip(zos: ZipOutputStream, file: File, entryName: String) {
        zos.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { fis ->
            fis.copyTo(zos, bufferSize = 8192)
        }
        zos.closeEntry()
    }

    private fun addTextToZip(zos: ZipOutputStream, text: String, entryName: String) {
        zos.putNextEntry(ZipEntry(entryName))
        zos.write(text.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }
}