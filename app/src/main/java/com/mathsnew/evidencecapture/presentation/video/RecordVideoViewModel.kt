// app/src/main/java/com/mathsnew/evidencecapture/presentation/video/RecordVideoViewModel.kt
// Kotlin - 表现层，录像取证 ViewModel

package com.mathsnew.evidencecapture.presentation.video

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mathsnew.evidencecapture.domain.model.Evidence
import com.mathsnew.evidencecapture.domain.model.MediaType
import com.mathsnew.evidencecapture.domain.model.SensorSnapshot
import com.mathsnew.evidencecapture.domain.repository.EvidenceRepository
import com.mathsnew.evidencecapture.domain.repository.SensorSnapshotRepository
import com.mathsnew.evidencecapture.service.InstantSensorCapture
import com.mathsnew.evidencecapture.util.HashUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

sealed class VideoUiState {
    object Idle : VideoUiState()
    data class Recording(val evidenceId: String) : VideoUiState()
    // 录完停止后停在此状态，等用户点保存或取消
    data class ReadyToSave(val evidenceId: String, val videoPath: String) : VideoUiState()
    object Saving : VideoUiState()
    data class Saved(val evidenceId: String) : VideoUiState()
    data class Error(val message: String) : VideoUiState()
}

@HiltViewModel
class RecordVideoViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val evidenceRepository: EvidenceRepository,
    private val snapshotRepository: SensorSnapshotRepository,
    private val sensorCapture: InstantSensorCapture
) : ViewModel() {

    private val _uiState = MutableStateFlow<VideoUiState>(VideoUiState.Idle)
    val uiState: StateFlow<VideoUiState> = _uiState

    private val _durationSeconds = MutableStateFlow(0)
    val durationSeconds: StateFlow<Int> = _durationSeconds

    private val _currentDecibel = MutableStateFlow(0f)
    val currentDecibel: StateFlow<Float> = _currentDecibel

    private var durationJob: Job? = null
    private var captureJob: Job? = null
    private var pendingSnapshot: SensorSnapshot? = null

    fun onRecordingStarted(evidenceId: String) {
        pendingSnapshot = null
        _durationSeconds.value = 0
        durationJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _durationSeconds.value++
            }
        }
        _uiState.value = VideoUiState.Recording(evidenceId)
        Log.i(TAG, "Video recording started: $evidenceId")

        captureJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val snapshot = sensorCapture.capture(evidenceId, measureDecibel = false)
                pendingSnapshot = snapshot
                Log.i(TAG, "Sensor captured in background: $evidenceId")
            } catch (e: Exception) {
                Log.w(TAG, "Background sensor capture failed: ${e.message}")
            }
        }
    }

    /** 录像停止后进入 ReadyToSave，等用户选择保存或取消 */
    fun onRecordingStopped(evidenceId: String, videoPath: String) {
        durationJob?.cancel()
        viewModelScope.launch {
            captureJob?.join()
            _uiState.value = VideoUiState.ReadyToSave(evidenceId, videoPath)
        }
    }

    /** 用户点保存：写数据库 */
    fun saveRecording(tag: String = "", title: String = "") {
        val state = _uiState.value as? VideoUiState.ReadyToSave ?: return
        val evidenceId = state.evidenceId
        val videoPath = state.videoPath

        viewModelScope.launch {
            _uiState.value = VideoUiState.Saving
            try {
                val hash = withContext(Dispatchers.IO) { HashUtil.hashFile(videoPath) }
                val snapshot = pendingSnapshot
                val evidence = Evidence(
                    id = evidenceId,
                    mediaType = MediaType.VIDEO,
                    mediaPath = videoPath,
                    tag = tag,
                    title = title.ifBlank { evidenceId },
                    sha256Hash = hash,
                    createdAt = snapshot?.capturedAt ?: System.currentTimeMillis(),
                    snapshotId = evidenceId
                )
                evidenceRepository.save(evidence)
                snapshot?.let { snapshotRepository.insert(it) }
                Log.i(TAG, "Video saved: $evidenceId")
                launch(Dispatchers.IO) {
                    val lat = snapshot?.latitude ?: 0.0
                    val lng = snapshot?.longitude ?: 0.0
                    sensorCapture.fetchAndFillAsync(evidenceId, lat, lng)
                }
                _uiState.value = VideoUiState.Saved(evidenceId)
            } catch (e: Exception) {
                Log.e(TAG, "Save video failed", e)
                _uiState.value = VideoUiState.Error(e.message ?: "保存失败")
            }
        }
    }

    /** 用户点取消：删除视频文件，重置状态 */
    fun cancelAndDelete() {
        val state = _uiState.value as? VideoUiState.ReadyToSave
        state?.let { File(it.videoPath).delete() }
        resetState()
    }

    fun resetState() {
        durationJob?.cancel()
        captureJob?.cancel()
        _uiState.value = VideoUiState.Idle
        _durationSeconds.value = 0
        pendingSnapshot = null
    }

    companion object {
        private const val TAG = "RecordVideoViewModel"
    }
}