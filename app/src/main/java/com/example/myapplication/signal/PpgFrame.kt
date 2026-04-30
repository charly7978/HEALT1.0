package com.example.myapplication.signal

import android.graphics.Rect

/**
 * Representación cruda de un frame óptico capturado para análisis PPG.
 */
data class PpgFrame(
    val timestampNs: Long,
    val fpsEstimate: Double,
    
    // Promedios por canal (DC)
    val avgRed: Double,
    val avgGreen: Double,
    val avgBlue: Double,
    
    // Componentes AC estimadas (variación local)
    val redAc: Double,
    val greenAc: Double,
    val blueAc: Double,
    
    val roiRect: Rect,
    
    // Métricas de calidad de imagen
    val saturationRatio: Double, // % de píxeles saturados (>250)
    val darknessRatio: Double,   // % de píxeles negros (<10)
    val textureScore: Double,    // Varianza espacial (baja en piel, alta en objetos)
    
    // Indicadores de contacto
    val redDominance: Double,    // Relación Rojo / (Verde + Azul)
    val skinLikelihood: Double,  // Score probabilístico de contacto
    
    // Señal óptica pura para visualización cruda
    val rawOpticalSignal: Double = greenMeanToSignal(avgGreen)
) {
    companion object {
        private fun greenMeanToSignal(green: Double): Double = 255.0 - green
    }
}

enum class PpgValidityState {
    MEASURING_RAW_OPTICAL,          // Capturando datos, sin evidencia de PPG
    NO_PPG_PHYSIOLOGICAL_SIGNAL,    // Señal no compatible con fisiología humana (ej. objeto rojo)
    SEARCHING_PPG,                 // Buscando patrones pulsátiles
    PPG_CANDIDATE,                 // Patrón detectado pero no estabilizado
    PPG_VALID,                     // Señal confirmada, apta para métricas clínicas
    PPG_DEGRADED,                  // Señal detectada pero con ruido
    SATURATED,                     // Imagen saturada (demasiada luz o presión)
    MOTION_ARTIFACT,               // Movimiento excesivo detectado
    LOW_PERFUSION,                 // Señal muy débil
    ERROR
}
