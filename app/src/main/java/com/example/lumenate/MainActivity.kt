package com.example.lumenate

import android.Manifest

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels

import androidx.camera.lifecycle.ProcessCameraProvider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.lumenate.ui.theme.LumenateTheme
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Locale
import androidx.camera.core.ImageAnalysis
import android.util.Size
import android.view.OrientationEventListener
import androidx.compose.material3.Surface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithCache

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy

import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.lifecycle.viewmodel.compose.viewModel

import com.google.ar.core.Config

import com.google.ar.core.exceptions.NotYetAvailableException
import io.github.sceneview.ar.ARSceneView

import org.tensorflow.lite.task.vision.detector.Detection





// DataStore Preferences to store onboarding completion status, voice, and unit selection

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

private val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
private val KEY_VOICE_PREFERENCE    = stringPreferencesKey("voice_preference")

private val KEY_UNIT_PREFERENCE = stringPreferencesKey("unit_preference")

private val KEY_MAX_RESULTS_PREFERENCE = intPreferencesKey("max_results_preference")

private val KEY_OBJECT_DETECTION_INTERVAL_PREFERENCE = longPreferencesKey("object_detection_interval_preference")
// app-specific preferences class
//TODO (jimmy): change this to your settings accordingly
// once i get the google voice api working ill change it after you
class UserPreferencesRepository(private val context: Context) {

    val onboardingComplete: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_ONBOARDING_COMPLETE] ?: false }

    val voicePreference: Flow<String> = context.dataStore.data
        .map { it[KEY_VOICE_PREFERENCE] ?: "" }

    val unitPreference: Flow<String> = context.dataStore.data
        .map {  it[KEY_UNIT_PREFERENCE] ?: ""  }

    val maxResultsPreference: Flow<Int> = context.dataStore.data.map { it[KEY_MAX_RESULTS_PREFERENCE] ?: 5 }

    val objectDetectionIntervalPreference: Flow<Long> = context.dataStore.data.map { it[KEY_OBJECT_DETECTION_INTERVAL_PREFERENCE] ?: 5000L }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDING_COMPLETE] = complete }
    }

    suspend fun setVoicePreference(preference: String) {
        context.dataStore.edit { it[KEY_VOICE_PREFERENCE] = preference }
    }

    suspend fun setUnitPreference(unit: String) {
        context.dataStore.edit { it[KEY_UNIT_PREFERENCE] = unit}
    }

    suspend fun setMaxResultsPreference(maxResults: Int) {
        context.dataStore.edit { it[KEY_MAX_RESULTS_PREFERENCE] = maxResults }
    }

    suspend fun setObjectDetectionIntervalPreference(interval: Long) {
        context.dataStore.edit { it[KEY_OBJECT_DETECTION_INTERVAL_PREFERENCE] = interval}
    }

}

// ViewModel for preferences

class MainViewModel(application: android.app.Application) : AndroidViewModel(application) {

    private val repository = UserPreferencesRepository(application)

    val onboardingComplete: StateFlow<Boolean?> = repository.onboardingComplete
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val voicePreference: StateFlow<String> = repository.voicePreference
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val maxResultsPreference: StateFlow<Int> = repository.maxResultsPreference.stateIn(viewModelScope,
        SharingStarted.WhileSubscribed((5000)), 5)

    val objectDetectionIntervalPreference: StateFlow<Long> = repository.objectDetectionIntervalPreference.stateIn(viewModelScope,
        SharingStarted.WhileSubscribed((5000)), 5000L)
    fun completeOnboarding() {
        viewModelScope.launch { repository.setOnboardingComplete(true) }
    }

    fun setVoicePreference(preference: String) {
        viewModelScope.launch { repository.setVoicePreference(preference) }
    }

    fun setUnitPreference(unit: String) {
        viewModelScope.launch { repository.setUnitPreference(unit) }
    }


    fun setMaxResultsPreference(maxResults: Int) {
        viewModelScope.launch { repository.setMaxResultsPreference(maxResults) }
    }

    fun setObjectDetectionIntervalPreference(interval: Long) {
        viewModelScope.launch { repository.setObjectDetectionIntervalPreference((interval)) }
    }
}

// ─── Google Cloud Text-to-Speech ─────────────────────────────────────────────
// Replaces the Android built-in TextToSpeech engine. The old Android TTS code
// is commented out

