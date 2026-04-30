package com.example.myapplication.signal

import kotlin.math.max

class FingerDetectionEngine {

    private var stableFrameCount = 0
    private val STABILITY_THRESHOLD = 30 // ~1 segundo a 30fps

    fun evaluate(features: PPGFrameFeatures): FingerDetectionResult {
        // 1. Contact Score: Basado en dominancia roja y brillo mínimo
        val colorScore = (features.redDominance - 1.5).coerceIn(0.0, 1.0)
        val brightnessScore = (features.brightness / 100.0).coerceIn(0.0, 1.0)
        val contactScore = colorScore * 0.7 + brightnessScore * 0.3

        // 2. Saturation Score
        val saturationScore = (1.0 - features.clippedHighRatio * 2.0).coerceIn(0.0, 1.0)

        // 3. Pressure Score (Heurística: si el verde es muy bajo respecto al rojo, puede ser mucha presión)
        // O si el brillo es demasiado alto (blanqueamiento)
        val pressureScore = when {
            features.clippedHighRatio > 0.1 -> 0.2 // Saturado -> Mucha presión
            features.brightness > 220 -> 0.3 // Muy brillante -> Mucha presión
            features.redDominance > 10.0 && features.brightness < 100 -> 0.4 // Muy rojo pero oscuro -> Poca presión o tapado
            else -> 1.0
        }

        val totalScore = contactScore * 0.5 + saturationScore * 0.3 + pressureScore * 0.2

        val fingerState = when {
            contactScore < 0.4 -> FingerState.NO_FINGER
            features.brightness < 30 -> FingerState.LOW_LIGHT
            features.clippedHighRatio > 0.15 -> FingerState.SATURATED
            totalScore > 0.7 -> {
                stableFrameCount++
                if (stableFrameCount > STABILITY_THRESHOLD) FingerState.STABLE else FingerState.FINGER_DETECTED
            }
            else -> {
                stableFrameCount = max(0, stableFrameCount - 2)
                FingerState.UNSTABLE
            }
        }

        val pressureState = when {
            features.clippedHighRatio > 0.15 || features.brightness > 230 -> PressureState.TOO_HIGH
            features.redDominance > 15.0 && features.brightness < 80 -> PressureState.TOO_LOW
            else -> PressureState.OPTIMAL
        }

        if (fingerState == FingerState.NO_FINGER) {
            stableFrameCount = 0
        }

        return FingerDetectionResult(fingerState, pressureState, totalScore)
    }

    data class FingerDetectionResult(
        val state: FingerState,
        val pressure: PressureState,
        val score: Double
    )
}
