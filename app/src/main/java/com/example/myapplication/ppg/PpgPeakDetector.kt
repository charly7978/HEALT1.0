package com.example.myapplication.ppg

import kotlin.math.abs

/**
 * Evento de latido detectado con metadatos completos.
 * Solo emitido cuando PpgValidityState == PPG_VALID.
 */
data class BeatEvent(
    val timestampNs: Long,
    val rrMs: Double?,           // Intervalo RR en ms desde el latido anterior
    val instantaneousBpm: Double?, // BPM instantáneo calculado de este RR
    val confidence: Double,        // Confianza 0.0-1.0 basada en SQI y prominencia
    val amplitude: Double,         // Amplitud del pico en señal filtrada
    val prominence: Double,      // Prominencia del pico sobre umbral
    val sourceChannel: String    // Canal fuente: "green", "red", "blue"
)

/**
 * Detector de picos PPG con validación estricta.
 * REGLA CRÍTICA: Solo detecta picos cuando PpgValidityState == PPG_VALID.
 * No genera latidos falsos sobre objetos, sábanas o señal no fisiológica.
 */
class PpgPeakDetector {

    // Configuración de límites fisiológicos
    companion object {
        const val MIN_RR_MS = 300L        // 200 BPM máximo (extremo)
        const val PREFERRED_MIN_RR_MS = 350L // 171 BPM (evita dobles picos)
        const val MAX_RR_MS = 2000L       // 30 BPM mínimo (bradicardia severa)
        const val MIN_PROMINENCE = 0.05   // Prominencia mínima relativa
        const val REFRACTORY_MS = 250L    // Período refractario post-latido
    }

    private var lastPeakTimeNs: Long = 0
    private var lastPeakValue: Double = 0.0
    private var lastValue: Double = 0.0
    private var isRising: Boolean = false
    private var adaptiveThreshold: Double = 0.0
    private var peakCount = 0
    private val rrHistory = java.util.LinkedList<Long>()
    
    // Tracking de estadísticas
    private var maxAmplitudeSeen: Double = 0.0
    private var sinceLastPeakMs: Long = 0

