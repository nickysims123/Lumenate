package com.example.lumenate

import android.content.Context
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector as TFObjectDetector

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
    private val onResults: (List<Detection>, Size) -> Unit
) : ImageAnalysis.Analyzer {
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
    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()

        // Skip frame if not enough time has passed
        if (now - lastAnalyzedTime < intervalMs) {
            imageProxy.close()
            return
        }
        lastAnalyzedTime = now
        val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }

        // Convert ImageProxy to Bitmap for TFLite Task Library
        val bitmap = imageProxy.toBitmap()

        // Setting size of overall image captured by user camera so we know how to draw bounding boxes later
        val isRotated = imageProxy.imageInfo.rotationDegrees == 90
                || imageProxy.imageInfo.rotationDegrees == 270
        val imageSize = if (isRotated) {
            Size(imageProxy.height, imageProxy.width)
        } else {
            Size(imageProxy.width, imageProxy.height)
        }

        // Convert Bitmap to TensorImage for TFLite Task Library. Then detect object(s)
        val tensorImage = TensorImage.fromBitmap(bitmap)
        val results = detector.detect(tensorImage)

        onResults(results, imageSize)
        imageProxy.close()
    }
}