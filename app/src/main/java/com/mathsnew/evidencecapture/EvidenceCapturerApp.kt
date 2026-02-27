// app/src/main/java/com/mathsnew/evidencecapture/EvidenceCapturerApp.kt
// Kotlin - Application 入口，初始化 Hilt 并注册通知渠道

package com.mathsnew.evidencecapture

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class EvidenceCapturerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    /**
     * 注册所有通知渠道（Android 8.0+ 必须在使用前注册）
     * CHANNEL_ID_RECORD：前台录音服务常驻通知，低重要级避免打扰用户
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val recordChannel = NotificationChannel(
                CHANNEL_ID_RECORD,
                "录音服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "取证录音进行中的常驻通知"
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