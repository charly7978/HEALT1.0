package com.example.myapplication.signal

import kotlin.math.abs

/**
 * Detector de picos PPG con umbral adaptativo y refractariedad.
 * Solo detecta si la calidad es suficiente.
 */
class PpgPeakDetector {

    data class BeatEvent(
        val timestampNs: Long,
        val rrMs: Long,
        val confidence: Double
    )

    private var lastPeakTimeNs: Long = 0
    private var lastValue: Double = 0.0
    private var isRising: Boolean = false
    private var adaptiveThreshold: Double = 0.0
    
    private val minRRIntervalNs = 350_000_000L // ~170 BPM max
    private val maxRRIntervalNs = 2_000_000_000L // ~30 BPM min

    fun detect(sample: PpgSignalProcessor.ProcessedSample, sqi: Double): BeatEvent? {
        if (sqi < 0.5) return null

        val currentValue = sample.filteredValue
        val slope = currentValue - lastValue
        
        var beat: BeatEvent? = null

        // Detector de máximo local con umbral adaptativo
        if (isRising && slope < 0 && lastValue > adaptiveThreshold) {
            val currentTime = sample.timestamp
            val rrNs = currentTime - lastPeakTimeNs

            if (rrNs in minRRIntervalNs..maxRRIntervalNs) {
                beat = BeatEvent(
                    timestampNs = currentTime,
                    rrMs = rrNs / 1_000_000,
                    confidence = sqi
                )
                lastPeakTimeNs = currentTime
                // Ajustar umbral adaptativo (60% del pico actual)
                adaptiveThreshold = adaptiveThreshold * 0.7 + lastValue * 0.3 * 0.6
            }
        }

        isRising = slope > 0
        lastValue = currentValue
        
        // Decaimiento lento del umbral para no perder picos si la amplitud baja
        adaptiveThreshold *= 0.995

        return beat
    }
}
