/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.mediapipe.examples.handlandmarker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

class HandLandmarkerHelper(
    var minConfidence: Float = 0.5f,
    var numThreads: Int = 2,
    var maxNumHands: Int = 2,
    var currentDelegate: Int = 0,
    val context: Context,
    val handLandmarkerHelperListener: LandmarkerListener
) {

    // For this example this needs to be a var so it can be reset on changes.
    // If the Hand Landmarker will not change, a lazy val would be preferable.
    private var handLandmarker: HandLandmarker? = null

    init {
        setupHandLandmarker()
    }

    fun clearHandLandmarker() {
        handLandmarker?.close()
        handLandmarker = null
    }

    // Initialize the Hand landmarker using current settings on the
    // thread that is using it. CPU can be used with Landmarker
    // that are created on the main thread and used on a background thread, but
    // the GPU delegate needs to be used on the thread that initialized the
    // Landmarker
    fun setupHandLandmarker() {
        Log.d(TAG, "${this.hashCode()} setupHandLandmarker started.")
        // Set general hand landmarker options, including number of used threads
        val baseOptionBuilder = BaseOptions.builder()

        // Use the specified hardware for running the model. Default to CPU
        when (currentDelegate) {
            DELEGATE_CPU -> {
                baseOptionBuilder.setDelegate(Delegate.CPU)
            }
            DELEGATE_GPU -> {
                baseOptionBuilder.setDelegate(Delegate.GPU)
            }
        }

        baseOptionBuilder.setModelAssetPath(MP_HAND_LANDMARKER_TASK)

        try {
            val optionsBuilder =
                HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(baseOptionBuilder.build())
                    .setMinHandDetectionConfidence(minConfidence)
                    .setNumHands(maxNumHands)
                    // Because the running mode is LIVE_STREAM, we always need
                    // setResultListener
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setResultListener(this::returnLivestreamResult)
            val options = optionsBuilder.build()
            handLandmarker =
                HandLandmarker.createFromOptions(context, options)
        } catch (e: IllegalStateException) {
            handLandmarkerHelperListener.onError(
                "Hand Landmarker failed to initialize. See error logs for " +
                        "details"
            )
            Log.e(
                TAG, "MediaPipe failed to load the task with error: " + e
                    .message
            )
        }
    }

    // Convert the ImageProxy to MP Image and feed it to HandlandmakerHelper.
    fun detectLiveStream(
        imageProxy: ImageProxy,
        isFrontCamera: Boolean
    ) {
        val frameTime = SystemClock.uptimeMillis()

        // Copy out RGB bits from the frame to a bitmap buffer
        val bitmapBuffer =
            Bitmap.createBitmap(
                imageProxy.width,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            )
        imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
        imageProxy.close()

        val matrix = Matrix().apply {
            // Rotate the frame received from the camera to be in the same direction as it'll be shown
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())

            // flip image if user use front camera
            if (isFrontCamera) {
                postScale(
                    -1f,
                    1f,
                    imageProxy.width.toFloat(),
                    imageProxy.height.toFloat()
                )
            }
        }
        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
            matrix, true
        )

        // Convert the input Bitmap object to an MPImage object to run inference
        val mpImage = BitmapImageBuilder(rotatedBitmap).build()

        // Run hand hand landmark using MediaPipe Hand Landmarker API
        handLandmarker?.detectAsync(mpImage, frameTime)

        // As we're using running mode LIVE_STREAM, the landmark result will
        // be returned in returnLivestreamResult function
    }

    // Return running status of HandLandmarkerhelper
    fun isClose(): Boolean {
        return handLandmarker == null
    }

    // Return the landmark result to this HandLandmarkerHelper's caller
    private fun returnLivestreamResult(
        result: HandLandmarkerResult,
        input: MPImage
    ) {
        val finishTimeMs = SystemClock.uptimeMillis()
        val inferenceTime = finishTimeMs - result.timestampMs()

        handLandmarkerHelperListener.onResults(
            ResultBundle(
                listOf(result),
                inferenceTime,
                input.height,
                input.width
            )
        )
    }

    companion object {
        val TAG = "HandLandmarkerHelper ${this.hashCode()}"
        private const val MP_HAND_LANDMARKER_TASK = "hand_landmarker.task"

        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1

        // Values representing the hand landmark types that the model can detect
        const val WRIST = 0
        const val THUMB_CMC = 1
        const val THUMB_MCP = 2
        const val THUMB_IP = 3
        const val THUMB_TIP = 4
        const val INDEX_FINGER_MCP = 5
        const val INDEX_FINGER_PIP = 6
        const val INDEX_FINGER_DIP = 7
        const val INDEX_FINGER_TIP = 8
        const val MIDDLE_FINGER_MCP = 9
        const val MIDDLE_FINGER_PIP = 10
        const val MIDDLE_FINGER_DIP = 11
        const val MIDDLE_FINGER_TIP = 12
        const val RING_FINGER_MCP = 13
        const val RING_FINGER_PIP = 14
        const val RING_FINGER_DIP = 15
        const val RING_FINGER_TIP = 16
        const val PINKY_MCP = 17
        const val PINKY_PIP = 18
        const val PINKY_DIP = 19
        const val PINKY_TIP = 20
    }

    data class ResultBundle(
        val results: List<HandLandmarkerResult>,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
    )

    interface LandmarkerListener {
        fun onError(error: String)
        fun onResults(resultBundle: ResultBundle)
    }
}
