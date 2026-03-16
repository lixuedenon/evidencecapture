// app/src/main/java/com/mathsnew/evidencecapture/service/QuickCaptureService.kt
// 新建文件 - Kotlin

package com.mathsnew.evidencecapture.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mathsnew.evidencecapture.R
import com.mathsnew.evidencecapture.presentation.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

@AndroidEntryPoint
class QuickCaptureService : Service() {

    companion object {
        const val CHANNEL_ID = "quick_capture_channel"
        const val NOTIFICATION_ID = 1002

        const val ACTION_QUICK_AUDIO   = "com.mathsnew.evidencecapture.QUICK_AUDIO"
        const val ACTION_QUICK_VIDEO   = "com.mathsnew.evidencecapture.QUICK_VIDEO"
        const val ACTION_QUICK_PHOTO   = "com.mathsnew.evidencecapture.QUICK_PHOTO"
        const val ACTION_START_SERVICE = "com.mathsnew.evidencecapture.START_QUICK_SERVICE"
        const val ACTION_STOP_SERVICE  = "com.mathsnew.evidencecapture.STOP_QUICK_SERVICE"

        private const val VOLUME_TAP_COUNT_REQUIRED = 3
        private const val VOLUME_TAP_WINDOW_MS      = 1500L
        private const val TAG = "QuickCaptureService"

        fun start(context: Context) {
            val intent = Intent(context, QuickCaptureService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(Intent(context, QuickCaptureService::class.java).apply {
                action = ACTION_STOP_SERVICE
            })
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val volumePressTimes = ArrayDeque<Long>()

    private val volumeKeyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != "android.media.VOLUME_CHANGED_ACTION") return
            val stream  = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
            if (stream != AudioManager.STREAM_MUSIC) return
            val prevVol = intent.getIntExtra("android.media.EXTRA_PREV_VOLUME_STREAM_VALUE", -1)
            val newVol  = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1)
            if (newVol >= prevVol) return
            onVolumeDownPressed()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        registerVolumeKeyReceiver()
        Log.d(TAG, "QuickCaptureService started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> stopSelf()
            ACTION_QUICK_AUDIO  -> launchScreen(ACTION_QUICK_AUDIO)
            ACTION_QUICK_VIDEO  -> launchScreen(ACTION_QUICK_VIDEO)
            ACTION_QUICK_PHOTO  -> launchScreen(ACTION_QUICK_PHOTO)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(volumeKeyReceiver)
        serviceScope.cancel()
        Log.d(TAG, "QuickCaptureService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "快速取证",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "常驻快捷取证按钮"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val mainPi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("取证记录仪")
            .setContentText("快速取证 · 随时待命")
            .setContentIntent(mainPi)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                android.R.drawable.ic_btn_speak_now,
                "● 录音",
                buildActionPendingIntent(ACTION_QUICK_AUDIO, 1)
            )
            .addAction(
                android.R.drawable.ic_media_play,
                "▶ 录像",
                buildActionPendingIntent(ACTION_QUICK_VIDEO, 2)
            )
            .addAction(
                android.R.drawable.ic_menu_camera,
                "◉ 拍照",
                buildActionPendingIntent(ACTION_QUICK_PHOTO, 3)
            )
            .build()
    }

    // requestCode 每个 Action 必须不同，否则系统复用同一个 PendingIntent
    private fun buildActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        return PendingIntent.getService(
            this, requestCode,
            Intent(this, QuickCaptureService::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun registerVolumeKeyReceiver() {
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(volumeKeyReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(volumeKeyReceiver, filter)
        }
    }

    // 维护时间戳队列，窗口内连按达到次数即触发录音
    private fun onVolumeDownPressed() {
        val now = SystemClock.elapsedRealtime()
        volumePressTimes.addLast(now)
        while (volumePressTimes.size > VOLUME_TAP_COUNT_REQUIRED) {
            volumePressTimes.removeFirst()
        }
        if (volumePressTimes.size == VOLUME_TAP_COUNT_REQUIRED) {
            val windowMs = volumePressTimes.last() - volumePressTimes.first()
            if (windowMs <= VOLUME_TAP_WINDOW_MS) {
                volumePressTimes.clear()
                Log.d(TAG, "音量下键连按 $VOLUME_TAP_COUNT_REQUIRED 次，触发快速录音")
                launchScreen(ACTION_QUICK_AUDIO)
            }
        }
    }

    private fun launchScreen(action: String) {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags       = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            this.action = action
        })
    }
}