// app/src/main/java/com/mathsnew/evidencecapture/presentation/detail/EvidenceDetailViewModel.kt
// Kotlin - 表现层，证据详情 ViewModel，含音频播放状态管理

package com.mathsnew.evidencecapture.presentation.detail

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mathsnew.evidencecapture.domain.model.Evidence
import com.mathsnew.evidencecapture.domain.model.SensorSnapshot
import com.mathsnew.evidencecapture.domain.repository.EvidenceRepository
import com.mathsnew.evidencecapture.domain.repository.SensorSnapshotRepository
import com.mathsnew.evidencecapture.util.HashUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DetailUiState {
    object Loading : DetailUiState()
    data class Success(
        val evidence: Evidence,
        val snapshot: SensorSnapshot?,
        val hashVerified: Boolean
    ) : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}

/** 音频播放状态 */
sealed class AudioPlayState {
    object Idle : AudioPlayState()
    data class Playing(
        val path: String,
        val currentMs: Int,
        val totalMs: Int,
        val label: String   // "录音" 或 "语音备注"
    ) : AudioPlayState()
    data class Paused(
        val path: String,
        val currentMs: Int,
        val totalMs: Int,
        val label: String
    ) : AudioPlayState()
}

@HiltViewModel
class EvidenceDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val evidenceRepository: EvidenceRepository,
    private val snapshotRepository: SensorSnapshotRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState

    private val _audioPlayState = MutableStateFlow<AudioPlayState>(AudioPlayState.Idle)
    val audioPlayState: StateFlow<AudioPlayState> = _audioPlayState

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    fun loadEvidence(evidenceId: String) {
        Log.e(TAG, "loadEvidence called: $evidenceId")
        viewModelScope.launch {
            try {
                val evidence = evidenceRepository.getById(evidenceId)
                Log.e(TAG, "evidence loaded: ${evidence?.id} mediaType=${evidence?.mediaType} mediaPath=${evidence?.mediaPath}")
                if (evidence == null) {
                    _uiState.value = DetailUiState.Error("证据不存在")
                    return@launch
                }
                val snapshot = snapshotRepository.getByEvidenceId(evidenceId)
                val hashVerified = if (evidence.mediaPath.isNotEmpty() &&
                    evidence.sha256Hash.isNotEmpty()
                ) {
                    HashUtil.verifyFile(evidence.mediaPath, evidence.sha256Hash)
                } else true
                _uiState.value = DetailUiState.Success(evidence, snapshot, hashVerified)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load evidence", e)
                _uiState.value = DetailUiState.Error(e.message ?: "加载失败")
            }
        }
    }

    /**
     * 播放或暂停指定音频文件
     * @param path   音频文件绝对路径
     * @param label  显示标签，"录音" 或 "语音备注"
     */
    fun togglePlay(path: String, label: String) {
        val current = _audioPlayState.value
        when {
            // 当前正在播放同一文件 → 暂停
            current is AudioPlayState.Playing && current.path == path -> {
                mediaPlayer?.pause()
                _audioPlayState.value = AudioPlayState.Paused(
                    path = path,
                    currentMs = mediaPlayer?.currentPosition ?: 0,
                    totalMs = mediaPlayer?.duration ?: 0,
                    label = label
                )
                progressJob?.cancel()
            }
            // 当前已暂停同一文件 → 继续播放
            current is AudioPlayState.Paused && current.path == path -> {
                mediaPlayer?.start()
                _audioPlayState.value = AudioPlayState.Playing(
                    path = path,
                    currentMs = mediaPlayer?.currentPosition ?: 0,
                    totalMs = mediaPlayer?.duration ?: 0,
                    label = label
                )
                startProgressTracking(path, label)
            }
            // 其他情况（Idle 或切换到另一文件）→ 重新初始化播放
            else -> {
                stopAudio()
                startPlay(path, label)
            }
        }
    }

    /** 停止播放，释放资源 */
    fun stopAudio() {
        progressJob?.cancel()
        progressJob = null
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        _audioPlayState.value = AudioPlayState.Idle
    }

    /** 跳转到指定进度 */
    fun seekTo(ms: Int) {
        mediaPlayer?.seekTo(ms)
        val current = _audioPlayState.value
        if (current is AudioPlayState.Playing) {
            _audioPlayState.value = current.copy(currentMs = ms)
        } else if (current is AudioPlayState.Paused) {
            _audioPlayState.value = current.copy(currentMs = ms)
        }
    }

    private fun startPlay(path: String, label: String) {
        try {
            val player = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                start()
                setOnCompletionListener {
                    progressJob?.cancel()
                    _audioPlayState.value = AudioPlayState.Idle
                    stopAudio()
                }
            }
            mediaPlayer = player
            _audioPlayState.value = AudioPlayState.Playing(
                path = path,
                currentMs = 0,
                totalMs = player.duration,
                label = label
            )
            startProgressTracking(path, label)
        } catch (e: Exception) {
            Log.e(TAG, "MediaPlayer failed: ${e.message}")
            _audioPlayState.value = AudioPlayState.Idle
        }
    }

    private fun startProgressTracking(path: String, label: String) {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                val player = mediaPlayer ?: break
                if (!player.isPlaying) break
                _audioPlayState.value = AudioPlayState.Playing(
                    path = path,
                    currentMs = player.currentPosition,
                    totalMs = player.duration,
                    label = label
                )
                delay(200)
            }
        }
    }

    override fun onCleared() {
        stopAudio()
        super.onCleared()
    }

    companion object {
        private const val TAG = "EvidenceDetailViewModel"
    }
}