// app/src/main/java/com/mathsnew/evidencecapture/navigation/NavGraph.kt
// Kotlin - 导航层，统一管理所有页面路由
// PhotoConfirmScreen 与 CapturePhotoScreen 共享同一个 CaptureViewModel 实例

package com.mathsnew.evidencecapture.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mathsnew.evidencecapture.presentation.audio.RecordAudioScreen
import com.mathsnew.evidencecapture.presentation.capture.CapturePhotoScreen
import com.mathsnew.evidencecapture.presentation.capture.CaptureViewModel
import com.mathsnew.evidencecapture.presentation.capture.PhotoConfirmScreen
import com.mathsnew.evidencecapture.presentation.detail.EvidenceDetailScreen
import com.mathsnew.evidencecapture.presentation.main.MainScreen
import com.mathsnew.evidencecapture.presentation.textnote.TextNoteScreen
import com.mathsnew.evidencecapture.presentation.video.RecordVideoScreen
import androidx.hilt.navigation.compose.hiltViewModel

object Routes {
    const val MAIN = "main"
    const val CAPTURE_PHOTO = "capture_photo"
    const val PHOTO_CONFIRM = "photo_confirm/{evidenceId}/{photoPath}"
    const val RECORD_VIDEO = "record_video"
    const val RECORD_AUDIO = "record_audio"
    const val TEXT_NOTE = "text_note"
    const val EVIDENCE_DETAIL = "evidence_detail/{evidenceId}"

    fun photoConfirm(evidenceId: String, photoPath: String) =
        "photo_confirm/$evidenceId/${photoPath.encodeForNav()}"

    fun evidenceDetail(evidenceId: String) = "evidence_detail/$evidenceId"

    private fun String.encodeForNav() =
        java.net.URLEncoder.encode(this, "UTF-8")
}

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Routes.MAIN
    ) {
        composable(Routes.MAIN) {
            MainScreen(
                onNavigateCapturePhoto = { navController.navigate(Routes.CAPTURE_PHOTO) },
                onNavigateRecordVideo = { navController.navigate(Routes.RECORD_VIDEO) },
                onNavigateRecordAudio = { navController.navigate(Routes.RECORD_AUDIO) },
                onNavigateTextNote = { navController.navigate(Routes.TEXT_NOTE) },
                onNavigateDetail = { id -> navController.navigate(Routes.evidenceDetail(id)) }
            )
        }

        composable(Routes.CAPTURE_PHOTO) {
            // CapturePhotoScreen 创建 CaptureViewModel，保存其 BackStackEntry
            val captureEntry = remember(it) {
                navController.getBackStackEntry(Routes.CAPTURE_PHOTO)
            }
            val captureViewModel: CaptureViewModel = hiltViewModel(captureEntry)
            CapturePhotoScreen(
                onPhotoCaptured = { evidenceId, photoPath ->
                    navController.navigate(Routes.photoConfirm(evidenceId, photoPath))
                },
                onBack = { navController.popBackStack() },
                viewModel = captureViewModel
            )
        }

        composable(
            route = Routes.PHOTO_CONFIRM,
            arguments = listOf(
                navArgument("evidenceId") { type = NavType.StringType },
                navArgument("photoPath") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val evidenceId = backStackEntry.arguments?.getString("evidenceId") ?: ""
            val photoPath = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("photoPath") ?: "", "UTF-8"
            )
            // 从 CAPTURE_PHOTO 的 BackStackEntry 取同一个 CaptureViewModel 实例
            val captureEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Routes.CAPTURE_PHOTO)
            }
            val captureViewModel: CaptureViewModel = hiltViewModel(captureEntry)
            PhotoConfirmScreen(
                evidenceId = evidenceId,
                photoPath = photoPath,
                onSaved = { id ->
                    navController.navigate(Routes.evidenceDetail(id)) {
                        popUpTo(Routes.MAIN)
                    }
                },
                onBack = { navController.popBackStack() },
                viewModel = captureViewModel
            )
        }

        composable(Routes.RECORD_VIDEO) {
            RecordVideoScreen(
                onSaved = { id ->
                    navController.navigate(Routes.evidenceDetail(id)) {
                        popUpTo(Routes.MAIN)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.RECORD_AUDIO) {
            RecordAudioScreen(
                onSaved = { id ->
                    navController.navigate(Routes.evidenceDetail(id)) {
                        popUpTo(Routes.MAIN)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.TEXT_NOTE) {
            TextNoteScreen(
                onSaved = { id ->
                    navController.navigate(Routes.evidenceDetail(id)) {
                        popUpTo(Routes.MAIN)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.EVIDENCE_DETAIL,
            arguments = listOf(navArgument("evidenceId") { type = NavType.StringType })
        ) { backStackEntry ->
            val evidenceId = backStackEntry.arguments?.getString("evidenceId") ?: ""
            EvidenceDetailScreen(
                evidenceId = evidenceId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}