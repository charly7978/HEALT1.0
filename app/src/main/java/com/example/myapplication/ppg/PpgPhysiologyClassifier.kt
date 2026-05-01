package com.example.myapplication.ppg

import kotlin.math.abs

/**
 * Clasificador fisiológico PPG estricto.
 * Distingue entre datos ópticos crudos y señal fisiológica real.
 * REGLA DE ORO: Nunca declara PPG válido sin evidencia fisiológica real.
 */
class PpgPhysiologyClassifier {

    /**
     * Estados de validez PPG según especificación.
     * La app nunca deja de medir, pero solo PPG_VALID habilita métricas biomédicas.
     */
    enum class PpgValidityState {
        MEASURING_RAW_OPTICAL,      // Midiendo datos ópticos crudos, sin validación
        NO_PPG_PHYSIOLOGICAL_SIGNAL, // No hay componente pulsátil fisiológico (sábana, pared, aire)
        SEARCHING_PPG,              // Buscando señal fisiológica, acumulando evidencia
        PPG_CANDIDATE,              // Posible PPG, requiere más ventana temporal
        PPG_VALID,                  // PPG confirmado, métricas biomédicas habilitadas
        PPG_DEGRADED,               // PPG válido pero calidad degradada
        SATURATED,                  // Señal saturada/clipping
        MOTION_ARTIFACT,            // Artefacto de movimiento detectado
        LOW_PERFUSION,              // Perfusión insuficiente
        ERROR                       // Error técnico en procesamiento
    }

    data class ClassificationResult(
        val state: PpgValidityState,
        val confidence: Double,
        val dominantFrequencyHz: Double,
        val bpmEstimate: Double,
        val isPeriodical: Boolean,
        val perfusionIndex: Double,
        val reason: String,
        val isPhysiologicalSignal: Boolean
    )

    // Buffer para análisis temporal (mínimo 8-12 segundos para validación inicial)
    private val signalBuffer = java.util.LinkedList<Double>()
    private val timestampBuffer = java.util.LinkedList<Long>()
    private val redDcBuffer = java.util.LinkedList<Double>()
    private val greenAcBuffer = java.util.LinkedList<Double>()
    
    // Ventanas requeridas según especificación
    private val minValidationWindow = 240  // 8 segundos a 30 FPS
    private val robustWindow = 600         // 20 segundos a 30 FPS
    
    // Contadores de estabilidad
    private var consecutiveValidFrames = 0
    private var consecutiveInvalidFrames = 0
    private var candidateStartTime: Long = 0
    
    // Estado anterior para transiciones
    private var previousState = PpgValidityState.MEASURING_RAW_OPTICAL

