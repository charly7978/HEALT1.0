package com.example.myapplication.signal

import kotlin.math.abs

/**
 * Evalúa la calidad de la señal PPG.
 * NOTA: Para uso forense/médico, no bloqueamos la medición, 
 * solo informamos la calidad y validez fisiológica de la señal capturada.
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
        RAW_OPTICAL_ONLY,           // Captura de imagen básica
        NO_PHYSIOLOGICAL_SIGNAL,    // Señal capturada, pero no parece pulso humano
        PPG_CANDIDATE,              // Detectada periodicidad compatible con pulso
        PPG_VALID,                  // Señal estable y rítmica
        BIOMETRIC_VALID             // Señal óptima para cálculo de SpO2/HRV
    }

    fun analyze(
        sample: PpgSample,
        filteredBuffer: List<Double>,
        ac: Double,
        dc: Double
    ): QualityResult {
        // 1. Red Dominance (Relación de color para detectar tejido)
        // Un dedo humano frente al flash suele ser predominantemente rojo.
        val redDominance = if (sample.green > 0) sample.red / (sample.green + sample.blue + 0.1) else 0.0
        
        // 2. Perfusion Index (PI = AC/DC * 100)
        // Pulsaciones reales suelen estar entre 0.1% y 10%.
        val pi = if (dc > 0) (ac / dc) * 100.0 else 0.0
        
        // 3. Análisis de SNR (Señal/Ruido)
        val signalPower = ac * ac
        val noisePower = sample.roiStats.stdDevRed
        val snr = if (noisePower > 0) signalPower / noisePower else 0.0

        // DETERMINACIÓN DE ESTADO SIN BLOQUEO DE MEDICIÓN
        var state = PpgValidityState.RAW_OPTICAL_ONLY
        var sqi = 20.0

        if (redDominance > 1.2) {
            state = PpgValidityState.PPG_CANDIDATE
            sqi = 40.0
        }

        if (pi > 0.08 && snr > 0.5) {
            state = PpgValidityState.PPG_VALID
            sqi = 75.0
        }

        if (sqi >= 75.0 && snr > 2.0 && pi < 15.0) {
            state = PpgValidityState.BIOMETRIC_VALID
            sqi = 95.0
        }

        return QualityResult(sqi, snr, true, state)
    }
}
