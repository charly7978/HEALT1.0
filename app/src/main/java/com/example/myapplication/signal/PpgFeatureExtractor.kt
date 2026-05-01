package com.example.myapplication.signal

import android.media.Image
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Extractor de características PPG de frames de cámara.
 * Calcula métricas ópticas completas sin asumir señal fisiológica.
 */
class PpgFeatureExtractor {
    
    data class ExtractedFeatures(
        // Valores medios RGB
        val avgRed: Double,
        val avgGreen: Double,
        val avgBlue: Double,
        
        // Estadísticas
        val stdRed: Double,
        val stdGreen: Double,
        val stdBlue: Double,
        
        // Rangos
        val minRed: Double,
        val maxRed: Double,
        val minGreen: Double,
        val maxGreen: Double,
        val minBlue: Double,
        val maxBlue: Double,
        
        // Métricas de calidad
        val saturatedPixelRatio: Double,
        val darkPixelRatio: Double,
        val validPixelRatio: Double,
        
        // Características espaciales
        val textureVariance: Double,
        val spatialGradient: Double,
        
        // Ratios de color
        val redGreenRatio: Double,
        val redBlueRatio: Double,
        val greenBlueRatio: Double
    )
    
    private var previousFrameFeatures: ExtractedFeatures? = null
    
    /**
     * Extrae características ópticas de un frame YUV.
     * No asume PPG - solo datos ópticos crudos.
     */
    fun extract(image: Image, roiPercent: Double = 0.15): ExtractedFeatures {
        val width = image.width
        val height = image.height
        
        // ROI central
        val roiWidth = (width * roiPercent).toInt().coerceAtLeast(10)
        val roiHeight = (height * roiPercent).toInt().coerceAtLeast(10)
        val left = (width - roiWidth) / 2
        val top = (height - roiHeight) / 2
        
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        
        val yRowStride = image.planes[0].rowStride
        val uvRowStride = image.planes[1].rowStride
        val uvPixelStride = image.planes[1].pixelStride
        
        val redValues = ArrayList<Double>(roiWidth * roiHeight)
        val greenValues = ArrayList<Double>(roiWidth * roiHeight)
        val blueValues = ArrayList<Double>(roiWidth * roiHeight)
        
        var saturatedCount = 0
        var darkCount = 0
        var validCount = 0
        
        for (y in top until min(top + roiHeight, height)) {
            for (x in left until min(left + roiWidth, width)) {
                val yIndex = y * yRowStride + x
                val uvIndex = (y / 2) * uvRowStride + (x / 2) * uvPixelStride
                
                val yVal = (yBuffer[yIndex].toInt() and 0xFF)
                val uVal = (uBuffer[uvIndex].toInt() and 0xFF) - 128
                val vVal = (vBuffer[uvIndex].toInt() and 0xFF) - 128
                
                // Conversión YUV a RGB (BT.601)
                val r = (yVal + 1.370705 * vVal).coerceIn(0.0, 255.0)
                val g = (yVal - 0.337633 * uVal - 0.698001 * vVal).coerceIn(0.0, 255.0)
                val b = (yVal + 1.732446 * uVal).coerceIn(0.0, 255.0)
                
                redValues.add(r)
                greenValues.add(g)
                blueValues.add(b)
                
                // Detectar saturación
                if (r > 250.0 || g > 250.0 || b > 250.0) saturatedCount++
                if (r < 10.0 && g < 10.0 && b < 10.0) darkCount++
                if (r in 10.0..250.0 && g in 10.0..250.0 && b in 10.0..250.0) validCount++
            }
        }
        
        val totalPixels = redValues.size
        
        // Calcular estadísticas
        val avgRed = redValues.average()
        val avgGreen = greenValues.average()
        val avgBlue = blueValues.average()
        
        val stdRed = calculateStdDev(redValues, avgRed)
        val stdGreen = calculateStdDev(greenValues, avgGreen)
        val stdBlue = calculateStdDev(blueValues, avgBlue)
        
        val minRed = redValues.minOrNull() ?: 0.0
        val maxRed = redValues.maxOrNull() ?: 255.0
        val minGreen = greenValues.minOrNull() ?: 0.0
        val maxGreen = greenValues.maxOrNull() ?: 255.0
        val minBlue = blueValues.minOrNull() ?: 0.0
        val maxBlue = blueValues.maxOrNull() ?: 255.0
        
        // Métricas de calidad
        val saturatedRatio = saturatedCount.toDouble() / totalPixels
        val darkRatio = darkCount.toDouble() / totalPixels
        val validRatio = validCount.toDouble() / totalPixels
        
        // Características espaciales
        val textureVar = (stdRed + stdGreen + stdBlue) / 3.0
        
        // Gradiente espacial simplificado (varianza de diferencias vecinas)
        val spatialGrad = calculateSpatialGradient(redValues, roiWidth, roiHeight)
        
        // Ratios de color
        val rgRatio = if (avgGreen > 0) avgRed / avgGreen else 0.0
        val rbRatio = if (avgBlue > 0) avgRed / avgBlue else 0.0
        val gbRatio = if (avgBlue > 0) avgGreen / avgBlue else 0.0
        
        val features = ExtractedFeatures(
            avgRed = avgRed,
            avgGreen = avgGreen,
            avgBlue = avgBlue,
            stdRed = stdRed,
            stdGreen = stdGreen,
            stdBlue = stdBlue,
            minRed = minRed, maxRed = maxRed,
            minGreen = minGreen, maxGreen = maxGreen,
            minBlue = minBlue, maxBlue = maxBlue,
            saturatedPixelRatio = saturatedRatio,
            darkPixelRatio = darkRatio,
            validPixelRatio = validRatio,
            textureVariance = textureVar,
            spatialGradient = spatialGrad,
            redGreenRatio = rgRatio,
            redBlueRatio = rbRatio,
            greenBlueRatio = gbRatio
        )
        
        previousFrameFeatures = features
        return features
    }
    
