package com.example.myapplication.sensors

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Estimador de artefactos de movimiento.
 * Evalúa si el movimiento contamina la señal PPG.
 */
class MotionArtifactEstimator {

    private val motionBuffer = ArrayList<Double>(100)
    private val maxBufferSize = 100

    /**
     * Evalúa el nivel de artefacto de movimiento.
     * Retorna un score de 0.0 (sin movimiento) a 1.0 (movimiento extremo).
     */
    fun evaluate(sample: MotionSample): MotionArtifactResult {
        val accelMag = sample.accelerationMagnitude
        val gravity = 9.81f

        // 1. Desviación de gravedad (movimiento lineal)
        val gravityDeviation = abs(accelMag - gravity) / gravity

        // 2. Rotación (si hay giroscopio)
        val rotationScore = sample.rotationMagnitude?.let { rot ->
            (rot / 2.0f).coerceAtMost(1.0f).toDouble()
        } ?: 0.0

        // 3. Score combinado
        val combinedScore = (gravityDeviation * 0.7 + rotationScore * 0.3).coerceIn(0.0, 1.0)

        // 4. Historial temporal para detectar golpes súbitos
        motionBuffer.add(combinedScore)
        if (motionBuffer.size > maxBufferSize) {
            motionBuffer.removeAt(0)
        }

        // 5. Detección de picos de movimiento (golpes)
        val suddenMotion = if (motionBuffer.size >= 5) {
            val recent = motionBuffer.takeLast(5)
            val avgBefore = motionBuffer.dropLast(5).takeLast(10).average()
            val avgRecent = recent.average()
            avgRecent - avgBefore > 0.3
        } else {
            false
        }

        // 6. Energía de movimiento en ventana
        val motionEnergy = if (motionBuffer.size >= 20) {
            val recent = motionBuffer.takeLast(20)
            sqrt(recent.map { it * it }.average())
        } else {
            combinedScore
        }

        return MotionArtifactResult(
            motionScore = combinedScore,
            motionEnergy = motionEnergy,
            suddenMotion = suddenMotion,
            isAcceptable = combinedScore < 0.15 && !suddenMotion
        )
    }

    data class MotionArtifactResult(
        val motionScore: Double,
        val motionEnergy: Double,
        val suddenMotion: Boolean,
        val isAcceptable: Boolean
    )

    fun reset() {
        motionBuffer.clear()
    }
}
