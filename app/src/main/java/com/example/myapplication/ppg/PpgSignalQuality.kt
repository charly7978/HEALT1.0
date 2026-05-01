package com.example.myapplication.ppg

import kotlin.math.abs

/**
 * Calculador de calidad de señal PPG (SQI).
 * Evalúa múltiples factores para determinar si la señal es apta para diagnóstico.
 */
class PpgSignalQuality {

    data class SqiResult(
        val totalSqi: Double,
        val perfusionIndex: Double,
        val snr: Double,
        val isStable: Boolean
    )

    private var lastRedDc: Double = 0.0
    private val signalBuffer = java.util.LinkedList<Double>()
    private val maxBufferSize = 300 // ~10 segundos a 30 FPS

    fun compute(
        sample: PpgSample,
        acRed: Double,
        acGreen: Double,
        filteredSignal: Double,
        isPeriodical: Boolean
    ): SqiResult {
        // 1. Perfusion Index (PI) = (AC / DC) * 100
        val piRed = if (sample.rawRed > 0) (acRed / sample.rawRed) * 100.0 else 0.0
        val piGreen = if (sample.rawGreen > 0) (acGreen / sample.rawGreen) * 100.0 else 0.0
        
        // 2. Estabilidad DC (Detección de artefactos de movimiento brusco)
        val dcChange = if (lastRedDc > 0) abs(sample.rawRed - lastRedDc) / lastRedDc else 0.0
        lastRedDc = sample.rawRed
        val isStable = dcChange < 0.05 // Menos del 5% de cambio entre frames

        // 3. Cálculo de SNR
        signalBuffer.addLast(filteredSignal)
        if (signalBuffer.size > maxBufferSize) signalBuffer.removeFirst()
        
        val snr = if (signalBuffer.size > 30) {
            val recent = signalBuffer.takeLast(30)
            val signalPower = recent.map { it * it }.average()
            val noisePower = recent.zipWithNext { a, b -> (a - b) * (a - b) }.average()
            if (noisePower > 0) signalPower / noisePower else 0.0
        } else 0.0

        // 4. Cálculo de SQI Compuesto
        var score = 0.0
        
        // Peso Perfusion (25%)
        if (piRed in 0.05..5.0) score += 0.25
        
        // Peso Estabilidad (25%)
        if (isStable) score += 0.25
        
        // Peso Periodicidad (25%)
        if (isPeriodical) score += 0.25
        
        // Peso SNR (25%)
        if (snr > 3.0) score += 0.25
        else if (snr > 1.5) score += 0.15

        return SqiResult(
            totalSqi = score,
            perfusionIndex = piRed,
            snr = snr,
            isStable = isStable
        )
    }

    fun reset() {
        signalBuffer.clear()
        lastRedDc = 0.0
    }
}
