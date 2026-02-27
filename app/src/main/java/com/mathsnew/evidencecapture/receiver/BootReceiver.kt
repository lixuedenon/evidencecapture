// app/src/main/java/com/mathsnew/evidencecapture/receiver/BootReceiver.kt
// Kotlin - 广播接收器，开机自启（预留，当前版本无后台任务需要恢复）

package com.mathsnew.evidencecapture.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i(TAG, "Boot completed received")
            // 预留：开机后恢复未完成的云端上传任务
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}