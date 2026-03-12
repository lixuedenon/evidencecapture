// app/src/main/java/com/mathsnew/evidencecapture/util/ExportHelper.kt
// Kotlin - 工具层，证据导出：ZIP打包 + HTML自包含报告生成 + 分享Intent

package com.mathsnew.evidencecapture.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.gson.GsonBuilder
import com.mathsnew.evidencecapture.domain.model.Evidence
import com.mathsnew.evidencecapture.domain.model.MediaType
import com.mathsnew.evidencecapture.domain.model.SensorSnapshot
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import android.util.Base64

object ExportHelper {

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    // ─────────────────────────────────────────────────────────────
    // ZIP 导出（保留原有功能）
    // ─────────────────────────────────────────────────────────────

    fun exportToZip(
        context: Context,
        evidence: Evidence,
        snapshot: SensorSnapshot?
    ): File? {
        return try {
            val exportDir = File(context.cacheDir, "exports").also { it.mkdirs() }
            val zipFile = File(exportDir, "evidence_${evidence.id}.zip")

            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                if (evidence.mediaPath.isNotEmpty()) {
                    val f = File(evidence.mediaPath)
                    if (f.exists()) addFileToZip(zos, f, "media/${f.name}")
                }
                if (evidence.voiceNotePath.isNotEmpty()) {
                    val f = File(evidence.voiceNotePath)
                    if (f.exists()) addFileToZip(zos, f, "media/${f.name}")
                }
                val sensorJson = gson.toJson(mapOf(
                    "evidenceId" to evidence.id,
                    "mediaType"  to evidence.mediaType.name,
                    "title"      to evidence.title,
                    "tag"        to evidence.tag,
                    "createdAt"  to dateFmt.format(Date(evidence.createdAt)),
                    "sha256"     to evidence.sha256Hash,
                    "sensor"     to snapshot
                ))
                addTextToZip(zos, sensorJson, "sensor_data.json")
                if (evidence.sha256Hash.isNotEmpty()) {
                    val mediaFile = File(evidence.mediaPath)
                    addTextToZip(zos, "${evidence.sha256Hash}  ${mediaFile.name}\n", "hash.txt")
                }
                addTextToZip(zos, buildReadme(evidence), "README.txt")
            }
            zipFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ─────────────────────────────────────────────────────────────
    // HTML 自包含报告生成
    // 媒体文件以 Base64 内嵌，任何浏览器离线可查看
    // ─────────────────────────────────────────────────────────────

    fun exportToHtml(
        context: Context,
        evidence: Evidence,
        snapshot: SensorSnapshot?
    ): File? {
        return try {
            val exportDir = File(context.cacheDir, "exports").also { it.mkdirs() }
            val htmlFile = File(exportDir, "evidence_${evidence.id}.html")
            val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
            val content = buildHtml(evidence, snapshot).toByteArray(Charsets.UTF_8)
            FileOutputStream(htmlFile).use { fos ->
                fos.write(bom)
                fos.write(content)
            }
            htmlFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun buildHtml(evidence: Evidence, snapshot: SensorSnapshot?): String {
        val timeStr = dateFmt.format(Date(evidence.createdAt))
        val mediaTypeLabel = when (evidence.mediaType) {
            MediaType.PHOTO -> "📷 拍照取证"
            MediaType.VIDEO -> "🎬 录像取证"
            MediaType.AUDIO -> "🎙 录音取证"
            MediaType.TEXT  -> "📝 文字记录"
        }
        val hashShort = if (evidence.sha256Hash.length >= 16)
            evidence.sha256Hash.take(16) + "..." else evidence.sha256Hash
        val hashColor = if (evidence.sha256Hash.isNotEmpty()) "#27ae60" else "#95a5a6"
        val hashLabel = if (evidence.sha256Hash.isNotEmpty()) "✓ 完整性已记录" else "未计算"

        // ── 媒体区域 HTML ──────────────────────────────────────
        val mediaHtml = buildMediaHtml(evidence)

        // ── 语音备注 HTML ──────────────────────────────────────
        val voiceHtml = if (evidence.voiceNotePath.isNotEmpty() &&
            File(evidence.voiceNotePath).exists()
        ) {
            val b64 = fileToBase64(evidence.voiceNotePath)
            val ext = evidence.voiceNotePath.substringAfterLast(".")
            val mime = if (ext == "mp3") "audio/mpeg" else "audio/mp4"
            """
            <div class="section">
              <div class="section-title">🎙 语音备注</div>
              <audio controls style="width:100%">
                <source src="data:$mime;base64,$b64" type="$mime">
                您的浏览器不支持音频播放
              </audio>
            </div>
            """.trimIndent()
        } else ""

        // ── 文字备注 ───────────────────────────────────────────
        val notesHtml = if (evidence.notes.isNotEmpty()) """
            <div class="section">
              <div class="section-title">📌 备注</div>
              <div class="notes-content">${evidence.notes.replace("\n", "<br>")}</div>
            </div>
        """.trimIndent() else ""

        // ── 环境数据 ───────────────────────────────────────────
        val envHtml = buildEnvHtml(snapshot)

        return """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta http-equiv="Content-Type" content="text/html; charset=utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>取证记录 - ${evidence.title.ifEmpty { evidence.id }}</title>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
         background: #f0f2f5; color: #333; }
  .header { background: linear-gradient(135deg, #1a237e, #283593);
            color: white; padding: 24px 20px; }
  .header-badge { display: inline-block; background: rgba(255,255,255,0.2);
                  border-radius: 20px; padding: 4px 12px; font-size: 13px;
                  margin-bottom: 10px; }
  .header-title { font-size: 22px; font-weight: 700; margin-bottom: 6px;
                  word-break: break-all; }
  .header-time { font-size: 13px; opacity: 0.85; }
  .header-id { font-size: 11px; opacity: 0.6; margin-top: 4px; }
  .container { max-width: 680px; margin: 0 auto; padding: 16px; }
  .section { background: white; border-radius: 12px; padding: 16px;
             margin-bottom: 12px; box-shadow: 0 1px 4px rgba(0,0,0,0.08); }
  .section-title { font-size: 14px; font-weight: 600; color: #1a237e;
                   margin-bottom: 12px; display: flex; align-items: center; gap: 6px; }
  .integrity-bar { display: flex; align-items: center; gap: 10px;
                   background: #e8f5e9; border-radius: 8px; padding: 12px; }
  .integrity-icon { font-size: 22px; }
  .integrity-label { font-weight: 600; color: $hashColor; font-size: 14px; }
  .integrity-hash { font-family: monospace; font-size: 11px; color: #666;
                    word-break: break-all; margin-top: 2px; }
  .env-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
  .env-item { background: #f8f9fa; border-radius: 8px; padding: 10px; }
  .env-label { font-size: 11px; color: #888; margin-bottom: 2px; }
  .env-value { font-size: 14px; font-weight: 600; color: #333; }
  .env-full { grid-column: 1 / -1; }
  .gps-link { color: #1a237e; text-decoration: none; font-size: 13px; }
  .tag-chip { display: inline-block; background: #e8eaf6; color: #3949ab;
              border-radius: 16px; padding: 3px 12px; font-size: 12px; }
  .notes-content { font-size: 14px; line-height: 1.7; color: #444; }
  .text-content { font-size: 15px; line-height: 1.8; color: #333;
                  background: #f8f9fa; border-radius: 8px; padding: 14px; }
  img.evidence-img { width: 100%; border-radius: 8px; display: block; }
  video.evidence-video { width: 100%; border-radius: 8px; display: block; }
  audio { width: 100%; margin-top: 4px; }
  .footer { text-align: center; font-size: 11px; color: #aaa;
            padding: 20px 0 32px; }
  @media (max-width: 400px) { .env-grid { grid-template-columns: 1fr; } }
</style>
</head>
<body>

<div class="header">
  <div class="header-badge">$mediaTypeLabel</div>
  <div class="header-title">${evidence.title.ifEmpty { "无标题" }}</div>
  <div class="header-time">$timeStr</div>
  <div class="header-id">${evidence.id}</div>
</div>

<div class="container">

  ${if (evidence.tag.isNotEmpty()) """
  <div class="section">
    <span class="tag-chip">${evidence.tag}</span>
  </div>
  """.trimIndent() else ""}

  <!-- 媒体内容 -->
  $mediaHtml

  <!-- 语音备注 -->
  $voiceHtml

  <!-- 文字备注 -->
  $notesHtml

  <!-- 完整性 -->
  <div class="section">
    <div class="section-title">🔒 文件完整性</div>
    <div class="integrity-bar">
      <div class="integrity-icon">${if (evidence.sha256Hash.isNotEmpty()) "✅" else "⚪"}</div>
      <div>
        <div class="integrity-label">$hashLabel</div>
        ${if (evidence.sha256Hash.isNotEmpty())
            """<div class="integrity-hash">SHA-256: ${evidence.sha256Hash}</div>"""
          else ""}
      </div>
    </div>
  </div>

  <!-- 环境数据 -->
  $envHtml

  <div class="footer">
    由 EvidenceCapturer 生成 · ${dateFmt.format(Date())}
  </div>

</div>
</body>
</html>
        """.trimIndent()
    }

    private fun buildMediaHtml(evidence: Evidence): String {
        return when (evidence.mediaType) {
            MediaType.PHOTO -> {
                if (evidence.mediaPath.isNotEmpty() && File(evidence.mediaPath).exists()) {
                    val b64 = fileToBase64(evidence.mediaPath)
                    val ext = evidence.mediaPath.substringAfterLast(".").lowercase()
                    val mime = if (ext == "png") "image/png" else "image/jpeg"
                    """
                    <div class="section">
                      <div class="section-title">📷 证据照片</div>
                      <img class="evidence-img" src="data:$mime;base64,$b64" alt="证据照片">
                    </div>
                    """.trimIndent()
                } else ""
            }
            MediaType.VIDEO -> {
                if (evidence.mediaPath.isNotEmpty() && File(evidence.mediaPath).exists()) {
                    val b64 = fileToBase64(evidence.mediaPath)
                    """
                    <div class="section">
                      <div class="section-title">🎬 证据视频</div>
                      <video class="evidence-video" controls playsinline>
                        <source src="data:video/mp4;base64,$b64" type="video/mp4">
                        您的浏览器不支持视频播放
                      </video>
                    </div>
                    """.trimIndent()
                } else ""
            }
            MediaType.AUDIO -> {
                if (evidence.mediaPath.isNotEmpty() && File(evidence.mediaPath).exists()) {
                    val b64 = fileToBase64(evidence.mediaPath)
                    val ext = evidence.mediaPath.substringAfterLast(".")
                    val mime = if (ext == "mp3") "audio/mpeg" else "audio/mp4"
                    """
                    <div class="section">
                      <div class="section-title">🎙 证据录音</div>
                      <audio controls style="width:100%">
                        <source src="data:$mime;base64,$b64" type="$mime">
                        您的浏览器不支持音频播放
                      </audio>
                    </div>
                    """.trimIndent()
                } else ""
            }
            MediaType.TEXT -> {
                if (evidence.textContent.isNotEmpty()) {
                    """
                    <div class="section">
                      <div class="section-title">📝 文字记录</div>
                      <div class="text-content">${evidence.textContent.replace("\n", "<br>")}</div>
                    </div>
                    """.trimIndent()
                } else ""
            }
        }
    }

    private fun buildEnvHtml(snapshot: SensorSnapshot?): String {
        if (snapshot == null) return ""

        val timeStr = dateFmt.format(Date(snapshot.capturedAt))
        val ntpStr  = if (snapshot.networkTime > 0)
            dateFmt.format(Date(snapshot.networkTime)) else "获取失败"

        val gpsHtml = if (snapshot.latitude != 0.0 || snapshot.longitude != 0.0) {
            val mapsUrl = "https://maps.google.com/?q=${snapshot.latitude},${snapshot.longitude}"
            """
            <div class="env-item env-full">
              <div class="env-label">📍 GPS 坐标</div>
              <div class="env-value">
                ${String.format("%.6f", snapshot.latitude)}, ${String.format("%.6f", snapshot.longitude)}
                ${if (snapshot.altitude != 0.0) "· 海拔 ${"%.1f".format(snapshot.altitude)}m" else ""}
              </div>
              ${if (snapshot.address.isNotEmpty())
                """<div style="font-size:12px;color:#666;margin-top:4px">${snapshot.address}</div>"""
              else ""}
              <a class="gps-link" href="$mapsUrl" target="_blank">🗺 在地图中查看</a>
            </div>
            """.trimIndent()
        } else """
            <div class="env-item env-full">
              <div class="env-label">📍 GPS 坐标</div>
              <div class="env-value" style="color:#aaa">未获取到定位</div>
            </div>
        """.trimIndent()

        val weatherHtml = if (snapshot.weatherDesc.isNotEmpty()) """
            <div class="env-item env-full">
              <div class="env-label">🌤 天气</div>
              <div class="env-value">${snapshot.weatherDesc} · ${"%.1f".format(snapshot.temperature)}℃
                · 湿度 ${"%.0f".format(snapshot.humidity)}%
                · 风速 ${"%.1f".format(snapshot.windSpeed)} m/s
              </div>
            </div>
        """.trimIndent() else ""

        val wifiHtml = if (snapshot.wifiSsid.isNotEmpty()) """
            <div class="env-item">
              <div class="env-label">📶 WiFi</div>
              <div class="env-value">${snapshot.wifiSsid}</div>
            </div>
        """.trimIndent() else ""

        val operatorHtml = if (snapshot.operator.isNotEmpty()) """
            <div class="env-item">
              <div class="env-label">📡 运营商</div>
              <div class="env-value">${snapshot.operator}</div>
            </div>
        """.trimIndent() else ""

        return """
        <div class="section">
          <div class="section-title">🌍 环境数据</div>
          <div class="env-grid">

            $gpsHtml
            $weatherHtml

            <div class="env-item">
              <div class="env-label">⏱ 设备时间</div>
              <div class="env-value" style="font-size:13px">$timeStr</div>
            </div>
            <div class="env-item">
              <div class="env-label">🌐 NTP网络时间</div>
              <div class="env-value" style="font-size:13px">$ntpStr</div>
            </div>

            <div class="env-item">
              <div class="env-label">💡 光照强度</div>
              <div class="env-value">${"%.1f".format(snapshot.lightLux)} lux</div>
            </div>
            <div class="env-item">
              <div class="env-label">🧭 方位角</div>
              <div class="env-value">${"%.1f".format(snapshot.azimuth)}°</div>
            </div>
            <div class="env-item">
              <div class="env-label">🔊 环境分贝</div>
              <div class="env-value">${"%.1f".format(snapshot.decibel)} dB</div>
            </div>
            <div class="env-item">
              <div class="env-label">🌡 气压</div>
              <div class="env-value">${"%.1f".format(snapshot.pressureHpa)} hPa</div>
            </div>

            $wifiHtml
            $operatorHtml

          </div>
        </div>
        """.trimIndent()
    }

    // ─────────────────────────────────────────────────────────────
    // 分享 Intent
    // ─────────────────────────────────────────────────────────────

    /**
     * 获取 HTML 报告分享 Intent
     * 调用方式：生成HTML → 调此函数 → startActivity(createChooser)
     */
    fun getHtmlShareIntent(context: Context, htmlFile: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            htmlFile
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/html"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * 获取媒体文件直接分享 Intent（保留原有功能）
     */
    fun getShareIntent(context: Context, evidence: Evidence): Intent? {
        val path = if (evidence.mediaPath.isNotEmpty()) evidence.mediaPath else return null
        val file = File(path).takeIf { it.exists() } ?: return null
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val mimeType = when {
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            path.endsWith(".png")  -> "image/png"
            path.endsWith(".mp4")  -> "video/mp4"
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
     * 获取 ZIP 导出分享 Intent（保留原有功能）
     */
    fun getZipShareIntent(context: Context, zipFile: File): Intent {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", zipFile
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 工具函数
    // ─────────────────────────────────────────────────────────────

    private fun fileToBase64(path: String): String {
        return try {
            val bytes = File(path).readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            ""
        }
    }

    private fun buildReadme(evidence: Evidence): String = buildString {
        appendLine("EvidenceCapturer 取证记录")
        appendLine("========================")
        appendLine("证据ID: ${evidence.id}")
        appendLine("类型: ${evidence.mediaType.name}")
        appendLine("标题: ${evidence.title}")
        appendLine("标签: ${evidence.tag}")
        appendLine("时间: ${dateFmt.format(Date(evidence.createdAt))}")
        appendLine()
        appendLine("文件说明:")
        appendLine("- media/            媒体文件（照片/视频/录音）及语音备注")
        appendLine("- sensor_data.json  完整环境数据（GPS/气压/光照等）")
        appendLine("- hash.txt          SHA-256 哈希值，用于验证文件完整性")
    }

    private fun addFileToZip(zos: ZipOutputStream, file: File, entryName: String) {
        zos.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { it.copyTo(zos, bufferSize = 8192) }
        zos.closeEntry()
    }

    private fun addTextToZip(zos: ZipOutputStream, text: String, entryName: String) {
        zos.putNextEntry(ZipEntry(entryName))
        zos.write(text.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }
}