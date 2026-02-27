// app/src/main/java/com/mathsnew/evidencecapture/presentation/main/MainActivity.kt
// Kotlin - 表现层，主 Activity，挂载导航图并在启动时申请核心权限

package com.mathsnew.evidencecapture.presentation.main

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.mathsnew.evidencecapture.navigation.AppNavGraph
import com.mathsnew.evidencecapture.ui.theme.EvidenceCaptureTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** 启动时申请的核心权限：麦克风 + 精确定位 + Android 13+ 通知 */
    private val corePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        results.forEach { (permission, granted) ->
            android.util.Log.i(TAG, "Permission $permission granted=$granted")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestCorePermissions()
        setContent {
            EvidenceCaptureTheme {
                AppNavGraph()
            }
        }
    }

    private fun requestCorePermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        corePermissionLauncher.launch(permissions.toTypedArray())
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}