package com.example.myapplication.ppg

import kotlin.math.abs

/**
 * Screening de irregularidad de pulso basado en PPG.
 * Detecta patrones de irregularidad sin hacer diagnóstico médico.
 * 
 * ADVERTENCIA: Esta app trabaja con PPG, no ECG.
 * Solo indica irregularidad de pulso, no diagnósticos cardíacos.
 */
class ArrhythmiaScreening {

    private val beatHistory = ArrayList<BeatEvent>(50)
    private val maxHistorySize = 50

    private var irregularityScore: Double = 0.0
    private var lastIrregularityAssessment: Long = 0

    data class ArrhythmiaResult(
        val isIrregular: Boolean,
        val irregularityScore: Double,
        val pattern: ArrhythmiaPattern,
        val message: String
    )

    enum class ArrhythmiaPattern {
        REGULAR,
        MILD_IRREGULARITY,
        MODERATE_IRREGULARITY,
        SIGNIFICANT_IRREGULARITY,
        INSUFFICIENT_DATA
    }

    /**
     * Evalúa si hay irregularidad en el pulso.
     * Solo evalúa si hay suficientes latidos y calidad de señal.
     */
    fun evaluate(beat: BeatEvent, sqi: Double): ArrhythmiaResult {
        // Requerir calidad mínima
        if (sqi < 0.6) {
            return ArrhythmiaResult(
                isIrregular = false,
                irregularityScore = 0.0,
                pattern = ArrhythmiaPattern.INSUFFICIENT_DATA,
                message = "Señal insuficiente para evaluación"
            )
        }

        // Agregar latido al historial
        beatHistory.add(beat)
        if (beatHistory.size > maxHistorySize) {
            beatHistory.removeAt(0)
        }

        // Necesitar al menos 10 latidos para evaluación
        if (beatHistory.size < 10) {
            return ArrhythmiaResult(
                isIrregular = false,
                irregularityScore = 0.0,
                pattern = ArrhythmiaPattern.INSUFFICIENT_DATA,
                message = "Insuficientes latidos para evaluación"
            )
        }

        // Calcular métricas de irregularidad
        val rrValues = beatHistory.mapNotNull { it.rrMs }
        if (rrValues.size < 8) {
            return ArrhythmiaResult(
                isIrregular = false,
                irregularityScore = 0.0,
                pattern = ArrhythmiaPattern.INSUFFICIENT_DATA,
                message = "Insuficientes intervalos RR válidos"
            )
        }

        // 1. Coeficiente de variación de RR
        val meanRr = rrValues.average()
        val variance = rrValues.map { (it - meanRr) * (it - meanRr) }.average()
        val stdDev = kotlin.math.sqrt(variance)
        val cv = if (meanRr > 0) (stdDev / meanRr) * 100.0 else 0.0

        // 2. RMSSD (Root Mean Square of Successive Differences)
        var sumDiffSq = 0.0
        for (i in 0 until rrValues.size - 1) {
            val diff = abs(rrValues[i + 1] - rrValues[i])
            sumDiffSq += diff * diff
        }
        val rmssd = if (rrValues.size > 1) {
            kotlin.math.sqrt(sumDiffSq / (rrValues.size - 1))
        } else {
            0.0
        }

        // 3. Conteo de latidos anómalos
        val abnormalBeats = beatHistory.count { 
            it.type != BeatType.NORMAL && it.type != BeatType.INVALID_SIGNAL
        }
        val abnormalRatio = abnormalBeats.toDouble() / beatHistory.size

        // 4. Score compuesto de irregularidad
        val cvScore = (cv / 20.0).coerceIn(0.0, 1.0) // CV > 20% es irregular
        val rmssdScore = (rmssd / 50.0).coerceIn(0.0, 1.0) // RMSSD > 50ms es irregular
        val abnormalScore = abnormalRatio * 2.0 // Penalizar latidos anómalos

        irregularityScore = (cvScore * 0.4 + rmssdScore * 0.3 + abnormalScore * 0.3).coerceIn(0.0, 1.0)

        // 5. Determinar patrón
        val pattern = when {
            irregularityScore < 0.2 -> ArrhythmiaPattern.REGULAR
            irregularityScore < 0.4 -> ArrhythmiaPattern.MILD_IRREGULARITY
            irregularityScore < 0.6 -> ArrhythmiaPattern.MODERATE_IRREGULARITY
            else -> ArrhythmiaPattern.SIGNIFICANT_IRREGULARITY
        }

        // 6. Mensaje descriptivo
        val message = when (pattern) {
            ArrhythmiaPattern.REGULAR -> "Ritmo regular"
            ArrhythmiaPattern.MILD_IRREGULARITY -> "Variabilidad leve del pulso"
            ArrhythmiaPattern.MODERATE_IRREGULARITY -> "Variabilidad moderada del pulso"
            ArrhythmiaPattern.SIGNIFICANT_IRREGULARITY -> "Variabilidad significativa del pulso"
            ArrhythmiaPattern.INSUFFICIENT_DATA -> "Datos insuficientes"
        }

        return ArrhythmiaResult(
            isIrregular = pattern != ArrhythmiaPattern.REGULAR,
            irregularityScore = irregularityScore,
            pattern = pattern,
            message = message
        )
    }

    fun reset() {
        beatHistory.clear()
        irregularityScore = 0.0
        lastIrregularityAssessment = 0
    }
}
