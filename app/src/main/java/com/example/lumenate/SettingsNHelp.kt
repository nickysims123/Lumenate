package com.example.lumenate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val maxResults by viewModel.maxResultsPreference.collectAsState()
    val interval by viewModel.objectDetectionIntervalPreference.collectAsState()
    val voicePreference by viewModel.voicePreference.collectAsState()
    val tts = rememberTts(voicePreference)
    var spokenOnce by remember { mutableStateOf(false) }

    val intervalSecondsSpoken = interval / 1000f
    val settingsBlurb =
        "Settings menu. There are three settings you can change. " +
                "First, max objects, currently set to $maxResults. This controls how many " +
                "nearby objects are reported each scan, from one to ten. To change it, say " +
                "objects, followed by a number from one to ten. For example, say objects five. " +
                "Second, scan interval, currently set to ${"%.1f".format(intervalSecondsSpoken)} seconds. " +
                "This controls how often the camera scans for objects, from one to ten seconds. " +
                "To change it, say interval, followed by a number from one to ten. " +
                "For example, say interval three. " +
                "Third, voice. To change the voice, say voice, followed by one, two, or three. " +
                "For example, say voice two."

    LaunchedEffect(tts) {
        if (spokenOnce) return@LaunchedEffect
        spokenOnce = true
        tts.speak(settingsBlurb, "settings_blurb")
    }

    // STT for voice selection ("1"/"2"/"3"), and slider commands ("objects N", "interval N")
    LaunchedEffect(Unit) {
        continuousSpeechFlow(context).collect { transcript ->
            val lower = transcript.lowercase()
            val words = lower.split(Regex("\\W+"))

            val numberWords = mapOf(
                "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
                "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10
            )
            fun firstNumberAfter(keyword: String): Int? {
                val idx = words.indexOf(keyword)
                if (idx == -1) return null
                for (i in (idx + 1) until words.size) {
                    val w = words[i]
                    val n = w.toIntOrNull() ?: numberWords[w]
                    if (n != null && n in 1..10) return n
                }
                return null
            }

            val objectsValue = firstNumberAfter("objects") ?: firstNumberAfter("object")
            val intervalValue = firstNumberAfter("interval")
            val voiceValue = firstNumberAfter("voice")?.takeIf { it in 1..3 }

            when {
                objectsValue != null -> {
                    viewModel.setMaxResultsPreference(objectsValue)
                    tts.speak("Objects set to $objectsValue.", "objects_set")
                }
                intervalValue != null -> {
                    viewModel.setObjectDetectionIntervalPreference(intervalValue * 1000L)
                    tts.speak("Interval set to $intervalValue seconds.", "interval_set")
                }
                voiceValue != null -> {
                    val voiceKey = voiceValue.toString()
                    tts.voiceName = voiceForPref(voiceKey)
                    viewModel.setVoicePreference(voiceKey)
                    tts.speak("Okay, this is the voice you have selected.", "voice_selected")
                }
            }
        }
    }

    Column(modifier = Modifier.padding(16.dp).statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 4.dp)
        ) {
            IconButton(
                onClick = { onBack() },
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(28.dp)
                )
            }

            Text(
                text = "Settings Menu",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                ),
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        // --- Max Results Slider ---
        Text(text = "Max Results: $maxResults", style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = maxResults.toFloat(),
            onValueChange = { newValue ->
                viewModel.setMaxResultsPreference(newValue.toInt())
            },
            valueRange = 1f..10f,
            steps = 8
        )

        Spacer(modifier = Modifier.height(24.dp))

        // --- Detection Interval Slider ---
        // Displaying in seconds for better readability
        val intervalInSeconds = interval / 1000f
        Text(text = "Scan Every: ${"%.1f".format(intervalInSeconds)} seconds")

        Slider(
            value = interval.toFloat(),
            onValueChange = { newValue ->
                viewModel.setObjectDetectionIntervalPreference(newValue.toLong())
            },
            valueRange = 1000f..10000f, // 1s to 10s
        )

        Spacer(modifier = Modifier.height(24.dp))

        // --- Voice Adjustment Slider ---
        Text(text = "Voice $voicePreference")
        Slider(
            value = voicePreference.toFloat(),
            onValueChange = { newValue ->
                val voiceKey = newValue.toInt().toString()
                tts.voiceName = voiceForPref(voiceKey)
                viewModel.setVoicePreference(voiceKey)
                tts.speak("Okay, this is the voice you have selected.", "voice_selected")
            },
            valueRange = 1f..3f, // 1 to 3
            steps = 3
        )
    }
}

// ─── Help Screen ─────────────────────────────────────────────────────────────

@Composable
fun HelpScreen(
    onReturnToCamera: () -> Unit,
    onSettingsNavigate: () -> Unit,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val voicePreference by viewModel.voicePreference.collectAsState()
    val tts = rememberTts(voicePreference)
    var spokenOnce by remember { mutableStateOf(false) }
    val maxResults by viewModel.maxResultsPreference.collectAsState()
    val interval by viewModel.objectDetectionIntervalPreference.collectAsState()

    val helpBlurb =
        "This is the help screen. I will reread a description of the app, then give" +
                " you some options." + getBlurb(maxResults, interval) +
                "to navigate to the settings screen to change x y and z, please say settings"

    // TTS - read the help blurb on arrival
    LaunchedEffect(tts) {
        if (spokenOnce) return@LaunchedEffect
        spokenOnce = true
        tts.speak(helpBlurb, "help_blurb")
    }

    // STT
    LaunchedEffect(tts) {
        continuousSpeechFlow(context).collect { transcript ->
            when {
                // reread the help blurb even if on help screen
                transcript.contains("help", ignoreCase = true) ->
                    tts.speak(helpBlurb, "help_blurb")
                transcript.contains("settings", ignoreCase = true) -> onSettingsNavigate()
            }
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
            text = "Help",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = helpBlurb,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = onReturnToCamera,
            modifier = Modifier.fillMaxWidth().height(100.dp)
        ) {
            Text("Return to Camera", fontSize = 25.sp)
        }

        Spacer(modifier = Modifier.height(5.dp))

        Button(
            onClick = onSettingsNavigate,
            modifier = Modifier.fillMaxWidth().height(100.dp)
        ) {
            Text("Settings", fontSize = 25.sp)
        }
    }
}