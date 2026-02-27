// app/src/main/java/com/mathsnew/evidencecapture/util/EvidenceIdGenerator.kt
// Kotlin - 工具类，证据唯一 ID 生成器

package com.mathsnew.evidencecapture.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * 生成格式为 EV20240220-143052-001 的唯一证据 ID
 * 同一秒内支持最多 999 个不重复 ID，计数器跨秒自动归零
 */
object EvidenceIdGenerator {

    private val counter = AtomicInteger(0)
    private var lastSecond = ""
    private val formatter = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault())

    @Synchronized
    fun generate(): String {
        val nowStr = formatter.format(Date())
        if (nowStr != lastSecond) {
            lastSecond = nowStr
            counter.set(0)
        }
        val seq = counter.incrementAndGet()
        return "EV$nowStr-${seq.toString().padStart(3, '0')}"
    }
}