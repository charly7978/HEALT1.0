package com.example.myapplication.signal

import java.util.LinkedList

/**
 * Detector de picos avanzado para señales PPG.
 * Utiliza detección de máximos locales con ventana deslizante y umbral adaptativo dinámico.
 */
class PpgPeakDetector {

    data class ConfirmedBeat(
        val timestampNs: Long,
        val rrIntervalMs: Long,
        val confidence: Double,
        val amplitude: Double
    )

    private val windowSize = 5
    private val signalWindow = LinkedList<Double>()
    private var lastPeakTimestampNs: Long = 0
    private val refractoryPeriodNs = 300_000_000L // 300ms (max 200 BPM)
    
    private var adaptiveThreshold = 0.0
    private val peakAmplitudes = LinkedList<Double>()

    fun detect(sample: PpgSample, sqi: Double): ConfirmedBeat? {
        val value = sample.redFiltered
        val timestamp = sample.timestampNs

        signalWindow.addLast(value)
        if (signalWindow.size > 3) signalWindow.removeFirst()

        if (signalWindow.size < 3) return null

        // Detección de máximo local: el punto medio es mayor que sus vecinos
        val prev = signalWindow[0]
        val curr = signalWindow[1]
        val next = signalWindow[2]

        if (curr > prev && curr > next && curr > adaptiveThreshold) {
            val timeSinceLast = timestamp - lastPeakTimestampNs
            
            if (timeSinceLast > refractoryPeriodNs) {
                val rrMs = if (lastPeakTimestampNs == 0L) 0L else timeSinceLast / 1_000_000L
                
                // Validación de intervalo fisiológico estricto (30 - 220 BPM)
                if (rrMs == 0L || (rrMs in 270..2000)) {
                    val beat = ConfirmedBeat(
                        timestampNs = timestamp,
                        rrIntervalMs = rrMs,
                        confidence = sqi / 100.0,
                        amplitude = curr
                    )

                    lastPeakTimestampNs = timestamp
                    
                    // Actualizar umbral adaptativo basado en la amplitud del pico
                    peakAmplitudes.addLast(curr)
                    if (peakAmplitudes.size > windowSize) peakAmplitudes.removeFirst()
                    adaptiveThreshold = peakAmplitudes.average() * 0.5
                    
                    return beat
                }
            }
        }

        // Si no hay picos por mucho tiempo, bajamos el umbral lentamente para recuperar señal
        adaptiveThreshold *= 0.995
        if (adaptiveThreshold < 0.01) adaptiveThreshold = 0.01

        return null
    }

    fun reset() {
        signalWindow.clear()
        peakAmplitudes.clear()
        lastPeakTimestampNs = 0
        adaptiveThreshold = 0.0
    }
}
