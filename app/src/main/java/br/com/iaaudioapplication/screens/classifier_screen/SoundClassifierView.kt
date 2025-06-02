package br.com.iaaudioapplication.screens.classifier_screen
import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.iaaudioapplication.SoundClassifierViewModel

class SoundClassifierView : ComponentActivity() {

    private val audioViewModel: SoundClassifierViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            audioViewModel.initializeClassifier(this)
        } else {
            val permanentlyDenied = !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
            if (permanentlyDenied) {
                showGoToSettingsDialog()
            } else {
//                updateOutputText("Permissão negada - o microfone não pode ser acessado")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermission()

        setContent {
            SoundClassifierScreen(viewModel = audioViewModel)
        }
    }

        private fun showGoToSettingsDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Permissão negada permanentemente")
        builder.setMessage("Para usar o app, conceda a permissão de microfone nas configurações.")
        builder.setPositiveButton("Abrir configurações") { _, _ ->
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", packageName, null)
            intent.data = uri
            startActivity(intent)
        }
        builder.setNegativeButton("Cancelar") { dialog, _ ->
            dialog.dismiss()
        }
        builder.show()
    }
    private fun checkPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                audioViewModel.initializeClassifier(this)
            }

            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }

            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    override fun onRestart() {
        checkPermission()
        super.onRestart()
    }
}

@Composable
fun SoundClassifierScreen(viewModel: SoundClassifierViewModel) {
    val uiState by viewModel.soundClassifierViewState.collectAsState()

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (uiState) {
                is SoundClassifierViewState.Initial -> Text("Waiting for microphone access...")
                is SoundClassifierViewState.Loading -> Text("Loading...")
                is SoundClassifierViewState.Success -> SoundClassificationResult(
                    outputText = (uiState as SoundClassifierViewState.Success).output,
                    recorderSpecs = ""
                )
                is SoundClassifierViewState.Error -> Text("Error: ${(uiState as SoundClassifierViewState.Error).message}")
                is SoundClassifierViewState.RecordingSpecs -> SoundClassificationResult(
                    outputText = "",
                    recorderSpecs = (uiState as SoundClassifierViewState.RecordingSpecs).specs
                )
            }
        }
    }
}

@Composable
fun SoundClassificationResult(
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
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(text = outputText)
    }
}
