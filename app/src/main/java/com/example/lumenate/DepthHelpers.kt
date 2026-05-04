package com.example.lumenate

import android.media.Image
import android.util.Size
import com.google.ar.core.Frame
import com.google.ar.core.exceptions.NotYetAvailableException
import org.tensorflow.lite.task.vision.detector.Detection
import java.nio.ByteOrder
import kotlin.collections.any
import kotlin.collections.map
import kotlin.collections.sorted
import kotlin.collections.zip
import kotlin.ranges.coerceIn
import kotlin.to
import kotlin.use

/*
DepthHelpers.kt

getBestDepthForDetections is called, selecting from raw depth, or smooth depth as a fallback to calculate
depth using ARCore's acquireRawDepthImage16Bits or acquireDepthImage16Bits. Through having this fall back, distance is more
accurate, and we have far less null depth calculations during runtime.
*/



/**
 * Returns the best available depth measurement for each detection in the given ARCore frame.
 *
 * Raw depth (acquireRawDepthImage16Bits) is attempted first as it offers higher precision.
 * If any detection comes back without a depth value, a smooth depth image
 * (acquireDepthImage16Bits) is acquired as a fallback. For each detection, the raw value
 * is preferred and the smooth value is only used when the raw depth is null.
 *
 * Both depth image types may be unavailable on the first few frames after the session starts.
 * A NotYetAvailableException from either source is caught and treated as all-null results
 * for that source.
 *
 * @param frame      The current ARCore Frame from which depth images are acquired.
 * @param detections List of detected objects whose depth should be measured.
 * @param imageSize  Pixel dimensions of the camera image in which detections were made.
 * @return A list of pairs, each matching a Detection to its best available depth in metres,
 *         or null if neither depth source produced a reading for that detection.
 */
fun getBestDepthForDetections(
    frame: Frame,
    detections: List<Detection>,
    imageSize: Size
): List<Pair<Detection, Float?>> {

    val rawResults = try {
        frame.acquireRawDepthImage16Bits().use { rawDepth ->
            frame.acquireRawDepthConfidenceImage().use { confidence ->
                // Gets the depth for each detection using the raw depth image and raw depth conf image
                getDepthForDetections(
                    depthImage = rawDepth,
                    confidenceImage = confidence,
                    detections = detections,
                    imageSize = imageSize
                )
            }
        }
    } catch (e: NotYetAvailableException) {
        detections.map { it to null }
    }

    // If one of our detections has null distance, try smooth depth image
    val needsFallback = rawResults.any { (_, distance) -> distance == null }

    if (!needsFallback) {
        return rawResults
    }

    val smoothResults = try {
        frame.acquireDepthImage16Bits().use { smoothDepth ->
            // Gets the depth for each detection using the smooth depth image. No conf image needed bc data already processed
            getSmoothDepthForDetections(
                depthImage = smoothDepth,
                detections = detections,
                imageSize = imageSize
            )
        }
    } catch (e: NotYetAvailableException) {
        detections.map { it to null }
    }

    return rawResults.zip(smoothResults).map { (rawPair, smoothPair) ->
        val detection = rawPair.first
        val rawDistance = rawPair.second
        val smoothDistance = smoothPair.second

        // Select whichever is available for a detection
        detection to (rawDistance ?: smoothDistance)
    }
}

/**
 * Samples depth values from a raw 16-bit depth image for each detected object.
 *
 * For each detection, the center of its bounding box is located and a small neighborhood
 * of depth pixels is sampled around that point. Low-confidence depth readings are discarded
 * before sampling, and the median of valid samples is returned to reduce the impact of
 * outlier pixels.
 *
 * @param depthImage      Raw 16-bit depth image acquired via acquireRawDepthImage16Bits().
 *                        All 16 bits of each pixel encode distance in millimeters.
 * @param confidenceImage Confidence image acquired via acquireRawDepthConfidenceImage().
 *                        Each byte is a confidence value in the range 0–255; values below
 *                        30 are treated as unreliable and excluded from sampling.
 * @param detections      List of detected objects whose depth should be measured.
 * @param imageSize       Pixel dimensions of the camera image in which detections were made.
 * @return A list of pairs, each matching a Detection to its sampled depth in meters,
 *         or null if no reliable depth reading was available for that detection.
 */
