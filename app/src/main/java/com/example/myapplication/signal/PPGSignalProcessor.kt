package com.example.myapplication.signal

import java.util.*
import kotlin.math.abs

/**
 * Pipeline de procesamiento de señal PPG de alta precisión.
 * Implementa eliminación de deriva DC (detrending), filtrado paso-banda y cálculo de AC/DC.
 */
class PpgSignalProcessor(private val samplingRate: Double) {

    private val filter = PPGBandpassFilter(samplingRate)
    private val qualityAnalyzer = PpgSignalQuality()
    
    private val bufferSize = 450 // ~15 segundos a 30fps
    private val redBuffer = LinkedList<Double>()
    private val filteredBuffer = LinkedList<Double>()
    
    // Ventana corta para cálculo dinámico de AC/DC (2 segundos aprox)
    private val windowSize = (samplingRate * 2).toInt().coerceAtLeast(10)

    var lastQuality: PpgSignalQuality.QualityResult? = null
        private set

    fun process(sample: PpgSample): PpgSample {
        // 1. Filtrado Paso-Banda (0.5Hz - 5.0Hz)
        val filtered = filter.filter(sample.red)
        
        // 2. Gestión de Buffers circulares
        redBuffer.addLast(sample.red)
        filteredBuffer.addLast(filtered)
        if (redBuffer.size > bufferSize) {
            redBuffer.removeFirst()
            filteredBuffer.removeFirst()
        }
        
        // 3. Cálculo de componentes AC y DC mediante método de envolvente
        val dc = if (redBuffer.size >= windowSize) {
            redBuffer.takeLast(windowSize).average()
        } else sample.red

        val ac = if (filteredBuffer.size >= windowSize) {
            val recent = filteredBuffer.takeLast(windowSize)
            val max = recent.maxOrNull() ?: 0.0
            val min = recent.minOrNull() ?: 0.0
            (max - min) / 2.0 // Amplitud pico
        } else 0.0

        // 4. Análisis de Calidad y Clasificación Fisiológica
        lastQuality = qualityAnalyzer.analyze(sample, filteredBuffer, ac, dc)

        return sample.copy(
            redFiltered = filtered,
            greenFiltered = 0.0 
        )
    }
    
    fun getFilteredBuffer(): List<Double> = filteredBuffer.toList()
    
    fun getAcDcRatio(): Double {
        val q = lastQuality ?: return 0.0
        // Perfusion Index simple
        return if (redBuffer.isNotEmpty()) (acFromBuffer() / redBuffer.last()) * 100.0 else 0.0
    }

    private fun acFromBuffer(): Double {
        if (filteredBuffer.size < windowSize) return 0.0
        val recent = filteredBuffer.takeLast(windowSize)
        return (recent.maxOrNull()!! - recent.minOrNull()!!) / 2.0
    }
}
