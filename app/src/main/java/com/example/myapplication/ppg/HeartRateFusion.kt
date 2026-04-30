package com.example.myapplication.ppg

import kotlin.math.abs

/**
 * Fusión de detectores de latidos para mayor robustez.
 * Combina resultados de Elgendi y Derivative, solo aceptando latidos con consenso.
 * 
 * REGLA CRÍTICA: No sintetiza latidos. Solo acepta picos cuando hay evidencia real.
 */
class HeartRateFusion(
    private val samplingRate: Double
) {

    private val elgendiDetector = PeakDetectorElgendi(samplingRate)
    private val derivativeDetector = PeakDetectorDerivative(samplingRate)

    private val rrHistory = ArrayList<Double>(30)
    private val maxHistorySize = 30

    private var lastAcceptedBeatTimeNs: Long = 0
    private var lastBeat: BeatEvent? = null

    /**
     * Procesa una muestra y retorna un BeatEvent si se detecta un latido válido.
     * Retorna null si no hay evidencia suficiente de latido.
     */
    fun processSample(value: Double, timestampNs: Long, sqi: Double): BeatEvent? {
        // Solo procesar si la calidad es suficiente
        if (sqi < 0.5) return null

        // Ejecutar ambos detectores
        val elgendiPeak = elgendiDetector.processSample(value, timestampNs)
        val derivativePeak = derivativeDetector.processSample(value, timestampNs)

        // Estrategia de fusión:
        // 1. Consenso: ambos detectores marcan pico cercano en tiempo
        // 2. Si solo uno marca, exigir SQI alto y morfología fuerte
        // 3. Nunca inventar latidos

        val beat: BeatEvent?

        when {
            // Caso 1: Consenso de ambos detectores
            elgendiPeak != null && derivativePeak != null -> {
                val timeDiff = abs(elgendiPeak.timestampNs - derivativePeak.timestampNs)
                if (timeDiff < 50_000_000L) { // 50ms de tolerancia
                    beat = createBeatEvent(
                        timestampNs = (elgendiPeak.timestampNs + derivativePeak.timestampNs) / 2,
                        amplitude = (elgendiPeak.amplitude + derivativePeak.amplitude) / 2,
                        confidence = (elgendiPeak.confidence + derivativePeak.confidence) / 2,
                        sqi = sqi
                    )
                } else {
                    // Picos desalineados, descartar
                    beat = null
                }
            }
            // Caso 2: Solo Elgendi detecta, requiere SQI alto
            elgendiPeak != null && derivativePeak == null -> {
                if (sqi > 0.7 && elgendiPeak.confidence > 0.6) {
                    beat = createBeatEvent(
                        timestampNs = elgendiPeak.timestampNs,
                        amplitude = elgendiPeak.amplitude,
                        confidence = elgendiPeak.confidence * 0.8, // Penalizar por falta de consenso
                        sqi = sqi
                    )
                } else {
                    beat = null
                }
            }
            // Caso 3: Solo Derivative detecta, requiere SQI muy alto
            elgendiPeak == null && derivativePeak != null -> {
                if (sqi > 0.8 && derivativePeak.confidence > 0.7) {
                    beat = createBeatEvent(
                        timestampNs = derivativePeak.timestampNs,
                        amplitude = derivativePeak.amplitude,
                        confidence = derivativePeak.confidence * 0.7, // Penalizar más
                        sqi = sqi
                    )
                } else {
                    beat = null
                }
            }
            // Caso 4: Ningún detector marca pico
            else -> {
                beat = null
            }
        }

        // Si aceptamos un latido, actualizar historial
        if (beat != null) {
            // Validar intervalo RR fisiológico
            if (lastAcceptedBeatTimeNs > 0) {
                val rrMs = (beat.timestampNs - lastAcceptedBeatTimeNs) / 1_000_000.0
                if (rrMs in 250.0..2000.0) { // 30-240 BPM
                    rrHistory.add(rrMs)
                    if (rrHistory.size > maxHistorySize) {
                        rrHistory.removeAt(0)
                    }
                    lastBeat = beat.copy(rrMs = rrMs)
                } else {
                    // Intervalo inválido, rechazar
                    return null
                }
            }
            lastAcceptedBeatTimeNs = beat.timestampNs
        }

        return lastBeat
    }

    /**
     * Crea un BeatEvent a partir de datos de detección.
     */
    private fun createBeatEvent(
        timestampNs: Long,
        amplitude: Double,
        confidence: Double,
        sqi: Double
    ): BeatEvent {
        val rrMs = if (lastAcceptedBeatTimeNs > 0) {
            (timestampNs - lastAcceptedBeatTimeNs) / 1_000_000.0
        } else {
            null
        }

        val bpmInstant = rrMs?.let { 60000.0 / it }

        return BeatEvent(
            timestampNs = timestampNs,
            amplitude = amplitude,
            rrMs = rrMs,
            bpmInstant = bpmInstant,
            quality = confidence * sqi,
            type = BeatType.NORMAL, // La clasificación se hace en BeatClassifier
            reason = "Fusion detection"
        )
    }

    /**
     * Obtiene el BPM actual basado en historial de RR.
     * Retorna null si no hay suficientes datos.
     */
    fun getCurrentBpm(): Double? {
        if (rrHistory.size < 3) return null

        // Usar mediana robusta de últimos RR
        val recentRr = rrHistory.takeLast(10)
        val medianRr = recentRr.sorted()[recentRr.size / 2]
        return 60000.0 / medianRr
    }

    /**
     * Obtiene el historial de intervalos RR.
     */
    fun getRrHistory(): List<Double> = rrHistory.toList()

    fun reset() {
        elgendiDetector.reset()
        derivativeDetector.reset()
        rrHistory.clear()
        lastAcceptedBeatTimeNs = 0
        lastBeat = null
    }
}
