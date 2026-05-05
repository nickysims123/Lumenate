package com.example.lumenate

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

// onboarding screen

private const val ONBOARDING_TTS =
    "Welcome to Lumenate. This app uses your camera and microphone to detect nearby objects " +
            "and keep you aware of your surroundings. Permission dialogs will appear now. " +
            "Please grant both permissions to continue. " +
            "If a permission was denied, say allow or tap the button to try again."

//Displays first time opening the app
@Composable
fun OnboardingScreen(onPermissionGranted: () -> Unit, viewModel: MainViewModel) {
    val context = LocalContext.current
    val voicePref by viewModel.voicePreference.collectAsState()
    val tts = rememberTts(voicePref)

    var cameraGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var audioGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        audioGranted = results[Manifest.permission.RECORD_AUDIO] == true
        cameraGranted = results[Manifest.permission.CAMERA] == true
        if (cameraGranted) onPermissionGranted()
    }

    LaunchedEffect(Unit) {
        if (cameraGranted && audioGranted) onPermissionGranted()
        else permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA))
    }

    // speak instructions to user
    LaunchedEffect(tts) {
        tts.speak(ONBOARDING_TTS, "onboarding")
    }

    LaunchedEffect(audioGranted) {
        if (!audioGranted) return@LaunchedEffect
        continuousSpeechFlow(context).collect { transcript ->
            if (transcript.contains("allow", ignoreCase = true) ||
                transcript.contains("yes", ignoreCase = true) ||
                transcript.contains("grant", ignoreCase = true)
            ) {
                permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA))
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Welcome to Lumenate",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Camera and microphone access are required. Permission dialogs will appear automatically.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(40.dp))
        Button(
            onClick = { permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)) },
            modifier = Modifier.fillMaxWidth().height(150.dp)
        ) {
            Text("Grant Permissions", fontSize = 27.sp)
        }
    }
}