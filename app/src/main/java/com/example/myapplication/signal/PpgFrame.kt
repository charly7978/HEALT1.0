package com.example.myapplication.signal

/**
 * Representación simplificada de un frame óptico capturado para análisis PPG.
 * Consolidado para ser compatible con el pipeline profesional.
 */
data class PpgFrame(
    val timestampNs: Long,
    val actualFps: Double,
    val red: Double,
    val green: Double,
    val blue: Double
)
