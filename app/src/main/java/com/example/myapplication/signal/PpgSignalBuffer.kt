package com.example.myapplication.signal

/**
 * Buffer circular especializado para señales PPG.
 * Mantiene ventanas temporales con timestamps para análisis de señal.
 * Versión unificada - solo soporta PpgFrame.
 */
class PpgSignalBuffer(
    private val maxSize: Int,
    private val samplingRate: Double
) {
    private val frameBuffer = ArrayDeque<PpgFrame>(maxSize)
    private val timestampBuffer = ArrayDeque<Long>(maxSize)

    // Buffers de señales procesadas
    private val filteredBuffer = ArrayDeque<Double>(maxSize)
    private val rawBuffer = ArrayDeque<Double>(maxSize)

    var currentSize: Int = 0
        private set

    val isFull: Boolean
        get() = currentSize >= maxSize

    /**
     * Agrega un frame al buffer.
     */
    fun add(frame: PpgFrame, filteredValue: Double, rawValue: Double) {
        frameBuffer.addLast(frame)
        timestampBuffer.addLast(frame.timestampNs)
        filteredBuffer.addLast(filteredValue)
        rawBuffer.addLast(rawValue)

        if (frameBuffer.size > maxSize) {
            frameBuffer.removeFirst()
            timestampBuffer.removeFirst()
            filteredBuffer.removeFirst()
            rawBuffer.removeFirst()
        }

        currentSize = frameBuffer.size
    }

    /**
     * Obtiene la ventana de señal filtrada.
     */
    fun getFilteredWindow(size: Int = currentSize): List<Double> {
        return filteredBuffer.takeLast(size.coerceAtMost(currentSize))
    }

    /**
     * Obtiene la ventana de señal cruda.
     */
    fun getRawWindow(size: Int = currentSize): List<Double> {
        return rawBuffer.takeLast(size.coerceAtMost(currentSize))
    }

    /**
     * Obtiene timestamps de la ventana.
     */
    fun getTimestamps(size: Int = currentSize): List<Long> {
        return timestampBuffer.takeLast(size.coerceAtMost(currentSize))
    }

    /**
     * Obtiene frames de la ventana.
     */
    fun getFrames(size: Int = currentSize): List<PpgFrame> {
        return frameBuffer.takeLast(size.coerceAtMost(currentSize))
    }
    
    /**
     * Calcula estadísticas de la ventana actual.
     */
    fun calculateWindowStats(): WindowStats {
        if (currentSize < 2) return WindowStats(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        
        val filtered = filteredBuffer.toList()
        val mean = filtered.average()
        val variance = filtered.map { (it - mean) * (it - mean) }.average()
        val stdDev = kotlin.math.sqrt(variance)
        val min = filtered.minOrNull() ?: 0.0
        val max = filtered.maxOrNull() ?: 0.0
        
        // Tasa de cambio promedio
        var totalChange = 0.0
        for (i in 1 until filtered.size) {
            totalChange += kotlin.math.abs(filtered[i] - filtered[i-1])
        }
        val avgRateOfChange = if (filtered.size > 1) totalChange / (filtered.size - 1) else 0.0
        
        return WindowStats(mean, variance, stdDev, min, max, avgRateOfChange)
    }
    
    /**
     * Duración de la ventana en segundos.
     */
    fun getWindowDurationSeconds(): Double {
        if (currentSize < 2) return 0.0
        val timestamps = timestampBuffer.toList()
        val first = timestamps.first()
        val last = timestamps.last()
        return (last - first) / 1_000_000_000.0
    }
    
    /**
     * Calcula el FPS efectivo basado en timestamps.
     */
    fun calculateEffectiveFps(): Double {
        if (currentSize < 2) return 0.0
        val duration = getWindowDurationSeconds()
        return if (duration > 0) currentSize / duration else 0.0
    }
    
    fun clear() {
        frameBuffer.clear()
        timestampBuffer.clear()
        filteredBuffer.clear()
        rawBuffer.clear()
        currentSize = 0
    }
    
    /**
     * Estadísticas de ventana de señal.
     */
    data class WindowStats(
        val mean: Double,
        val variance: Double,
        val stdDev: Double,
        val min: Double,
        val max: Double,
        val avgRateOfChange: Double
    )
}
