// app/src/main/java/com/mathsnew/evidencecapture/util/PdfReportBuilder.kt
// 修改文件 - Kotlin - 使用 Android 原生 Canvas PDF 生成证据记录报告
// 不依赖第三方库，中文字体原生支持，需在 Dispatchers.IO 中调用
// 多语言：所有 UI 字符串通过 context.getString(R.string.xxx) 读取

package com.mathsnew.evidencecapture.util

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.core.content.FileProvider
import com.mathsnew.evidencecapture.R
import com.mathsnew.evidencecapture.domain.model.Evidence
import com.mathsnew.evidencecapture.domain.model.MediaType
import com.mathsnew.evidencecapture.domain.model.SensorSnapshot
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfReportBuilder {

    private const val TAG = "PdfReportBuilder"

    // ── 页面尺寸：A4 72dpi ────────────────────────────────────
    private const val PAGE_WIDTH  = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN      = 40f
    private const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2

    // ── 主题色 ────────────────────────────────────────────────
    private val COLOR_PRIMARY     = Color.rgb(26, 35, 126)
    private val COLOR_PRIMARY_MID = Color.rgb(40, 53, 147)
    private val COLOR_ACCENT      = Color.rgb(66, 165, 245)
    private val COLOR_BG_LIGHT    = Color.rgb(240, 244, 255)
    private val COLOR_TEXT_MAIN   = Color.rgb(33, 33, 33)
    private val COLOR_TEXT_SUB    = Color.rgb(117, 117, 117)
    private val COLOR_TEXT_HINT   = Color.rgb(189, 189, 189)
    private val COLOR_GREEN       = Color.rgb(39, 174, 96)
    private val COLOR_ORANGE      = Color.rgb(230, 126, 18)
    private val COLOR_DIVIDER     = Color.rgb(220, 226, 240)
    private val COLOR_CARD_BG     = Color.rgb(248, 249, 252)

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    // ── 画笔工厂 ──────────────────────────────────────────────

    private fun paintText(
        size: Float,
        color: Int = COLOR_TEXT_MAIN,
        bold: Boolean = false,
        align: Paint.Align = Paint.Align.LEFT
    ) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize   = size
        this.color = color
        typeface   = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        textAlign  = align
    }

    private fun paintFill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style      = Paint.Style.FILL
    }

    private fun paintStroke(color: Int, strokeWidth: Float = 1f) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color       = color
            style            = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
        }

    // ── 主入口 ────────────────────────────────────────────────

    /**
     * 生成单条证据 PDF 报告，写入 cache/exports/ 目录
     * 必须在 Dispatchers.IO 中调用（含 Bitmap 解码和文件 IO）
     *
     * @return 生成的 PDF 文件，失败返回 null
     */
    fun build(
        context: Context,
        evidence: Evidence,
        snapshot: SensorSnapshot?
    ): File? {
        return try {
            val exportDir = File(context.cacheDir, "exports").also { it.mkdirs() }
            val pdfFile   = File(exportDir, "evidence_${evidence.id}.pdf")

            val document    = PdfDocument()
            val mediaBitmap = loadMediaBitmap(evidence)

            val renderer = PageRenderer(document, PAGE_WIDTH, PAGE_HEIGHT, MARGIN)
            drawReport(renderer, context, evidence, snapshot, mediaBitmap)
            renderer.finish()

            mediaBitmap?.recycle()

            FileOutputStream(pdfFile).use { document.writeTo(it) }
            document.close()

            Log.i(TAG, "PDF generated: ${pdfFile.absolutePath}")
            pdfFile
        } catch (e: Exception) {
            Log.e(TAG, "PDF generation failed", e)
            null
        }
    }

    /**
     * 获取 PDF 文件分享 Intent，通过 FileProvider 授权
     */
    fun getShareIntent(context: Context, pdfFile: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.pdf_share_text))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    // ── 绘制主流程 ────────────────────────────────────────────

    private fun drawReport(
        r: PageRenderer,
        ctx: Context,
        evidence: Evidence,
        snapshot: SensorSnapshot?,
        mediaBitmap: Bitmap?
    ) {
        drawHeader(r, ctx, evidence)
        r.advanceY(24f)
        drawMediaSection(r, ctx, evidence, mediaBitmap)
        r.advanceY(16f)
        drawBasicInfo(r, ctx, evidence)
        drawLocationTime(r, ctx, snapshot, evidence)
        drawEnvironment(r, ctx, snapshot)
        drawIntegrity(r, ctx, evidence)
        drawFooter(r, ctx, evidence)
    }

    // ── 顶部色块 ──────────────────────────────────────────────

    private fun drawHeader(r: PageRenderer, ctx: Context, evidence: Evidence) {
        val headerH = 110f
        val canvas  = r.canvas

        val gradient = LinearGradient(
            0f, 0f, PAGE_WIDTH.toFloat(), headerH,
            COLOR_PRIMARY, COLOR_PRIMARY_MID,
            Shader.TileMode.CLAMP
        )
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = gradient
            style  = Paint.Style.FILL
        }
        canvas.drawRect(0f, r.currentY, PAGE_WIDTH.toFloat(), r.currentY + headerH, bgPaint)

        // 类型徽标
        val typeLabel = when (evidence.mediaType) {
            MediaType.PHOTO -> ctx.getString(R.string.pdf_type_photo)
            MediaType.VIDEO -> ctx.getString(R.string.pdf_type_video)
            MediaType.AUDIO -> ctx.getString(R.string.pdf_type_audio)
            MediaType.TEXT  -> ctx.getString(R.string.pdf_type_text)
        }
        val badgePaint = paintFill(Color.argb(60, 255, 255, 255))
        val badgeRect  = RectF(MARGIN, r.currentY + 16f, MARGIN + 110f, r.currentY + 32f)
        canvas.drawRoundRect(badgeRect, 8f, 8f, badgePaint)
        canvas.drawText(typeLabel, MARGIN + 8f, r.currentY + 27f, paintText(10f, Color.WHITE))

        // 证据标题
        val title = evidence.title.ifEmpty { ctx.getString(R.string.pdf_no_title) }
        canvas.drawText(
            title.take(30),
            MARGIN, r.currentY + 54f,
            paintText(18f, Color.WHITE, bold = true)
        )

        // 时间和 ID
        canvas.drawText(
            dateFmt.format(Date(evidence.createdAt)),
            MARGIN, r.currentY + 74f,
            paintText(11f, Color.argb(210, 255, 255, 255))
        )
        canvas.drawText(
            evidence.id,
            MARGIN, r.currentY + 90f,
            paintText(9f, Color.argb(140, 255, 255, 255))
        )

        r.advanceY(headerH)
    }

    // ── 媒体内容区 ────────────────────────────────────────────

    private fun drawMediaSection(
        r: PageRenderer,
        ctx: Context,
        evidence: Evidence,
        mediaBitmap: Bitmap?
    ) {
        when (evidence.mediaType) {
            MediaType.PHOTO -> {
                if (mediaBitmap != null) {
                    drawSectionTitle(r, ctx.getString(R.string.pdf_section_photo))
                    drawBitmapFit(r, mediaBitmap, maxHeight = 220f)
                }
            }
            MediaType.VIDEO -> {
                drawSectionTitle(r, ctx.getString(R.string.pdf_section_video))
                if (mediaBitmap != null) {
                    drawBitmapFit(r, mediaBitmap, maxHeight = 200f, overlayPlay = true)
                } else {
                    drawPlaceholderCard(
                        r,
                        "🎬",
                        ctx.getString(R.string.pdf_video_placeholder),
                        getDuration(evidence.mediaPath)
                    )
                }
                val duration = getDuration(evidence.mediaPath)
                if (duration.isNotEmpty()) {
                    r.canvas.drawText(
                        ctx.getString(R.string.pdf_video_duration, duration),
                        MARGIN, r.currentY + 14f,
                        paintText(10f, COLOR_TEXT_SUB)
                    )
                    r.advanceY(20f)
                }
            }
            MediaType.AUDIO -> {
                drawSectionTitle(r, ctx.getString(R.string.pdf_section_audio))
                drawPlaceholderCard(
                    r,
                    "🎙",
                    ctx.getString(R.string.pdf_audio_placeholder),
                    getDuration(evidence.mediaPath)
                )
            }
            MediaType.TEXT -> {
                drawSectionTitle(r, ctx.getString(R.string.pdf_section_text))
                drawTextContentCard(r, evidence.textContent)
            }
        }
    }

    // ── 基本信息（标签 / 备注）────────────────────────────────

    private fun drawBasicInfo(r: PageRenderer, ctx: Context, evidence: Evidence) {
        val hasTag   = evidence.tag.isNotEmpty()
        val hasNotes = evidence.notes.isNotEmpty()
        if (!hasTag && !hasNotes) return

        r.advanceY(8f)
        drawSectionTitle(r, ctx.getString(R.string.pdf_section_basic))

        if (hasTag) {
            drawLabelValue(r, ctx.getString(R.string.pdf_label_tag),
                evidence.tag, valueColor = COLOR_PRIMARY)
        }
        if (hasNotes) {
            drawLabelValue(r, ctx.getString(R.string.pdf_label_notes), evidence.notes)
        }
        r.advanceY(8f)
    }

    // ── 地点与时间 ────────────────────────────────────────────

    private fun drawLocationTime(
        r: PageRenderer,
        ctx: Context,
        snapshot: SensorSnapshot?,
        evidence: Evidence
    ) {
        r.advanceY(8f)
        drawSectionTitle(r, ctx.getString(R.string.pdf_section_location))

        val deviceTime = if (snapshot != null)
            dateFmt.format(Date(snapshot.capturedAt)) else "—"
        drawLabelValue(r, ctx.getString(R.string.pdf_label_capture_time), deviceTime)

        if (snapshot != null) {
            if (snapshot.networkTime > 0) {
                val ntpTime = dateFmt.format(Date(snapshot.networkTime))
                val diffMs  = snapshot.networkTime - snapshot.capturedAt
                val diffStr = when {
                    Math.abs(diffMs) < 1000 ->
                        ctx.getString(R.string.pdf_ntp_diff_less1s)
                    diffMs > 0 ->
                        ctx.getString(R.string.pdf_ntp_diff_slow,
                            (Math.abs(diffMs) / 1000).toInt())
                    else ->
                        ctx.getString(R.string.pdf_ntp_diff_fast,
                            (Math.abs(diffMs) / 1000).toInt())
                }
                drawLabelValue(
                    r,
                    ctx.getString(R.string.pdf_label_ntp_time),
                    "$ntpTime（$diffStr）"
                )
            } else {
                drawLabelValue(
                    r,
                    ctx.getString(R.string.pdf_label_ntp_time),
                    ctx.getString(
                        R.string.pdf_ntp_fail,
                        dateFmt.format(Date(snapshot.capturedAt))
                    ),
                    valueColor = COLOR_ORANGE
                )
            }
        }

        if (snapshot != null && (snapshot.latitude != 0.0 || snapshot.longitude != 0.0)) {
            val coord = "%.6f, %.6f".format(snapshot.latitude, snapshot.longitude)
            val alt   = if (snapshot.altitude != 0.0)
                "  ${"%.1f".format(snapshot.altitude)}m" else ""
            drawLabelValue(r, ctx.getString(R.string.pdf_label_gps), "$coord$alt")
            if (snapshot.address.isNotEmpty()) {
                drawLabelValue(r, ctx.getString(R.string.pdf_label_address), snapshot.address)
            }
        } else {
            drawLabelValue(
                r,
                ctx.getString(R.string.pdf_label_gps),
                ctx.getString(R.string.pdf_gps_none),
                valueColor = COLOR_TEXT_HINT
            )
        }

        r.advanceY(8f)
    }

    // ── 环境数据（两列小卡片）────────────────────────────────

    private fun drawEnvironment(r: PageRenderer, ctx: Context, snapshot: SensorSnapshot?) {
        if (snapshot == null) return

        r.advanceY(8f)
        drawSectionTitle(r, ctx.getString(R.string.pdf_section_env))

        // 天气（独占一行）
        if (snapshot.weatherDesc.isNotEmpty()) {
            val weatherText = "${snapshot.weatherDesc}  " +
                    "${"%.1f".format(snapshot.temperature)}℃  " +
                    "${"%.0f".format(snapshot.humidity)}%  " +
                    "${"%.1f".format(snapshot.windSpeed)} m/s"
            drawLabelValue(r, ctx.getString(R.string.pdf_label_weather), weatherText)
        }

        val items = mutableListOf<Pair<String, String>>()
        items.add(ctx.getString(R.string.pdf_label_light) to
                "${"%.0f".format(snapshot.lightLux)} lux")
        items.add(ctx.getString(R.string.pdf_label_decibel) to
                "${"%.1f".format(snapshot.decibel)} dB")
        items.add(ctx.getString(R.string.pdf_label_azimuth) to
                "${"%.1f".format(snapshot.azimuth)}°")
        if (snapshot.pressureHpa != 0f)
            items.add(ctx.getString(R.string.pdf_label_pressure) to
                    "${"%.1f".format(snapshot.pressureHpa)} hPa")
        if (snapshot.wifiSsid.isNotEmpty())
            items.add(ctx.getString(R.string.pdf_label_wifi) to snapshot.wifiSsid)
        if (snapshot.operator.isNotEmpty())
            items.add(ctx.getString(R.string.pdf_label_operator) to snapshot.operator)

        drawEnvGrid(r, items)
        r.advanceY(8f)
    }

    // ── 两列环境数据网格 ──────────────────────────────────────

    private fun drawEnvGrid(r: PageRenderer, items: List<Pair<String, String>>) {
        val colW  = CONTENT_WIDTH / 2f - 4f
        val cardH = 36f

        var idx = 0
        while (idx < items.size) {
            r.ensureSpace(cardH + 8f)
            val y = r.currentY
            drawEnvCard(r.canvas, MARGIN, y, colW, cardH, items[idx])
            if (idx + 1 < items.size) {
                drawEnvCard(r.canvas, MARGIN + colW + 8f, y, colW, cardH, items[idx + 1])
            }
            r.advanceY(cardH + 6f)
            idx += 2
        }
    }

    private fun drawEnvCard(
        canvas: Canvas,
        x: Float, y: Float,
        w: Float, h: Float,
        item: Pair<String, String>
    ) {
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), 6f, 6f, paintFill(COLOR_CARD_BG))
        canvas.drawText(item.first,  x + 10f, y + 17f, paintText(9f,  COLOR_TEXT_SUB))
        canvas.drawText(item.second.take(20), x + 10f, y + 32f,
            paintText(12f, COLOR_TEXT_MAIN, bold = true))
    }

    // ── 完整性 ────────────────────────────────────────────────

    private fun drawIntegrity(r: PageRenderer, ctx: Context, evidence: Evidence) {
        r.advanceY(8f)
        drawSectionTitle(r, ctx.getString(R.string.pdf_section_integrity))
        r.ensureSpace(56f)

        val canvas = r.canvas
        val y      = r.currentY
        val cardH  = 52f

        val bgColor = if (evidence.sha256Hash.isNotEmpty())
            Color.rgb(232, 245, 233) else Color.rgb(245, 245, 245)
        canvas.drawRoundRect(
            RectF(MARGIN, y, MARGIN + CONTENT_WIDTH, y + cardH),
            8f, 8f, paintFill(bgColor)
        )

        if (evidence.sha256Hash.isNotEmpty()) {
            canvas.drawText("✅", MARGIN + 10f, y + 22f, paintText(16f))
            canvas.drawText(
                ctx.getString(R.string.pdf_integrity_ok),
                MARGIN + 36f, y + 22f,
                paintText(12f, COLOR_GREEN, bold = true)
            )
            val hashPaint = paintText(8f, COLOR_TEXT_SUB)
                .apply { typeface = Typeface.MONOSPACE }
            canvas.drawText(
                "${ctx.getString(R.string.detail_hash_prefix)}${evidence.sha256Hash}",
                MARGIN + 36f, y + 40f,
                hashPaint
            )
            canvas.drawText(
                ctx.getString(R.string.pdf_integrity_desc),
                MARGIN + 10f, y + cardH + 14f,
                paintText(9f, COLOR_TEXT_SUB)
            )
            r.advanceY(cardH + 20f)
        } else {
            canvas.drawText("⚪", MARGIN + 10f, y + 22f, paintText(16f))
            canvas.drawText(
                ctx.getString(R.string.pdf_integrity_none),
                MARGIN + 36f, y + 22f,
                paintText(12f, COLOR_TEXT_SUB)
            )
            r.advanceY(cardH + 8f)
        }
    }

    // ── 底部 ──────────────────────────────────────────────────

    private fun drawFooter(r: PageRenderer, ctx: Context, evidence: Evidence) {
        r.advanceY(20f)
        r.ensureSpace(30f)

        r.canvas.drawLine(
            MARGIN, r.currentY,
            MARGIN + CONTENT_WIDTH, r.currentY,
            paintStroke(COLOR_DIVIDER)
        )
        r.advanceY(10f)

        r.canvas.drawText(
            ctx.getString(
                R.string.pdf_footer,
                dateFmt.format(Date(evidence.createdAt)),
                dateFmt.format(Date())
            ),
            PAGE_WIDTH / 2f,
            r.currentY + 12f,
            paintText(8f, COLOR_TEXT_HINT, align = Paint.Align.CENTER)
        )
        r.advanceY(20f)
    }

    // ── 通用组件 ──────────────────────────────────────────────

    /** 带左侧蓝色竖条的节标题 */
    private fun drawSectionTitle(r: PageRenderer, title: String) {
        r.ensureSpace(24f)
        r.canvas.drawRect(
            MARGIN, r.currentY + 2f,
            MARGIN + 3f, r.currentY + 16f,
            paintFill(COLOR_PRIMARY)
        )
        r.canvas.drawText(
            title,
            MARGIN + 10f, r.currentY + 14f,
            paintText(13f, COLOR_PRIMARY, bold = true)
        )
        r.advanceY(22f)
    }

    /** label + value 一行，value 支持自动换行 */
    private fun drawLabelValue(
        r: PageRenderer,
        label: String,
        value: String,
        valueColor: Int = COLOR_TEXT_MAIN
    ) {
        r.ensureSpace(18f)
        val labelW = 80f
        r.canvas.drawText(label, MARGIN, r.currentY + 12f, paintText(10f, COLOR_TEXT_SUB))
        val lines  = wrapText(value, paintText(10f, valueColor), CONTENT_WIDTH - labelW)
        lines.forEachIndexed { i, line ->
            if (i > 0) r.ensureSpace(14f)
            r.canvas.drawText(
                line,
                MARGIN + labelW, r.currentY + 12f,
                paintText(10f, valueColor)
            )
            if (i < lines.size - 1) r.advanceY(14f)
        }
        r.advanceY(18f)
    }

    /** 图片按比例缩放嵌入，可叠加播放图标水印 */
    private fun drawBitmapFit(
        r: PageRenderer,
        bitmap: Bitmap,
        maxHeight: Float,
        overlayPlay: Boolean = false
    ) {
        val scale = minOf(CONTENT_WIDTH / bitmap.width, maxHeight / bitmap.height)
        val drawW = bitmap.width  * scale
        val drawH = bitmap.height * scale
        val left  = MARGIN + (CONTENT_WIDTH - drawW) / 2f

        r.ensureSpace(drawH + 8f)
        val top     = r.currentY
        val bmpRect = RectF(left, top, left + drawW, top + drawH)

        r.canvas.save()
        val clipPath = Path().apply { addRoundRect(bmpRect, 8f, 8f, Path.Direction.CW) }
        r.canvas.clipPath(clipPath)
        r.canvas.drawBitmap(bitmap, null, bmpRect, null)
        r.canvas.restore()

        if (overlayPlay) {
            val cx = left + drawW / 2f
            val cy = top  + drawH / 2f
            r.canvas.drawCircle(cx, cy, 28f, paintFill(Color.argb(140, 0, 0, 0)))
            val triPath = Path().apply {
                moveTo(cx - 10f, cy - 14f)
                lineTo(cx + 18f, cy)
                lineTo(cx - 10f, cy + 14f)
                close()
            }
            r.canvas.drawPath(triPath, paintFill(Color.WHITE))
        }
        r.advanceY(drawH + 8f)
    }

    /** 音频/视频无图时的占位卡片 */
    private fun drawPlaceholderCard(
        r: PageRenderer,
        emoji: String,
        typeLabel: String,
        duration: String
    ) {
        val cardH = 72f
        r.ensureSpace(cardH + 8f)
        val y = r.currentY
        r.canvas.drawRoundRect(
            RectF(MARGIN, y, MARGIN + CONTENT_WIDTH, y + cardH),
            8f, 8f, paintFill(COLOR_BG_LIGHT)
        )
        r.canvas.drawText(emoji,
            MARGIN + CONTENT_WIDTH / 2f, y + 28f,
            paintText(22f, align = Paint.Align.CENTER))
        r.canvas.drawText(typeLabel,
            MARGIN + CONTENT_WIDTH / 2f, y + 46f,
            paintText(11f, COLOR_TEXT_SUB, align = Paint.Align.CENTER))
        if (duration.isNotEmpty()) {
            r.canvas.drawText(duration,
                MARGIN + CONTENT_WIDTH / 2f, y + 62f,
                paintText(10f, COLOR_TEXT_HINT, align = Paint.Align.CENTER))
        }
        r.advanceY(cardH + 8f)
    }

    /** 文字记录内容卡片，浅灰背景，支持多行 */
    private fun drawTextContentCard(r: PageRenderer, text: String) {
        val paint = paintText(11f, COLOR_TEXT_MAIN)
        val lines = text.split("\n").flatMap { wrapText(it, paint, CONTENT_WIDTH - 20f) }
        val cardH = (lines.size * 16f + 20f).coerceAtLeast(48f)

        r.ensureSpace(cardH + 8f)
        val y = r.currentY
        r.canvas.drawRoundRect(
            RectF(MARGIN, y, MARGIN + CONTENT_WIDTH, y + cardH),
            8f, 8f, paintFill(COLOR_CARD_BG)
        )
        lines.forEachIndexed { i, line ->
            r.canvas.drawText(line, MARGIN + 10f, y + 16f + i * 16f, paint)
        }
        r.advanceY(cardH + 8f)
    }

    // ── 文字换行 ──────────────────────────────────────────────

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        val result = mutableListOf<String>()
        var start  = 0
        while (start < text.length) {
            val count = paint.breakText(text, start, text.length, true, maxWidth, null)
            if (count <= 0) break
            result.add(text.substring(start, start + count))
            start += count
        }
        return if (result.isEmpty()) listOf(text) else result
    }

    // ── 媒体资源加载 ──────────────────────────────────────────

    private fun loadMediaBitmap(evidence: Evidence): Bitmap? {
        return try {
            when (evidence.mediaType) {
                MediaType.PHOTO -> {
                    if (evidence.mediaPath.isEmpty()) return null
                    val file = File(evidence.mediaPath)
                    if (!file.exists()) return null
                    BitmapFactory.decodeFile(evidence.mediaPath)
                }
                MediaType.VIDEO -> {
                    if (evidence.mediaPath.isEmpty()) return null
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(evidence.mediaPath)
                    val bmp = retriever.getFrameAtTime(
                        0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )
                    retriever.release()
                    bmp
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "loadMediaBitmap failed: ${e.message}")
            null
        }
    }

    /** 从媒体文件获取时长字符串，如"01:23" */
    private fun getDuration(path: String): String {
        if (path.isEmpty()) return ""
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(path)
            val ms = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L
            retriever.release()
            if (ms <= 0) return ""
            val sec = ms / 1000
            "%02d:%02d".format(sec / 60, sec % 60)
        } catch (e: Exception) {
            ""
        }
    }
}

// ── 多页渲染助手 ──────────────────────────────────────────────
// 负责管理当前页和 Y 坐标，内容超出页底时自动创建新页

private class PageRenderer(
    private val document: PdfDocument,
    private val pageWidth: Int,
    private val pageHeight: Int,
    private val margin: Float
) {
    private val bottomLimit = pageHeight - margin

    private var pageInfo: PdfDocument.PageInfo =
        PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
    private var page: PdfDocument.Page = document.startPage(pageInfo)
    private var pageIndex = 1

    var currentY: Float = 0f
        private set

    val canvas: Canvas get() = page.canvas

    fun advanceY(delta: Float) {
        currentY += delta
    }

    /**
     * 确保当前页剩余空间足够 requiredHeight，
     * 不够则结束当前页，开始新页
     */
    fun ensureSpace(requiredHeight: Float) {
        if (currentY + requiredHeight > bottomLimit) {
            newPage()
        }
    }

    private fun newPage() {
        document.finishPage(page)
        pageIndex++
        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex).create()
        page     = document.startPage(pageInfo)
        currentY = 40f
    }

    fun finish() {
        document.finishPage(page)
    }
}