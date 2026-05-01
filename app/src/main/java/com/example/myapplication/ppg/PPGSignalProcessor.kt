package com.example.myapplication.ppg

import com.example.myapplication.signal.PpgFrame
import java.util.*

/**
 * Procesador de señal PPG con pipeline completo.
 * Implementa filtrado, detrending, normalización y extracción de AC/DC.
 */
class PpgSignalProcessor(samplingRate: Double) {

    private val filter = PPGBandpassFilter(samplingRate)
    private val signalBuffer = LinkedList<Double>()
    private val rawBuffer = LinkedList<Double>()
    private val maxBufferSize = (samplingRate * 30).toInt() // 30 segundos de buffer

    private var currentAcRed: Double = 0.0
    private var currentDcRed: Double = 0.0
    private var currentAcGreen: Double = 0.0
    private var currentDcGreen: Double = 0.0
    private var currentAcBlue: Double = 0.0
    private var currentDcBlue: Double = 0.0

    private val dcWindow = LinkedList<Double>()
    private val dcWindowSize = (samplingRate * 2.0).toInt().coerceAtLeast(30)

    data class ProcessedSample(
        val filteredValue: Double,
        val rawValue: Double,
        val acRed: Double,
        val dcRed: Double,
        val acGreen: Double,
        val dcGreen: Double,
        val acBlue: Double,
        val dcBlue: Double,
        val timestamp: Long,
        val isDetrended: Boolean
    )

    fun process(frame: PpgFrame): ProcessedSample {
        // 1. Filtrado de la señal (canal verde para mejor SNR en piel)
        val filtered = filter.filter(frame.avgGreen)

        // 2. Detrending simple (restar media móvil)
        val detrended = detrend(filtered)

        signalBuffer.addLast(detrended)
        if (signalBuffer.size > maxBufferSize) signalBuffer.removeFirst()

        rawBuffer.addLast(frame.avgGreen)
        if (rawBuffer.size > maxBufferSize) rawBuffer.removeFirst()

        // 3. Usar AC/DC calculados por PpgFrameAnalyzer (más precisos)
        // PpgFrame ya contiene greenAc, greenDc calculados por ventana móvil
        currentAcGreen = frame.greenAc
        currentDcGreen = frame.greenDc
        currentAcRed = frame.redAc
        currentDcRed = frame.redDc
        currentAcBlue = frame.blueAc
        currentDcBlue = frame.blueDc

        return ProcessedSample(
            filteredValue = detrended,
            rawValue = frame.avgGreen,
            acRed = currentAcRed,
            dcRed = currentDcRed,
            acGreen = currentAcGreen,
            dcGreen = currentDcGreen,
            acBlue = currentAcBlue,
            dcBlue = currentDcBlue,
            timestamp = frame.timestampNs,
            isDetrended = true
        )
    }

    /**
     * Detrending simple: resta la media móvil.
     */
    private fun detrend(value: Double): Double {
        if (signalBuffer.size < 10) return value
        val windowSize = 10
        val recent = signalBuffer.takeLast(windowSize)
        val mean = recent.average()
        return value - mean
    }

    fun getFilteredBuffer(): List<Double> = signalBuffer.toList()
    fun getRawBuffer(): List<Double> = rawBuffer.toList()

    fun reset() {
        signalBuffer.clear()
        rawBuffer.clear()
        dcWindow.clear()
        currentAcRed = 0.0
        currentDcRed = 0.0
        currentAcGreen = 0.0
        currentDcGreen = 0.0
        currentAcBlue = 0.0
        currentDcBlue = 0.0
    }
}
