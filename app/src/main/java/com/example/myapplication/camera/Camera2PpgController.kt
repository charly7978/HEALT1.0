package com.example.myapplication.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.example.myapplication.signal.PpgFrameAnalyzer
import com.example.myapplication.signal.PpgFrame
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * Controlador avanzado de Camera2 optimizado para captura PPG continua con FLASH estable.
 * Usa PpgFrameAnalyzer para extraer datos ópticos completos.
 */
class Camera2PpgController(private val context: Context) {

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private val cameraOpenCloseLock = Semaphore(1)
    private var currentCameraId: String = "unknown"
    private val frameAnalyzer = PpgFrameAnalyzer()

    // Salida: PpgFrame como única estructura de datos PPG
    var onFrameAvailable: ((PpgFrame) -> Unit)? = null
    
    private var lastFrameTimestampNs: Long = 0
    private var actualFps: Double = 0.0
    private var exposureTimeNs: Long? = null
    private var iso: Int? = null
    private var frameDurationNs: Long? = null

    @SuppressLint("MissingPermission")
    fun start() {
        if (cameraDevice != null) return 

        startBackgroundThread()
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: return
            currentCameraId = cameraId

            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) return

            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraOpenCloseLock.release()
                    cameraDevice = camera
                    setupSession(manager, cameraId)
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
                    Log.e("Camera2PpgController", "Camera error: $error")
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e("Camera2PpgController", "Start error", e)
            cameraOpenCloseLock.release()
        }
    }

    private fun setupSession(manager: CameraManager, cameraId: String) {
        val device = cameraDevice ?: return
        
        imageReader = ImageReader.newInstance(320, 240, ImageFormat.YUV_420_888, 3)
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                val now = image.timestamp
                if (lastFrameTimestampNs != 0L) {
                    val frameDuration = now - lastFrameTimestampNs
                    if (frameDuration > 0) {
                        actualFps = 1_000_000_000.0 / frameDuration
                        frameDurationNs = frameDuration
                    }
                }
                lastFrameTimestampNs = now
                
                try {
                    val frame = frameAnalyzer.analyze(
                        image = image,
                        fps = actualFps,
                        exposureTimeNs = exposureTimeNs,
                        iso = iso,
                        frameDurationNs = frameDurationNs,
                        externalMotionScore = 0.0 // Se actualiza externamente con acelerómetro
                    )
                    onFrameAvailable?.invoke(frame)
                } catch (e: Exception) {
                    Log.e("Camera2PpgController", "Analysis error", e)
                } finally {
                    image.close()
                }
            }
        }, backgroundHandler)

        val surface = imageReader?.surface ?: return
        val requestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        requestBuilder.addTarget(surface)

        // Intentar configuración manual si está disponible
        val characteristics = manager.getCameraCharacteristics(currentCameraId)
        val supportsManualSensor = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.contains(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
        ) == true

        if (supportsManualSensor) {
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            requestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, 10_000_000L) // 10ms
            requestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, 800) // ISO 800
            exposureTimeNs = 10_000_000L
            iso = 800
        } else {
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            requestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true)
        }

        requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
        requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
        requestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f)

        val surfaceList = listOf(surface)
        
        @Suppress("DEPRECATION")
        device.createCaptureSession(surfaceList, object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                captureSession = session
                try {
                    session.setRepeatingRequest(requestBuilder.build(), null, backgroundHandler)
                    Log.d("Camera2PpgController", "Session configured successfully")
                } catch (e: Exception) {
                    Log.e("Camera2PpgController", "Repeating request error", e)
                }
            }
            override fun onConfigureFailed(session: CameraCaptureSession) {
                Log.e("Camera2PpgController", "Session configuration failed")
            }
        }, backgroundHandler)
    }

    fun stop() {
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) return
            captureSession?.stopRepeating()
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
            stopBackgroundThread()
        } catch (e: Exception) {
            Log.e("Camera2PpgController", "Stop error", e)
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    private fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("PpgCameraThread").apply { start() }
            backgroundHandler = Handler(backgroundThread!!.looper)
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        backgroundThread = null
        backgroundHandler = null
    }
}
