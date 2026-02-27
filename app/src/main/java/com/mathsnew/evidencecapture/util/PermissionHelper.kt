// app/src/main/java/com/mathsnew/evidencecapture/util/PermissionHelper.kt
// Kotlin - 工具类，权限状态检查

package com.mathsnew.evidencecapture.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object PermissionHelper {

    fun hasAudioPermission(context: Context): Boolean =
        hasPermission(context, Manifest.permission.RECORD_AUDIO)

    fun hasCameraPermission(context: Context): Boolean =
        hasPermission(context, Manifest.permission.CAMERA)

    fun hasLocationPermission(context: Context): Boolean =
        hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)

    /** 核心权限（麦克风 + 位置）同时满足才返回 true */
    fun hasCorePermissions(context: Context): Boolean =
        hasAudioPermission(context) && hasLocationPermission(context)

    private fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}