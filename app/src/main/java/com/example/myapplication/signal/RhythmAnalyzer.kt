package com.example.myapplication.signal

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Analizador de ritmo cardíaco basado en intervalos pulso-a-pulso.
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

    private val ppiBuffer = mutableListOf<Long>()
    private val maxBufferSize = 100

    fun addInterval(ppiMs: Long) {
        if (ppiMs in 300..2000) {
            ppiBuffer.add(ppiMs)
            if (ppiBuffer.size > maxBufferSize) ppiBuffer.removeAt(0)
        }
    }

    fun analyze(): RhythmMetrics {
        if (ppiBuffer.size < 10) {
            return RhythmMetrics(RhythmState.INSUFFICIENT_DATA, 0.0, 0.0, 0.0, 0.0, 0.0)
        }

        val mean = ppiBuffer.average()
        
        // SDNN
        val sdnn = sqrt(ppiBuffer.map { (it - mean).pow(2.0) }.sum() / ppiBuffer.size)
        
        // RMSSD
        var sumDiffSq = 0.0
        var nn50Count = 0
        for (i in 0 until ppiBuffer.size - 1) {
            val diff = Math.abs(ppiBuffer[i+1] - ppiBuffer[i]).toDouble()
            sumDiffSq += diff.pow(2.0)
            if (diff > 50.0) nn50Count++
        }
        val rmssd = sqrt(sumDiffSq / (ppiBuffer.size - 1))
        val pnn50 = (nn50Count.toDouble() / (ppiBuffer.size - 1)) * 100.0
        
        val cv = (sdnn / mean) * 100.0

        // Índice de irregularidad simplificado
        val irregularityIndex = (rmssd / mean) * 100.0

        val state = when {
            ppiBuffer.size < 20 -> RhythmState.INSUFFICIENT_DATA
            cv > 15.0 || pnn50 > 30.0 -> RhythmState.POSSIBLE_AF_PATTERN_EXPERIMENTAL
            cv > 8.0 || rmssd > 80.0 -> RhythmState.IRREGULAR
            cv > 5.0 -> RhythmState.POSSIBLE_ECTOPIC_BEATS
            else -> RhythmState.REGULAR
        }

        return RhythmMetrics(state, rmssd, sdnn, pnn50, cv, irregularityIndex)
    }

    fun reset() {
        ppiBuffer.clear()
    }
}
