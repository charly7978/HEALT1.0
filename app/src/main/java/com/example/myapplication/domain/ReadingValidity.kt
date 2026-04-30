package com.example.myapplication.domain

/**
 * Validez de una lectura fisiológica.
 * Determina si los datos son confiables para presentación.
 */
enum class ReadingValidity {
    VALID,
    LOW_QUALITY,
    INSUFFICIENT_DATA,
    MOTION_CORRUPTED,
    OPTICAL_SATURATION,
    CALIBRATION_MISSING
}
