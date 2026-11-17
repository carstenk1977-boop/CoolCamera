package com.stylecam.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.DisplayOrientedMeteringPointFactory
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.stylecam.filters.FilterRenderer
import java.util.concurrent.Executors

/**
 * Camera ACQUISITION only (no processing, no UI):
 *  - ImageAnalysis (RGBA_8888, STRATEGY_KEEP_ONLY_LATEST) feeds frames to the renderer;
 *  - ImageCapture grabs a high resolution still for photo mode.
 * There is intentionally no CameraX Preview: the filtered GL surface IS the viewfinder.
 */
class CameraController(
    private val activity: AppCompatActivity,
    private val renderer: FilterRenderer
) {
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val captureExecutor = Executors.newSingleThreadExecutor()

    var lensFront = false
        private set
    private var torchOn = false

    /** Called (main thread) after every (re)bind, e.g. so the UI can show/hide the torch button. */
    var onBound: (() -> Unit)? = null

    val hasFlash: Boolean get() = camera?.cameraInfo?.hasFlashUnit() == true
    val hasFrontCamera: Boolean get() = provider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) == true
    val hasBackCamera: Boolean get() = provider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) == true

    fun start() {
        val future = ProcessCameraProvider.getInstance(activity)
        future.addListener({
            provider = future.get()
            if (!hasBackCamera && hasFrontCamera) lensFront = true
            bind()
        }, ContextCompat.getMainExecutor(activity))
    }

    fun stop() {
        provider?.unbindAll()
        camera = null
    }

    fun switchCamera() {
        val target = !lensFront
        val ok = if (target) hasFrontCamera else hasBackCamera
        if (!ok) return
        lensFront = target
        torchOn = false
        bind()
    }

    private fun bind() {
        val p = provider ?: return
        val selector = if (lensFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

        // 16:9, ~720p analysis stream: plenty for a live effect and cheap to copy/upload.
        val analysisSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
            )
            .build()
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(analysisSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setTargetRotation(Surface.ROTATION_0)   // activity is locked to portrait
            .build()
        val mirror = lensFront
        analysis.setAnalyzer(analysisExecutor) { image: ImageProxy ->
            try {
                renderer.submitFrame(image, mirror)
            } finally {
                image.close()
            }
        }

        // Same 16:9 field of view as the viewfinder, highest resolution available.
        val captureSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
            .build()
        val capture = ImageCapture.Builder()
            .setResolutionSelector(captureSelector)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setTargetRotation(Surface.ROTATION_0)
            .build()
        imageCapture = capture

        try {
            p.unbindAll()
            camera = p.bindToLifecycle(activity, selector, analysis, capture)
            if (torchOn) camera?.cameraControl?.enableTorch(true)
        } catch (t: Throwable) {
            Log.e(TAG, "Use case binding failed", t)
            // Fallback: live view only (no photo capture) rather than a dead screen
            try {
                p.unbindAll()
                camera = p.bindToLifecycle(activity, selector, analysis)
                imageCapture = null
            } catch (t2: Throwable) {
                Log.e(TAG, "Fallback binding failed", t2)
            }
        }
        onBound?.invoke()
    }

    fun setTorch(on: Boolean) {
        torchOn = on
        camera?.cameraControl?.enableTorch(on)
    }

    fun isTorchOn(): Boolean = torchOn

    fun zoomBy(factor: Float) {
        val cam = camera ?: return
        val state = cam.cameraInfo.zoomState.value ?: return
        val target = (state.zoomRatio * factor).coerceIn(state.minZoomRatio, state.maxZoomRatio)
        cam.cameraControl.setZoomRatio(target)
    }

    /** Tap to focus + meter. x/y are view coordinates, w/h the view size. */
    @Suppress("DEPRECATION")
    fun focusAt(x: Float, y: Float, w: Int, h: Int) {
        val cam = camera ?: return
        val display = activity.windowManager.defaultDisplay
        val factory = DisplayOrientedMeteringPointFactory(display, cam.cameraInfo, w.toFloat(), h.toFloat())
        val action = FocusMeteringAction.Builder(factory.createPoint(x, y)).build()
        cam.cameraControl.startFocusAndMetering(action)
    }

    /**
     * Captures a full-resolution still, upright (and mirrored for the front camera when
     * [mirrorFront] is true, to match the viewfinder). The callback runs on a background thread.
     */
    fun takePhoto(mirrorFront: Boolean, callback: (Bitmap?) -> Unit) {
        val cap = imageCapture
        if (cap == null) {
            callback(null)
            return
        }
        cap.takePicture(captureExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val result = try {
                    val raw = image.toBitmap()
                    val rotation = image.imageInfo.rotationDegrees
                    val m = Matrix()
                    if (rotation != 0) m.postRotate(rotation.toFloat())
                    if (lensFront && mirrorFront) m.postScale(-1f, 1f)
                    if (m.isIdentity) raw else {
                        val out = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
                        if (out !== raw) raw.recycle()
                        out
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Could not decode capture", t)
                    null
                } finally {
                    image.close()
                }
                callback(result)
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Capture failed", exception)
                callback(null)
            }
        })
    }

    companion object {
        private const val TAG = "CameraController"
    }
}
