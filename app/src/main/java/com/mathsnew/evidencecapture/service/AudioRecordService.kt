// app/src/main/java/com/mathsnew/evidencecapture/service/AudioRecordService.kt
// 修改文件 - Kotlin

package com.mathsnew.evidencecapture.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mathsnew.evidencecapture.EvidenceCapturerApp
import com.mathsnew.evidencecapture.presentation.main.MainActivity
import java.io.File

class AudioRecordService : Service() {

    private var mediaRecorder: MediaRecorder? = null
    private var outputPath: String = ""
    private var isRecording = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                outputPath = intent.getStringExtra(EXTRA_OUTPUT_PATH) ?: ""
                if (outputPath.isNotEmpty()) {
                    startForeground(NOTIFICATION_ID, buildNotification())
                    startRecording()
                } else {
                    Log.e(TAG, "ACTION_START received but output path is empty")
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                stopRecording()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        try {
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(outputPath)
                prepare()
                start()
            }
            isRecording = true
            Log.i(TAG, "Recording started: $outputPath")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            stopSelf()
        }
    }

    private fun stopRecording() {
        if (!isRecording) return
        try {
            mediaRecorder?.stop()
            Log.i(TAG, "Recording stopped: $outputPath")
        } catch (e: Exception) {
            // MediaRecorder.stop() 在未正常录制时会抛异常，捕获避免崩溃
            Log.w(TAG, "MediaRecorder stop exception (may be normal): ${e.message}")
            File(outputPath).takeIf { it.exists() }?.delete()
        } finally {
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, EvidenceCapturerApp.CHANNEL_ID_RECORD)
            .setContentTitle("取证录音中")
            .setContentText("正在录音，点击返回应用")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopRecording()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AudioRecordService"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_OUTPUT_PATH = "extra_output_path"
    }
}