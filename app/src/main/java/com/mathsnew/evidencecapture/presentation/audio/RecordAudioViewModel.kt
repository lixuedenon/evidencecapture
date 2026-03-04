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
import javax.inject.Inject

sealed class AudioUiState {
    object Idle : AudioUiState()
    data class Recording(val evidenceId: String) : AudioUiState()
    object Saving : AudioUiState()
    data class Saved(val evidenceId: String) : AudioUiState()
    data class Error(val message: String) : AudioUiState()
}

// DisguiseMode 枚举统一使用 domain.model.DisguiseMode，此处不重复定义

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

    // 供 RecordAudioScreen 中 SnapshotCard 展示环境数据
    private val _snapshot = MutableStateFlow<SensorSnapshot?>(null)
    val snapshot: StateFlow<SensorSnapshot?> = _snapshot

    private var durationJob: Job? = null
    private var captureJob: Job? = null
    private var currentEvidenceId: String = ""

    // 内存暂存快照：startRecording 时后台采集，stopAndSave 时再写库
    // 写库顺序：先 evidence，再 snapshot，满足外键约束（snapshot.evidenceId → evidence.id）
    private var pendingSnapshot: SensorSnapshot? = null

    fun startRecording(title: String = "") {
        val evidenceId = EvidenceIdGenerator.generate()
        currentEvidenceId = evidenceId
        pendingSnapshot = null
        _snapshot.value = null

        // 立即启动录音服务，用户零等待
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
        Log.i(TAG, "Recording started immediately: $evidenceId")

        // 后台并行采集传感器，不采集分贝（麦克风已被录音服务占用）
        captureJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val snapshot = sensorCapture.capture(evidenceId, measureDecibel = false)
                pendingSnapshot = snapshot
                _snapshot.value = snapshot
                Log.i(TAG, "Sensor captured in background: $evidenceId")
            } catch (e: Exception) {
                Log.w(TAG, "Background sensor capture failed: ${e.message}")
            }
        }
    }

    fun stopAndSave(tag: String = "", title: String = "") {
        val evidenceId = currentEvidenceId
        if (evidenceId.isEmpty()) return
        durationJob?.cancel()

        // 发送停止指令给前台服务，Service 收到后同步执行 stop()+release() 写盘
        val stopIntent = Intent(context, AudioRecordService::class.java).apply {
            action = AudioRecordService.ACTION_STOP
        }
        context.startService(stopIntent)

        viewModelScope.launch {
            _uiState.value = AudioUiState.Saving
            try {
                // 等待后台传感器采集完成
                captureJob?.join()

                // 等待 AudioRecordService 完成 MediaRecorder.stop()+release() 写盘
                // Service 的 onStartCommand 在主线程同步执行，500ms 足够其完成文件写入
                // 此时 UI 已显示"保存中..."，用户无感知
                delay(500)

                val audioPath = FileHelper.getAudioFile(context, evidenceId).absolutePath
                val hash = withContext(Dispatchers.IO) { HashUtil.hashFile(audioPath) }
                val snapshot = pendingSnapshot
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
                // 先写 evidence，再写 snapshot，满足外键约束
                evidenceRepository.save(evidence)
                snapshot?.let { snapshotRepository.insert(it) }
                Log.i(TAG, "Audio saved: $evidenceId, hash=$hash")

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