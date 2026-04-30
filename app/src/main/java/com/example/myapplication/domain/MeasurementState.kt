package com.example.myapplication.domain

/**
 * Estados de medición del sistema.
 * Cada estado refleja la calidad y validez de la señal capturada.
 */
enum class MeasurementState {
    NO_CONTACT,
    CONTACT_PARTIAL,
    WARMUP,
    MEASURING,
    DEGRADED,
    INVALID,
    CALIBRATION_REQUIRED
}
