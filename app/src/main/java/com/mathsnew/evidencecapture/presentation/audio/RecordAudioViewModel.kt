// app/src/main/java/com/mathsnew/evidencecapture/presentation/audio/RecordAudioViewModel.kt
// Kotlin - 表现层，录音取证 ViewModel

package com.mathsnew.evidencecapture.presentation.audio

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mathsnew.evidencecapture.domain.model.DisguiseMode
import com.mathsnew.evidencecapture.domain.model.Evidence
import com.mathsnew.evidencecapture.domain.model.MediaType
import com.mathsnew.evidencecapture.domain.model.SensorSnapshot
import com.mathsnew.evidencecapture.domain.repository.EvidenceRepository
import com.mathsnew.evidencecapture.domain.repository.SensorSnapshotRepository
import com.mathsnew.evidencecapture.service.AudioRecordService
import com.mathsnew.evidencecapture.service.InstantSensorCapture
import com.mathsnew.evidencecapture.util.EvidenceIdGenerator
import com.mathsnew.evidencecapture.util.FileHelper
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

sealed class AudioUiState {
    object Idle : AudioUiState()
    data class Recording(val evidenceId: String) : AudioUiState()
    object Stopping : AudioUiState()
    // 录完停止后停在此状态，等用户点保存或取消
    data class ReadyToSave(val evidenceId: String, val audioPath: String) : AudioUiState()
    object Saving : AudioUiState()
    data class Saved(val evidenceId: String) : AudioUiState()
    data class Error(val message: String) : AudioUiState()
}

@HiltViewModel
class RecordAudioViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val evidenceRepository: EvidenceRepository,
    private val snapshotRepository: SensorSnapshotRepository,
    private val sensorCapture: InstantSensorCapture
) : ViewModel() {

    private val _uiState = MutableStateFlow<AudioUiState>(AudioUiState.Idle)
    val uiState: StateFlow<AudioUiState> = _uiState

    private val _durationSeconds = MutableStateFlow(0)
    val durationSeconds: StateFlow<Int> = _durationSeconds

    private val _disguiseMode = MutableStateFlow(DisguiseMode.NONE)
    val disguiseMode: StateFlow<DisguiseMode> = _disguiseMode

    private val _snapshot = MutableStateFlow<SensorSnapshot?>(null)
    val snapshot: StateFlow<SensorSnapshot?> = _snapshot

    private var durationJob: Job? = null
    private var captureJob: Job? = null
    private var currentEvidenceId: String = ""
    private var pendingSnapshot: SensorSnapshot? = null

    fun startRecording() {
        val evidenceId = EvidenceIdGenerator.generate()
        currentEvidenceId = evidenceId
        pendingSnapshot = null
        _snapshot.value = null

        val audioPath = FileHelper.getAudioFile(context, evidenceId).absolutePath
        val intent = Intent(context, AudioRecordService::class.java).apply {
            action = AudioRecordService.ACTION_START
            putExtra(AudioRecordService.EXTRA_OUTPUT_PATH, audioPath)
        }
        context.startForegroundService(intent)

        _durationSeconds.value = 0
        durationJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _durationSeconds.value++
            }
        }
        _uiState.value = AudioUiState.Recording(evidenceId)
        Log.i(TAG, "Recording started: $evidenceId")

        captureJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val snapshot = sensorCapture.capture(evidenceId, measureDecibel = false)
                pendingSnapshot = snapshot
                _snapshot.value = snapshot
            } catch (e: Exception) {
                Log.w(TAG, "Background sensor capture failed: ${e.message}")
            }
        }
    }

    /** 停止录音，进入 ReadyToSave，等用户选择保存或取消 */
    fun stopRecording() {
        val evidenceId = currentEvidenceId
        if (evidenceId.isEmpty()) return
        durationJob?.cancel()

        val stopIntent = Intent(context, AudioRecordService::class.java).apply {
            action = AudioRecordService.ACTION_STOP
        }
        context.startService(stopIntent)

        viewModelScope.launch {
            _uiState.value = AudioUiState.Stopping
            captureJob?.join()
            delay(500)
            val audioPath = FileHelper.getAudioFile(context, evidenceId).absolutePath
            _uiState.value = AudioUiState.ReadyToSave(evidenceId, audioPath)
        }
    }

    /** 用户点保存：写数据库 */
    fun saveRecording(tag: String = "", title: String = "") {
        val state = _uiState.value as? AudioUiState.ReadyToSave ?: return
        val evidenceId = state.evidenceId
        val audioPath = state.audioPath

        viewModelScope.launch {
            _uiState.value = AudioUiState.Saving
            try {
                val hash = withContext(Dispatchers.IO) { HashUtil.hashFile(audioPath) }
                val snapshot = pendingSnapshot
                val evidence = Evidence(
                    id = evidenceId,
                    mediaType = MediaType.AUDIO,
                    mediaPath = audioPath,
                    tag = tag,
                    title = title.ifBlank { evidenceId },
                    sha256Hash = hash,
                    createdAt = snapshot?.capturedAt ?: System.currentTimeMillis(),
                    snapshotId = evidenceId
                )
                evidenceRepository.save(evidence)
                snapshot?.let { snapshotRepository.insert(it) }
                Log.i(TAG, "Audio saved: $evidenceId")
                launch(Dispatchers.IO) {
                    val lat = snapshot?.latitude ?: 0.0
                    val lng = snapshot?.longitude ?: 0.0
                    sensorCapture.fetchAndFillAsync(evidenceId, lat, lng)
                }
                _uiState.value = AudioUiState.Saved(evidenceId)
            } catch (e: Exception) {
                Log.e(TAG, "Save audio failed", e)
                _uiState.value = AudioUiState.Error(e.message ?: "保存失败")
            }
        }
    }

    /** 用户点取消：删除录音文件，重置状态 */
    fun cancelAndDelete() {
        val state = _uiState.value as? AudioUiState.ReadyToSave
        state?.let { File(it.audioPath).delete() }
        resetState()
    }

    fun setDisguiseMode(mode: DisguiseMode) {
        _disguiseMode.value = mode
    }

    fun resetState() {
        durationJob?.cancel()
        captureJob?.cancel()
        _uiState.value = AudioUiState.Idle
        _durationSeconds.value = 0
        _snapshot.value = null
        _disguiseMode.value = DisguiseMode.NONE
        currentEvidenceId = ""
        pendingSnapshot = null
    }

    companion object {
        private const val TAG = "RecordAudioViewModel"
    }
}