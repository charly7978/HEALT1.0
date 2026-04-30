package com.example.myapplication.sensors

/**
 * Muestra de movimiento del dispositivo.
 * Valores provenientes de sensores inerciales reales.
 */
data class MotionSample(
    val timestampNs: Long,
    val accelerometerX: Float,
    val accelerometerY: Float,
    val accelerometerZ: Float,
    val gyroscopeX: Float?,
    val gyroscopeY: Float?,
    val gyroscopeZ: Float?,
    val rotationVectorX: Float?,
    val rotationVectorY: Float?,
    val rotationVectorZ: Float?,
    val rotationVectorW: Float?
) {
    /**
     * Magnitud de aceleración total.
     */
    val accelerationMagnitude: Float
        get() = kotlin.math.sqrt(
            accelerometerX * accelerometerX +
            accelerometerY * accelerometerY +
            accelerometerZ * accelerometerZ
        )

    /**
     * Magnitud de rotación total (si hay giroscopio).
     */
    val rotationMagnitude: Float?
        get() = if (gyroscopeX != null && gyroscopeY != null && gyroscopeZ != null) {
            kotlin.math.sqrt(
                gyroscopeX * gyroscopeX +
                gyroscopeY * gyroscopeY +
                gyroscopeZ * gyroscopeZ
            )
        } else null
}
