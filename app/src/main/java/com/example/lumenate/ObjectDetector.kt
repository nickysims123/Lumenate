package com.example.lumenate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.Detection
import java.io.ByteArrayOutputStream
import org.tensorflow.lite.task.vision.detector.ObjectDetector as TFObjectDetector
import android.view.Surface
import android.view.WindowManager
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.ops.Rot90Op

// Constructor:
// 1. Context to access assets to grab pretrained model
// 2. Callback to a list of type Detection (Tflite object) and Size (the size of the image captured by our Camera)

/* Detection Type:
Detection from the TFLite Task Library has two attributes:
boundingBox: RectF — the object's bounding box in image pixel coordinates, with these fields:
    box.left
    box.top
    box.right
    box.bottom

categories: List<Category> — list of classification results for the detected object, where each Category has:

category.label — the class name string (e.g. "chair", "person")
category.score — confidence from 0.0 to 1.0
category.index — class index from the label file
category.displayName — alternative display name if the model metadata provides one (often the same as label)
 */
class ObjectDetector(
    context: Context,
    private val onResults: (List<Detection>, Size) -> Unit,
) {
    // Initialize the detector, it's model, & all options
    private val detector = TFObjectDetector.createFromFileAndOptions(
        context,
        "efficientdet.tflite",
        TFObjectDetector.ObjectDetectorOptions.builder()
            .setMaxResults(5)
            .setScoreThreshold(0.4f)
            .build()
    )
    // Setting a ticker to go off every 5 seconds. Otherwise model will jump between objects if the room is crowded
    // TODO: In future updates, perhaps we pause detection and allow user to give voice feedback to select an object and navigate to it
    private var lastAnalyzedTime = 0L
    private val intervalMs = 5000L

    fun Image.toBitmap(): Bitmap {
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
        val jpegBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
    }

    fun analyze(image: Image, orientation: DeviceOrientation) {
        val now = System.currentTimeMillis()

        val numRotations = when (orientation) {
            DeviceOrientation.PORTRAIT          -> 3
            DeviceOrientation.LANDSCAPE         -> 0
            DeviceOrientation.REVERSE_PORTRAIT  -> 1
            DeviceOrientation.REVERSE_LANDSCAPE -> 2
        }

        // Skip frame if not enough time has passed
        val timeNotEnough = (now - lastAnalyzedTime) < intervalMs
        if (timeNotEnough) {
            image.close()
            return
        }
        lastAnalyzedTime = now

        val imageProcessor = ImageProcessor.Builder()
            .add(Rot90Op(numRotations))
            .build()

        val tensorImage = imageProcessor.process(TensorImage.fromBitmap(image.toBitmap()))
        val result = tensorImage.bitmap
        val results = detector.detect(tensorImage)

        onResults(results, Size(tensorImage.width, tensorImage.height))
        image.close()
    }
}