// 3 selectable voices (chosen via "1", "2", or "3" on the settings screen)
private val VOICE_OPTIONS = mapOf(
    "1" to "en-US-Neural2-A", // male
    "2" to "en-US-Neural2-F", // female
    "3" to "en-US-Neural2-J"  // male, deeper
)
private const val DEFAULT_VOICE = "en-US-Neural2-C"

private fun voiceForPref(pref: String): String = VOICE_OPTIONS[pref] ?: DEFAULT_VOICE

class GoogleTts(
    private val context: Context,
    private val scope: CoroutineScope
) {
    @Volatile var voiceName: String = DEFAULT_VOICE

    private val client = OkHttpClient()
    private var mediaPlayer: MediaPlayer? = null
    private var currentJob: Job? = null

    fun speak(text: String, utteranceId: String = "default") {
        currentJob?.cancel()
        currentJob = scope.launch {
            runCatching { playText(text, null) }
                .onFailure { Log.e("GoogleTts", "speak failed: ${it.message}") }
        }
    }

    suspend fun speakAndAwait(text: String, utteranceId: String = "default") {
        currentJob?.cancel()
        suspendCancellableCoroutine<Unit> { cont ->
            val job = scope.launch {
                runCatching {
                    playText(text) { if (cont.isActive) cont.resume(Unit) {} }
                }.onFailure {
                    Log.e("GoogleTts", "speakAndAwait failed: ${it.message}")
                    if (cont.isActive) cont.resume(Unit) {}
                }
            }
            currentJob = job
            cont.invokeOnCancellation {
                job.cancel()
                stop()
            }
        }
    }

    fun stop() {
        currentJob?.cancel()
        runCatching { mediaPlayer?.stop() }
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun shutdown() = stop()

    private suspend fun playText(text: String, onComplete: (() -> Unit)?) {
        val audio = withContext(Dispatchers.IO) { synthesize(text) }
        if (audio == null) {
            onComplete?.invoke()
            return
        }
        val tempFile = File.createTempFile("tts_", ".mp3", context.cacheDir).apply {
            writeBytes(audio)
            deleteOnExit()
        }
        withContext(Dispatchers.Main) {
            runCatching { mediaPlayer?.stop() }
            mediaPlayer?.release()
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(tempFile.absolutePath)
                setOnCompletionListener { player ->
                    player.release()
                    tempFile.delete()
                    if (mediaPlayer === player) mediaPlayer = null
                    onComplete?.invoke()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("GoogleTts", "MediaPlayer error what=$what extra=$extra")
                    tempFile.delete()
                    onComplete?.invoke()
                    true
                }
                prepare()
                start()
            }
            mediaPlayer = mp
        }
    }

    private fun synthesize(text: String): ByteArray? {
        val key = BuildConfig.GOOGLE_API_KEY
        if (key.isBlank()) {
            Log.e("GoogleTts", "GOOGLE_API_KEY is empty — set it in local.properties")
            return null
        }
        val body = JSONObject().apply {
            put("input", JSONObject().put("text", text))
            put("voice", JSONObject().apply {
                put("languageCode", "en-US")
                put("name", voiceName)
            })
            put("audioConfig", JSONObject().put("audioEncoding", "MP3"))
        }.toString().toRequestBody("application/json".toMediaType())

        val req = Request.Builder()
            .url("https://texttospeech.googleapis.com/v1/text:synthesize?key=$key")
            .post(body)
            .build()

        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.e("GoogleTts", "synthesize HTTP ${resp.code}: ${resp.body?.string()}")
                return null
            }
            val audioContent = JSONObject(resp.body?.string() ?: "{}").optString("audioContent")
            if (audioContent.isBlank()) null else Base64.decode(audioContent, Base64.DEFAULT)
        }
    }
}

@Composable
fun rememberTts(voicePref: String = ""): GoogleTts {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tts = remember { GoogleTts(context, scope) }
    LaunchedEffect(voicePref) {
        tts.voiceName = voiceForPref(voicePref)
    }
    DisposableEffect(Unit) {
        onDispose { tts.shutdown() }
    }
    return tts
}

/* ─── OLD: Android built-in TextToSpeech (kept for revert) ────────────────────
@Composable
fun rememberTts(): TextToSpeech? {
    val context = LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.US
                tts = engine
            }
        }
        onDispose {
            engine?.stop()
            engine?.shutdown()
        }
    }

    return tts
}
*/

// activity routes - we don't really need multiple activities if the app has one function

