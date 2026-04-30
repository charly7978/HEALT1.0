package com.example.myapplication.signal

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Motor de análisis de ritmo cardíaco.
 * Analiza la variabilidad de los intervalos pulso-a-pulso (PPI) para detectar irregularidades.
 */
class RhythmAnalysisEngine {

    enum class RhythmStatus {
        NORMAL,
        IRREGULAR,
        SUSPICIOUS_ARRHYTHMIA,
        INSUFFICIENT_DATA
    }

    private val ppiBuffer = mutableListOf<Long>()
    private val maxBufferSize = 60 // Ventana de ~60 latidos

    /**
     * Añade un nuevo intervalo pulso-a-pulso (en milisegundos).
     */
    fun addInterval(ppiMs: Long) {
        if (ppiMs in 300..2000) { // Rango fisiológico 30-200 BPM
            ppiBuffer.add(ppiMs)
            if (ppiBuffer.size > maxBufferSize) {
                ppiBuffer.removeAt(0)
            }
        }
    }

    fun analyze(): AnalysisResult {
        if (ppiBuffer.size < 10) {
            return AnalysisResult(RhythmStatus.INSUFFICIENT_DATA, 0.0, 0.0)
        }

        // RMSSD (Root Mean Square of Successive Differences)
        var sumDiffSq = 0.0
        for (i in 0 until ppiBuffer.size - 1) {
            val diff = (ppiBuffer[i+1] - ppiBuffer[i]).toDouble()
            sumDiffSq += diff.pow(2.0)
        }
        val rmssd = sqrt(sumDiffSq / (ppiBuffer.size - 1))

        // Coeficiente de Variación (CV)
        val mean = ppiBuffer.average()
        val stdDev = sqrt(ppiBuffer.map { (it - mean).pow(2.0) }.sum() / ppiBuffer.size)
        val cv = (stdDev / mean) * 100.0

        // Clasificación simplificada (basada en umbrales clínicos típicos para PPG)
        val status = when {
            cv > 15.0 || rmssd > 120.0 -> RhythmStatus.SUSPICIOUS_ARRHYTHMIA
            cv > 8.0 || rmssd > 60.0 -> RhythmStatus.IRREGULAR
            else -> RhythmStatus.NORMAL
        }

        return AnalysisResult(status, rmssd, cv)
    }

    data class AnalysisResult(
        val status: RhythmStatus,
        val rmssd: Double,
        val cv: Double
    )

    fun reset() {
        ppiBuffer.clear()
    }
}
