// app/src/main/java/com/mathsnew/evidencecapture/presentation/main/MainActivity.kt
// 修改文件 - Kotlin

package com.mathsnew.evidencecapture.presentation.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.mathsnew.evidencecapture.navigation.AppNavGraph
import com.mathsnew.evidencecapture.navigation.Routes
import com.mathsnew.evidencecapture.service.QuickCaptureService
import com.mathsnew.evidencecapture.ui.theme.EvidenceCaptureTheme
import com.mathsnew.evidencecapture.util.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // navController 提升到 Activity 层，供 onNewIntent 导航使用
    private var navController: NavController? = null

    // 在 Activity 级别应用保存的语言设置
    // 与 Application 的 attachBaseContext 共同确保语言一致
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 启动常驻前台服务（通知栏快捷按钮 + 音量键监听）
        QuickCaptureService.start(this)

        setContent {
            EvidenceCaptureTheme {
                val navCtrl = rememberNavController()
                navController = navCtrl
                AppNavGraph(navController = navCtrl)
            }
        }

        // 处理冷启动携带的快速取证 Intent（App 进程未存在时由通知栏/音量键启动）
        handleQuickCaptureIntent(intent)
    }

    // App 已在后台时，通知栏按钮或音量键触发此回调
    // launchMode="singleTop" 保证不会重复创建 Activity 实例
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleQuickCaptureIntent(intent)
    }

    // 根据 Intent Action 导航到对应取证页面
    // window.decorView.post 延迟一帧，确保 NavController 组合完成后再导航
    private fun handleQuickCaptureIntent(intent: Intent?) {
        val action = intent?.action ?: return
        window.decorView.post {
            when (action) {
                QuickCaptureService.ACTION_QUICK_AUDIO -> {
                    navController?.navigate(Routes.RECORD_AUDIO) {
                        launchSingleTop = true
                    }
                }
                QuickCaptureService.ACTION_QUICK_VIDEO -> {
                    navController?.navigate(Routes.RECORD_VIDEO) {
                        launchSingleTop = true
                    }
                }
                QuickCaptureService.ACTION_QUICK_PHOTO -> {
                    navController?.navigate(Routes.CAPTURE_PHOTO) {
                        launchSingleTop = true
                    }
                }
            }
        }
    }
}