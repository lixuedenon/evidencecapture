// app/src/main/java/com/mathsnew/evidencecapture/presentation/video/RecordVideoViewModel.kt
// Kotlin - 表现层，录像取证 ViewModel

package com.mathsnew.evidencecapture.presentation.video

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mathsnew.evidencecapture.domain.model.Evidence
import com.mathsnew.evidencecapture.domain.model.MediaType
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

    private val _currentDecibel = MutableStateFlow(0f)
    val currentDecibel: StateFlow<Float> = _currentDecibel

    private var durationJob: Job? = null

    fun onRecordingStarted(evidenceId: String) {
        viewModelScope.launch {
            try {
                val snapshot = sensorCapture.capture(evidenceId)
                snapshotRepository.insert(snapshot)
                _durationSeconds.value = 0
                durationJob = launch {
                    while (true) {
                        delay(1000)
                        _durationSeconds.value++
                    }
                }
                _uiState.value = VideoUiState.Recording(evidenceId)
                Log.i(TAG, "Video recording started: $evidenceId")
            } catch (e: Exception) {
                Log.e(TAG, "Sensor capture failed", e)
                _uiState.value = VideoUiState.Error(e.message ?: "传感器采集失败")
            }
        }
    }

    fun onRecordingStopped(evidenceId: String, videoPath: String, tag: String = "", title: String = "") {
        durationJob?.cancel()
        viewModelScope.launch {
            _uiState.value = VideoUiState.Saving
            try {
                val hash = withContext(Dispatchers.IO) { HashUtil.hashFile(videoPath) }
                val snapshot = snapshotRepository.getByEvidenceId(evidenceId)
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
                evidenceRepository.save(evidence)
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

    companion object {
        private const val TAG = "RecordVideoViewModel"
    }
}