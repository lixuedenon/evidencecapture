// app/src/main/java/com/mathsnew/evidencecapture/receiver/QuickStartReceiver.kt
// Kotlin - 广播接收器，快捷启动（预留，悬浮按钮功能实现时激活）

package com.mathsnew.evidencecapture.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class QuickStartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "QuickStart received: ${intent.action}")
        // 预留：接收悬浮按钮点击广播，快速启动录音服务
    }

    companion object {
        private const val TAG = "QuickStartReceiver"
        const val ACTION_QUICK_START = "com.mathsnew.evidencecapture.ACTION_QUICK_START"
    }
}