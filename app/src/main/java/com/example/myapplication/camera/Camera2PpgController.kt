package com.example.myapplication.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.example.myapplication.signal.PpgFrame
import com.example.myapplication.signal.PpgFrameAnalyzer
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * Controlador avanzado de Camera2 optimizado para captura PPG continua.
 */
class Camera2PpgController(private val context: Context) {

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private val cameraOpenCloseLock = Semaphore(1)
    private val frameAnalyzer = PpgFrameAnalyzer()

    var onFrameAvailable: ((PpgFrame) -> Unit)? = null
    
    private var lastFrameTimestampNs: Long = 0
    private var actualFps: Double = 0.0

    @SuppressLint("MissingPermission")
    fun start() {
        startBackgroundThread()
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: return

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
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e("Camera2PpgController", "Start error", e)
        }
    }

    private fun setupSession(manager: CameraManager, cameraId: String) {
        val device = cameraDevice ?: return
        val characteristics = manager.getCameraCharacteristics(cameraId)

        imageReader = ImageReader.newInstance(320, 240, ImageFormat.YUV_420_888, 3)
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                val now = image.timestamp
                if (lastFrameTimestampNs != 0L) {
                    actualFps = 1_000_000_000.0 / (now - lastFrameTimestampNs)
                }
                lastFrameTimestampNs = now
                
                val ppgFrame = frameAnalyzer.analyze(image, actualFps)
                onFrameAvailable?.invoke(ppgFrame)
                image.close()
            }
        }, backgroundHandler)

        val surface = imageReader?.surface ?: return
        val requestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        requestBuilder.addTarget(surface)

        // Configuración crítica: Flash y control manual
        requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
        
        // Intentar fijar exposición para evitar fluctuaciones
        requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        requestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true)
        
        device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                captureSession = session
                try {
                    session.setRepeatingRequest(requestBuilder.build(), null, backgroundHandler)
                } catch (e: Exception) {
                    Log.e("Camera2PpgController", "Repeating request error", e)
                }
            }
            override fun onConfigureFailed(session: CameraCaptureSession) {}
        }, backgroundHandler)
    }

    fun stop() {
        cameraOpenCloseLock.acquire()
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
        stopBackgroundThread()
        cameraOpenCloseLock.release()
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("PpgCameraThread").apply { start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        backgroundThread = null
        backgroundHandler = null
    }
}