    /**
     * Clasifica la señal PPG con criterios estrictos de validación fisiológica.
     * 
     * @param sample Muestra PPG con datos ópticos crudos
     * @param filteredSignal Señal filtrada bandpass
     * @param acRed Componente AC rojo (pulsátil)
     * @param dcRed Componente DC rojo (baseline)
     * @param acGreen Componente AC verde (pulsátil)
     * @param dcGreen Componente DC verde (baseline)
     * @param sqi Signal Quality Index (0.0 - 1.0)
     * @param samplingRate FPS efectivo
     */
    fun classify(
        sample: PpgSample,
        filteredSignal: Double,
        acRed: Double,
        dcRed: Double,
        acGreen: Double,
        dcGreen: Double,
        sqi: Double,
        samplingRate: Double = 30.0
    ): ClassificationResult {
        
        val timestamp = sample.timestampNs
        
        // Actualizar buffers
        signalBuffer.addLast(filteredSignal)
        timestampBuffer.addLast(timestamp)
        redDcBuffer.addLast(sample.rawRed)
        greenAcBuffer.addLast(acGreen)
        
        if (signalBuffer.size > robustWindow) {
            signalBuffer.removeFirst()
            timestampBuffer.removeFirst()
            redDcBuffer.removeFirst()
            greenAcBuffer.removeFirst()
        }
        
        // ========== 1. ANÁLISIS DE SATURACIÓN/CLIPPING ==========
        if (sample.clipping.isSaturated || sample.clipping.highClipRatio > 0.2) {
            consecutiveValidFrames = 0
            return buildResult(
                PpgValidityState.SATURATED, 
                0.0, 0.0, 0.0, false, 0.0,
                "Señal saturada - reducir exposición o alejar dedo", false
            )
        }
        
        if (sample.clipping.isDark || sample.clipping.lowClipRatio > 0.8) {
            consecutiveValidFrames = 0
            return buildResult(
                PpgValidityState.MEASURING_RAW_OPTICAL,
                0.0, 0.0, 0.0, false, 0.0,
                "Señal oscura - acercar dedo o verificar flash", false
            )
        }
        
        // ========== 2. ANÁLISIS DE PERFUSIÓN ==========
        val piRed = if (dcRed > 0) (acRed / dcRed) * 100.0 else 0.0
        val piGreen = if (dcGreen > 0) (acGreen / dcGreen) * 100.0 else 0.0
        
        if (piRed < 0.02 && piGreen < 0.02) {
            consecutiveValidFrames = 0
            return buildResult(
                PpgValidityState.LOW_PERFUSION,
                0.0, 0.0, 0.0, false, max(piRed, piGreen),
                "Perfusión insuficiente - presionar más fuerte o revisar posición", false
            )
        }
        
        // ========== 3. ANÁLISIS DE DOMINANCIA ROJA ==========
        // Tejido humano traslúcido: dominancia roja pero NO saturación total
        val redRatio = sample.rawRed / (sample.rawGreen + 1.0)
        val isRedDominant = sample.rawRed > sample.rawGreen * 1.3 && 
                           sample.rawRed > sample.rawBlue * 1.5
        
        // Una sábana roja tiene dominancia roja pero sin componente AC pulsátil
        val hasAcPulsatility = acRed > 0.03 || acGreen > 0.03
        
        if (!isRedDominant || !hasAcPulsatility) {
            consecutiveValidFrames = 0
            val reason = when {
                !isRedDominant -> "No hay dominancia roja - posible objeto no traslúcido o aire"
                else -> "Sin componente pulsátil AC - posible objeto estático (sábana, pared)"
            }
            return buildResult(
                PpgValidityState.NO_PPG_PHYSIOLOGICAL_SIGNAL,
                0.0, 0.0, 0.0, false, max(piRed, piGreen),
                reason, false
            )
        }
        
        // ========== 4. ANÁLISIS DE MOVIMIENTO ==========
        if (sample.motionScore > 0.5) {
            consecutiveValidFrames = 0
            return buildResult(
                PpgValidityState.MOTION_ARTIFACT,
                0.0, 0.0, 0.0, false, max(piRed, piGreen),
                "Artefacto de movimiento detectado - mantener dedo estable", false
            )
        }
        
        // ========== 5. ANÁLISIS DE FRECUENCIA DOMINANTE ==========
        val (dominantFreq, isPhysiologicalFreq, periodicityScore) = analyzeFrequencyContent(
            samplingRate
        )
        val bpmEstimate = dominantFreq * 60.0
        
        // ========== 6. VALIDACIÓN TEMPORAL ==========
        // Requerir ventana mínima de 8-12 segundos para validación inicial
        if (signalBuffer.size < minValidationWindow) {
            return buildResult(
                PpgValidityState.SEARCHING_PPG,
                periodicityScore * 0.5, dominantFreq, bpmEstimate, 
                isPhysiologicalFreq, max(piRed, piGreen),
                "Acumulando datos ópticos (${signalBuffer.size}/${minValidationWindow} frames)...", false
            )
        }
        
        // ========== 7. VALIDACIÓN DE PERIODICIDAD ==========
        if (!isPhysiologicalFreq || periodicityScore < 0.4) {
            consecutiveValidFrames = 0
            return buildResult(
                PpgValidityState.NO_PPG_PHYSIOLOGICAL_SIGNAL,
                periodicityScore, dominantFreq, bpmEstimate, false, max(piRed, piGreen),
                "Frecuencia ${"%.1f".format(bpmEstimate)} BPM fuera de rango fisiológico o no periódica", false
            )
        }
        
        // ========== 8. VALIDACIÓN DE SQI ==========
        if (sqi < 0.3) {
            consecutiveValidFrames = 0
            return buildResult(
                PpgValidityState.PPG_CANDIDATE,
                sqi, dominantFreq, bpmEstimate, isPhysiologicalFreq, max(piRed, piGreen),
                "SQI insuficiente (${"%.2f".format(sqi)} < 0.3)", false
            )
        }
        
        // ========== 9. TRANSIENTES Y ESTABILIDAD ==========
        // Detectar cambios bruscos en DC que indican movimiento o pérdida de contacto
        val dcStability = calculateDcStability()
        if (!dcStability && sample.motionScore > 0.2) {
            consecutiveValidFrames = 0
            return buildResult(
                PpgValidityState.PPG_DEGRADED,
                sqi * 0.7, dominantFreq, bpmEstimate, isPhysiologicalFreq, max(piRed, piGreen),
                "PPG válido pero inestable - estabilizar dedo", true
            )
        }
        
        // ========== 10. CLASIFICACIÓN FINAL ==========
        consecutiveValidFrames++
        consecutiveInvalidFrames = 0
        
        // Requerir mínimo de frames consecutivos válidos para confirmar PPG_VALID
        val minConsecutiveValid = (samplingRate * 2).toInt() // 2 segundos de validez continua
        
        val finalState = when {
            consecutiveValidFrames < minConsecutiveValid -> PpgValidityState.PPG_CANDIDATE
            sqi >= 0.7 && periodicityScore >= 0.7 && dcStability -> PpgValidityState.PPG_VALID
            else -> PpgValidityState.PPG_DEGRADED
        }
        
        val confidence = (sqi * 0.4 + periodicityScore * 0.4 + (if (dcStability) 0.2 else 0.1))
            .coerceIn(0.0, 1.0)
        
        val reason = when (finalState) {
            PpgValidityState.PPG_CANDIDATE -> 
                "Candidato PPG - validando ${consecutiveValidFrames}/${minConsecutiveValid} frames"
            PpgValidityState.PPG_VALID -> 
                "PPG fisiológico confirmado - ${"%.0f".format(bpmEstimate)} BPM, SQI ${"%.2f".format(sqi)}"
            PpgValidityState.PPG_DEGRADED -> 
                "PPG degradado - estabilizar señal"
            else -> "Estado indeterminado"
        }
        
        previousState = finalState
        
        return buildResult(
            finalState, confidence, dominantFreq, bpmEstimate, 
            isPhysiologicalFreq, max(piRed, piGreen), reason,
            finalState == PpgValidityState.PPG_VALID || finalState == PpgValidityState.PPG_DEGRADED
        )
    }
    