private object Routes {
    const val ONBOARDING = "onboarding"
    const val BLURB      = "blurb"
    const val CAMERA     = "camera"
    const val SETTINGS      = "settings"
    const val HELP       = "help"
}

// main activity

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LumenateTheme {
                val onboardingComplete by viewModel.onboardingComplete.collectAsState()

                when (onboardingComplete) {
                    null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    false -> AppNavigation(Routes.ONBOARDING, viewModel)
                    true  -> AppNavigation(Routes.BLURB,      viewModel)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(startDestination: String, viewModel: MainViewModel) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onPermissionGranted = {
                    viewModel.completeOnboarding()
                    navController.navigate(Routes.BLURB) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.BLURB) {
            BlurbScreen(
                onReady = {
                    navController.navigate(Routes.CAMERA) {
                        popUpTo(Routes.BLURB) { inclusive = true }
                    }
                },
                viewModel = viewModel
            )
        }
        composable(Routes.CAMERA) {
            CameraScreen(
                onPermissionsRequired = {
                    navController.navigate(Routes.ONBOARDING) {
                        popUpTo(Routes.CAMERA) { inclusive = true }
                    }
                },
                onHelpRequested = {
                    navController.navigate(Routes.HELP) {
                        popUpTo(Routes.CAMERA) { inclusive = true }
                    }
                },
                viewModel = viewModel
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(viewModel, {navController.navigate((Routes.CAMERA))})
        }
        composable(Routes.HELP) {
            HelpScreen(
                onReturnToCamera = {
                    navController.navigate(Routes.CAMERA) {
                        popUpTo(Routes.HELP) { inclusive = true }
                    }
                },
                onSettingsNavigate = { navController.navigate(Routes.SETTINGS)},
                viewModel = viewModel
                // TODO jimmy: add nav call back for setting screen
            )
        }
    }
}

// onboarding screen

private const val ONBOARDING_TTS =
    "Welcome to Lumenate. This app uses your camera and microphone to detect nearby objects " +
            "and keep you aware of your surroundings. Permission dialogs will appear now. " +
            "Please grant both permissions to continue. " +
            "If a permission was denied, say allow or tap the button to try again."

@Composable
fun OnboardingScreen(onPermissionGranted: () -> Unit) {
    val context = LocalContext.current
    val tts = rememberTts()

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
        // OLD (Android TTS): tts?.speak(ONBOARDING_TTS, TextToSpeech.QUEUE_FLUSH, null, "onboarding")
        tts.speak(ONBOARDING_TTS, "onboarding")
    }

    // potentially could allow for permission grants, haven't tested yet
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

@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    // 1. Collect the state from ViewModel
    val context = LocalContext.current
    val maxResults by viewModel.maxResultsPreference.collectAsState()
    val interval by viewModel.objectDetectionIntervalPreference.collectAsState()
    val voicePreference by viewModel.voicePreference.collectAsState()
    val tts = rememberTts(voicePreference)

    // STT for voice selection: user says "1", "2", or "3"
    LaunchedEffect(Unit) {
        continuousSpeechFlow(context).collect { transcript ->
            val words = transcript.lowercase().split(Regex("\\W+"))
            val voiceKey = when {
                "3" in words || "three" in words -> "3"
                "2" in words || "two" in words -> "2"
                "1" in words || "one" in words -> "1"
                else -> null
            }
            if (voiceKey != null) {
                tts.voiceName = voiceForPref(voiceKey)
                viewModel.setVoicePreference(voiceKey)
                tts.speak("Okay, this is the voice you have selected.", "voice_selected")
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
    }
}

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
        // OLD (Android TTS): tts.speak("${getBlurb(maxResults, interval)} $BLURB_POSITIONING", TextToSpeech.QUEUE_FLUSH, null, "blurb")
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
        // OLD (Android TTS): tts.speak(helpBlurb, TextToSpeech.QUEUE_FLUSH, null, "help_blurb")
        tts.speak(helpBlurb, "help_blurb")
    }

    // STT
    LaunchedEffect(tts) {
        continuousSpeechFlow(context).collect { transcript ->
            when {
                // reread the help blurb even if on help screen
                transcript.contains("help", ignoreCase = true) ->
                    // OLD (Android TTS): tts.speak(helpBlurb, TextToSpeech.QUEUE_FLUSH, null, "help_blurb")
                    tts.speak(helpBlurb, "help_blurb")
                transcript.contains("settings", ignoreCase = true) -> onSettingsNavigate()
                // TODO (jimmy): add callback mappings for settings screen here
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

        // TODO (jimmy): maybe have button for settings screen here too
    }
}

// ─── Speech-to-Text ──────────────────────────────────────────────────────────

private val sttIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
}

// to mute the beeps from speechrecognizer
private val beepStreams = listOf(
    AudioManager.STREAM_NOTIFICATION,
    AudioManager.STREAM_RING,
    AudioManager.STREAM_SYSTEM,
    AudioManager.STREAM_ALARM
)

// mute all speechRecognizer beeps (so annoying)
private fun AudioManager.muteBeep() {
    beepStreams.forEach { runCatching { adjustStreamVolume(it, AudioManager.ADJUST_MUTE, 0) } }
}

// unmute system sounds after app closure
private fun AudioManager.unmuteBeep() {
    beepStreams.forEach { runCatching { adjustStreamVolume(it, AudioManager.ADJUST_UNMUTE, 0) } }
}

private fun continuousSpeechFlow(context: Context): Flow<String> = callbackFlow {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    if (!SpeechRecognizer.isRecognitionAvailable(context)) { close(); return@callbackFlow }
    val recognizer = SpeechRecognizer.createSpeechRecognizer(context)

    fun restart() {
        audioManager.muteBeep()
        recognizer.startListening(sttIntent)
    }

    recognizer.setRecognitionListener(object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
        override fun onResults(results: Bundle?) {
            val transcript = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
            if (transcript.isNotBlank()) trySend(transcript)
            restart()
        }
        override fun onError(error: Int) {
            restart()
        }
    })

    restart()

    awaitClose {
        audioManager.unmuteBeep()
        recognizer.stopListening()
        recognizer.destroy()
    }
}

