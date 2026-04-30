package com.example.myapplication.signal

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Detector de artefactos por movimiento utilizando acelerómetro y giroscopio.
 * Ayuda a invalidar tramos de señal PPG contaminados por movimiento físico.
 */
class MotionArtifactDetector(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var lastAcceleration = 0f
    private var currentAcceleration = 0f
    private var accelerationMagnitude = 0f

    private var _isMovingSignificant = false
    val isMovingSignificant: Boolean get() = _isMovingSignificant

    fun start() {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            lastAcceleration = currentAcceleration
            currentAcceleration = sqrt(x * x + y * y + z * z)
            val delta = currentAcceleration - lastAcceleration
            accelerationMagnitude = accelerationMagnitude * 0.9f + delta * 0.1f

            // Umbral de movimiento significativo para PPG (muy sensible)
            _isMovingSignificant = Math.abs(accelerationMagnitude) > 0.15f
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