fun getDepthForDetections(
    depthImage: Image,
    confidenceImage: Image,
    detections: List<Detection>,
    imageSize: Size
): List<Pair<Detection, Float?>> {

    val depthBuffer = depthImage.planes[0].buffer
        .order(ByteOrder.LITTLE_ENDIAN)
        .asShortBuffer()

    val confidenceBuffer = confidenceImage.planes[0].buffer

    val depthRowStride = depthImage.planes[0].rowStride / 2
    val depthPixelStride = depthImage.planes[0].pixelStride / 2

    val confidenceRowStride = confidenceImage.planes[0].rowStride
    val confidencePixelStride = confidenceImage.planes[0].pixelStride

    fun sampleDepth(x: Int, y: Int): Float? {
        val cx = x.coerceIn(0, depthImage.width - 1)
        val cy = y.coerceIn(0, depthImage.height - 1)

        val confidenceIndex =
            cy * confidenceRowStride + cx * confidencePixelStride

        val confidence =
            confidenceBuffer.get(confidenceIndex).toInt() and 0xFF

        // Raw depth confidence is 0..255  where 0 means no confidence.
        if (confidence < 30) {
            return null
        }

        val depthIndex =
            cy * depthRowStride + cx * depthPixelStride

        val rawDepthShort = depthBuffer.get(depthIndex)

        // For acquireRawDepthImage16Bits(), all 16 bits are depth in mm.
        val depthMm = rawDepthShort.toInt() and 0xFFFF

        if (depthMm == 0) {
            return null
        }

        return depthMm / 1000f
    }

    fun sampleNeighborhood(centerX: Int, centerY: Int, radius: Int = 4): Float? {
        val samples = mutableListOf<Float>()

        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                val depth = sampleDepth(centerX + dx, centerY + dy)
                if (depth != null) {
                    samples.add(depth)
                }
            }
        }

        if (samples.isEmpty()) {
            return null
        }

        // Median is better than average because one bad depth pixel can be wildly wrong. Outliers skew results
        return samples.sorted()[samples.size / 2]
    }

    return detections.map { detection ->
        val box = detection.boundingBox

        val centerX = (box.left + box.right) / 2f
        val centerY = (box.top + box.bottom) / 2f

        // The physical camera sensor for most devices operates in landscape. Our ARCore depth images will be in landscape mode
        // If we do not rotate the coordinates of our bounding box we wil be providing the wrong coordinates to check a detection's depth

        // The depth image is rotated 90 degrees relative to the camera image, so the axes must be swapped  and scaled to the depth image dimensions.
        // centerY (vertical in camera space) maps to depthX, and centerX (horizontal in camera space) maps to depthY, with a horizontal flip applied.
        val depthX = (centerY / imageSize.height * depthImage.width).toInt()
        val depthY = ((imageSize.width - centerX) / imageSize.width * depthImage.height).toInt()

        val depth = sampleNeighborhood(depthX, depthY)

        detection to depth
    }
}


/**
 * Samples depth values from a smoothed 16-bit depth image for each detected object.
 *
 * Uses ARCore's smooth depth image (acquireDepthImage16Bits), which applies temporal
 * smoothing and hole-filling across frames. Because the data has already been processed,
 * individual confidence checks are not needed. However, smooth depth is less precise than
 * raw depth and lags slightly behind fast movement, so this function is intended as a
 * fallback when raw depth readings are unavailable.
 *
 * @param depthImage  Smooth 16-bit depth image acquired via acquireDepthImage16Bits().
 *                    All 16 bits of each pixel encode distance in millimeters.
 * @param detections  List of detected objects whose depth should be measured.
 * @param imageSize   Pixel dimensions of the camera image in which detections were made.
 * @return A list of pairs, each matching a Detection to its sampled depth in meters,
 *         or null if no depth reading was available for that detection.
 */
fun getSmoothDepthForDetections(
    depthImage: Image,
    detections: List<Detection>,
    imageSize: Size
): List<Pair<Detection, Float?>> {

    val depthBuffer = depthImage.planes[0].buffer
        .order(ByteOrder.LITTLE_ENDIAN)
        .asShortBuffer()

    val depthRowStride = depthImage.planes[0].rowStride / 2
    val depthPixelStride = depthImage.planes[0].pixelStride / 2

    fun sampleDepth(x: Int, y: Int): Float? {
        val cx = x.coerceIn(0, depthImage.width - 1)
        val cy = y.coerceIn(0, depthImage.height - 1)

        val depthIndex = cy * depthRowStride + cx * depthPixelStride
        val depthMm = depthBuffer.get(depthIndex).toInt() and 0xFFFF

        if (depthMm == 0) return null

        return depthMm / 1000f
    }

    fun sampleNeighborhood(centerX: Int, centerY: Int, radius: Int = 4): Float? {
        val samples = mutableListOf<Float>()

        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                val depth = sampleDepth(centerX + dx, centerY + dy)
                if (depth != null) {
                    samples.add(depth)
                }
            }
        }

        if (samples.isEmpty()) return null

        return samples.sorted()[samples.size / 2]
    }

    return detections.map { detection ->
        val box = detection.boundingBox

        val centerX = (box.left + box.right) / 2f
        val centerY = (box.top + box.bottom) / 2f

        val depthX = (centerY / imageSize.height * depthImage.width).toInt()
        val depthY = ((imageSize.width - centerX) / imageSize.width * depthImage.height).toInt()

        detection to sampleNeighborhood(depthX, depthY)
    }
}