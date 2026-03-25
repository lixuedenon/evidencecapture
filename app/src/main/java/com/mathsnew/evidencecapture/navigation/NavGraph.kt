// app/src/main/java/com/mathsnew/evidencecapture/navigation/NavGraph.kt
// 修改文件 - Kotlin

package com.mathsnew.evidencecapture.navigation

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mathsnew.evidencecapture.presentation.audio.RecordAudioScreen
import com.mathsnew.evidencecapture.presentation.capture.CapturePhotoScreen
import com.mathsnew.evidencecapture.presentation.capture.CaptureViewModel
import com.mathsnew.evidencecapture.presentation.capture.PhotoConfirmScreen
import com.mathsnew.evidencecapture.presentation.detail.EvidenceDetailScreen
import com.mathsnew.evidencecapture.presentation.main.MainScreen
import com.mathsnew.evidencecapture.presentation.pdf.PdfViewerScreen
import com.mathsnew.evidencecapture.presentation.textnote.TextNoteScreen
import com.mathsnew.evidencecapture.presentation.trash.TrashScreen
import com.mathsnew.evidencecapture.presentation.video.RecordVideoScreen
import com.mathsnew.evidencecapture.util.PdfReportBuilder
import java.io.File

object Routes {
    const val MAIN            = "main"
    const val CAPTURE_PHOTO   = "capture_photo"
    const val PHOTO_CONFIRM   = "photo_confirm/{evidenceId}/{photoPath}"
    const val RECORD_VIDEO    = "record_video"
    const val RECORD_AUDIO    = "record_audio"
    const val TEXT_NOTE       = "text_note"
    const val EVIDENCE_DETAIL = "evidence_detail/{evidenceId}"
    const val PDF_VIEWER      = "pdf_viewer/{pdfPath}/{title}"
    const val TRASH           = "trash"   // 回收站

    fun photoConfirm(evidenceId: String, photoPath: String) =
        "photo_confirm/$evidenceId/${photoPath.encodeForNav()}"

    fun evidenceDetail(evidenceId: String) = "evidence_detail/$evidenceId"

    fun pdfViewer(pdfPath: String, title: String) =
        "pdf_viewer/${pdfPath.encodeForNav()}/${title.encodeForNav()}"

    private fun String.encodeForNav() =
        java.net.URLEncoder.encode(this, "UTF-8")
}

@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(
        navController    = navController,
        startDestination = Routes.MAIN
    ) {
        composable(Routes.MAIN) {
            MainScreen(
                onNavigateCapturePhoto = { navController.navigate(Routes.CAPTURE_PHOTO) },
                onNavigateRecordVideo  = { navController.navigate(Routes.RECORD_VIDEO) },
                onNavigateRecordAudio  = { navController.navigate(Routes.RECORD_AUDIO) },
                onNavigateTextNote     = { navController.navigate(Routes.TEXT_NOTE) },
                onNavigateDetail       = { id -> navController.navigate(Routes.evidenceDetail(id)) },
                onNavigateTrash        = { navController.navigate(Routes.TRASH) }
            )
        }

        composable(Routes.CAPTURE_PHOTO) {
            val captureEntry = remember(it) {
                navController.getBackStackEntry(Routes.CAPTURE_PHOTO)
            }
            val captureViewModel: CaptureViewModel = hiltViewModel(captureEntry)
            CapturePhotoScreen(
                onPhotoCaptured = { evidenceId, photoPath ->
                    navController.navigate(Routes.photoConfirm(evidenceId, photoPath))
                },
                onBack    = { navController.popBackStack() },
                viewModel = captureViewModel
            )
        }

        composable(
            route     = Routes.PHOTO_CONFIRM,
            arguments = listOf(
                navArgument("evidenceId") { type = NavType.StringType },
                navArgument("photoPath")  { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val evidenceId = backStackEntry.arguments?.getString("evidenceId") ?: ""
            val photoPath  = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("photoPath") ?: "", "UTF-8"
            )
            val captureEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Routes.CAPTURE_PHOTO)
            }
            val captureViewModel: CaptureViewModel = hiltViewModel(captureEntry)
            PhotoConfirmScreen(
                evidenceId = evidenceId,
                photoPath  = photoPath,
                onSaved    = { id ->
                    navController.navigate(Routes.evidenceDetail(id)) {
                        popUpTo(Routes.MAIN)
                    }
                },
                onBack    = { navController.popBackStack() },
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
            route     = Routes.EVIDENCE_DETAIL,
            arguments = listOf(navArgument("evidenceId") { type = NavType.StringType })
        ) { backStackEntry ->
            val evidenceId = backStackEntry.arguments?.getString("evidenceId") ?: ""
            EvidenceDetailScreen(
                evidenceId         = evidenceId,
                onBack             = { navController.popBackStack() },
                onNavigatePdfViewer = { pdfPath, title ->
                    navController.navigate(Routes.pdfViewer(pdfPath, title))
                }
            )
        }

        composable(
            route     = Routes.PDF_VIEWER,
            arguments = listOf(
                navArgument("pdfPath") { type = NavType.StringType },
                navArgument("title")   { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val context = LocalContext.current
            val pdfPath = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("pdfPath") ?: "", "UTF-8"
            )
            val title = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("title") ?: "", "UTF-8"
            )
            PdfViewerScreen(
                pdfPath = pdfPath,
                title   = title,
                onBack  = { navController.popBackStack() },
                onShare = {
                    val pdfFile = File(pdfPath)
                    if (pdfFile.exists()) {
                        val intent = PdfReportBuilder.getShareIntent(context, pdfFile)
                        context.startActivity(Intent.createChooser(intent, "分享 PDF 报告"))
                    }
                }
            )
        }

        // 回收站页面
        composable(Routes.TRASH) {
            TrashScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}