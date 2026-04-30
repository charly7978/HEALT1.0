package com.example.myapplication.signal

/**
 * Representa una muestra de señal óptica capturada en un instante de tiempo.
 */
data class PpgSample(
    val timestampNs: Long,
    val red: Double,
    val green: Double,
    val blue: Double,
    val redFiltered: Double = 0.0,
    val greenFiltered: Double = 0.0,
    val actualFps: Double = 0.0,
    val roiStats: RoiStats = RoiStats(),
    val diagnostics: SampleDiagnostics = SampleDiagnostics()
)

data class RoiStats(
    val meanRed: Double = 0.0,
    val medianRed: Double = 0.0,
    val stdDevRed: Double = 0.0,
    val meanGreen: Double = 0.0,
    val meanBlue: Double = 0.0
)

data class SampleDiagnostics(
    val clippingHigh: Boolean = false,
    val clippingLow: Boolean = false,
    val lowLight: Boolean = false,
    val motionScore: Double = 0.0,
    val exposureTimeNs: Long = 0,
    val iso: Int = 0
)
