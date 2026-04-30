package com.example.myapplication.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Controlador de sensores inerciales para detección de movimiento.
 * Usa acelerómetro, giroscopio y rotation vector si están disponibles.
 */
class MotionSensorController(context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val _motionSample = MutableStateFlow<MotionSample?>(null)
    val motionSample = _motionSample.asStateFlow()

    private val isRunning = AtomicBoolean(false)

    private val accelerometerListener = object : SensorEventListener {
        private var lastX: Float = 0f
        private var lastY: Float = 0f
        private var lastZ: Float = 0f
        private var lastGyroX: Float? = null
        private var lastGyroY: Float? = null
        private var lastGyroZ: Float? = null
        private var lastRotX: Float? = null
        private var lastRotY: Float? = null
        private var lastRotZ: Float? = null
        private var lastRotW: Float? = null

        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null || !isRunning.get()) return

            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    lastX = event.values[0]
                    lastY = event.values[1]
                    lastZ = event.values[2]
                }
                Sensor.TYPE_GYROSCOPE -> {
                    lastGyroX = event.values[0]
                    lastGyroY = event.values[1]
                    lastGyroZ = event.values[2]
                }
                Sensor.TYPE_ROTATION_VECTOR -> {
                    lastRotX = event.values[0]
                    lastRotY = event.values[1]
                    lastRotZ = event.values[2]
                    lastRotW = if (event.values.size > 3) event.values[3] else 0f
                }
            }

            // Emitir muestra cuando tenemos acelerómetro (mínimo requerido)
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                _motionSample.value = MotionSample(
                    timestampNs = event.timestamp,
                    accelerometerX = lastX,
                    accelerometerY = lastY,
                    accelerometerZ = lastZ,
                    gyroscopeX = lastGyroX,
                    gyroscopeY = lastGyroY,
                    gyroscopeZ = lastGyroZ,
                    rotationVectorX = lastRotX,
                    rotationVectorY = lastRotY,
                    rotationVectorZ = lastRotZ,
                    rotationVectorW = lastRotW
                )
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // No requerido para este caso de uso
        }
    }

    fun start() {
        if (isRunning.getAndSet(true)) return

        accelerometer?.let {
            sensorManager.registerListener(
                accelerometerListener,
                it,
                SensorManager.SENSOR_DELAY_FASTEST,
                mainHandler
            )
        }

        gyroscope?.let {
            sensorManager.registerListener(
                accelerometerListener,
                it,
                SensorManager.SENSOR_DELAY_FASTEST,
                mainHandler
            )
        }

        rotationVector?.let {
            sensorManager.registerListener(
                accelerometerListener,
                it,
                SensorManager.SENSOR_DELAY_FASTEST,
                mainHandler
            )
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return

        sensorManager.unregisterListener(accelerometerListener)
        _motionSample.value = null
    }
}
