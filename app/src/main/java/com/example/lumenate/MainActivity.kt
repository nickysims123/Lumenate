package com.example.lumenate

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.camera.core.ImageAnalysis
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.sp
import com.google.ar.core.Anchor
import com.google.ar.core.exceptions.NotYetAvailableException
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.arcore.getUpdatedPlanes
import org.tensorflow.lite.task.vision.detector.Detection

import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader

// DataStore Preferences to store onboarding completion status, voice, and unit selection

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

private val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
private val KEY_VOICE_PREFERENCE    = stringPreferencesKey("voice_preference")

private val KEY_UNIT_PREFERENCE = stringPreferencesKey("unit_preference")


// app-specific preferences class
class UserPreferencesRepository(private val context: Context) {

    val onboardingComplete: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_ONBOARDING_COMPLETE] ?: false }

    val voicePreference: Flow<String> = context.dataStore.data
        .map { it[KEY_VOICE_PREFERENCE] ?: "" }

    val unitPreference: Flow<String> = context.dataStore.data
        .map {  it[KEY_UNIT_PREFERENCE] ?: ""  }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDING_COMPLETE] = complete }
    }

    suspend fun setVoicePreference(preference: String) {
        context.dataStore.edit { it[KEY_VOICE_PREFERENCE] = preference }
    }

    suspend fun setUnitPreference(unit: String) {
        context.dataStore.edit { it[KEY_UNIT_PREFERENCE] = unit}
    }
}

// ViewModel for preferences

class MainViewModel(application: android.app.Application) : AndroidViewModel(application) {

    private val repository = UserPreferencesRepository(application)

    val onboardingComplete: StateFlow<Boolean?> = repository.onboardingComplete
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun completeOnboarding() {
        viewModelScope.launch { repository.setOnboardingComplete(true) }
    }

    fun setVoicePreference(preference: String) {
        viewModelScope.launch { repository.setVoicePreference(preference) }
    }

    fun setUnitPreference(unit: String) {
        viewModelScope.launch { repository.setUnitPreference(unit) }
    }
}

// text to speech help func
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

// activity routes - we don't really need multiple activities if the app has one function

private object Routes {
    const val ONBOARDING = "onboarding"
    const val BLURB      = "blurb"
    const val CAMERA     = "camera"
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
                }
            )
        }
        composable(Routes.CAMERA) {
            CameraScreen()
        }
    }
}

// onboarding screen

private const val ONBOARDING_TTS =
    "Welcome to Lumenate. This app uses your camera to detect nearby objects " +
    "and keep you aware of your surroundings. Camera access is required for " +
    "the app to function. Please tap the Allow Camera Access button to continue."

@Composable
fun OnboardingScreen(onPermissionGranted: () -> Unit) {
    val context = LocalContext.current
    var permissionDenied by remember { mutableStateOf(false) }
    val tts = rememberTts()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onPermissionGranted()
        else permissionDenied = true
    }

    // if permission granted, skip through
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            onPermissionGranted()
        }
    }

    // speak instructions to user
    LaunchedEffect(tts) {
        tts?.speak(ONBOARDING_TTS, TextToSpeech.QUEUE_FLUSH, null, "onboarding")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
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
            text = "Lumenate uses your camera to detect nearby objects and help keep you aware of your surroundings.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Camera access is required for the app to function.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (permissionDenied) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Camera permission is required. Please grant access to continue.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            modifier = Modifier.fillMaxWidth().height(150.dp)
        ) {
            Text("Allow Camera Access", fontSize=(27.sp))
        }
    }
}

// blurb screen
private const val BLURB =
    "The app will help you navigate around the objects nearby. " +
    "Every 5 seconds, it will give an accurate depiction of the closest " +
    "object and its distance in feet or meters. If there is an object " +
    "within 5 feet, you will be alerted via an emergency message."

