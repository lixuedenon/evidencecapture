// app/src/main/java/com/mathsnew/evidencecapture/service/QuickCaptureService.kt
// 修改文件 - Kotlin

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
            val stream = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
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
                // 注意：NotificationChannel 名称在渠道首次创建后系统会缓存
                // 切换语言后需用户在系统设置中看到的渠道名不会实时更新，这是 Android 系统限制
                getString(R.string.notif_channel_quick_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description          = getString(R.string.notif_channel_quick_desc)
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
            .setContentTitle(getString(R.string.notif_quick_title))
            .setContentText(getString(R.string.notif_quick_text))
            .setContentIntent(mainPi)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                android.R.drawable.ic_btn_speak_now,
                getString(R.string.notif_quick_audio),
                buildActionPendingIntent(ACTION_QUICK_AUDIO, 1)
            )
            .addAction(
                android.R.drawable.ic_media_play,
                getString(R.string.notif_quick_video),
                buildActionPendingIntent(ACTION_QUICK_VIDEO, 2)
            )
            .addAction(
                android.R.drawable.ic_menu_camera,
                getString(R.string.notif_quick_photo),
                buildActionPendingIntent(ACTION_QUICK_PHOTO, 3)
            )
            .build()
    }

    // 通知栏按钮直接启动 MainActivity 并携带 action，
    // 比先发给 Service 再转发更快，点击后立即跳转到对应取证页面
    private fun buildActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        return PendingIntent.getActivity(
            this, requestCode,
            Intent(this, MainActivity::class.java).apply {
                this.action = action
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            },
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
                Log.d(TAG, "Volume down x$VOLUME_TAP_COUNT_REQUIRED → quick audio")
                launchScreen(ACTION_QUICK_AUDIO)
            }
        }
    }

    private fun launchScreen(action: String) {
        startActivity(Intent(this, MainActivity::class.java).apply {
            // FLAG_ACTIVITY_NEW_TASK：从 Service 启动 Activity 必须加
            // FLAG_ACTIVITY_SINGLE_TOP：避免重复创建实例
            // FLAG_ACTIVITY_REORDER_TO_FRONT：App 在后台时直接拉到前台，不重建
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            this.action = action
        })
    }
}