package com.example.myapplication.signal

import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Buffer de señal con soporte para resampleo y gestión de timestamps.
 * Mantiene una ventana temporal para análisis de ritmo y estabilidad.
 */
class PPGSignalBuffer(
    private val windowSeconds: Int = 30,
    private val internalSamplingRate: Double = 30.0
) {
    private val rawSamples = ConcurrentLinkedQueue<PPGFrameFeatures>()
    private val maxSamples = (windowSeconds * 60).toInt() // Margen superior

    fun add(features: PPGFrameFeatures) {
        rawSamples.add(features)
        while (rawSamples.size > maxSamples) {
            rawSamples.poll()
        }
    }

    fun getStableWindow(durationSeconds: Double): List<PPGFrameFeatures>? {
        val all = rawSamples.toList()
        if (all.isEmpty()) return null
        
        val lastTs = all.last().timestampNs
        val startTimeNs = lastTs - (durationSeconds * 1_000_000_000).toLong()
        
        val window = all.filter { it.timestampNs >= startTimeNs }
        
        // Verificar si la ventana está completa y es estable
        if (window.isEmpty()) return null
        
        val actualDuration = (window.last().timestampNs - window.first().timestampNs) / 1_000_000_000.0
        if (actualDuration < durationSeconds * 0.9) return null
        
        return window
    }

    fun clear() {
        rawSamples.clear()
    }

    val size: Int get() = rawSamples.size
}
