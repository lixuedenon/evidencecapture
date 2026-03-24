// app/src/main/java/com/mathsnew/evidencecapture/EvidenceCapturerApp.kt
// 修改文件 - Kotlin

package com.mathsnew.evidencecapture

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.mathsnew.evidencecapture.util.LocaleHelper
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class EvidenceCapturerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    // 在 Application 级别应用保存的语言设置
    // 确保 App 所有组件使用同一语言 Context
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    /**
     * 注册所有通知渠道（Android 8.0+ 必须在使用前注册）
     * CHANNEL_ID_RECORD：前台录音服务常驻通知，低重要级避免打扰用户
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val recordChannel = NotificationChannel(
                CHANNEL_ID_RECORD,
                getString(R.string.notif_channel_record_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_record_desc)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(recordChannel)
        }
    }

    companion object {
        const val CHANNEL_ID_RECORD = "channel_record"
    }
}