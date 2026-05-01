package com.example.myapplication.signal

import com.example.myapplication.ppg.RoiStats
import com.example.myapplication.ppg.ClippingInfo
import com.example.myapplication.ppg.ExposureDiagnostics

/**
 * Frame PPG procesado con todas las características extraídas.
 * Representa un instante de medición óptica con metadata completa.
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
 * Convierte un PpgSample legacy a PpgFrame moderno.
 * Para compatibilidad durante migración.
 */
fun com.example.myapplication.ppg.PpgSample.toPpgFrame(): PpgFrame {
    return PpgFrame(
        timestampNs = this.timestampNs,
        fpsEstimate = 30.0, // Valor por defecto, debe ser actualizado por caller
        avgRed = this.rawRed,
        avgGreen = this.rawGreen,
        avgBlue = this.rawBlue,
        redDc = this.rawRed,
        greenDc = this.rawGreen,
        blueDc = this.rawBlue,
        redAc = 0.0, // Calculado por PpgFeatureExtractor
        greenAc = 0.0,
        blueAc = 0.0,
        roiRect = PpgFrame.RoiRect(
            centerX = this.roiStats.centerX,
            centerY = this.roiStats.centerY,
            width = this.roiStats.width,
            height = this.roiStats.height
        ),
        saturationRatio = this.clipping.highClipRatio,
        darknessRatio = this.clipping.lowClipRatio,
        skinLikelihood = 0.0,
        redDominance = this.rawRed / (this.rawGreen + 1.0),
        greenPulseCandidate = 0.0,
        textureScore = this.roiStats.stdGreen / 50.0,
        motionScore = this.motionScore,
        rawOpticalSignal = this.rawGreen,
        normalizedSignal = 0.0,
        clipping = this.clipping,
        exposureDiagnostics = this.exposureDiagnostics,
        perfusionIndex = 0.0
    )
}
