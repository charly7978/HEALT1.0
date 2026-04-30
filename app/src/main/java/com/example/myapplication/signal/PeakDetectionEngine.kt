package com.example.myapplication.signal

import kotlin.math.abs
import kotlin.math.max

/**
 * Motor de detección de picos mejorado.
 * Usa umbral adaptativo y validación de prominencia.
 */
class PeakDetectionEngine(private val samplingRate: Double) {

    private val windowSize = (samplingRate * 1.5).toInt()
    private val signalBuffer = mutableListOf<Double>()
    
    private val refractoryFrames = (samplingRate * 0.3).toInt() // 300ms
    private var framesSinceLastPeak = refractoryFrames

    private var adaptiveThreshold = 0.0
    private val peakTimestamps = mutableListOf<Long>()

    fun process(value: Double, timestampNs: Long): PeakResult {
        signalBuffer.add(value)
        if (signalBuffer.size > windowSize) signalBuffer.removeAt(0)
        
        framesSinceLastPeak++
        
        // Calcular MAD (Median Absolute Deviation) para umbral robusto
        val median = calculateMedian(signalBuffer)
        val mad = calculateMedian(signalBuffer.map { abs(it - median) })
        adaptiveThreshold = median + max(mad * 3.0, 0.05)

        var isPeak = false
        if (value > adaptiveThreshold && framesSinceLastPeak >= refractoryFrames) {
            // Verificar máximo local en una pequeña vecindad
            if (isLocalMaximum()) {
                isPeak = true
                framesSinceLastPeak = 0
                peakTimestamps.add(timestampNs)
                if (peakTimestamps.size > 20) peakTimestamps.removeAt(0)
            }
        }

        val bpm = calculateBpm()
        
        return PeakResult(isPeak, bpm, adaptiveThreshold)
    }

    private fun isLocalMaximum(): Boolean {
        if (signalBuffer.size < 3) return false
        val last = signalBuffer.last()
        val prev = signalBuffer[signalBuffer.size - 2]
        val prevPrev = signalBuffer[signalBuffer.size - 3]
        return prev > prevPrev && prev > last // El pico es el penúltimo
    }

    private fun calculateBpm(): Int {
        if (peakTimestamps.size < 3) return 0
        
        val intervals = mutableListOf<Long>()
        for (i in 1 until peakTimestamps.size) {
            val diff = peakTimestamps[i] - peakTimestamps[i-1]
            val ms = diff / 1_000_000
            if (ms in 300..2000) {
                intervals.add(ms)
            }
        }
        
        if (intervals.isEmpty()) return 0
        
        // Usar mediana de intervalos para robustez
        intervals.sort()
        val medianInterval = intervals[intervals.size / 2]
        return (60000 / medianInterval).toInt()
    }

    private fun calculateMedian(list: List<Double>): Double {
        if (list.isEmpty()) return 0.0
        val sorted = list.sorted()
        return if (sorted.size % 2 == 0) {
            (sorted[sorted.size / 2] + sorted[sorted.size / 2 - 1]) / 2.0
        } else {
            sorted[sorted.size / 2]
        }
    }

    fun reset() {
        signalBuffer.clear()
        peakTimestamps.clear()
        framesSinceLastPeak = refractoryFrames
    }

    data class PeakResult(val isPeak: Boolean, val bpm: Int, val threshold: Double)
}
