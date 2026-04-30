package com.example.myapplication.signal

import kotlin.math.abs

/**
 * Motor de calidad de señal (SQI).
 * Evalúa múltiples factores para determinar si la señal es apta para diagnóstico.
 */
class PpgSignalQualityEngine {

    data class SqiResult(
        val totalSqi: Double,
        val perfusionIndex: Double,
        val isStable: Boolean,
        val state: MeasurementState
    )

    private var lastRedDc: Double = 0.0

    fun compute(
        features: PpgFrameAnalyzer.FrameFeatures,
        acRed: Double,
        acGreen: Double,
        isPeriodical: Boolean
    ): SqiResult {
        // 1. Perfusion Index (PI) = (AC / DC) * 100
        val piRed = if (features.redMean > 0) (acRed / features.redMean) * 100.0 else 0.0
        val piGreen = if (features.greenMean > 0) (acGreen / features.greenMean) * 100.0 else 0.0
        
        // 2. Estabilidad DC (Detección de artefactos de movimiento brusco)
        val dcChange = if (lastRedDc > 0) abs(features.redMean - lastRedDc) / lastRedDc else 0.0
        lastRedDc = features.redMean
        val isStable = dcChange < 0.05 // Menos del 5% de cambio entre frames

        // 3. Cálculo de SQI Compuesto
        var score = 0.0
        
        // Peso Perfusion (30%)
        if (piRed in 0.05..5.0) score += 0.3
        
        // Peso Estabilidad (30%)
        if (isStable) score += 0.3
        
        // Peso Periodicidad (40%)
        if (isPeriodical) score += 0.4

        // Determinación del Estado
        val state = when {
            features.clippedPixelRatio > 0.3 -> MeasurementState.SATURATED
            features.darkPixelRatio > 0.8 -> MeasurementState.SEARCHING_FINGER
            !isStable -> MeasurementState.MOTION_ARTIFACT
            score < 0.4 -> MeasurementState.LOW_QUALITY
            score < 0.7 -> MeasurementState.LOCKING_SIGNAL
            else -> MeasurementState.MEASURING
        }

        return SqiResult(
            totalSqi = score,
            perfusionIndex = piRed,
            isStable = isStable,
            state = state
        )
    }
}
