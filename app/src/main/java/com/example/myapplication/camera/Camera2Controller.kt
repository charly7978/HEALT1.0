package com.example.myapplication.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import com.example.myapplication.signal.FingerRoiExtractor
import com.example.myapplication.signal.PPGFrameFeatures
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class Camera2Controller(private val context: Context) {

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private val cameraOpenCloseLock = Semaphore(1)
    private val roiExtractor = FingerRoiExtractor()

    var onFrameProcessed: ((PPGFrameFeatures) -> Unit)? = null
    var onDiagnosticsUpdated: ((CameraDiagnostics) -> Unit)? = null

    private var lastFrameTimestampNs: Long = 0
    private var frameCount = 0
    private var droppedFrames = 0

    @SuppressLint("MissingPermission")
    fun start(cameraId: String? = null) {
        startBackgroundThread()
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val id = cameraId ?: findBestCamera(manager) ?: return
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraOpenCloseLock.release()
                    cameraDevice = camera
                    createCaptureSession(manager, id)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    cameraOpenCloseLock.release()
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    cameraOpenCloseLock.release()
                    camera.close()
                    cameraDevice = null
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e("Camera2Controller", "Error starting camera", e)
        }
    }

    private fun findBestCamera(manager: CameraManager): String? {
        return manager.cameraIdList.firstOrNull { id ->
            val characteristics = manager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            facing == CameraCharacteristics.LENS_FACING_BACK
        }
    }

    private fun createCaptureSession(manager: CameraManager, cameraId: String) {
        try {
            val device = cameraDevice ?: return
            val characteristics = manager.getCameraCharacteristics(cameraId)
            
            // Resolución optimizada para procesamiento PPG (baja para latencia, suficiente para ROI)
            imageReader = ImageReader.newInstance(320, 240, ImageFormat.YUV_420_888, 3)
            imageReader?.setOnImageAvailableListener({ reader ->
                val image = try { reader.acquireLatestImage() } catch (e: Exception) { null }
                if (image != null) {
                    val timestamp = image.timestamp
                    if (lastFrameTimestampNs != 0L) {
                        val interval = (timestamp - lastFrameTimestampNs) / 1_000_000
                        updateDiagnostics(characteristics, interval)
                    }
                    lastFrameTimestampNs = timestamp
                    
                    val features = roiExtractor.extractFeatures(image)
                    onFrameProcessed?.invoke(features)
                    image.close()
                } else {
                    droppedFrames++
                }
            }, backgroundHandler)

            val surface = imageReader?.surface ?: return
            val previewRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder.addTarget(surface)
            
            // Forzar Flash
            previewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
            
            // Intentar configuración manual para evitar variaciones de brillo que arruinan el PPG
            val aeModes = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)
            var cameraMode = CameraMode.AUTO_FALLBACK
            
            if (aeModes?.contains(CameraMetadata.CONTROL_AE_MODE_OFF) == true) {
                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                previewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, 10_000_000L) // 10ms
                previewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, 400)
                cameraMode = CameraMode.MANUAL
            } else {
                // Fallback: Bloquear AE después de un tiempo si no hay manual
                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true)
                cameraMode = CameraMode.AE_LOCKED
            }

            // Fijar FPS a 30 si es posible
            val fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            val targetFps = fpsRanges?.find { it.lower >= 30 } ?: fpsRanges?.lastOrNull()
            targetFps?.let {
                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it)
            }

            device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    try {
                        session.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler)
                    } catch (e: CameraAccessException) {
                        Log.e("Camera2Controller", "Session configuration failed", e)
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e("Camera2Controller", "Configure failed")
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e("Camera2Controller", "Error creating session", e)
        }
    }

    private fun updateDiagnostics(characteristics: CameraCharacteristics, intervalMs: Long) {
        val fps = if (intervalMs > 0) 1000.0 / intervalMs else 0.0
        val diag = CameraDiagnostics(
            torchEnabled = true,
            droppedFrames = droppedFrames,
            frameIntervalMs = intervalMs,
            actualFps = fps,
            cameraMode = if (characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)?.contains(0) == true) CameraMode.MANUAL else CameraMode.AE_LOCKED
        )
        onDiagnosticsUpdated?.invoke(diag)
    }

    fun stop() {
        try {
            cameraOpenCloseLock.acquire()
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
            stopBackgroundThread()
        } catch (e: Exception) {
            Log.e("Camera2Controller", "Error stopping camera", e)
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e("Camera2Controller", "Thread stopped with error", e)
        }
    }
}