    /**
     * Intenta detectar un pico/latido en la señal filtrada.
     * 
     * REQUISITOS OBLIGATORIOS:
     * - validityState debe ser PPG_VALID o PPG_DEGRADED
     * - sqi >= umbral mínimo
     * - Período refractario cumplido
     * - Prominencia mínima sobre umbral adaptativo
     * - Intervalo RR fisiológico
     * 
     * @param filteredValue Valor de señal filtrada bandpass
     * @param timestampNs Timestamp monotónico en nanosegundos
     * @param sqi Signal Quality Index (0.0-1.0)
     * @param validityState Estado de validez PPG del clasificador
     * @param acComponent Componente AC (amplitud pulsátil) del canal usado
     * @return BeatEvent si se detectó latido válido, null en caso contrario
     */
    fun detect(
        filteredValue: Double,
        timestampNs: Long,
        sqi: Double,
        validityState: PpgPhysiologyClassifier.PpgValidityState,
        acComponent: Double
    ): BeatEvent? {
        
        // ========== VALIDACIÓN DE ESTADO FISIOLÓGICO ==========
        // REGLA CRÍTICA: Solo detectar si hay PPG validado
        if (validityState != PpgPhysiologyClassifier.PpgValidityState.PPG_VALID &&
            validityState != PpgPhysiologyClassifier.PpgValidityState.PPG_DEGRADED) {
            // Actualizar tracking pero no detectar
            updateTracking(filteredValue, timestampNs)
            return null
        }
        
        // ========== VALIDACIÓN DE CALIDAD ==========
        if (sqi < 0.4) {
            updateTracking(filteredValue, timestampNs)
            return null
        }
        
        // ========== VALIDACIÓN DE COMPONENTE PULSÁTIL ==========
        if (acComponent < 0.02) {
            updateTracking(filteredValue, timestampNs)
            return null
        }
        
        val currentTimeMs = timestampNs / 1_000_000
        val lastPeakMs = lastPeakTimeNs / 1_000_000
        
        // ========== PERÍODO REFRACTARIO ==========
        val timeSinceLastPeak = if (lastPeakTimeNs > 0) {
            currentTimeMs - lastPeakMs
        } else Long.MAX_VALUE
        
        if (timeSinceLastPeak < REFRACTORY_MS) {
            updateTracking(filteredValue, timestampNs)
            return null
        }
        
        // ========== DETECCIÓN DE PICO ==========
        val slope = filteredValue - lastValue
        var beat: BeatEvent? = null
        
        // Detectar máximo local: estaba subiendo y ahora baja
        if (isRising && slope < 0 && lastValue > adaptiveThreshold) {
            
            // ========== VALIDAR PROMINENCIA ==========
            val prominence = lastValue - adaptiveThreshold
            val relativeProminence = if (maxAmplitudeSeen > 0) {
                prominence / maxAmplitudeSeen
            } else prominence
            
            if (relativeProminence >= MIN_PROMINENCE) {
                
                // ========== VALIDAR INTERVALO RR ==========
                val rrMs = if (lastPeakTimeNs > 0) timeSinceLastPeak else null
                
                val isValidRR = rrMs == null || (rrMs in MIN_RR_MS..MAX_RR_MS)
                
                // Validar consistencia con histórico RR (evita picos ectópicos falsos)
                val isConsistentWithHistory = if (rrMs != null && rrHistory.size >= 3) {
                    val meanRR = rrHistory.average()
                    val deviation = abs(rrMs - meanRR) / meanRR
                    deviation < 0.4 // 40% de tolerancia
                } else true
                
                if (isValidRR) {
                    // Calcular confianza compuesta
                    val sqiScore = sqi.coerceIn(0.0, 1.0)
                    val prominenceScore = (relativeProminence / (relativeProminence + 0.1)).coerceIn(0.5, 1.0)
                    val consistencyScore = if (isConsistentWithHistory) 1.0 else 0.7
                    val confidence = sqiScore * 0.5 + prominenceScore * 0.3 + consistencyScore * 0.2
                    
                    // Calcular BPM instantáneo
                    val instantBpm = rrMs?.let { 60000.0 / it }
                    
                    beat = BeatEvent(
                        timestampNs = lastPeakTimeNs, // Usar timestamp del pico real
                        rrMs = rrMs?.toDouble(),
                        instantaneousBpm = instantBpm,
                        confidence = confidence,
                        amplitude = lastValue,
                        prominence = prominence,
                        sourceChannel = "green"
                    )
                    
                    // Actualizar tracking
                    if (rrMs != null) {
                        rrHistory.addLast(rrMs)
                        if (rrHistory.size > 10) rrHistory.removeFirst()
                    }
                    
                    lastPeakTimeNs = timestampNs
                    lastPeakValue = lastValue
                    peakCount++
                    
                    // Actualizar umbral adaptativo (60% del pico actual, 40% histórico)
                    adaptiveThreshold = adaptiveThreshold * 0.6 + lastValue * 0.4 * 0.5
                }
            }
        }
        
        updateTracking(filteredValue, timestampNs)
        return beat
    }
    
    private fun updateTracking(filteredValue: Double, timestampNs: Long) {
        val slope = filteredValue - lastValue
        isRising = slope > 0
        lastValue = filteredValue
        
        // Actualizar máximo amplitud visto (para normalización de prominencia)
        if (abs(filteredValue) > maxAmplitudeSeen) {
            maxAmplitudeSeen = abs(filteredValue)
        }
        
        // Decaimiento lento del umbral para adaptarse a cambios de amplitud
        adaptiveThreshold *= 0.998
        
        // Resetear umbral si queda muy bajo
        if (adaptiveThreshold < maxAmplitudeSeen * 0.05) {
            adaptiveThreshold = maxAmplitudeSeen * 0.1
        }
    }

    /**
     * Obtiene estadísticas de detección actuales.
     */
    fun getStats(): PeakDetectorStats {
        val lastRR = if (rrHistory.isNotEmpty()) rrHistory.last else null
        val meanRR = if (rrHistory.size >= 2) rrHistory.average() else null
        val currentBpm = meanRR?.let { 60000.0 / it }
        
        return PeakDetectorStats(
            peakCount = peakCount,
            lastRrMs = lastRR,
            meanRrMs = meanRR,
            currentBpm = currentBpm,
            recentRrs = rrHistory.toList()
        )
    }
    
    fun reset() {
        lastPeakTimeNs = 0
        lastPeakValue = 0.0
        lastValue = 0.0
        isRising = false
        adaptiveThreshold = 0.0
        peakCount = 0
        rrHistory.clear()
        maxAmplitudeSeen = 0.0
        sinceLastPeakMs = 0
    }
}

/**
 * Estadísticas del detector de picos.
 */
data class PeakDetectorStats(
    val peakCount: Int,
    val lastRrMs: Long?,
    val meanRrMs: Double?,
    val currentBpm: Double?,
    val recentRrs: List<Long>
)
