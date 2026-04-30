package com.example.myapplication.signal

import kotlin.math.abs

/**
 * Evalúa la calidad de la señal PPG basándose en múltiples factores.
 */
class PpgSignalQuality {

    data class QualityResult(
        val sqi: Double,
        val snr: Double,
        val isPhysiological: Boolean,
        val state: PpgValidityState,
        val reason: String = ""
    )

    enum class PpgValidityState {
        RAW_OPTICAL_ONLY,
        NO_PHYSIOLOGICAL_SIGNAL,
        PPG_CANDIDATE,
        PPG_VALID,
        BIOMETRIC_VALID
    }

    fun analyze(
        sample: PpgSample,
        filteredBuffer: List<Double>,
        ac: Double,
        dc: Double
    ): QualityResult {
        // 1. Red Dominance Check (Fisiología básica)
        val redDominance = if (sample.green > 0) sample.red / sample.green else 0.0
        if (redDominance < 1.5) {
            return QualityResult(0.0, 0.0, false, PpgValidityState.RAW_OPTICAL_ONLY, "No red dominance")
        }

        // 2. Perfusion Index (PI = AC/DC * 100)
        val pi = if (dc > 0) (ac / dc) * 100.0 else 0.0
        if (pi < 0.05) {
            return QualityResult(pi, 0.0, false, PpgValidityState.NO_PHYSIOLOGICAL_SIGNAL, "Low perfusion")
        }

        // 3. Clipping check
        if (sample.diagnostics.clippingHigh || sample.diagnostics.clippingLow) {
            return QualityResult(10.0, 0.0, false, PpgValidityState.NO_PHYSIOLOGICAL_SIGNAL, "Signal clipping")
        }

        // 4. SNR básica (Relación entre varianza de señal filtrada y ruido)
        // Por ahora una estimación simple basada en la amplitud de la señal filtrada
        val snr = ac / (sample.roiStats.stdDevRed + 0.0001)

        var state = PpgValidityState.PPG_CANDIDATE
        var sqi = 50.0

        if (pi in 0.2..10.0 && snr > 2.0) {
            state = PpgValidityState.PPG_VALID
            sqi = 80.0
        }

        if (sqi >= 80.0 && filteredBuffer.size > 50) {
            // Aquí se podrían añadir chequeos de autocorrelación
            state = PpgValidityState.BIOMETRIC_VALID
            sqi = 95.0
        }

        return QualityResult(sqi, snr, state >= PpgValidityState.PPG_CANDIDATE, state)
    }
}
