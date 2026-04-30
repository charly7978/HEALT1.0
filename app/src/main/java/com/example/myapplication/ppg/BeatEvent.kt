package com.example.myapplication.ppg

/**
 * Evento de latido cardíaco detectado.
 * Solo se crea cuando hay evidencia real de un pico en la señal.
 */
data class BeatEvent(
    val timestampNs: Long,
    val amplitude: Double,
    val rrMs: Double?,
    val bpmInstant: Double?,
    val quality: Double,
    val type: BeatType,
    val reason: String
)

/**
 * Clasificación de tipo de latido.
 * Basada en morfología y intervalos RR reales.
 */
enum class BeatType {
    NORMAL,
    SUSPECT_PREMATURE,
    SUSPECT_PAUSE,
    SUSPECT_MISSED,
    IRREGULAR,
    INVALID_SIGNAL
}