    private fun buildResult(
        state: PpgValidityState,
        confidence: Double,
        dominantFreq: Double,
        bpmEstimate: Double,
        isPeriodical: Boolean,
        perfusionIndex: Double,
        reason: String,
        isPhysiological: Boolean
    ): ClassificationResult {
        return ClassificationResult(
            state = state,
            confidence = confidence,
            dominantFrequencyHz = dominantFreq,
            bpmEstimate = bpmEstimate,
            isPeriodical = isPeriodical,
            perfusionIndex = perfusionIndex,
            reason = reason,
            isPhysiologicalSignal = isPhysiological
        )
    }
    
    /**
     * Análisis de contenido frecuencial usando autocorrelación.
     * Más robusto que zero-crossing para señales PPG reales.
     */
    private fun analyzeFrequencyContent(samplingRate: Double): Triple<Double, Boolean, Double> {
        if (signalBuffer.size < minValidationWindow) {
            return Triple(0.0, false, 0.0)
        }
        
        val signal = signalBuffer.toList()
        val n = signal.size
        
        // Calcular autocorrelación
        val maxLag = (samplingRate * 2.0).toInt() // Hasta 2 segundos de lag
        var bestLag = 0
        var bestCorrelation = 0.0
        
        val mean = signal.average()
        val variance = signal.map { (it - mean) * (it - mean) }.average()
        
        if (variance < 0.001) return Triple(0.0, false, 0.0) // Señal plana
        
        for (lag in (samplingRate * 0.3).toInt()..minOf(maxLag, n / 2)) {
            var correlation = 0.0
            var count = 0
            for (i in lag until n) {
                correlation += (signal[i] - mean) * (signal[i - lag] - mean)
                count++
            }
            correlation /= count
            
            if (correlation > bestCorrelation) {
                bestCorrelation = correlation
                bestLag = lag
            }
        }
        
        // Calcular periodicidad desde autocorrelación
        val periodicityScore = if (variance > 0) (bestCorrelation / variance).coerceIn(0.0, 1.0) else 0.0
        
        // Frecuencia dominante desde lag
        val dominantFreq = if (bestLag > 0) samplingRate / bestLag else 0.0
        
        // Rango fisiológico: 0.5-4.0 Hz (30-240 BPM), preferente 0.7-3.5 Hz
        val isPhysiological = dominantFreq in 0.5..4.0 && periodicityScore > 0.3
        
        return Triple(dominantFreq, isPhysiological, periodicityScore)
    }
    
    /**
     * Calcula estabilidad del componente DC (baseline).
     * Detecta cambios bruscos que indican pérdida de contacto o movimiento.
     */
    private fun calculateDcStability(): Boolean {
        if (redDcBuffer.size < 60) return true // No hay suficiente historial
        
        val recent = redDcBuffer.takeLast(30) // Último segundo
        val older = redDcBuffer.take(30) // Segundo anterior
        
        val recentMean = recent.average()
        val olderMean = older.average()
        
        val changeRatio = abs(recentMean - olderMean) / (olderMean + 1.0)
        
        return changeRatio < 0.05 // Menos del 5% de cambio
    }
    
    fun reset() {
        signalBuffer.clear()
        timestampBuffer.clear()
        redDcBuffer.clear()
        greenAcBuffer.clear()
        consecutiveValidFrames = 0
        consecutiveInvalidFrames = 0
        candidateStartTime = 0
        previousState = PpgValidityState.MEASURING_RAW_OPTICAL
    }
}