// ─── Camera Screen ───────────────────────────────────────────────────────────

/* OLD (Android TTS): replaced by GoogleTts.speakAndAwait member function — kept for revert.
private suspend fun TextToSpeech.speakAndAwait(text: String, id: String) =
    suspendCancellableCoroutine<Unit> { cont ->
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(u: String?) {}
            override fun onDone(u: String?) { if (cont.isActive) cont.resume(Unit) {} }
            override fun onError(u: String?) { if (cont.isActive) cont.resume(Unit) {} }
            override fun onStop(u: String?, interrupted: Boolean) { if (cont.isActive) cont.resume(Unit) {} }
        })
        speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        cont.invokeOnCancellation { stop() }
    }
*/

enum class DeviceOrientation {
    PORTRAIT, REVERSE_LANDSCAPE, LANDSCAPE, REVERSE_PORTRAIT
}

// Orientation Listener for passing in images to the object detection model
@Composable
fun DeviceOrientationListener(applicationContext: Context, onOrientationChange: (DeviceOrientation) -> Unit) {
    DisposableEffect(Unit) {
        val orientationEventListener = object : OrientationEventListener(applicationContext) {
            override fun onOrientationChanged(orientation: Int) {
                val newOrientation = when (orientation) {
                    in 315..359, in 0..44   -> DeviceOrientation.PORTRAIT
                    in 45..134              -> DeviceOrientation.REVERSE_LANDSCAPE
                    in 135..224             -> DeviceOrientation.REVERSE_PORTRAIT
                    in 225..314             -> DeviceOrientation.LANDSCAPE
                    else                    -> return // shouldn't happen
                }
                onOrientationChange(newOrientation)
            }
        }
        orientationEventListener.enable()

        // Disable the event onDispose
        onDispose {
            orientationEventListener.disable()
        }
    }
}







