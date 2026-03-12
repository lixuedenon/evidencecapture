// app/src/main/java/com/mathsnew/evidencecapture/presentation/capture/CaptureViewModel.kt
// Kotlin - 表现层，拍照流程 ViewModel

package com.mathsnew.evidencecapture.presentation.capture

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
import com.mathsnew.evidencecapture.util.HashUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed class CaptureUiState {
    object Idle : CaptureUiState()
    object Capturing : CaptureUiState()
    data class PhotoTaken(val evidenceId: String, val photoPath: String) : CaptureUiState()
    object Saving : CaptureUiState()
    data class Saved(val evidenceId: String) : CaptureUiState()
    data class Error(val message: String) : CaptureUiState()
}

@HiltViewModel
class CaptureViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val evidenceRepository: EvidenceRepository,
    private val snapshotRepository: SensorSnapshotRepository,
    private val sensorCapture: InstantSensorCapture
) : ViewModel() {

    private val _uiState = MutableStateFlow<CaptureUiState>(CaptureUiState.Idle)
    val uiState: StateFlow<CaptureUiState> = _uiState

    private val _snapshot = MutableStateFlow<SensorSnapshot?>(null)
    val snapshot: StateFlow<SensorSnapshot?> = _snapshot

    fun onShutterPressed(): String {
        val evidenceId = EvidenceIdGenerator.generate()
        _uiState.value = CaptureUiState.Capturing
        viewModelScope.launch {
            try {
                val snapshot = sensorCapture.capture(evidenceId)
                _snapshot.value = snapshot
                val current = _uiState.value
                if (current is CaptureUiState.PhotoTaken &&
                    current.evidenceId == evidenceId) {
                    doSave(evidenceId, current.photoPath, snapshot)
                }
                if (snapshot.latitude != 0.0 || snapshot.longitude != 0.0) {
                    launch(Dispatchers.IO) {
                        val address = sensorCapture.reverseGeocode(
                            snapshot.latitude, snapshot.longitude
                        )
                        if (address.isNotEmpty()) {
                            _snapshot.value = snapshot.copy(address = address)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sensor capture failed", e)
                _uiState.value = CaptureUiState.Error(e.message ?: "传感器采集失败")
            }
        }
        return evidenceId
    }

    fun onPhotoSaved(evidenceId: String, photoPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val decibel = sensorCapture.measureDecibelPublic()
                val current = _snapshot.value
                if (current != null) {
                    _snapshot.value = current.copy(decibel = decibel)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Decibel after photo failed: ${e.message}")
            }
        }
        val currentSnapshot = _snapshot.value
        if (currentSnapshot != null) {
            viewModelScope.launch {
                doSave(evidenceId, photoPath, currentSnapshot)
            }
        } else {
            _uiState.value = CaptureUiState.PhotoTaken(evidenceId, photoPath)
        }
    }

    private suspend fun doSave(
        evidenceId: String,
        photoPath: String,
        snapshot: SensorSnapshot
    ) {
        _uiState.value = CaptureUiState.Saving
        try {
            val hash = withContext(Dispatchers.IO) { HashUtil.hashFile(photoPath) }
            val evidence = Evidence(
                id = evidenceId,
                mediaType = MediaType.PHOTO,
                mediaPath = photoPath,
                // 默认用 evidenceId 作为标题，用户可在编辑时修改
                title = evidenceId,
                sha256Hash = hash,
                createdAt = snapshot.capturedAt,
                snapshotId = evidenceId
            )
            evidenceRepository.save(evidence)
            snapshotRepository.insert(snapshot)
            _uiState.value = CaptureUiState.PhotoTaken(evidenceId, photoPath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save", e)
            _uiState.value = CaptureUiState.Error(e.message ?: "保存失败")
        }
    }

    fun saveEvidence(
        evidenceId: String,
        photoPath: String,
        voiceNotePath: String = "",
        tag: String = "",
        title: String = ""
    ) {
        viewModelScope.launch {
            _uiState.value = CaptureUiState.Saving
            try {
                val hash = withContext(Dispatchers.IO) { HashUtil.hashFile(photoPath) }
                val snapshot = _snapshot.value ?: SensorSnapshot(
                    id = evidenceId,
                    evidenceId = evidenceId,
                    capturedAt = System.currentTimeMillis()
                )
                val evidence = Evidence(
                    id = evidenceId,
                    mediaType = MediaType.PHOTO,
                    mediaPath = photoPath,
                    voiceNotePath = voiceNotePath,
                    tag = tag,
                    // 用户未填标题时，默认用 evidenceId 作为标题
                    title = title.ifBlank { evidenceId },
                    sha256Hash = hash,
                    createdAt = snapshot.capturedAt,
                    snapshotId = evidenceId
                )
                evidenceRepository.save(evidence)
                snapshotRepository.insert(snapshot)
                Log.i(TAG, "Evidence confirmed: $evidenceId tag=$tag voicePath=$voiceNotePath")
                launch(Dispatchers.IO) {
                    sensorCapture.fetchAndFillAsync(
                        evidenceId, snapshot.latitude, snapshot.longitude
                    )
                }
                _uiState.value = CaptureUiState.Saved(evidenceId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to confirm evidence", e)
                _uiState.value = CaptureUiState.Error(e.message ?: "保存失败")
            }
        }
    }

    fun resetState() {
        _uiState.value = CaptureUiState.Idle
        _snapshot.value = null
    }

    companion object {
        private const val TAG = "CaptureViewModel"
    }
}