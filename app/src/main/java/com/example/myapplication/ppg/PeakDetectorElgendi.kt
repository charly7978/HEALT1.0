package com.example.myapplication.ppg

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Detector de picos PPG basado en el algoritmo de Elgendi et al.
 * "Optimal Systolic Peak Detection in Photoplethysmogram"
 * 
 * Implementa:
 * - Squaring de la señal
 * - Ventanas móviles (corta y larga)
 * - Threshold adaptativo
 * - Período refractario
 * - Validación de prominencia
 */
class PeakDetectorElgendi(
    private val samplingRate: Double
) {

    // Parámetros del algoritmo de Elgendi
    private val fs = samplingRate
    private val windowShort = (0.15 * fs).toInt()  // 150ms ventana corta
    private val windowLong = (0.6 * fs).toInt()    // 600ms ventana larga
    private val refractoryPeriod = (0.25 * fs).toInt()  // 250ms refractario

    private val signalBuffer = ArrayList<Double>(windowLong * 2)
    private val squaredBuffer = ArrayList<Double>(windowLong * 2)
    private val mwiBuffer = ArrayList<Double>(windowLong * 2)

    private var lastPeakIndex = -refractoryPeriod
    private var adaptiveThreshold = 0.0

    data class ElgendiPeak(
        val index: Int,
        val timestampNs: Long,
        val amplitude: Double,
        val confidence: Double
    )

    /**
     * Procesa una nueva muestra y detecta picos si existen.
     * Retorna null si no hay pico, o el pico detectado.
     */
    fun processSample(value: Double, timestampNs: Long): ElgendiPeak? {
        signalBuffer.add(value)
        if (signalBuffer.size > windowLong * 2) {
            signalBuffer.removeAt(0)
        }

        // Necesitamos suficientes muestras para procesar
        if (signalBuffer.size < windowLong) return null

        // 1. Squaring de la señal
        val squared = value * value
        squaredBuffer.add(squared)
        if (squaredBuffer.size > windowLong * 2) {
            squaredBuffer.removeAt(0)
        }

        // 2. Moving Window Integration (MWI)
        val mwi = calculateMWI(squaredBuffer, windowShort)
        mwiBuffer.add(mwi)
        if (mwiBuffer.size > windowLong * 2) {
            mwiBuffer.removeAt(0)
        }

        if (mwiBuffer.size < windowLong) return null

        val currentIndex = mwiBuffer.size - 1
        val currentMwi = mwiBuffer[currentIndex]

        // 3. Threshold adaptativo basado en ventana larga
        val recentMwi = mwiBuffer.takeLast(windowLong)
        val mwiMean = recentMwi.average()
        val mwiMax = recentMwi.maxOrNull() ?: 0.0
        val mwiMin = recentMwi.minOrNull() ?: 0.0

        // Threshold dinámico: media + 0.25 * rango
        adaptiveThreshold = mwiMean + 0.25 * (mwiMax - mwiMin)

        // 4. Detección de pico: máximo local que excede threshold
        if (currentIndex > 0 && currentIndex < mwiBuffer.size - 1) {
            val prevMwi = mwiBuffer[currentIndex - 1]
            val nextMwi = mwiBuffer[currentIndex + 1]

            if (currentMwi > prevMwi && currentMwi > nextMwi && currentMwi > adaptiveThreshold) {
                // 5. Verificar período refractario
                if (currentIndex - lastPeakIndex < refractoryPeriod) {
                    return null
                }

                // 6. Validar prominencia
                val prominence = calculateProminence(mwiBuffer, currentIndex)
                if (prominence < adaptiveThreshold * 0.3) {
                    return null
                }

                lastPeakIndex = currentIndex

                // Mapear índice MWI a índice de señal original
                val signalIndex = currentIndex - windowShort / 2
                if (signalIndex >= 0 && signalIndex < signalBuffer.size) {
                    val amplitude = signalBuffer[signalIndex]
                    val confidence = prominence / (mwiMax - mwiMin + 0.001)

                    return ElgendiPeak(
                        index = signalIndex,
                        timestampNs = timestampNs,
                        amplitude = amplitude,
                        confidence = confidence.coerceIn(0.0, 1.0)
                    )
                }
            }
        }

        return null
    }

    /**
     * Calcula Moving Window Integration.
     */
    private fun calculateMWI(buffer: ArrayList<Double>, windowSize: Int): Double {
        if (buffer.size < windowSize) return buffer.last()

        val window = buffer.takeLast(windowSize)
        return window.sum() / windowSize
    }

    /**
     * Calcula la prominencia de un pico.
     */
    private fun calculateProminence(buffer: ArrayList<Double>, index: Int): Double {
        if (index <= 0 || index >= buffer.size - 1) return 0.0

        val peakValue = buffer[index]

        // Buscar mínimo a la izquierda
        var leftMin = peakValue
        for (i in (index - 1) downTo 0) {
            if (buffer[i] < leftMin) {
                leftMin = buffer[i]
            }
            if (buffer[i] > peakValue) break
        }

        // Buscar mínimo a la derecha
        var rightMin = peakValue
        for (i in (index + 1) until buffer.size) {
            if (buffer[i] < rightMin) {
                rightMin = buffer[i]
            }
            if (buffer[i] > peakValue) break
        }

        val referenceLevel = max(leftMin, rightMin)
        return peakValue - referenceLevel
    }

    fun reset() {
        signalBuffer.clear()
        squaredBuffer.clear()
        mwiBuffer.clear()
        lastPeakIndex = -refractoryPeriod
        adaptiveThreshold = 0.0
    }
}
