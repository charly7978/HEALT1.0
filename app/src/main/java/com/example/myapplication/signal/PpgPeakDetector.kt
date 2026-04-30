package com.example.myapplication.signal

import kotlin.math.abs

/**
 * Detector de picos sistólicos adaptativo.
 */
class PpgPeakDetector(private val samplingRate: Double) {

    private val windowSize = (samplingRate * 2).toInt()
    private val buffer = mutableListOf<Double>()
    private val refractoryFrames = (samplingRate * 0.35).toInt()
    private var framesSinceLastPeak = refractoryFrames
    
    private val peakTimestamps = mutableListOf<Long>()

    fun process(value: Double, timestampNs: Long): PeakResult {
        buffer.add(value)
        if (buffer.size > windowSize) buffer.removeAt(0)
        
        framesSinceLastPeak++
        
        val median = buffer.sorted().let { it[it.size / 2] }
        val threshold = median + 0.1 // Umbral mínimo de prominencia

        var isPeak = false
        if (value > threshold && framesSinceLastPeak >= refractoryFrames) {
            if (isLocalMax()) {
                isPeak = true
                framesSinceLastPeak = 0
                peakTimestamps.add(timestampNs)
                if (peakTimestamps.size > 15) peakTimestamps.removeAt(0)
            }
        }

        return PeakResult(isPeak, calculateBpm())
    }

    private fun isLocalMax(): Boolean {
        if (buffer.size < 3) return false
        return buffer[buffer.size - 2] > buffer.last() && buffer[buffer.size - 2] > buffer[buffer.size - 3]
    }

    private fun calculateBpm(): Int {
        if (peakTimestamps.size < 4) return 0
        val intervals = mutableListOf<Long>()
        for (i in 1 until peakTimestamps.size) {
            val ms = (peakTimestamps[i] - peakTimestamps[i-1]) / 1_000_000
            if (ms in 300..1800) intervals.add(ms)
        }
        if (intervals.isEmpty()) return 0
        val avgInterval = intervals.average()
        return (60000.0 / avgInterval).toInt()
    }

    fun reset() {
        buffer.clear()
        peakTimestamps.clear()
        framesSinceLastPeak = refractoryFrames
    }

    data class PeakResult(val isPeak: Boolean, val bpm: Int)
}
