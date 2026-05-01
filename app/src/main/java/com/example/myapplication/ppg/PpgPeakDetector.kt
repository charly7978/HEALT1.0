package com.example.myapplication.ppg

import kotlin.math.abs

/**
 * Detector de picos PPG con criterios estrictos de validación fisiológica.
 * Solo confirma latidos si pasa múltiples validaciones.
 */
class PpgPeakDetector {

    data class ConfirmedBeat(
        val timestampNs: Long,
        val amplitude: Double,
        val rrMs: Long,
        val confidence: Double,
        val sourceChannel: String,
        val sqi: Double
    )

    private var lastPeakTimeNs: Long = 0
    private var lastValue: Double = 0.0
    private var isRising: Boolean = false
    private var adaptiveThreshold: Double = 0.0
    private var peakCount = 0
    
    private val minRRIntervalNs = 350_000_000L // ~170 BPM max
    private val maxRRIntervalNs = 2_000_000_000L // ~30 BPM min
    private val minProminence = 0.1 // Mínima prominencia del pico

    fun detect(
        filteredValue: Double,
        timestampNs: Long,
        sqi: Double,
        acGreen: Double,
        dcGreen: Double
    ): ConfirmedBeat? {
        // 1. Validar calidad mínima
        if (sqi < 0.5) return null
        
        // 2. Validar componente pulsátil
        if (acGreen < 0.05 || dcGreen < 1.0) return null

        val currentValue = filteredValue
        val slope = currentValue - lastValue
        
        var beat: ConfirmedBeat? = null

        // 3. Detector de máximo local con umbral adaptativo
        if (isRising && slope < 0 && lastValue > adaptiveThreshold) {
            val currentTime = timestampNs
            val rrNs = currentTime - lastPeakTimeNs

            // 4. Validar intervalo RR fisiológico
            if (rrNs in minRRIntervalNs..maxRRIntervalNs) {
                // 5. Validar prominencia del pico
                val prominence = lastValue - adaptiveThreshold
                if (prominence > minProminence) {
                    beat = ConfirmedBeat(
                        timestampNs = currentTime,
                        amplitude = lastValue,
                        rrMs = rrNs / 1_000_000,
                        confidence = sqi * calculateProminenceScore(prominence),
                        sourceChannel = "green",
                        sqi = sqi
                    )
                    lastPeakTimeNs = currentTime
                    peakCount++
                    // Ajustar umbral adaptativo (60% del pico actual)
                    adaptiveThreshold = adaptiveThreshold * 0.7 + lastValue * 0.3 * 0.6
                }
            }
        }

        isRising = slope > 0
        lastValue = currentValue
        
        // 6. Decaimiento lento del umbral para no perder picos si la amplitud baja
        adaptiveThreshold *= 0.995

        return beat
    }

    /**
     * Calcula puntuación basada en prominencia.
     */
    private fun calculateProminenceScore(prominence: Double): Double {
        return (prominence / (prominence + 0.1)).coerceIn(0.5, 1.0)
    }

    fun getCurrentBpm(): Double {
        if (peakCount < 2) return 0.0
        // BPM promedio basado en el último RR
        val lastRR = if (lastPeakTimeNs > 0) {
            System.nanoTime() - lastPeakTimeNs
        } else 0L
        return if (lastRR > 0) 60_000_000_000.0 / lastRR else 0.0
    }

    fun reset() {
        lastPeakTimeNs = 0
        lastValue = 0.0
        isRising = false
        adaptiveThreshold = 0.0
        peakCount = 0
    }
}