// will be changed to: speak when ready
private const val BLURB_POSITIONING =
    "To get started, hold your phone flush against your chest with the screen " +
    "facing your clothes. When you are in position, tap the I'm Ready button."

@Composable
fun BlurbScreen(onReady: () -> Unit) {
    val tts = rememberTts()
    var spokenOnce by remember { mutableStateOf(false) }

    // speak blurb and positioning instructions once text to speech is ready
    LaunchedEffect(tts) {
        if (tts != null && !spokenOnce) {
            spokenOnce = true
            val fullText = "$BLURB $BLURB_POSITIONING"
            tts.speak(fullText, TextToSpeech.QUEUE_FLUSH, null, "blurb")
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
            text = BLURB,
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

// ─── Camera Screen ───────────────────────────────────────────────────────────

enum class DeviceOrientation {
   PORTRAIT, REVERSE_LANDSCAPE, LANDSCAPE, REVERSE_PORTRAIT
}

// Orientation
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

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var orientation by remember { mutableStateOf(DeviceOrientation.PORTRAIT)}
    DeviceOrientationListener(context.applicationContext) { orientation = it }

    // Hold detection results in state so you can react to them
    var detectedObjects by remember { mutableStateOf<List<Detection>>(emptyList()) }
    var imageSize by remember { mutableStateOf(Size(1, 1)) }  // avoid div-by-zero
    val detector = ObjectDetector(context) { results, size ->
                                    detectedObjects = results
                                  imageSize = size
                                }
    Box(modifier = Modifier.fillMaxSize()) {
        ARSceneView(
            modifier = Modifier.fillMaxSize(),
            planeRenderer = true,
            onSessionUpdated = { session, frame ->
                val image = frame.acquireCameraImage()
                detector.analyze(image, orientation)
            }
        )

        // Detection Results Overlay
        BoundingBoxOverlay(
            detectedObjects = detectedObjects,
            imageSize = imageSize,
            modifier = Modifier.fillMaxSize()
        )

    }
}

@Composable
fun BoundingBoxOverlay(
    detectedObjects: List<Detection>,
    imageSize: Size,
    modifier: Modifier = Modifier
) {
    val colors = listOf(Color.Red, Color.Cyan, Color.Yellow, Color.Green, Color.Magenta)

    Canvas(modifier = modifier) {
        // scaleX/scaleY calculation stretches the box coordinates to match exactly what the preview is showing.
        // Otherwise the bounding box returned is just the
        // The rotation swap in the analyzer handles the fact that the back camera sensor is landscape-native but you're holding the phone in portrait
        val scaleX = size.width  / imageSize.width.toFloat()
        val scaleY = size.height / imageSize.height.toFloat()


       for ((index, detection) in detectedObjects.withIndex()) {
            val label = detection.categories.maxByOrNull { it.score }
            // If whatever's detected could not be properly or confidently labeled, skip to avoid clutter or misinformation to user
            if (label == null) {
                continue
            }
            val box = detection.boundingBox
            val color = colors[index % colors.size]

            // Scale bounding box from image coords to match image coords
            // FOR LATER UPDATE: What if the camera/image size does not match the actual screen size. This seems to be a bug, where bounding boxes are slightly off
            // Not a huge deal right now. Afterall our user's will not really be using the screen. But still...
            // For example, objects that appear off-screen to the user, actually get captured by the camera & detector, and classified. Which is great, but we should see how DepthAPI interacts with this later. Cause we still need to give spacially salient info to the user.
            val left   = box.left   * scaleX
            val top    = box.top    * scaleY
            val right  = box.right  * scaleX
            val bottom = box.bottom * scaleY

            // Draw the rectangle outline
            drawRect(
                color = color,
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                style = Stroke(width = 4.dp.toPx())
            )

            // Draw label above the box
            drawContext.canvas.nativeCanvas.drawText(
                "${label.label} ${(label.score * 100).toInt()}%",
                left,
                (top - 8.dp.toPx()).coerceAtLeast(16.dp.toPx()),
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
