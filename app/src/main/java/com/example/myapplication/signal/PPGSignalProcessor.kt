package com.example.myapplication.signal

import java.util.*

/**
 * Procesa el flujo de muestras PPG, aplicando filtrado y calculando métricas de calidad.
 */
class PpgSignalProcessor(samplingRate: Double) {

    private val filter = PPGBandpassFilter(samplingRate)
    private val qualityAnalyzer = PpgSignalQuality()
    
    private val bufferSize = 300 // ~10 segundos a 30fps
    private val redBuffer = LinkedList<Double>()
    private val filteredBuffer = LinkedList<Double>()
    
    var lastQuality: PpgSignalQuality.QualityResult? = null
        private set

    fun process(sample: PpgSample): PpgSample {
        // 1. Filtrado
        val filtered = filter.filter(sample.red)
        
        // 2. Gestión de Buffers
        redBuffer.addLast(sample.red)
        filteredBuffer.addLast(filtered)
        if (redBuffer.size > bufferSize) {
            redBuffer.removeFirst()
            filteredBuffer.removeFirst()
        }
        
        // 3. Cálculo de AC/DC dinámico (ventana corta para SpO2 y PI)
        val dc = if (redBuffer.size > 10) redBuffer.takeLast(10).average() else sample.red
        val ac = if (filteredBuffer.size > 10) {
            val window = filteredBuffer.takeLast(10)
            window.maxOrNull()!! - window.minOrNull()!!
        } else 0.0

        // 4. Análisis de Calidad
        lastQuality = qualityAnalyzer.analyze(sample, filteredBuffer, ac, dc)

        return sample.copy(
            redFiltered = filtered,
            greenFiltered = 0.0 // Opcional si se quiere filtrar verde también
        )
    }
    
    fun getFilteredBuffer(): List<Double> = filteredBuffer.toList()
}
