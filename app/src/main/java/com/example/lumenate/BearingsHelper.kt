package com.example.lumenate

import android.media.Image
import android.util.Size
import com.google.ar.core.Frame
import org.tensorflow.lite.task.vision.detector.Detection

/*
FILE: BearingsHelper.kt
DESCRIPTION:
    Calculates the degrees left or right a detected object is, compared to the user.
    Inputs: DetectedObject
    Outputs: Triple(Detected Object, Bearing Object
*/

// While theoretically we could tell the user up/down based on bounding box logic, I'm not sure how that would behave
// What if we detect an object that is 12 feet away, directly in front of the user, but sitting atop a table. I'm not sure providing degrees up/down would be helpful here
// Perhaps the whole idea of "x degrees left/right is flawed to begin with". Normal vision does not work in 2D, but we are operating on limited time, and the point is to enrich the user's experience
enum class Direction {
    LEFT,
    RIGHT,
    FRONT
}

data class Bearing (
    val direction: Direction,
    val degrees: Float
)


/**
 * Returns a bearing calculation for each detection in the given ARCore frame.
 *
 * The center of the frame (image) is used as a reference point. A 90 degree line if you will
 * LEFT: 0-89 degrees
 * FRONT: 90 degrees, on the center line of the camera's line of sight
 * RIGHT: 91-180 degrees
 *
 * @param Image      The image generated from ARCore frame.acquireCameraImage
 * @param detections List of detected objects whose bearings should be calculated.
 * @param imageSize  Pixel dimensions of the camera image in which detections were made.
 * @return A list of pairs, each matching a Detection to its best available bearings in degrees left or right to the center line of the image.
 */
fun getBearingsForDetections(
    image: Image,
    detections: List<Detection>,
    imageSize: Size
): List<Pair<Detection, Bearing?>> {
    val imageCenterX = imageSize.width / 2.0f

    return detections.map { detection ->
        val boundingBox = detection.boundingBox

        // Find the horizontal center of the detected object's bounding box
        val objectCenterX = boundingBox.centerX()

        // Map the object's X position across the image width to a 0–180 degree scale,
        // where 0° = far left edge, 90° = center, 180° = far right edge
        val degrees = (objectCenterX / imageSize.width) * 180.0f

        val direction = when {
            degrees < 89f  -> Direction.LEFT
            degrees > 91f  -> Direction.RIGHT
            else           -> Direction.FRONT
        }

        val bearing = Bearing(direction = direction, degrees = degrees)
        Pair(detection, bearing)
    }
}
