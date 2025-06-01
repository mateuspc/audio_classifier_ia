package br.com.iaaudioapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.tensorflow.lite.task.audio.classifier.AudioClassifier
import java.util.*
import kotlin.concurrent.scheduleAtFixedRate

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"
    private val modelPath = "lite-model_yamnet_classification_tflite_1.tflite"
    private val probabilityThreshold: Float = 0.3f
    private var audioRecordAttempts = 0
    private val MAX_RECORD_ATTEMPTS = 3

    // Audio resources
    private var classifier: AudioClassifier? = null
    private var record: android.media.AudioRecord? = null
    private var timer: Timer? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            initializeAudioClassification()
        } else {
            val permanentlyDenied = !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
            if (permanentlyDenied) {
                showGoToSettingsDialog()
            } else {
                updateOutputText("Permissão negada - o microfone não pode ser acessado")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                val outputText = remember { mutableStateOf("Waiting for microphone access...") }
                val recorderSpecsText = remember { mutableStateOf("") }

                SoundClassificationScreen(
                    outputText = outputText.value,
                    recorderSpecs = recorderSpecsText.value
                )

                LaunchedEffect(Unit) {
                    checkAndRequestPermission()
                }
            }
        }
    }

    private fun checkAndRequestPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                initializeAudioClassification()
            }

            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                // O usuário negou anteriormente, mas não marcou "Não perguntar novamente"
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }

            else -> {
                Log.i(TAG, "Requesting permission")
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun initializeAudioClassification() {
        try {
            // Release any existing resources
            releaseAudioResources()

            classifier = AudioClassifier.createFromFile(this, modelPath)
            val tensor = classifier?.createInputTensorAudio()

            val format = classifier?.requiredTensorAudioFormat
            updateRecorderSpecs("Number Of Channels: ${format?.channels}\nSample Rate: ${format?.sampleRate}")

            // Check audio focus
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            if (audioManager.isMicrophoneMute) {
                Log.w(TAG, "Microphone is muted")
                updateOutputText("Microphone is muted - please unmute")
                return
            }

            try {
                record = classifier?.createAudioRecord()
                record?.startRecording()
                audioRecordAttempts = 0 // Reset on success

                startClassification(record, tensor)
            } catch (e: IllegalStateException) {
                if (audioRecordAttempts < MAX_RECORD_ATTEMPTS) {
                    audioRecordAttempts++
                    Log.w(TAG, "AudioRecord initialization failed, retrying... Attempt $audioRecordAttempts")
                    Handler(Looper.getMainLooper()).postDelayed({
                        initializeAudioClassification()
                    }, 1000L * audioRecordAttempts) // Exponential backoff
                } else {
                    Log.e(TAG, "Max AudioRecord initialization attempts reached", e)
                    updateOutputText("Failed to initialize microphone after $MAX_RECORD_ATTEMPTS attempts")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio initialization failed", e)
            updateOutputText("Initialization error: ${e.localizedMessage}")
        }
    }

    private fun showGoToSettingsDialog() {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Permissão negada permanentemente")
        builder.setMessage("Para usar o app, conceda a permissão de microfone nas configurações.")
        builder.setPositiveButton("Abrir configurações") { _, _ ->
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", packageName, null)
            intent.data = uri
            startActivity(intent)
        }
        builder.setNegativeButton("Cancelar") { dialog, _ ->
            dialog.dismiss()
        }
        builder.show()
    }

    private fun startClassification(
        record: android.media.AudioRecord?,
        tensor: org.tensorflow.lite.support.audio.TensorAudio?
    ) {
        timer = Timer()
        timer?.scheduleAtFixedRate(1, 500) {
            try {
                val numberOfSamples = tensor?.load(record)
                val output = classifier?.classify(tensor)

                output?.let {
                    val filteredModelOutput = output[0].categories.filter {
                        it.score > probabilityThreshold
                    }

                    val outputStr = filteredModelOutput.sortedBy { -it.score }
                        .joinToString(separator = "\n") { "${it.label} -> ${it.score} " }

                    if (outputStr.isNotEmpty()) {
                        updateOutputText(outputStr)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Classification error", e)
            }
        }
    }

    private fun updateOutputText(text: String) {
        runOnUiThread {
            setContent {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val currentRecorderSpecs = remember { mutableStateOf("") }
                        SoundClassificationScreen(
                            outputText = text,
                            recorderSpecs = currentRecorderSpecs.value
                        )
                    }
                }
            }
        }
    }

    private fun updateRecorderSpecs(text: String) {
        runOnUiThread {
            setContent {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val currentOutputText = remember { mutableStateOf("") }
                        SoundClassificationScreen(
                            outputText = currentOutputText.value,
                            recorderSpecs = text
                        )
                    }
                }
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
            Log.e(TAG, "Error releasing audio resources", e)
        }
    }

    override fun onPause() {
        super.onPause()
        releaseAudioResources()
    }

    override fun onRestart() {
        Log.i("Script:", "onRestart()")
        checkAndRequestPermission()
        super.onRestart()
    }

    override fun onResume() {
        Log.i("Script:", "onResume()")
        super.onResume()
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            initializeAudioClassification()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseAudioResources()
    }
}

@Composable
fun SoundClassificationScreen(
    outputText: String,
    recorderSpecs: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = recorderSpecs,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = "Classification Results:",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp))
             Text(text = outputText)
    }
}