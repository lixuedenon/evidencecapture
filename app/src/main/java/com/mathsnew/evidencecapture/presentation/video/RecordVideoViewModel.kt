// app/src/main/java/com/mathsnew/evidencecapture/presentation/video/RecordVideoViewModel.kt
// Kotlin - 表现层，录像取证 ViewModel，支持 45 秒自动分段保存

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

    companion object {
        private const val TAG = "RecordVideoViewModel"

        // 每段最长录制时间（秒），到时自动保存并开始下一段
        const val SEGMENT_MAX_SECONDS = 45
    }

    private val _uiState = MutableStateFlow<VideoUiState>(VideoUiState.Idle)
    val uiState: StateFlow<VideoUiState> = _uiState

    // 当前段已录制秒数，用于 UI 显示倒计时
    private val _durationSeconds = MutableStateFlow(0)
    val durationSeconds: StateFlow<Int> = _durationSeconds

    // 当前段序号，从 1 开始，UI 显示"第 N 段"
    private val _segmentIndex = MutableStateFlow(1)
    val segmentIndex: StateFlow<Int> = _segmentIndex

    // 已自动保存的段数，用于 UI 提示"已保存 N 段"
    private val _savedSegmentCount = MutableStateFlow(0)
    val savedSegmentCount: StateFlow<Int> = _savedSegmentCount

    private var durationJob: Job? = null
    private var captureJob: Job? = null
    private var pendingSnapshot: SensorSnapshot? = null

    // 当前段录制过程中用户输入的标题和标签
    // 自动分段时沿用，手动保存时以用户输入为准
    private var currentTag: String = ""
    private var currentTitle: String = ""

    // Screen 层持有的录制控制回调，ViewModel 在自动分段时通过此回调触发停止和重新开始
    // 使用函数类型避免 ViewModel 直接依赖 CameraX
    private var onRequestStopRecording: (() -> Unit)? = null
    private var onRequestStartRecording: ((String) -> Unit)? = null

    /**
     * Screen 层在 Composable 启动时注册录制控制回调
     * @param stopFn  停止当前录制的函数
     * @param startFn 以新 evidenceId 开始录制的函数
     */
    fun registerRecordingCallbacks(
        stopFn: () -> Unit,
        startFn: (evidenceId: String) -> Unit
    ) {
        onRequestStopRecording = stopFn
        onRequestStartRecording = startFn
    }

    fun clearRecordingCallbacks() {
        onRequestStopRecording = null
        onRequestStartRecording = null
    }

    /**
     * CameraX 录制开始后由 Screen 层调用
     * 启动计时 Job 和传感器采集，并启动分段倒计时
     */
    fun onRecordingStarted(evidenceId: String) {
        pendingSnapshot = null
        _durationSeconds.value = 0
        _uiState.value = VideoUiState.Recording(evidenceId)
        Log.i(TAG, "Segment ${_segmentIndex.value} started: $evidenceId")

        // 传感器采集在后台并行进行，不阻塞录制
        captureJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val snapshot = sensorCapture.capture(evidenceId, measureDecibel = false)
                pendingSnapshot = snapshot
                Log.i(TAG, "Sensor captured for segment ${_segmentIndex.value}: $evidenceId")
            } catch (e: Exception) {
                Log.w(TAG, "Sensor capture failed: ${e.message}")
            }
        }

        // 计时 + 自动分段倒计时
        durationJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val newDuration = _durationSeconds.value + 1
                _durationSeconds.value = newDuration

                // 到达最大段时长，自动停止当前段触发保存流程
                if (newDuration >= SEGMENT_MAX_SECONDS) {
                    Log.i(TAG, "Segment ${_segmentIndex.value} reached $SEGMENT_MAX_SECONDS s, auto-stopping")
                    onRequestStopRecording?.invoke()
                    break
                }
            }
        }
    }

    /**
     * CameraX 录制结束后由 Screen 层调用，携带视频文件路径
     * @param isAutoSegment true 表示由自动分段触发，保存后自动开始下一段；
     *                      false 表示用户手动停止，进入 ReadyToSave 等待用户确认
     */
    fun onRecordingStopped(
        evidenceId: String,
        videoPath: String,
        isAutoSegment: Boolean
    ) {
        durationJob?.cancel()
        viewModelScope.launch {
            captureJob?.join()
            if (isAutoSegment) {
                // 自动分段：静默保存当前段，保存完成后立即开始下一段
                autoSaveAndContinue(evidenceId, videoPath)
            } else {
                // 手动停止：进入 ReadyToSave，等用户点保存或取消
                _uiState.value = VideoUiState.ReadyToSave(evidenceId, videoPath)
            }
        }
    }

    /**
     * 自动分段保存：写库完成后立即通知 Screen 开始下一段录制
     * 此过程对用户完全透明，UI 保持 Recording 状态（段序号 +1）
     */
    private suspend fun autoSaveAndContinue(evidenceId: String, videoPath: String) {
        try {
            val hash = withContext(Dispatchers.IO) { HashUtil.hashFile(videoPath) }
            val snapshot = pendingSnapshot
            val evidence = Evidence(
                id = evidenceId,
                mediaType = MediaType.VIDEO,
                mediaPath = videoPath,
                tag = currentTag,
                title = if (currentTitle.isBlank()) evidenceId else currentTitle,
                sha256Hash = hash,
                createdAt = snapshot?.capturedAt ?: System.currentTimeMillis(),
                snapshotId = evidenceId
            )
            evidenceRepository.save(evidence)
            snapshot?.let { snapshotRepository.insert(it) }
            Log.i(TAG, "Auto-saved segment ${_segmentIndex.value}: $evidenceId")

            // 异步回填天气和地址，不阻塞下一段开始
            viewModelScope.launch(Dispatchers.IO) {
                val lat = snapshot?.latitude ?: 0.0
                val lng = snapshot?.longitude ?: 0.0
                sensorCapture.fetchAndFillAsync(evidenceId, lat, lng)
            }

            _savedSegmentCount.value += 1
            _segmentIndex.value += 1

            // 生成新 ID，通知 Screen 开始下一段
            val nextEvidenceId = EvidenceIdGenerator.generate()
            Log.i(TAG, "Starting next segment ${_segmentIndex.value}: $nextEvidenceId")
            onRequestStartRecording?.invoke(nextEvidenceId)

        } catch (e: Exception) {
            Log.e(TAG, "Auto-save segment failed", e)
            _uiState.value = VideoUiState.Error(e.message ?: "自动保存失败")
        }
    }

    /**
     * 用户手动点击保存，对应 ReadyToSave 状态下的保存按钮
     */
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
                Log.i(TAG, "Manual save: $evidenceId, total segments: ${_segmentIndex.value}")

                viewModelScope.launch(Dispatchers.IO) {
                    val lat = snapshot?.latitude ?: 0.0
                    val lng = snapshot?.longitude ?: 0.0
                    sensorCapture.fetchAndFillAsync(evidenceId, lat, lng)
                }

                // 手动保存后导航到最后一段的详情页
                _uiState.value = VideoUiState.Saved(evidenceId)
            } catch (e: Exception) {
                Log.e(TAG, "Manual save failed", e)
                _uiState.value = VideoUiState.Error(e.message ?: "保存失败")
            }
        }
    }

    /**
     * 用户手动点击取消，删除当前未保存的视频文件，重置全部状态
     * 注意：已自动保存的分段不受影响，仅删除当前 ReadyToSave 中的文件
     */
    fun cancelAndDelete() {
        val state = _uiState.value as? VideoUiState.ReadyToSave
        state?.let { File(it.videoPath).delete() }
        resetState()
    }

    /**
     * 更新当前录制会话的标题和标签
     * 自动分段时每段都使用此值，用户可在录制中随时修改
     */
    fun updateSessionMeta(tag: String, title: String) {
        currentTag = tag
        currentTitle = title
    }

    fun resetState() {
        durationJob?.cancel()
        captureJob?.cancel()
        _uiState.value = VideoUiState.Idle
        _durationSeconds.value = 0
        _segmentIndex.value = 1
        _savedSegmentCount.value = 0
        pendingSnapshot = null
        currentTag = ""
        currentTitle = ""
    }
}