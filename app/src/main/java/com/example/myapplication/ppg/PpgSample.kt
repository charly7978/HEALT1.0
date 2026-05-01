package com.example.myapplication.ppg

/**
 * Muestra de señal PPG cruda extraída de un frame de cámara.
 * Contiene datos ópticos sin procesamiento biomédico.
 */
data class PpgSample(
    val timestampNs: Long,
    val rawRed: Double,
    val rawGreen: Double,
    val rawBlue: Double,
    val roiStats: RoiStats,
    val clipping: ClippingInfo,
    val motionScore: Double,
    val exposureDiagnostics: ExposureDiagnostics
)

data class RoiStats(
    val centerX: Int,
    val centerY: Int,
    val width: Int,
    val height: Int,
    val pixelCount: Int,
    val medianRed: Double,
    val medianGreen: Double,
    val medianBlue: Double,
    val stdRed: Double,
    val stdGreen: Double,
    val stdBlue: Double
)

data class ClippingInfo(
    val highClipRatio: Double,  // Porcentaje de pixeles saturados (>250)
    val lowClipRatio: Double,   // Porcentaje de pixeles oscuros (<5)
    val isSaturated: Boolean,
    val isDark: Boolean
)

data class ExposureDiagnostics(
    val exposureTimeNs: Long?,
    val iso: Int?,
    val frameDurationNs: Long?,
    val torchEnabled: Boolean
)
