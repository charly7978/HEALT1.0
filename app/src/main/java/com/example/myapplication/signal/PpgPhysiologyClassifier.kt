package com.example.myapplication.signal

/**
 * Clasificador que determina si una señal óptica tiene origen fisiológico (PPG humano).
 */
class PpgPhysiologyClassifier {

    private var consistentPpgFrames = 0
    private val REQUIRED_STABLE_FRAMES = 90 // ~3 segundos de señal clara

    fun classify(
        sample: PpgSample, 
        sqi: Double, 
        pi: Double
    ): PpgSignalQuality.PpgValidityState {
        
        // 1. Verificar Saturación/Oscuridad
        if (sample.diagnostics.clippingHigh) return PpgSignalQuality.PpgValidityState.NO_PHYSIOLOGICAL_SIGNAL
        if (sample.diagnostics.lowLight) return PpgSignalQuality.PpgValidityState.RAW_OPTICAL_ONLY

        // 2. Verificar Dominancia Roja (Dedo sobre flash)
        val redDominance = if (sample.green > 0) sample.red / sample.green else 0.0
        if (redDominance < 2.0) {
            consistentPpgFrames = 0
            return PpgSignalQuality.PpgValidityState.NO_PHYSIOLOGICAL_SIGNAL
        }

        // 3. Análisis de Variación (Pulsatilidad)
        // Una sábana roja tiene redDominance alto pero PI casi cero y SQI bajo.
        if (pi < 0.05 || sqi < 20.0) {
            consistentPpgFrames = 0
            return PpgSignalQuality.PpgValidityState.RAW_OPTICAL_ONLY
        }

        // 4. Validación de Estabilidad
        if (sqi > 60.0 && pi > 0.2) {
            consistentPpgFrames++
        } else {
            consistentPpgFrames = (consistentPpgFrames - 2).coerceAtLeast(0)
        }

        return when {
            consistentPpgFrames > REQUIRED_STABLE_FRAMES -> PpgSignalQuality.PpgValidityState.BIOMETRIC_VALID
            consistentPpgFrames > 30 -> PpgSignalQuality.PpgValidityState.PPG_VALID
            else -> PpgSignalQuality.PpgValidityState.PPG_CANDIDATE
        }
    }

    fun reset() {
        consistentPpgFrames = 0
    }
}
