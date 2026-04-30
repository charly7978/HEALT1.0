package com.example.myapplication.signal

import kotlin.math.max

/**
 * Pipeline de procesamiento de señal PPG: Filtrado, AC/DC y Calidad.
 */
class PpgSignalProcessor(private val samplingRate: Double) {

    private val bandpassFilter = PPGBandpassFilter(samplingRate)
    private val dcBuffer = mutableListOf<Double>()
    private val acBuffer = mutableListOf<Double>()
    private val bufferSize = (samplingRate * 3).toInt() // 3 segundos para métricas estables

    data class SignalResult(
        val rawValue: Double,
        val filteredValue: Double,
        val normalizedValue: Double,
        val ac: Double,
        val dc: Double,
        val perfusionIndex: Double,
        val sqi: Double
    )

    fun process(value: Double): SignalResult {
        val filtered = bandpassFilter.filter(value)

        dcBuffer.add(value)
        if (dcBuffer.size > bufferSize) dcBuffer.removeAt(0)
        
        acBuffer.add(filtered)
        if (acBuffer.size > bufferSize) acBuffer.removeAt(0)

        val dc = if (dcBuffer.isNotEmpty()) dcBuffer.average() else value
        val ac = if (acBuffer.isNotEmpty()) {
            acBuffer.max() - acBuffer.min()
        } else 0.0

        val pi = if (dc > 1.0) (ac / dc) * 100.0 else 0.0

        // Normalización 0..1 para canvas
        val minAC = acBuffer.minOrNull() ?: -1.0
        val maxAC = acBuffer.maxOrNull() ?: 1.0
        val range = max(0.01, maxAC - minAC)
        val normalized = (filtered - minAC) / range

        val sqi = calculateSQI(ac, dc, pi)

        return SignalResult(value, filtered, normalized, ac, dc, pi, sqi)
    }

    private fun calculateSQI(ac: Double, dc: Double, pi: Double): Double {
        if (dc < 5 || ac < 0.05) return 0.0
        var score = 0.0
        if (pi in 0.05..15.0) score += 50.0
        if (ac > 0.2) score += 30.0
        if (dc in 20.0..250.0) score += 20.0
        return score
    }

    fun reset() {
        dcBuffer.clear()
        acBuffer.clear()
    }
}