    /**
     * Calcula score de movimiento inter-frame basado en cambios de features.
     * Retorna 0.0 (sin movimiento) a 1.0 (movimiento severo).
     */
    fun calculateMotionScore(current: ExtractedFeatures): Double {
        val previous = previousFrameFeatures ?: return 0.0
        
        // Cambio en valores DC (baseline)
        val dcChange = abs(current.avgRed - previous.avgRed) / (previous.avgRed + 1.0)
        
        // Cambio en textura (varianza local)
        val textureChange = abs(current.textureVariance - previous.textureVariance) / 
                          (previous.textureVariance + 1.0)
        
        // Cambio en ratios de color
        val ratioChange = abs(current.redGreenRatio - previous.redGreenRatio) / 
                         (previous.redGreenRatio + 0.1)
        
        // Score compuesto
        val rawScore = (dcChange * 0.5 + textureChange * 0.3 + ratioChange * 0.2)
        
        return rawScore.coerceIn(0.0, 1.0)
    }
    
    /**
     * Estima probabilidad de que el ROI contenga piel humana.
     * Basado en características de color y textura.
     * Retorna 0.0-1.0 (no es probabilidad estadística real, es heurística).
     */
    fun estimateSkinLikelihood(features: ExtractedFeatures): Double {
        // Piel típicamente: dominancia roja, RG ratio ~1.2-1.8, textura moderada
        val redDominant = features.redGreenRatio > 1.0
        val reasonableRatio = features.redGreenRatio in 0.8..2.5
        val moderateTexture = features.textureVariance in 5.0..50.0
        val notSaturated = features.saturatedPixelRatio < 0.1
        val notDark = features.darkPixelRatio < 0.5
        
        var score = 0.0
        if (redDominant) score += 0.2
        if (reasonableRatio) score += 0.3
        if (moderateTexture) score += 0.2
        if (notSaturated) score += 0.15
        if (notDark) score += 0.15
        
        return score
    }
    
    private fun calculateStdDev(values: List<Double>, mean: Double): Double {
        if (values.size < 2) return 0.0
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance)
    }
    
    private fun calculateSpatialGradient(values: List<Double>, width: Int, height: Int): Double {
        if (values.size < width * 2) return 0.0
        
        var totalGradient = 0.0
        var count = 0
        
        for (y in 0 until height - 1) {
            for (x in 0 until width - 1) {
                val idx = y * width + x
                if (idx + 1 < values.size && idx + width < values.size) {
                    val dx = abs(values[idx + 1] - values[idx])
                    val dy = abs(values[idx + width] - values[idx])
                    totalGradient += (dx + dy) / 2.0
                    count++
                }
            }
        }
        
        return if (count > 0) totalGradient / count else 0.0
    }
    
    fun reset() {
        previousFrameFeatures = null
    }
}
