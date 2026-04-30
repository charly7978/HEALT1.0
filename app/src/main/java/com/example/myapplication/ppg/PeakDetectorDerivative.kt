package com.example.myapplication.ppg

import kotlin.math.abs

/**
 * Detector de picos basado en derivadas y morfología.
 * Detecta picos por pendiente ascendente, máximo local y pendiente descendente.
 */
class PeakDetectorDerivative(
    private val samplingRate: Double
) {

    private val minRRIntervalMs = 250.0  // ~240 BPM máximo
    private val maxRRIntervalMs = 2000.0 // ~30 BPM mínimo

    private val signalBuffer = ArrayList<Double>(200)
    private val derivativeBuffer = ArrayList<Double>(200)

    private var lastPeakTimeNs: Long = 0
    private var lastValue: Double = 0.0
    private var isRising: Boolean = false
    private var adaptiveThreshold: Double = 0.0

    data class DerivativePeak(
        val timestampNs: Long,
        val amplitude: Double,
        val confidence: Double
    )

    /**
     * Procesa una nueva muestra y detecta picos si existen.
     */
    fun processSample(value: Double, timestampNs: Long): DerivativePeak? {
        signalBuffer.add(value)
        if (signalBuffer.size > 200) {
            signalBuffer.removeAt(0)
        }

        // Calcular derivada primera
        val derivative = value - lastValue
        derivativeBuffer.add(derivative)
        if (derivativeBuffer.size > 200) {
            derivativeBuffer.removeAt(0)
        }

        var peak: DerivativePeak? = null

        // Detector de máximo local con transición de pendiente
        if (isRising && derivative < 0 && lastValue > adaptiveThreshold) {
            val currentTime = timestampNs
            val rrMs = (currentTime - lastPeakTimeNs) / 1_000_000.0

            // Validar intervalo RR fisiológico
            if (rrMs in minRRIntervalMs..maxRRIntervalMs || lastPeakTimeNs == 0L) {
                // Validar morfología: pendiente ascendente debe ser significativa
                val recentDerivatives = derivativeBuffer.takeLast(10)
                val maxPositiveSlope = recentDerivatives.filter { it > 0 }.maxOrNull() ?: 0.0

                if (maxPositiveSlope > adaptiveThreshold * 0.1) {
                    val confidence = (lastValue / (adaptiveThreshold + 0.001)).coerceIn(0.0, 1.0)

                    peak = DerivativePeak(
                        timestampNs = currentTime,
                        amplitude = lastValue,
                        confidence = confidence
                    )

                    lastPeakTimeNs = currentTime
                    // Ajustar umbral adaptativo
                    adaptiveThreshold = adaptiveThreshold * 0.7 + lastValue * 0.3 * 0.5
                }
            }
        }

        isRising = derivative > 0
        lastValue = value

        // Decaimiento lento del umbral
        adaptiveThreshold *= 0.995

        // Inicializar umbral si es necesario
        if (adaptiveThreshold < 0.001 && abs(value) > 0.001) {
            adaptiveThreshold = abs(value) * 0.5
        }

        return peak
    }

    fun reset() {
        signalBuffer.clear()
        derivativeBuffer.clear()
        lastPeakTimeNs = 0
        lastValue = 0.0
        isRising = false
        adaptiveThreshold = 0.0
    }
}
