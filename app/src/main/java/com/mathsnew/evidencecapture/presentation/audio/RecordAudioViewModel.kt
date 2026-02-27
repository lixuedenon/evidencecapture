// app/src/main/java/com/mathsnew/evidencecapture/presentation/audio/RecordAudioViewModel.kt
// Kotlin - 表现层，录音取证 ViewModel

package com.mathsnew.evidencecapture.presentation.audio

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mathsnew.evidencecapture.domain.model.Evidence
import com.mathsnew.evidencecapture.domain.model.MediaType
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
import javax.inject.Inject

sealed class AudioUiState {
    object Idle : AudioUiState()
    data class Recording(val evidenceId: String) : AudioUiState()
    object Saving : AudioUiState()
    data class Saved(val evidenceId: String) : AudioUiState()
    data class Error(val message: String) : AudioUiState()
}

enum class DisguiseMode { NONE, MUSIC, CALCULATOR, CALL, NEWS }

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

    private var durationJob: Job? = null
    private var currentEvidenceId: String = ""

    fun startRecording(title: String = "") {
        val evidenceId = EvidenceIdGenerator.generate()
        currentEvidenceId = evidenceId
        viewModelScope.launch {
            try {
                val snapshot = sensorCapture.capture(evidenceId)
                snapshotRepository.insert(snapshot)
                val audioPath = FileHelper.getAudioFile(context, evidenceId).absolutePath
                val intent = Intent(context, AudioRecordService::class.java).apply {
                    action = AudioRecordService.ACTION_START
                    putExtra(AudioRecordService.EXTRA_OUTPUT_PATH, audioPath)
                }
                context.startForegroundService(intent)
                _durationSeconds.value = 0
                durationJob = launch {
                    while (true) {
                        delay(1000)
                        _durationSeconds.value++
                    }
                }
                _uiState.value = AudioUiState.Recording(evidenceId)
                Log.i(TAG, "Recording started: $evidenceId")
            } catch (e: Exception) {
                Log.e(TAG, "Start recording failed", e)
                _uiState.value = AudioUiState.Error(e.message ?: "启动录音失败")
            }
        }
    }

    fun stopAndSave(tag: String = "", title: String = "") {
        val evidenceId = currentEvidenceId
        if (evidenceId.isEmpty()) return
        durationJob?.cancel()
        val stopIntent = Intent(context, AudioRecordService::class.java).apply {
            action = AudioRecordService.ACTION_STOP
        }
        context.startService(stopIntent)
        viewModelScope.launch {
            _uiState.value = AudioUiState.Saving
            try {
                val audioPath = FileHelper.getAudioFile(context, evidenceId).absolutePath
                val hash = withContext(Dispatchers.IO) { HashUtil.hashFile(audioPath) }
                val snapshot = snapshotRepository.getByEvidenceId(evidenceId)
                val evidence = Evidence(
                    id = evidenceId,
                    mediaType = MediaType.AUDIO,
                    mediaPath = audioPath,
                    tag = tag,
                    title = title,
                    sha256Hash = hash,
                    createdAt = snapshot?.capturedAt ?: System.currentTimeMillis(),
                    snapshotId = evidenceId
                )
                evidenceRepository.save(evidence)
                Log.i(TAG, "Audio saved: $evidenceId")
                // 异步回填地址和天气
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

    fun setDisguiseMode(mode: DisguiseMode) {
        _disguiseMode.value = mode
    }

    companion object {
        private const val TAG = "RecordAudioViewModel"
    }
}