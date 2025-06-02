package br.com.iaaudioapplication.screens.classifier_screen

sealed interface SoundClassifierViewState {
    object Initial : SoundClassifierViewState
    object Loading : SoundClassifierViewState
    data class Success(val output: String) : SoundClassifierViewState
    data class Error(val message: String) : SoundClassifierViewState
    data class RecordingSpecs(val specs: String) : SoundClassifierViewState
}