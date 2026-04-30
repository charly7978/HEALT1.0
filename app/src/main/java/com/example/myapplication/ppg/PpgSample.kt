package com.example.myapplication.ppg

/**
 * Muestra de señal PPG procesada.
 * Contiene valores derivados de frames de cámara reales.
 */
data class PpgSample(
    val timestampNs: Long,
    val raw: Double,
    val filtered: Double,
    val displayValue: Double,
    val sqi: Double,
    val perfusionIndex: Double,
    val motionScore: Double,
    val valid: Boolean
)
