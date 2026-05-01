package com.example.myapplication.ppg

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Analizador de ritmo cardíaco basado en intervalos RR de latidos confirmados.
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
        val irregularityIndex: Double,
        val meanRR: Double,
        val beatCount: Int
    )

    data class ConfirmedBeat(
        val timestampNs: Long,
        val amplitude: Double,
        val rrMs: Long,
        val confidence: Double,
        val sourceChannel: String
    )

    private val rrBuffer = java.util.LinkedList<Long>()
    private val beatBuffer = java.util.LinkedList<ConfirmedBeat>()
    private val maxBufferSize = 60 // ~2 minutos a 30 FPS

    var isArhythmic: Boolean = false
        private set

    var currentMetrics: RhythmMetrics = RhythmMetrics(RhythmState.INSUFFICIENT_DATA, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0)
        private set

    /**
     * Agrega un latido confirmado al análisis.
     */
    fun addConfirmedBeat(beat: ConfirmedBeat) {
        beatBuffer.addLast(beat)
        if (beatBuffer.size > maxBufferSize) beatBuffer.removeFirst()

        if (beat.rrMs > 0) {
            rrBuffer.addLast(beat.rrMs)
            if (rrBuffer.size > maxBufferSize) rrBuffer.removeFirst()
        }
    }

    /**
     * Analiza el ritmo basado en los latidos confirmados.
     */
    fun analyze(): RhythmMetrics {
        if (rrBuffer.size < 4) {
            isArhythmic = false
            return RhythmMetrics(RhythmState.INSUFFICIENT_DATA, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, beatBuffer.size)
        }

        val mean = rrBuffer.average()
        
        // SDNN (Desviación estándar de los intervalos RR)
        val sdnn = sqrt(rrBuffer.map { (it - mean).pow(2.0) }.sum() / rrBuffer.size)
        
        // RMSSD (Raíz cuadrada de la media de las diferencias sucesivas al cuadrado)
        var sumDiffSq = 0.0
        var nn50Count = 0
        for (i in 0 until rrBuffer.size - 1) {
            val diff = Math.abs(rrBuffer[i+1] - rrBuffer[i]).toDouble()
            sumDiffSq += diff.pow(2.0)
            if (diff > 50.0) nn50Count++
        }
        val rmssd = sqrt(sumDiffSq / (rrBuffer.size - 1))
        val pnn50 = (nn50Count.toDouble() / (rrBuffer.size - 1)) * 100.0
        
        // Coeficiente de Variación
        val cv = if (mean > 0) (sdnn / mean) * 100.0 else 0.0

        // Índice de irregularidad basado en RMSSD relativo
        val irregularityIndex = if (mean > 0) (rmssd / mean) * 100.0 else 0.0

        val state = when {
            cv > 15.0 || pnn50 > 30.0 -> RhythmState.POSSIBLE_AF_PATTERN_EXPERIMENTAL
            cv > 8.0 || rmssd > 65.0 -> RhythmState.IRREGULAR
            cv > 4.0 -> RhythmState.POSSIBLE_ECTOPIC_BEATS
            else -> RhythmState.REGULAR
        }

        isArhythmic = state != RhythmState.REGULAR && state != RhythmState.INSUFFICIENT_DATA
        currentMetrics = RhythmMetrics(state, rmssd, sdnn, pnn50, cv, irregularityIndex, mean, beatBuffer.size)
        
        return currentMetrics
    }

    /**
     * Obtiene los intervalos RR actuales.
     */
    fun getRRIntervals(): List<Long> = rrBuffer.toList()

    /**
     * Obtiene los latidos confirmados.
     */
    fun getConfirmedBeats(): List<ConfirmedBeat> = beatBuffer.toList()

    fun reset() {
        rrBuffer.clear()
        beatBuffer.clear()
        isArhythmic = false
        currentMetrics = RhythmMetrics(RhythmState.INSUFFICIENT_DATA, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0)
    }
}
