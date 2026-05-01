package com.example.myapplication.signal

/**
 * Frame PPG procesado con todas las características extraídas.
 * Representa un instante de medición óptica con metadata completa.
 * Estructura unificada - única fuente de verdad para datos PPG.
 */
data class PpgFrame(
    // Timestamp
    val timestampNs: Long,
    val fpsEstimate: Double,

    // Valores medios de canales RGB en ROI
    val avgRed: Double,
    val avgGreen: Double,
    val avgBlue: Double,

    // Componentes DC (baseline) de cada canal
    val redDc: Double,
    val greenDc: Double,
    val blueDc: Double,

    // Componentes AC (pulsátiles) de cada canal
    val redAc: Double,
    val greenAc: Double,
    val blueAc: Double,

    // Información de ROI
    val roiRect: RoiRect,

    // Métricas de calidad del frame
    val saturationRatio: Double,     // Grado de saturación por canal
    val darknessRatio: Double,       // Porcentaje de píxeles oscuros
    val skinLikelihood: Double,      // Probabilidad de que sea piel (0-1)
    val redDominance: Double,        // Ratio de dominancia roja
    val greenPulseCandidate: Double, // Candidato a pulso en canal verde
    val textureScore: Double,        // Score de textura (bajo = uniforme)
    val motionScore: Double,         // Score de movimiento (0 = estático)

    // Señales procesadas
    val rawOpticalSignal: Double,    // Señal óptica cruda (tipicamente verde)
    val normalizedSignal: Double,    // Señal normalizada 0-1

    // Metadata de captura
    val clipping: ClippingInfo,
    val exposureDiagnostics: ExposureDiagnostics,
    val perfusionIndex: Double       // (AC/DC) * 100
) {
    /**
     * Información geométrica del ROI.
     */
    data class RoiRect(
        val centerX: Int,
        val centerY: Int,
        val width: Int,
        val height: Int
    )
}

/**
 * Estadísticas de la región de interés (ROI).
 */
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

/**
 * Información de saturación/clipping de la señal.
 */
data class ClippingInfo(
    val highClipRatio: Double,  // Porcentaje de pixeles saturados (>250)
    val lowClipRatio: Double,   // Porcentaje de pixeles oscuros (<5)
    val isSaturated: Boolean,
    val isDark: Boolean
)

/**
 * Diagnósticos de exposición de la cámara.
 */
data class ExposureDiagnostics(
    val exposureTimeNs: Long?,
    val iso: Int?,
    val frameDurationNs: Long?,
    val torchEnabled: Boolean
)
