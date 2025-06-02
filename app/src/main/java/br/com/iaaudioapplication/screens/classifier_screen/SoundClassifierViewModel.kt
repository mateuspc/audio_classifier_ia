package br.com.iaaudioapplication

import android.content.Context
import android.media.AudioManager
import android.media.AudioRecord
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.iaaudioapplication.screens.classifier_screen.SoundClassifierViewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.tensorflow.lite.task.audio.classifier.AudioClassifier
import org.tensorflow.lite.support.audio.TensorAudio
import java.util.*
import kotlin.concurrent.scheduleAtFixedRate

class SoundClassifierViewModel : ViewModel() {

    private val _soundClassifierViewState: MutableStateFlow<SoundClassifierViewState> = MutableStateFlow(SoundClassifierViewState.Initial)
    val soundClassifierViewState: StateFlow<SoundClassifierViewState> = _soundClassifierViewState.asStateFlow()

    private val modelPath = "lite-model_yamnet_classification_tflite_1.tflite"
    private val probabilityThreshold: Float = 0.3f
    private val MAX_RECORD_ATTEMPTS = 3
    private var audioRecordAttempts = 0

    private var classifier: AudioClassifier? = null
    private var record: AudioRecord? = null
    private var timer: Timer? = null

    fun initializeClassifier(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                releaseAudioResources()

                classifier = AudioClassifier.createFromFile(context, modelPath)
                val format = classifier?.requiredTensorAudioFormat

                _soundClassifierViewState.value = SoundClassifierViewState.RecordingSpecs(
                    "Channels: ${format?.channels}, Sample Rate: ${format?.sampleRate}"
                )

                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                if (audioManager.isMicrophoneMute) {
                    _soundClassifierViewState.value = SoundClassifierViewState.Error("Microphone is muted")
                    return@launch
                }

                try {
                    record = classifier?.createAudioRecord()
                    record?.startRecording()
                    audioRecordAttempts = 0

                    val tensor = classifier?.createInputTensorAudio()
                    startClassification(record, tensor)
                } catch (e: IllegalStateException) {
                    retryInitialization(context)
                }
            } catch (e: Exception) {
                _soundClassifierViewState.value = SoundClassifierViewState.Error("Initialization failed: ${e.localizedMessage}")
            }
        }
    }

    private fun retryInitialization(context: Context) {
        if (audioRecordAttempts < MAX_RECORD_ATTEMPTS) {
            audioRecordAttempts++
            Handler(Looper.getMainLooper()).postDelayed({
                initializeClassifier(context)
            }, 1000L * audioRecordAttempts)
        } else {
            _soundClassifierViewState.value = SoundClassifierViewState.Error("Failed after $MAX_RECORD_ATTEMPTS attempts")
        }
    }

    private fun startClassification(record: AudioRecord?, tensor: TensorAudio?) {
        timer = Timer()
        timer?.scheduleAtFixedRate(1, 500) {
            try {
                tensor?.load(record)
                val output = classifier?.classify(tensor)

                val result = output?.firstOrNull()?.categories
                    ?.filter { it.score > probabilityThreshold }
                    ?.sortedByDescending { it.score }
                    ?.joinToString("\n") { "${it.label} -> ${it.score}" }

                if (!result.isNullOrEmpty()) {
                    _soundClassifierViewState.value = SoundClassifierViewState.Success(result)
                }
            } catch (e: Exception) {
                _soundClassifierViewState.value = SoundClassifierViewState.Error("Classification error: ${e.localizedMessage}")
            }
        }
    }

    private fun releaseAudioResources() {
        try {
            timer?.cancel()
            record?.stop()
            record?.release()
            record = null
            timer = null
        } catch (e: Exception) {
            Log.e("SoundClassifierViewModel", "Error releasing audio", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        releaseAudioResources()
    }
}
