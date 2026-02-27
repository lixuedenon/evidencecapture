// app/src/main/java/com/mathsnew/evidencecapture/util/HashUtil.kt
// Kotlin - 工具类，SHA-256 文件哈希计算，用于防篡改验证

package com.mathsnew.evidencecapture.util

import java.io.File
import java.security.MessageDigest

object HashUtil {

    /**
     * 计算文件 SHA-256 哈希值
     * @param filePath 文件绝对路径
     * @return 十六进制哈希字符串，文件不存在或计算失败时返回空字符串
     */
    fun hashFile(filePath: String): String {
        val file = File(filePath)
        if (!file.exists()) return ""
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 验证文件完整性
     * @param filePath   文件绝对路径
     * @param storedHash 保存时记录的哈希值
     * @return true 表示文件未被篡改
     */
    fun verifyFile(filePath: String, storedHash: String): Boolean {
        if (storedHash.isEmpty()) return false
        return hashFile(filePath) == storedHash
    }
}