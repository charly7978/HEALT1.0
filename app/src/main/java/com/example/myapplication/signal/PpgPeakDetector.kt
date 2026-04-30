package com.example.myapplication.signal

import java.util.LinkedList

/**
 * Detector de picos adaptativo para señales PPG.
 */
class PpgPeakDetector {

    data class ConfirmedBeat(
        val timestampNs: Long,
        val rrIntervalMs: Long,
        val confidence: Double,
        val amplitude: Double
    )

    private var lastPeakTimestampNs: Long = 0
    private var lastPeakValue: Double = 0.0
    
    // Periodo refractario para evitar falsos picos (350ms -> max ~170 BPM)
    private val refractoryPeriodNs = 350_000_000L
    
    // Umbral adaptativo
    private var adaptiveThreshold = 0.0
    private val windowSize = 5
    private val recentAmplitudes = LinkedList<Double>()

    fun detect(sample: PpgSample, sqi: Double): ConfirmedBeat? {
        val value = sample.redFiltered
        val timestamp = sample.timestampNs

        // 1. Detección de máximo local básico (simplificado para flujo continuo)
        // En una implementación real se compararía con muestras anteriores/posteriores
        if (value > adaptiveThreshold && (timestamp - lastPeakTimestampNs) > refractoryPeriodNs) {
            
            val rrMs = if (lastPeakTimestampNs == 0L) 0L else (timestamp - lastPeakTimestampNs) / 1_000_000L
            
            // Validar intervalo fisiológico (30 - 200 BPM)
            if (rrMs == 0L || rrMs in 300..2000) {
                val beat = ConfirmedBeat(
                    timestampNs = timestamp,
                    rrIntervalMs = rrMs,
                    confidence = sqi / 100.0,
                    amplitude = value
                )
                
                lastPeakTimestampNs = timestamp
                lastPeakValue = value
                
                // Actualizar umbral adaptativo (media móvil de amplitudes de picos)
                recentAmplitudes.addLast(value)
                if (recentAmplitudes.size > windowSize) recentAmplitudes.removeFirst()
                adaptiveThreshold = recentAmplitudes.average() * 0.6
                
                return beat
            }
        } else {
            // Decaimiento lento del umbral para no perder señal si la amplitud baja
            adaptiveThreshold *= 0.999
        }

        return null
    }
}
