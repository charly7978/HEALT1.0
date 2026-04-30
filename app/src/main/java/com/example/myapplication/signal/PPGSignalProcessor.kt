package com.example.myapplication.signal

import kotlin.math.max
import kotlin.math.min

class PPGSignalProcessor(private val samplingRate: Double) {

    private val bandpassFilter = PPGBandpassFilter(samplingRate)
    private val dcBuffer = mutableListOf<Double>()
    private val acBuffer = mutableListOf<Double>()
    private val bufferSize = (samplingRate * 2).toInt() // 2 segundos para AC/DC

    data class SignalResult(
        val rawValue: Double,
        val filteredValue: Double,
        val normalizedValue: Double,
        val ac: Double,
        val dc: Double,
        val perfusionIndex: Double,
        val sqi: Double
    )

    fun process(value: Double): SignalResult {
        // 1. Filtrado
        val filtered = bandpassFilter.filter(value)

        // 2. Cálculo de AC y DC dinámico
        dcBuffer.add(value)
        if (dcBuffer.size > bufferSize) dcBuffer.removeAt(0)
        
        acBuffer.add(filtered)
        if (acBuffer.size > bufferSize) acBuffer.removeAt(0)

        val dc = if (dcBuffer.isNotEmpty()) dcBuffer.average() else value
        val ac = if (acBuffer.isNotEmpty()) {
            (acBuffer.maxOrNull() ?: 0.0) - (acBuffer.minOrNull() ?: 0.0)
        } else 0.0

        // 3. Índice de Perfusión (PI)
        val pi = if (dc > 0) (ac / dc) * 100.0 else 0.0

        // 4. Normalización para visualización (0..1)
        val minAC = acBuffer.minOrNull() ?: -1.0
        val maxAC = acBuffer.maxOrNull() ?: 1.0
        val range = max(0.1, maxAC - minAC)
        val normalized = (filtered - minAC) / range

        // 5. Signal Quality Index (SQI) simplificado
        // Se basa en la estabilidad de la amplitud y relación AC/DC razonable
        val sqi = calculateSQI(ac, dc, pi)

        return SignalResult(
            rawValue = value,
            filteredValue = filtered,
            normalizedValue = normalized,
            ac = ac,
            dc = dc,
            perfusionIndex = pi,
            sqi = sqi
        )
    }

    private fun calculateSQI(ac: Double, dc: Double, pi: Double): Double {
        if (dc < 10 || ac < 0.1) return 0.0
        
        var score = 0.0
        if (pi in 0.1..10.0) score += 0.5
        if (ac > 0.5) score += 0.3
        if (dc in 50.0..240.0) score += 0.2
        
        return score * 100.0
    }
}
