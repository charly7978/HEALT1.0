package com.example.myapplication.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.example.myapplication.ppg.CameraFrame
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * Controlador avanzado de Camera2 optimizado para captura PPG continua con FLASH estable.
 */
class Camera2PpgController(private val context: Context) {

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private val cameraOpenCloseLock = Semaphore(1)
    private var currentCameraId: String = "unknown"

    // Usamos CameraFrame como tipo de salida para el pipeline refactorizado
    var onFrameAvailable: ((CameraFrame) -> Unit)? = null
    
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
                    val frame = extractCameraFrame(image, actualFps)
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

        device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                captureSession = session
                try {
                    session.setRepeatingRequest(requestBuilder.build(), null, backgroundHandler)
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

    /**
     * Extrae CameraFrame de Image YUV.
     */
    private fun extractCameraFrame(image: Image, fps: Double): CameraFrame {
        val width = image.width
        val height = image.height
        
        // ROI central del 15%
        val roiWidth = (width * 0.15).toInt()
        val roiHeight = (height * 0.15).toInt()
        val left = (width - roiWidth) / 2
        val top = (height - roiHeight) / 2

        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val yRowStride = image.planes[0].rowStride
        val uvRowStride = image.planes[1].rowStride
        val uvPixelStride = image.planes[1].pixelStride

        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var saturatedCount = 0
        var darkCount = 0
        val totalPixels = roiWidth * roiHeight

        // Buffers para cálculo AC/DC
        val redValues = ArrayList<Double>(totalPixels)
        val greenValues = ArrayList<Double>(totalPixels)
        val blueValues = ArrayList<Double>(totalPixels)

        for (y in top until (top + roiHeight)) {
            for (x in left until (left + roiWidth)) {
                val yIndex = y * yRowStride + x
                val uvIndex = (y / 2) * uvRowStride + (x / 2) * uvPixelStride

                val yVal = (yBuffer[yIndex].toInt() and 0xFF)
                val uVal = (uBuffer[uvIndex].toInt() and 0xFF) - 128
                val vVal = (vBuffer[uvIndex].toInt() and 0xFF) - 128

                // Conversión YUV a RGB (BT.601)
                val r = (yVal + 1.370705 * vVal).coerceIn(0.0, 255.0)
                val g = (yVal - 0.337633 * uVal - 0.698001 * vVal).coerceIn(0.0, 255.0)
                val b = (yVal + 1.732446 * uVal).coerceIn(0.0, 255.0)

                sumR += r
                sumG += g
                sumB += b

                redValues.add(r)
                greenValues.add(g)
                blueValues.add(b)

                if (r > 250.0) saturatedCount++
                if (r < 5.0) darkCount++
            }
        }

        val redMean = sumR / totalPixels
        val greenMean = sumG / totalPixels
        val blueMean = sumB / totalPixels

        // Calcular AC/DC
        val redAcDc = calculateAcDc(redValues)
        val greenAcDc = calculateAcDc(greenValues)
        val blueAcDc = calculateAcDc(blueValues)

        return CameraFrame(
            timestampNs = image.timestamp,
            width = width,
            height = height,
            format = image.format,
            cameraId = currentCameraId,
            exposureTimeNs = exposureTimeNs,
            iso = iso,
            frameDurationNs = frameDurationNs,
            redMean = redMean,
            greenMean = greenMean,
            blueMean = blueMean,
            redAcDc = redAcDc,
            greenAcDc = greenAcDc,
            blueAcDc = blueAcDc,
            clipHighRatio = saturatedCount.toDouble() / totalPixels,
            clipLowRatio = darkCount.toDouble() / totalPixels,
            roiCoverage = (roiWidth * roiHeight).toDouble() / (width * height)
        )
    }

    /**
     * Calcula ratio AC/DC de un canal.
     */
    private fun calculateAcDc(values: ArrayList<Double>): Double {
        if (values.isEmpty()) return 0.0
        val dc = values.average()
        val ac = (values.maxOrNull()!! - values.minOrNull()!!) / 2.0
        return if (dc > 0) ac / dc else 0.0
    }
}
