package com.example.lumenate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.util.Log
import android.util.Size
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.Detection
import java.io.ByteArrayOutputStream
import org.tensorflow.lite.task.vision.detector.ObjectDetector as TFObjectDetector
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.ops.Rot90Op

// THIS NO LONGER USES THE CAMERAX API, MEANING THAT WE HAVE TO BUILD THE BITMAP OURSELVES

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

data class DetectionFrameResult(
    val detections: List<Detection>,
    val imageSize: Size,
    val frameTimestamp: Long,
)

class ObjectDetector(
    context: Context,
    interval: Long,
    maxResults: Int,
) {

    private val appContext = context.applicationContext
    // Initialize the detector, its model, & all options
    private var detector = TFObjectDetector.createFromFileAndOptions(
        appContext,
        "efficientdet.tflite",
        TFObjectDetector.ObjectDetectorOptions.builder()
            .setMaxResults(maxResults)
            .setScoreThreshold(0.4f) // Only accept detections the model is at least 40% confident about
            .build()
    )


    // Setting a ticker to go off every 5 seconds. Otherwise model will jump between objects if the room is crowded.
    // TODO: In future updates, perhaps we pause detection and allow user to give voice feedback to select an object and navigate to it
    private var lastAnalyzedTime = 0L
    private var intervalMs = interval

    fun updateMaxResults(newMax: Int) {
        detector?.close()
        detector = TFObjectDetector.createFromFileAndOptions(
            appContext,
            "efficientdet.tflite",
            TFObjectDetector.ObjectDetectorOptions.builder()
                .setMaxResults(newMax)
                .setScoreThreshold(0.4f)
                .build()
        )
    }

    fun updateInterval(newInterval: Long) {
        intervalMs = newInterval
    }
    // Extension function on Image so we can call it directly as image.toBitmap() throughout this class.
    // ARCore camera frames arrive in YUV_420_888 format, which TensorFlow Lite cannot consume directly,
    // so we convert to a standard Android Bitmap by going through the NV21 intermediate format.
    fun Image.toBitmap(): Bitmap {
        val yBuffer = planes[0].buffer // Y plane: brightness/luma for every pixel
        val uBuffer = planes[1].buffer // U plane: Cb chroma, sampled at half resolution
        val vBuffer = planes[2].buffer // V plane: Cr chroma, sampled at half resolution

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        // NV21 layout is: all Y bytes first, then interleaved V and U bytes
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)         // V before U to match the NV21 spec
        uBuffer.get(nv21, ySize + vSize, uSize)

        // YuvImage gives us a path to compress to JPEG, which BitmapFactory can then decode into a Bitmap
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out) // Quality 100 to avoid compression artifacts affecting detection
        val jpegBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
    }

    fun analyze(
        image: Image,
        orientation: DeviceOrientation,
        frameTimestamp: Long
    ): DetectionFrameResult? {
        try {
            val now = System.currentTimeMillis()

            val timeNotEnough = (now - lastAnalyzedTime) < intervalMs
            if (timeNotEnough) {
                return null
            }

            lastAnalyzedTime = now

            // The camera sensor is natively landscape, so we rotate the image to match the
            // current device orientation before running detection. Rot90Op takes a number of
            // 90° counter-clockwise rotations, so portrait needs 3 (270°) to correct for the
            // sensor's default landscape output.
            val numRotations = when (orientation) {
                DeviceOrientation.PORTRAIT -> 3
                DeviceOrientation.LANDSCAPE -> 0
                DeviceOrientation.REVERSE_PORTRAIT -> 1
                DeviceOrientation.REVERSE_LANDSCAPE -> 2
            }

            // ImageProcessor applies the rotation to the TensorImage before detection runs
            val imageProcessor = ImageProcessor.Builder()
                .add(Rot90Op(numRotations))
                .build()

            // Convert the ARCore frame to a useable image for EfficientDet
            val bitmap = image.toBitmap()

            // TensorImage is TensorFlow Lite's image container. imageProcessor.process() applies
            // the Rot90Op rotation defined above and returns the corrected image ready for the model.
            val tensorImage = imageProcessor.process(TensorImage.fromBitmap(bitmap))
            val results = detector.detect(tensorImage)

            return DetectionFrameResult(
                detections = results,
                imageSize = Size(tensorImage.width, tensorImage.height), // Use tensorImage dimensions since rotation may have swapped width and height
                frameTimestamp = frameTimestamp
            )
        } finally {
            // Always close the ARCore Image to release the underlying buffer back to the camera pipeline.
            image.close()
        }
    }
}