// Due to how the depth API works, if your phone doesn't have a ToF sensor, it relies on motion to find the depth, which means that sometimes it takes like 15 seconds of moving the camera around to find an accurate depthmap
@Composable
fun CameraScreen(
    onPermissionsRequired: () -> Unit,
    viewModel: MainViewModel,
    onHelpRequested: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val voicePreference by viewModel.voicePreference.collectAsState()
    val tts = rememberTts(voicePreference)
    var orientation by remember { mutableStateOf(DeviceOrientation.PORTRAIT) }
    DeviceOrientationListener(context.applicationContext) { orientation = it }
//    debug images for viewing
//    var debugDepthBitmap by remember { mutableStateOf<Bitmap?>(null) }
//    var debugCameraBitmap by remember { mutableStateOf<Bitmap?>(null) }
//    var debugConfidenceBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var detectedObjects by remember { mutableStateOf<List<Detection>>(emptyList()) }
    var imageSize by remember { mutableStateOf(Size(1, 1)) }  // avoid div-by-zero
    var detectedObjectsDistances by remember {mutableStateOf<List<Pair<Detection, Float?>>>(emptyList())}
    var detectedObjectsBearings by remember {mutableStateOf<List<Pair<Detection,Bearing?>>>(emptyList())}
    val maxResults by viewModel.maxResultsPreference.collectAsState()
    val interval by viewModel.objectDetectionIntervalPreference.collectAsState()

    var showDialog by remember { mutableStateOf(false) }
    var closestObjectDistance by remember { mutableStateOf(-1f) }
    var closestObject by remember { mutableStateOf("") }
    var lastAlertTime by remember { mutableLongStateOf(0L) }

    // 1. Get the vibrator service (standard way for API 26-30)
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    val effect = VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE)
    val detector = remember(context) {
        ObjectDetector(context, interval, maxResults)
    }

    //    settings updates
    LaunchedEffect(maxResults) {
        detector.updateMaxResults(maxResults)
    }

    LaunchedEffect(detectedObjectsDistances) {
        val now = System.currentTimeMillis()

        val closest = detectedObjectsDistances
            .filter { it.second != null }
            .minByOrNull { it.second!! }

        if (closest != null && closest.second!! < 0.3f) {
            closestObject = closest.first.categories.firstOrNull()?.label ?: "Object"
            closestObjectDistance = closest.second!!
            if (now - lastAlertTime > interval) {
                showDialog = true
                vibrator.vibrate(effect)
                lastAlertTime = now
            }
        } else {
            showDialog = false
        }
    }

    LaunchedEffect(interval) {
        detector.updateInterval(interval)
    }


    val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val audioGranted  = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    // STT - always listening for "help" - will nav to help screen later for preference updates
    LaunchedEffect(Unit) {
        if (!cameraGranted || !audioGranted) { onPermissionsRequired(); return@LaunchedEffect }
        continuousSpeechFlow(context).collect { transcript ->
            when {
                transcript.contains("help", ignoreCase = true) -> onHelpRequested()
            }
        }
    }

    LaunchedEffect(detectedObjectsDistances) { }

    // notification loop - speaks all detected objects & if they have a corresponding distance; 5-second gap starts after speech ends
    LaunchedEffect(tts) {
        while (true) {
            val announcement = if (detectedObjectsDistances.isEmpty()) {
                "No objects detected."
            } else if (showDialog) {
                "WARNING: YOU ARE TOO CLOSE TO ${closestObject}, DISTANCE ${closestObjectDistance}"
            } else {
                detectedObjectsDistances
                    .take(maxResults)
                    .joinToString(separator = ". ") { (detection, distance) ->
                        val label = detection.categories
                            .maxByOrNull { it.score }
                            ?.label
                            ?.takeIf { it.isNotBlank() }
                            ?: "unknown object"

                        val distanceText = if (distance != null) {
                            "%.1f meters away".format(distance)
                        } else {
                            "unknown distance"
                        }

                        "$label, $distanceText"
                    }

            }

            tts.speakAndAwait(announcement, "objects")
            kotlinx.coroutines.delay(5_000)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ARSceneView( // due to the Depth API being used, we have to switch from cameraX to ARCore's camera implementation
            modifier = Modifier.fillMaxSize(),
            planeRenderer = true,
            sessionConfiguration = { session, config ->
                config.depthMode = when {
                    session.isDepthModeSupported(Config.DepthMode.AUTOMATIC) -> {
                        Log.d("DMODE","Depth mode automatic")
                        Config.DepthMode.AUTOMATIC
                    }

                    session.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY) -> {
                        Log.d("DMODE","Depth mode RAW")
                        Config.DepthMode.RAW_DEPTH_ONLY
                    }

                    else -> {
                        Log.e("DMODE UNAVAIL","Depth mode DISABLED")
                        Config.DepthMode.DISABLED
                    }
                }
            },
            onSessionUpdated = {_, frame ->
                val frameTimestamp = frame.timestamp

                try {
                    val image = frame.acquireCameraImage()

                    val detectionFrameResult = detector.analyze(
                        image = image,
                        orientation = orientation,
                        frameTimestamp = frameTimestamp
                    )
                    // Distance reading is paired with the detection result from the current frame
                    if (detectionFrameResult != null) {
                        detectedObjects = detectionFrameResult.detections
                        imageSize = detectionFrameResult.imageSize

                        detectedObjectsDistances = getBestDepthForDetections(
                            frame = frame,
                            detections = detectionFrameResult.detections,
                            imageSize = detectionFrameResult.imageSize
                        )
                        detectedObjectsBearings = getBearingsForDetections(
                            image = image,
                            detections = detectionFrameResult.detections,
                            imageSize = imageSize
                        )
                    }

                } catch (e: NotYetAvailableException) {
                    Log.d("Camera", "Camera image not available for frame $frameTimestamp")
                }
            }
        )

        // Detection Results Overlay
        BoundingBoxOverlay(
            detectedObjects = detectedObjects,
            detectedObjectsDistances = detectedObjectsDistances,
            detectedObjectsBearings = detectedObjectsBearings,
            imageSize = imageSize,
            modifier = Modifier.fillMaxSize(),
            orientation = orientation,
        )

        // dev-only: tap to jump to help screen
        Button(
            onClick = onHelpRequested,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            Text("Help", fontSize = 14.sp)
        }

        Box(modifier = Modifier.fillMaxSize()) {

            if (showDialog) {
                // Non-blocking Card anchored to the top or center
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 100.dp) // Below your back button
                        .fillMaxWidth(0.85f),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                    tonalElevation = 6.dp,
                    shadowElevation = 10.dp
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "PROXIMITY WARNING",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "$closestObject is ${"%.1f".format(closestObjectDistance)}m away",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// Bounding box is only accurate for both portrait modes due to ARCore always sending images according to the camera position (almost always landscape-left), so must adjust for landscape later
@Composable
fun BoundingBoxOverlay(
    detectedObjects: List<Detection>,
    detectedObjectsDistances: List<Pair<Detection, Float?>>,
    detectedObjectsBearings: List<Pair<Detection, Bearing?>>,
    imageSize: Size,
    modifier: Modifier = Modifier,
    orientation: DeviceOrientation
) {
    val colors = listOf(Color.Red, Color.Cyan, Color.Yellow, Color.Green, Color.Magenta)
    Canvas(modifier = modifier) {
        val scaleX = size.width / imageSize.width.toFloat()
        val scaleY = size.height / imageSize.height.toFloat()

        for ((index, detection) in detectedObjects.withIndex()) {
            val label = detection.categories.maxByOrNull { it.score } ?: continue
            val box = detection.boundingBox
            val color = colors[index % colors.size]

            val left   = box.left   * scaleX
            val top    = box.top    * scaleY
            val right  = box.right  * scaleX
            val bottom = box.bottom * scaleY

            // Flip coordinates based on orientation
            val (adjLeft, adjTop, adjRight, adjBottom) = when (orientation) {
                DeviceOrientation.PORTRAIT -> listOf(left, top, right, bottom)
                DeviceOrientation.REVERSE_PORTRAIT -> listOf(
                    size.width - right,
                    size.height - bottom,
                    size.width - left,
                    size.height - top
                )
                DeviceOrientation.LANDSCAPE -> listOf(
                    size.width - right,
                    top,
                    size.width - left,
                    bottom
                )
                DeviceOrientation.REVERSE_LANDSCAPE -> listOf(
                    left,
                    size.height - bottom,
                    right,
                    size.height - top
                )
            }

            val distance = detectedObjectsDistances.getOrNull(index)?.second
            drawRect(
                color = color,
                topLeft = Offset(adjLeft, adjTop),
                size = androidx.compose.ui.geometry.Size(adjRight - adjLeft, adjBottom - adjTop),
                style = Stroke(width = 4.dp.toPx())
            )

            val bearing = detectedObjectsBearings.getOrNull(index)?.second
            val bearingString = if (bearing != null) {
                val directionLabel = when (bearing.direction) {
                    Direction.LEFT  -> "left"
                    Direction.RIGHT -> "right"
                    Direction.FRONT -> "forward"
                }
                "%.1f degrees $directionLabel".format(bearing.degrees)
            } else {
                ""
            }
            drawContext.canvas.nativeCanvas.drawText(
                "${label.label} ${(label.score * 100).toInt()}%}, $distance, $bearingString",
                adjLeft,
                (adjTop - 8.dp.toPx()).coerceAtLeast(16.dp.toPx()),
                android.graphics.Paint().apply {
                    this.color = android.graphics.Color.WHITE
                    textSize = 40f
                    isFakeBoldText = true
                    setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
                }
            )
        }
    }
}
