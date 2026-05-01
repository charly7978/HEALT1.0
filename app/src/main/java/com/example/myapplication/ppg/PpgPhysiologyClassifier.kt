package com.example.myapplication.ppg

/**
 * Clasificador fisiológico de señal PPG.
 * Distingue entre señal óptica cruda y señal fisiológica válida.
 * Evita falsos positivos de objetos rojos, sábanas, luces, etc.
 */
class PpgPhysiologyClassifier {

    enum class PpgValidityState {
        RAW_OPTICAL_ONLY,      // Solo señal óptica, no fisiológica
        NO_PHYSIOLOGICAL_SIGNAL, // No hay componente PPG
        PPG_CANDIDATE,         // Posible PPG, requiere más evidencia
        PPG_VALID,            // PPG confirmado
        BIOMETRIC_VALID       // Métricas biomédicas calculables
    }

    data class ClassificationResult(
        val state: PpgValidityState,
        val confidence: Double,
        val dominantFrequency: Double,
        val isPeriodical: Boolean,
        val morphologyScore: Double,
        val reason: String
    )

    private val frequencyBuffer = java.util.LinkedList<Double>()
    private val maxFrequencyBuffer = 60 // ~2 segundos a 30 FPS

    /**
     * Clasifica la señal PPG basado en criterios fisiológicos.
     */
    fun classify(
        sample: PpgSample,
        filteredSignal: Double,
        acRed: Double,
        acGreen: Double,
        sqi: Double
    ): ClassificationResult {
        
        // 1. Verificar dominancia roja (tejido humano traslúcido)
        val isRedDominant = sample.rawRed > (sample.rawGreen * 1.5) && 
                            sample.rawRed > (sample.rawBlue * 2.0)
        
        // 2. Verificar iluminación suficiente
        val hasSufficientLight = sample.rawRed > 50.0 && sample.rawGreen > 30.0
        
        // 3. Verificar clipping
        val hasClipping = sample.clipping.isSaturated || sample.clipping.highClipRatio > 0.3
        
        // 4. Verificar movimiento excesivo
        val hasExcessiveMotion = sample.motionScore > 0.3

        // 5. Verificar componente pulsátil
        val hasPulsatileComponent = acGreen > 0.05 && acRed > 0.05
        
        // 6. Verificar SQI
        val hasMinimumQuality = sqi > 0.4

        // 7. Análisis de frecuencia dominante
        val dominantFreq = analyzeFrequency(filteredSignal)
        val isPhysiologicalFreq = dominantFreq in 0.8..3.0 // 48-180 BPM
        val isPeriodical = isPhysiologicalFreq && hasMinimumQuality

        // 8. Puntuación de morfología
        val morphologyScore = calculateMorphologyScore(
            isRedDominant,
            hasPulsatileComponent,
            hasMinimumQuality,
            hasExcessiveMotion,
            hasClipping
        )

        // 9. Clasificación final (relajado para condiciones reales)
        val state = when {
            !hasSufficientLight -> PpgValidityState.RAW_OPTICAL_ONLY
            hasClipping -> PpgValidityState.RAW_OPTICAL_ONLY
            !isRedDominant -> PpgValidityState.NO_PHYSIOLOGICAL_SIGNAL
            !hasPulsatileComponent -> PpgValidityState.NO_PHYSIOLOGICAL_SIGNAL
            hasExcessiveMotion -> PpgValidityState.PPG_CANDIDATE
            !isPeriodical -> PpgValidityState.PPG_CANDIDATE
            sqi < 0.3 -> PpgValidityState.PPG_CANDIDATE
            sqi < 0.5 -> PpgValidityState.PPG_VALID
            else -> PpgValidityState.BIOMETRIC_VALID
        }

        val reason = when (state) {
            PpgValidityState.RAW_OPTICAL_ONLY -> 
                if (!hasSufficientLight) "Iluminación insuficiente" 
                else "Saturación óptica"
            PpgValidityState.NO_PHYSIOLOGICAL_SIGNAL -> 
                if (!isRedDominant) "No hay dominancia roja (tejido no traslúcido)" 
                else "Sin componente pulsátil"
            PpgValidityState.PPG_CANDIDATE -> 
                if (hasExcessiveMotion) "Movimiento excesivo" 
                else if (!isPeriodical) "Frecuencia no fisiológica" 
                else "Calidad insuficiente"
            PpgValidityState.PPG_VALID -> "PPG confirmado, calidad moderada"
            PpgValidityState.BIOMETRIC_VALID -> "Señal fisiológica de alta calidad"
        }

        return ClassificationResult(
            state = state,
            confidence = morphologyScore,
            dominantFrequency = dominantFreq,
            isPeriodical = isPeriodical,
            morphologyScore = morphologyScore,
            reason = reason
        )
    }

    /**
     * Analiza la frecuencia dominante de la señal.
     */
    private fun analyzeFrequency(signal: Double): Double {
        frequencyBuffer.addLast(signal)
        if (frequencyBuffer.size > maxFrequencyBuffer) frequencyBuffer.removeFirst()

        if (frequencyBuffer.size < 30) return 0.0

        // Contar cruces por cero (método simple para frecuencia)
        val mean = frequencyBuffer.average()
        var zeroCrossings = 0
        var lastSign = if (frequencyBuffer.first() > mean) 1 else -1

        for (value in frequencyBuffer) {
            val currentSign = if (value > mean) 1 else -1
            if (currentSign != lastSign) {
                zeroCrossings++
                lastSign = currentSign
            }
        }

        // Frecuencia = cruces / 2 / tiempo
        val frequency = (zeroCrossings / 2.0) / (frequencyBuffer.size / 30.0)
        return frequency
    }

    /**
     * Calcula puntuación de morfología.
     */
    private fun calculateMorphologyScore(
        isRedDominant: Boolean,
        hasPulsatileComponent: Boolean,
        hasMinimumQuality: Boolean,
        hasExcessiveMotion: Boolean,
        hasClipping: Boolean
    ): Double {
        var score = 0.0

        if (isRedDominant) score += 0.3
        if (hasPulsatileComponent) score += 0.3
        if (hasMinimumQuality) score += 0.2
        if (!hasExcessiveMotion) score += 0.1
        if (!hasClipping) score += 0.1

        return score
    }

    fun reset() {
        frequencyBuffer.clear()
    }
}
