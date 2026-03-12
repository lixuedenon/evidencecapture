// app/src/main/java/com/mathsnew/evidencecapture/presentation/textnote/TextNoteViewModel.kt
// Kotlin - 表现层，文字记录取证 ViewModel

package com.mathsnew.evidencecapture.presentation.textnote

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mathsnew.evidencecapture.domain.model.Evidence
import com.mathsnew.evidencecapture.domain.model.MediaType
import com.mathsnew.evidencecapture.domain.repository.EvidenceRepository
import com.mathsnew.evidencecapture.domain.repository.SensorSnapshotRepository
import com.mathsnew.evidencecapture.service.InstantSensorCapture
import com.mathsnew.evidencecapture.util.EvidenceIdGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class TextNoteUiState {
    object Idle : TextNoteUiState()
    object Saving : TextNoteUiState()
    data class Saved(val evidenceId: String) : TextNoteUiState()
    data class Error(val message: String) : TextNoteUiState()
}

@HiltViewModel
class TextNoteViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val evidenceRepository: EvidenceRepository,
    private val snapshotRepository: SensorSnapshotRepository,
    private val sensorCapture: InstantSensorCapture
) : ViewModel() {

    private val _uiState = MutableStateFlow<TextNoteUiState>(TextNoteUiState.Idle)
    val uiState: StateFlow<TextNoteUiState> = _uiState

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private val _voicePartialResult = MutableStateFlow("")
    val voicePartialResult: StateFlow<String> = _voicePartialResult

    private var speechRecognizer: SpeechRecognizer? = null

    fun startVoiceInput() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _uiState.value = TextNoteUiState.Error("设备不支持语音识别")
            return
        }
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: ""
                    _voicePartialResult.value = text
                    _isListening.value = false
                }
                override fun onPartialResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: ""
                    _voicePartialResult.value = text
                }
                override fun onError(error: Int) {
                    _isListening.value = false
                    Log.w(TAG, "Speech error: $error")
                }
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            startListening(intent)
        }
        _isListening.value = true
    }

    fun stopVoiceInput() {
        speechRecognizer?.stopListening()
        _isListening.value = false
    }

    fun clearVoiceResult() {
        _voicePartialResult.value = ""
    }

    fun saveNote(content: String, tag: String = "", title: String = "") {
        if (content.isBlank()) {
            _uiState.value = TextNoteUiState.Error("内容不能为空")
            return
        }
        viewModelScope.launch {
            _uiState.value = TextNoteUiState.Saving
            try {
                val evidenceId = EvidenceIdGenerator.generate()
                val snapshot = sensorCapture.capture(evidenceId)
                snapshotRepository.insert(snapshot)
                val evidence = Evidence(
                    id = evidenceId,
                    mediaType = MediaType.TEXT,
                    mediaPath = "",
                    textContent = content,
                    tag = tag,
                    // 用户未填标题时，默认用 evidenceId 作为标题
                    title = title.ifBlank { evidenceId },
                    sha256Hash = "",
                    createdAt = snapshot.capturedAt,
                    snapshotId = evidenceId
                )
                evidenceRepository.save(evidence)
                Log.i(TAG, "Text note saved: $evidenceId")
                launch(Dispatchers.IO) {
                    sensorCapture.fetchAndFillAsync(
                        evidenceId, snapshot.latitude, snapshot.longitude
                    )
                }
                _uiState.value = TextNoteUiState.Saved(evidenceId)
            } catch (e: Exception) {
                Log.e(TAG, "Save text note failed", e)
                _uiState.value = TextNoteUiState.Error(e.message ?: "保存失败")
            }
        }
    }

    override fun onCleared() {
        speechRecognizer?.destroy()
        super.onCleared()
    }

    companion object {
        private const val TAG = "TextNoteViewModel"
    }
}