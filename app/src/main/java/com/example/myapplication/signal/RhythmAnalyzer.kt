package com.example.myapplication.signal

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Analizador de ritmo cardíaco basado en intervalos RR (Peak-to-Peak).
 * Implementa métricas estándar de HRV (Heart Rate Variability) para detección de arritmias.
 */
class RhythmAnalyzer {

    enum class RhythmState {
        REGULAR,
        IRREGULAR,
        POSSIBLE_ECTOPIC_BEATS,
        POSSIBLE_AF_PATTERN_EXPERIMENTAL,
        INSUFFICIENT_DATA
    }

    data class RhythmMetrics(
        val state: RhythmState,
        val rmssd: Double,
        val sdnn: Double,
        val pnn50: Double,
        val cv: Double,
        val irregularityIndex: Double
    )

    var isArhythmic: Boolean = false
        private set

    var currentMetrics: RhythmMetrics = RhythmMetrics(RhythmState.INSUFFICIENT_DATA, 0.0, 0.0, 0.0, 0.0, 0.0)
        private set

    fun analyze(rrIntervals: List<Long>): RhythmMetrics {
        if (rrIntervals.size < 4) {
            isArhythmic = false
            return RhythmMetrics(RhythmState.INSUFFICIENT_DATA, 0.0, 0.0, 0.0, 0.0, 0.0)
        }

        val mean = rrIntervals.average()
        
        // SDNN (Desviación estándar de los intervalos RR)
        val sdnn = sqrt(rrIntervals.map { (it - mean).pow(2.0) }.sum() / rrIntervals.size)
        
        // RMSSD (Raíz cuadrada de la media de las diferencias sucesivas al cuadrado)
        var sumDiffSq = 0.0
        var nn50Count = 0
        for (i in 0 until rrIntervals.size - 1) {
            val diff = Math.abs(rrIntervals[i+1] - rrIntervals[i]).toDouble()
            sumDiffSq += diff.pow(2.0)
            if (diff > 50.0) nn50Count++
        }
        val rmssd = sqrt(sumDiffSq / (rrIntervals.size - 1))
        val pnn50 = (nn50Count.toDouble() / (rrIntervals.size - 1)) * 100.0
        
        // Coeficiente de Variación
        val cv = (sdnn / mean) * 100.0

        // Índice de irregularidad basado en RMSSD relativo
        val irregularityIndex = (rmssd / mean) * 100.0

        val state = when {
            cv > 15.0 || pnn50 > 30.0 -> RhythmState.POSSIBLE_AF_PATTERN_EXPERIMENTAL
            cv > 8.0 || rmssd > 65.0 -> RhythmState.IRREGULAR
            cv > 4.0 -> RhythmState.POSSIBLE_ECTOPIC_BEATS
            else -> RhythmState.REGULAR
        }

        isArhythmic = state != RhythmState.REGULAR && state != RhythmState.INSUFFICIENT_DATA
        currentMetrics = RhythmMetrics(state, rmssd, sdnn, pnn50, cv, irregularityIndex)
        
        return currentMetrics
    }
}
