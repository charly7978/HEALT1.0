package com.example.myapplication.ppg

import kotlin.math.abs

/**
 * Clasificador de latidos basado en morfología e intervalos RR.
 * Clasifica latidos como normales, prematuros, pausas, perdidos o irregulares.
 * 
 * IMPORTANTE: Solo clasifica si hay suficiente calidad de señal.
 */
class BeatClassifier {

    private val rrHistory = ArrayList<Double>(30)
    private val maxHistorySize = 30

    private var medianRr: Double = 0.0
    private var rrCv: Double = 0.0 // Coeficiente de variación

    /**
     * Clasifica un latido basado en su intervalo RR y el historial.
     * Retorna el tipo de latido y una razón descriptiva.
     */
    fun classify(beat: BeatEvent, sqi: Double): BeatEvent {
        // Si SQI es bajo, no clasificar
        if (sqi < 0.6) {
            return beat.copy(
                type = BeatType.INVALID_SIGNAL,
                reason = "Señal insuficiente para clasificar"
            )
        }

        // Agregar RR al historial si existe
        beat.rrMs?.let { rr ->
            rrHistory.add(rr)
            if (rrHistory.size > maxHistorySize) {
                rrHistory.removeAt(0)
            }
            updateStatistics()
        }

        // Si no hay suficiente historial, clasificar como normal por defecto
        if (rrHistory.size < 5) {
            return beat.copy(
                type = BeatType.NORMAL,
                reason = "Historial insuficiente"
            )
        }

        // Clasificación basada en RR actual vs mediana
        val currentRr = beat.rrMs ?: return beat.copy(
            type = BeatType.INVALID_SIGNAL,
            reason = "Sin intervalo RR"
        )

        val rrDiff = abs(currentRr - medianRr)
        val rrDiffPercent = (rrDiff / medianRr) * 100.0

        return when {
            // Latido prematuro: RR significativamente más corto que la mediana
            currentRr < medianRr * 0.7 && rrDiffPercent > 30.0 -> {
                beat.copy(
                    type = BeatType.SUSPECT_PREMATURE,
                    reason = "RR ${(currentRr - medianRr).toInt()}ms más corto que mediana"
                )
            }
            // Pausa sospechosa: RR significativamente más largo que la mediana
            currentRr > medianRr * 1.5 && rrDiffPercent > 50.0 -> {
                beat.copy(
                    type = BeatType.SUSPECT_PAUSE,
                    reason = "RR ${(currentRr - medianRr).toInt()}ms más largo que mediana"
                )
            }
            // Latido perdido: RR muy largo (> 1.8x mediana)
            currentRr > medianRr * 1.8 -> {
                beat.copy(
                    type = BeatType.SUSPECT_MISSED,
                    reason = "RR ${(currentRr - medianRr).toInt()}ms, posible latido perdido"
                )
            }
            // Irregular: variabilidad alta pero no extrema
            rrCv > 10.0 && rrDiffPercent > 15.0 -> {
                beat.copy(
                    type = BeatType.IRREGULAR,
                    reason = "Variabilidad RR ${"%.1f".format(rrCv)}%"
                )
            }
            // Normal por defecto
            else -> {
                beat.copy(
                    type = BeatType.NORMAL,
                    reason = "Morfología y RR dentro de rango normal"
                )
            }
        }
    }

    /**
     * Actualiza estadísticas de RR (mediana y CV).
     */
    private fun updateStatistics() {
        if (rrHistory.size < 3) return

        val sorted = rrHistory.sorted()
        medianRr = sorted[sorted.size / 2]

        val mean = rrHistory.average()
        val variance = rrHistory.map { (it - mean) * (it - mean) }.average()
        val stdDev = kotlin.math.sqrt(variance)

        rrCv = if (mean > 0) (stdDev / mean) * 100.0 else 0.0
    }

    /**
     * Obtiene el coeficiente de variación actual de RR.
     */
    fun getRrCv(): Double = rrCv

    /**
     * Obtiene la mediana actual de RR.
     */
    fun getMedianRr(): Double = medianRr

    fun reset() {
        rrHistory.clear()
        medianRr = 0.0
        rrCv = 0.0
    }
}
