package com.example.myapplication.signal

import kotlin.math.abs

/**
 * Clasificador que determina si una señal óptica tiene origen fisiológico (PPG humano).
 */
class PpgPhysiologyClassifier {

    private val windowSize = 150 // ~5 segundos a 30fps
    private val signalHistory = mutableListOf<Double>()
    
    // Contadores para estabilidad
    private var consistentPpgFrames = 0
    private val REQUIRED_STABLE_FRAMES = 90 // ~3 segundos de señal clara

    fun classify(frame: PpgFrame, sqi: Double, pi: Double): PpgValidityState {
        signalHistory.add(frame.avgGreen)
        if (signalHistory.size > windowSize) signalHistory.removeAt(0)

        // 1. Verificar Saturación/Oscuridad
        if (frame.saturationRatio > 0.2) return PpgValidityState.SATURATED
        if (frame.avgRed < 20 && frame.avgGreen < 10) return PpgValidityState.MEASURING_RAW_OPTICAL

        // 2. Verificar Dominancia Roja (Dedo sobre flash)
        // Un objeto rojo también tiene dominancia, por eso no es suficiente.
        if (frame.redDominance < 2.0) {
            consistentPpgFrames = 0
            return PpgValidityState.NO_PPG_PHYSIOLOGICAL_SIGNAL
        }

        // 3. Análisis de Variación (Pulsatilidad)
        // Una sábana roja tiene redDominance alto pero PI (Perfusion Index) casi cero y SQI bajo.
        if (pi < 0.05 || sqi < 20.0) {
            consistentPpgFrames = 0
            return if (frame.redDominance > 5.0) PpgValidityState.SEARCHING_PPG 
                   else PpgValidityState.NO_PPG_PHYSIOLOGICAL_SIGNAL
        }

        // 4. Validación de Estabilidad
        if (sqi > 60.0 && pi > 0.2) {
            consistentPpgFrames++
        } else {
            consistentPpgFrames = (consistentPpgFrames - 2).coerceAtLeast(0)
        }

        return when {
            consistentPpgFrames > REQUIRED_STABLE_FRAMES -> PpgValidityState.PPG_VALID
            consistentPpgFrames > 30 -> PpgValidityState.PPG_CANDIDATE
            else -> PpgValidityState.SEARCHING_PPG
        }
    }

    fun reset() {
        signalHistory.clear()
        consistentPpgFrames = 0
    }
}
