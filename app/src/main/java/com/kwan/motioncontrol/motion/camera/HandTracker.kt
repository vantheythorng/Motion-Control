package com.kwan.motioncontrol.motion.camera

import android.content.Context
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import com.kwan.motioncontrol.motion.HandFrame
import com.kwan.motioncontrol.motion.HandPoint
import com.kwan.motioncontrol.motion.HandPoint3
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.hypot
import kotlin.math.max

/**
 * Binds the front camera to an [ImageAnalysis] use case and runs the MediaPipe Gesture Recognizer
 * on each frame. [onFrame] and [onError] are called on MediaPipe's result thread.
 *
 * Two trackers exist (the service's and Test mode's), sharing the process-wide camera provider,
 * so each only ever unbinds its own use cases.
 */
class HandTracker(
    private val context: Context,
    private val onFrame: (HandFrame) -> Unit,
    private val onError: (Throwable) -> Unit,
    private val onDelegateChosen: (Delegate) -> Unit = {},
    /** Called on the main thread once the camera is actually bound. */
    private val onBound: () -> Unit = {},
) {
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var recognizer: GestureRecognizer? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var boundUseCases: Array<UseCase> = emptyArray()

    // Touched only on the analysis executor / MediaPipe result thread.
    @Volatile private var lastHandAt = 0L
    private var lastFrameAt = 0L
    private var lastTimestamp = 0L
    /** Lens focal length divided by sensor width; null when the camera doesn't report them. */
    @Volatile private var focalPerSensorWidth: Float? = null

    val isRunning: Boolean get() = analysis != null

    /** Binds the camera to [owner]. Safe to call repeatedly. Main thread only. */
    fun start(owner: LifecycleOwner) {
        if (analysis != null) return
        lastHandAt = SystemClock.uptimeMillis()
        analysisExecutor.execute { ensureRecognizer() }

        val useCase = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            TARGET_RESOLUTION,
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                        )
                    )
                    .build()
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { it.setAnalyzer(analysisExecutor, ::analyze) }
        analysis = useCase
        val useCases = arrayOf<UseCase>(useCase)

        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            // stop() may have been called while the provider was loading.
            if (analysis !== useCase) return@addListener
            try {
                val provider = future.get()
                cameraProvider = provider
                val camera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, *useCases)
                boundUseCases = useCases
                onBound()
                focalPerSensorWidth = readFocalPerSensorWidth(camera.cameraInfo)
            } catch (e: Exception) {
                analysis = null
                onError(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /** Releases the camera (the privacy indicator turns off) but keeps the recognizer. Main thread only. */
    fun stop() {
        analysis?.clearAnalyzer()
        analysis = null
        cameraProvider?.unbind(*boundUseCases)
        boundUseCases = emptyArray()
    }

    /** Main thread only. The tracker can't be restarted afterwards. */
    fun close() {
        stop()
        analysisExecutor.execute {
            recognizer?.close()
            recognizer = null
        }
        analysisExecutor.shutdown()
    }

    private fun ensureRecognizer() {
        if (recognizer != null) return
        for (delegate in listOf(Delegate.GPU, Delegate.CPU)) {
            try {
                recognizer = GestureRecognizer.createFromOptions(context, options(delegate))
                Log.i(TAG, "Gesture recognizer ready on $delegate")
                onDelegateChosen(delegate)
                return
            } catch (e: Exception) {
                Log.w(TAG, "Gesture recognizer failed on $delegate", e)
            }
        }
        onError(IllegalStateException("Gesture recognizer could not be created"))
    }

    private fun options(delegate: Delegate) = GestureRecognizer.GestureRecognizerOptions.builder()
        .setBaseOptions(
            BaseOptions.builder().setModelAssetPath(MODEL_ASSET).setDelegate(delegate).build()
        )
        .setRunningMode(RunningMode.LIVE_STREAM)
        .setNumHands(1)
        .setMinHandDetectionConfidence(0.5f)
        // Lower than the defaults so tracking holds on through motion blur during a swipe; the
        // engine's arming step filters out spurious hands.
        .setMinHandPresenceConfidence(0.3f)
        .setMinTrackingConfidence(0.3f)
        .setResultListener { result, image -> onResult(result, image.width, image.height) }
        .setErrorListener { e -> onError(e) }
        .build()

    private fun analyze(proxy: ImageProxy) {
        proxy.use {
            val rec = recognizer ?: return
            val now = SystemClock.uptimeMillis()
            // ~15 fps while a hand was seen recently, ~3 fps when idle, to save battery and heat.
            val interval = if (now - lastHandAt > IDLE_AFTER_MS) IDLE_INTERVAL_MS else ACTIVE_INTERVAL_MS
            if (now - lastFrameAt < interval) return
            lastFrameAt = now

            val bitmap = uprightMirrored(proxy)
            // LIVE_STREAM mode requires strictly increasing timestamps.
            val timestamp = max(now, lastTimestamp + 1)
            lastTimestamp = timestamp
            rec.recognizeAsync(BitmapImageBuilder(bitmap).build(), timestamp)
        }
    }

    /**
     * Analysis frames come in sensor orientation and unmirrored. Rotate them upright and mirror
     * them so landmark "up" is the user's up and "left" is the user's left.
     */
    private fun uprightMirrored(proxy: ImageProxy): Bitmap {
        val src = proxy.toBitmap()
        val matrix = Matrix().apply {
            postRotate(proxy.imageInfo.rotationDegrees.toFloat())
            postScale(-1f, 1f)
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, false)
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun readFocalPerSensorWidth(cameraInfo: CameraInfo): Float? {
        val info = Camera2CameraInfo.from(cameraInfo)
        val focalMm = info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            ?.firstOrNull()
        val sensorMm = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        if (focalMm == null || sensorMm == null || sensorMm.width <= 0f) return null
        Log.i(TAG, "Front camera: focal $focalMm mm, sensor ${sensorMm.width}x${sensorMm.height} mm")
        return focalMm / sensorMm.width
    }

    /**
     * Pinhole estimate: distance = focal length (px) × real size / image size (px). MediaPipe's
     * world landmarks give the palm's real size in metres, aligned with the image axes, so the
     * palm's tilt cancels out. Accuracy depends on how close the user's hand is to the model's
     * average hand (roughly ±20 %).
     */
    private fun estimateDistanceCm(result: GestureRecognizerResult, width: Int, height: Int): Float? {
        val ratio = focalPerSensorWidth ?: return null
        val image = result.landmarks().firstOrNull() ?: return null
        val world = result.worldLandmarks().firstOrNull() ?: return null
        // The sensor's long side maps to the frame's long side (the frame is rotated upright).
        val focalPx = ratio * max(width, height)
        var worldMetres = 0.0
        var imagePx = 0.0
        for ((a, b) in PALM_SEGMENTS) {
            worldMetres += hypot(world[a].x() - world[b].x(), world[a].y() - world[b].y())
            imagePx += hypot((image[a].x() - image[b].x()) * width, (image[a].y() - image[b].y()) * height)
        }
        if (imagePx < 1.0) return null
        return (focalPx * worldMetres / imagePx * 100).toFloat()
    }

    private fun onResult(result: GestureRecognizerResult, width: Int, height: Int) {
        val landmarks = result.landmarks().firstOrNull()?.map { HandPoint(it.x(), it.y()) }
        val world = result.worldLandmarks().firstOrNull()?.map { HandPoint3(it.x(), it.y(), it.z()) }
        // The frame is mirrored like a selfie, which is what MediaPipe's handedness assumes.
        val left = result.handedness().firstOrNull()?.firstOrNull()?.categoryName() == "Left"
        val top = result.gestures().firstOrNull()?.firstOrNull()
        if (landmarks != null) lastHandAt = SystemClock.uptimeMillis()
        onFrame(
            HandFrame(
                timestampMs = result.timestampMs(),
                landmarks = landmarks,
                gesture = top?.categoryName(),
                gestureScore = top?.score() ?: 0f,
                distanceCm = estimateDistanceCm(result, width, height),
                world = world,
                leftHand = left,
                aspect = width.toFloat() / height,
            )
        )
    }

    private companion object {
        const val TAG = "MotionControl"
        const val MODEL_ASSET = "gesture_recognizer.task"
        // 320x240 left the hand crop too soft to track while moving.
        val TARGET_RESOLUTION = Size(640, 480)
        const val ACTIVE_INTERVAL_MS = 66L
        const val IDLE_INTERVAL_MS = 333L
        const val IDLE_AFTER_MS = 5_000L
        /** Rigid palm segments (wrist and finger knuckles) used for the distance estimate. */
        val PALM_SEGMENTS = listOf(0 to 5, 0 to 9, 0 to 17, 5 to 9, 9 to 13, 13 to 17, 5 to 17)
    }
}
