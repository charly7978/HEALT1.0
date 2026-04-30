package com.example.myapplication.signal

import java.util.*

/**
 * Procesa FrameFeatures para obtener una señal PPG limpia.
 * Implementa filtros, detrending y extracción de AC/DC.
 */
class PpgSignalProcessor(samplingRate: Double) {

    private val filter = PPGBandpassFilter(samplingRate)
    private val signalBuffer = LinkedList<Double>()
    private val rawBuffer = LinkedList<Double>()
    private val maxBufferSize = (samplingRate * 10).toInt() // 10 segundos de buffer

    private var currentAcRed: Double = 0.0
    private var currentDcRed: Double = 0.0
    private var currentAcGreen: Double = 0.0
    private var currentDcGreen: Double = 0.0

    data class ProcessedSample(
        val filteredValue: Double,
        val acRed: Double,
        val dcRed: Double,
        val acGreen: Double,
        val dcGreen: Double,
        val timestamp: Long
    )

    fun process(features: PpgFrameAnalyzer.FrameFeatures): ProcessedSample {
        // 1. Filtrado de la señal (Usamos el canal verde para la onda visual por mejor SNR en piel)
        val filtered = filter.filter(features.greenMean)
        
        signalBuffer.addLast(filtered)
        if (signalBuffer.size > maxBufferSize) signalBuffer.removeFirst()

        rawBuffer.addLast(features.redMean)
        if (rawBuffer.size > maxBufferSize) rawBuffer.removeFirst()

        // 2. Extracción de AC/DC (Ventana móvil de 2 segundos)
        val windowSize = (features.actualFps * 2.0).toInt().coerceAtLeast(10)
        if (signalBuffer.size >= windowSize) {
            val recentSignal = signalBuffer.takeLast(windowSize)
            currentAcGreen = (recentSignal.maxOrNull()!! - recentSignal.minOrNull()!!) / 2.0
            currentDcGreen = features.greenMean

            val recentRawRed = rawBuffer.takeLast(windowSize)
            currentAcRed = (recentRawRed.maxOrNull()!! - recentRawRed.minOrNull()!!) / 2.0
            currentDcRed = features.redMean
        }

        return ProcessedSample(
            filteredValue = filtered,
            acRed = currentAcRed,
            dcRed = currentDcRed,
            acGreen = currentAcGreen,
            dcGreen = currentDcGreen,
            timestamp = features.timestampNs
        )
    }

    fun getFilteredBuffer(): List<Double> = signalBuffer.toList()
}
