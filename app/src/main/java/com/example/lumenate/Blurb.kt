package com.example.lumenate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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

fun getBlurb(maxResults: Int, intervalMs: Long): String {
    val intervalSeconds = intervalMs / 1000f

    return "The app will help you navigate around the objects nearby." +
            "Every $intervalSeconds seconds, it will give an accurate depiction of the closest $maxResults " +
            "object and its distance in feet or meters. If there is an object " +
            "within 5 feet, you will be alerted via an emergency message."
}

private const val BLURB_POSITIONING =
    "To get started, hold your phone flush against your chest with the screen " +
            "facing your clothes. When you are in position, say I'm Ready."

// Displays everytime you open the app
@Composable
fun BlurbScreen(onReady: () -> Unit, viewModel: MainViewModel) {
    val context = LocalContext.current
    val voicePreference by viewModel.voicePreference.collectAsState()
    val tts = rememberTts(voicePreference)
    var spokenOnce by remember { mutableStateOf(false) }
    val maxResults by viewModel.maxResultsPreference.collectAsState()
    val interval by viewModel.objectDetectionIntervalPreference.collectAsState()

    LaunchedEffect(tts) {
        if (spokenOnce) return@LaunchedEffect
        spokenOnce = true
        tts.speak("${getBlurb(maxResults, interval)} $BLURB_POSITIONING", "blurb")
    }

    LaunchedEffect(Unit) {
        continuousSpeechFlow(context).collect { transcript ->
            if (transcript.contains("ready", ignoreCase = true)) onReady()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "About Lumenate",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = getBlurb(maxResults, interval),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Text(
                text = "Hold your phone flush against your chest with the screen facing your clothes.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = onReady,
            modifier = Modifier.fillMaxWidth().height(150.dp)
        ) {
            Text("I'm Ready", fontSize=27.sp)
        }
    }
}