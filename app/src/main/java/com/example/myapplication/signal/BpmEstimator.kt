package com.example.myapplication.signal

import com.example.myapplication.ppg.BeatEvent
import kotlin.math.abs

/**
 * Estimador de BPM basado en intervalos RR.
 * Calcula BPM instantáneo, promedio estable y confianza.
 */
class BpmEstimator {
    
    companion object {
        const val MIN_BEATS_FOR_BPM = 5
        const val STABLE_WINDOW_SIZE = 12 // ~12 RR intervals para estabilidad
        const val EMA_ALPHA = 0.3 // Factor de suavizado exponencial
    }
    
    private val rrBuffer = ArrayDeque<Double>(STABLE_WINDOW_SIZE)
    private var smoothedBpm: Double? = null
    private var totalBeats: Int = 0
    
    // Estadísticas
    private var lastBpm: Double? = null
    private var lastReliableBpm: Double? = null
    private var bpmConfidence: Double = 0.0
    
    /**
     * Agrega un nuevo latido para estimación de BPM.
     */
    fun addBeat(beat: BeatEvent) {
        totalBeats++
        
        val rr = beat.rrMs
        if (rr != null && rr > 0) {
            rrBuffer.addLast(rr)
            if (rrBuffer.size > STABLE_WINDOW_SIZE) {
                rrBuffer.removeFirst()
            }
            
            // Calcular BPM instantáneo
            val instantBpm = 60000.0 / rr
            
            // Actualizar EMA
            smoothedBpm = if (smoothedBpm == null) {
                instantBpm
            } else {
                EMA_ALPHA * instantBpm + (1 - EMA_ALPHA) * smoothedBpm!!
            }
            
            // Calcular confianza basada en consistencia de RR
            bpmConfidence = calculateConfidence()
            
            // Guardar último BPM confiable
            if (bpmConfidence > 0.6) {
                lastReliableBpm = smoothedBpm
            }
            
            lastBpm = instantBpm
        }
    }
    
    /**
     * Obtiene el BPM actual (suavizado EMA).
     * Retorna null si no hay suficientes latidos.
     */
    fun getCurrentBpm(): Double? {
        return if (totalBeats >= MIN_BEATS_FOR_BPM) smoothedBpm else null
    }
    
    /**
     * Obtiene el último BPM confiable (con confianza > 0.6).
     */
    fun getLastReliableBpm(): Double? {
        return lastReliableBpm
    }
    
    /**
     * Obtiene el BPM instantáneo del último latido.
     */
    fun getInstantBpm(): Double? {
        return lastBpm
    }
    
    /**
     * Obtiene la confianza del BPM actual (0.0 - 1.0).
     */
    fun getConfidence(): Double {
        return if (totalBeats >= MIN_BEATS_FOR_BPM) bpmConfidence else 0.0
    }
    
    /**
     * Obtiene estadísticas de los intervalos RR.
     */
    fun getRRStats(): RRStats {
        if (rrBuffer.size < 2) {
            return RRStats(0.0, 0.0, 0.0, 0, 0.0)
        }
        
        val rrs = rrBuffer.toList()
        val mean = rrs.average()
        val variance = rrs.map { (it - mean) * (it - mean) }.average()
        val stdDev = kotlin.math.sqrt(variance)
        val cv = if (mean > 0) (stdDev / mean) * 100.0 else 0.0 // Coeficiente de variación
        
        return RRStats(mean, stdDev, cv, rrBuffer.size, bpmConfidence)
    }
    
    /**
     * Obtiene cantidad de latidos acumulados.
     */
    fun getBeatCount(): Int {
        return totalBeats
    }
    
    /**
     * Verifica si hay suficientes datos para mostrar BPM.
     */
    fun hasEnoughData(): Boolean {
        return totalBeats >= MIN_BEATS_FOR_BPM
    }
    
    /**
     * Congela el BPM actual (usado cuando se pierde PPG_VALID).
     */
    fun freeze(): FrozenBpm {
        return FrozenBpm(
            bpm = smoothedBpm,
            confidence = bpmConfidence,
            beatCount = totalBeats,
            frozenAt = System.nanoTime()
        )
    }
    
    private fun calculateConfidence(): Double {
        if (rrBuffer.size < 3) return 0.0
        
        val rrs = rrBuffer.toList()
        val mean = rrs.average()
        
        // Calcular desviación estándar relativa (CV)
        val variance = rrs.map { (it - mean) * (it - mean) }.average()
        val stdDev = kotlin.math.sqrt(variance)
        val cv = if (mean > 0) stdDev / mean else 0.0
        
        // Calcular score de consistencia temporal
        var consistentCount = 0
        for (i in 1 until rrs.size) {
            val change = abs(rrs[i] - rrs[i-1]) / rrs[i-1]
            if (change < 0.15) consistentCount++ // 15% de tolerancia
        }
        val consistencyScore = if (rrs.size > 1) consistentCount.toDouble() / (rrs.size - 1) else 0.0
        
        // Score basado en cantidad de muestras
        val sampleScore = (rrBuffer.size.toDouble() / STABLE_WINDOW_SIZE).coerceIn(0.0, 1.0)
        
        // Confianza compuesta: inversamente proporcional a CV
        val cvScore = (1.0 - cv).coerceIn(0.0, 1.0)
        
        return (cvScore * 0.4 + consistencyScore * 0.4 + sampleScore * 0.2)
    }
    
    fun reset() {
        rrBuffer.clear()
        smoothedBpm = null
        totalBeats = 0
        lastBpm = null
        lastReliableBpm = null
        bpmConfidence = 0.0
    }
    
    /**
     * Estadísticas de intervalos RR.
     */
    data class RRStats(
        val meanRR: Double,      // ms
        val stdDev: Double,      // ms
        val cv: Double,          // %
        val sampleCount: Int,
        val confidence: Double
    )
    
    /**
     * BPM congelado cuando se pierde señal.
     */
    data class FrozenBpm(
        val bpm: Double?,
        val confidence: Double,
        val beatCount: Int,
        val frozenAt: Long
    )
}
