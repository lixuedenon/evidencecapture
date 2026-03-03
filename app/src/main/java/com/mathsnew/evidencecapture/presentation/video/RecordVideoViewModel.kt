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
import javax.inject.Inject

sealed class VideoUiState {
    object Idle : VideoUiState()
    data class Recording(val evidenceId: String) : VideoUiState()
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

    // 录像时不采集分贝，保留 StateFlow 供 Screen 正常编译，值始终为 0
    private val _currentDecibel = MutableStateFlow(0f)
    val currentDecibel: StateFlow<Float> = _currentDecibel

    private var durationJob: Job? = null
    private var captureJob: Job? = null

    // 内存暂存快照：onRecordingStarted 时后台采集，onRecordingStopped 时再写库
    // 写库顺序：先 evidence，再 snapshot，满足外键约束（snapshot.evidenceId → evidence.id）
    private var pendingSnapshot: SensorSnapshot? = null

    fun onRecordingStarted(evidenceId: String) {
        pendingSnapshot = null

        // 立即切换 UI 到录制状态，用户零等待
        _durationSeconds.value = 0
        durationJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _durationSeconds.value++
            }
        }
        _uiState.value = VideoUiState.Recording(evidenceId)
        Log.i(TAG, "Video recording started immediately: $evidenceId")

        // 后台并行采集传感器，不采集分贝（麦克风已被 CameraX 录像占用）
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

    fun onRecordingStopped(evidenceId: String, videoPath: String, tag: String = "", title: String = "") {
        durationJob?.cancel()
        viewModelScope.launch {
            _uiState.value = VideoUiState.Saving
            try {
                // 等待后台传感器采集完成（正常录几秒后早已结束，几乎不等待）
                captureJob?.join()

                val hash = withContext(Dispatchers.IO) { HashUtil.hashFile(videoPath) }
                val snapshot = pendingSnapshot
                val evidence = Evidence(
                    id = evidenceId,
                    mediaType = MediaType.VIDEO,
                    mediaPath = videoPath,
                    tag = tag,
                    title = title,
                    sha256Hash = hash,
                    createdAt = snapshot?.capturedAt ?: System.currentTimeMillis(),
                    snapshotId = evidenceId
                )
                // 先写 evidence，再写 snapshot，满足外键约束
                evidenceRepository.save(evidence)
                snapshot?.let { snapshotRepository.insert(it) }
                Log.i(TAG, "Video saved: $evidenceId")

                // 异步回填地址和天气
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

    // 重置状态回 Idle，用于放弃录制或错误恢复
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