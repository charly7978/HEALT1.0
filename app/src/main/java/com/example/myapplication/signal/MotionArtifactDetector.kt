package com.example.myapplication.signal

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Detector de artefactos de movimiento usando el acelerómetro del dispositivo.
 * Proporciona un score de movimiento en tiempo real para invalidar segmentos de PPG.
 */
class MotionArtifactDetector(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    
    private var motionScore = 0.0
    private val alpha = 0.1 // Filtro paso bajo para el score

    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    fun getMotionScore(): Double = motionScore

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            if (lastX != 0f || lastY != 0f || lastZ != 0f) {
                val delta = sqrt(
                    (x - lastX).let { it * it } +
                    (y - lastY).let { it * it } +
                    (z - lastZ).let { it * it }
                )
                // Acumular movimiento con decaimiento
                motionScore = motionScore * (1.0 - alpha) + (delta.toDouble() * alpha)
            }

            lastX = x
            lastY = y
            lastZ = z
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun reset() {
        motionScore = 0.0
        lastX = 0f
        lastY = 0f
        lastZ = 0f
    }
}
