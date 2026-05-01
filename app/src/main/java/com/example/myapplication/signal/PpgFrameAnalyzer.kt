package com.example.myapplication.signal

import android.media.Image
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Analiza frames de cámara YUV_420_888 para extraer PpgFrame con datos ópticos completos.
 * Calcula estadísticas ROI, clipping, movimiento inter-frame y diagnóstico de exposición.
 * Única fuente de verdad para análisis de frames PPG.
 */
class PpgFrameAnalyzer {

    // Buffer para cálculo de AC/DC por ventana móvil
    private val greenValuesHistory = ArrayDeque<Double>(60)
    private val redValuesHistory = ArrayDeque<Double>(60)
    private var lastAvgGreen: Double = 0.0
    private var lastAvgRed: Double = 0.0

    /**
     * Analiza un frame de cámara y retorna un PpgFrame completo.
     *
     * @param image Frame YUV_420_888 de ImageReader
     * @param fps FPS estimado actual
     * @param exposureTimeNs Tiempo de exposición en nanosegundos (null si AE automático)
     * @param iso Valor ISO/sensibilidad (null si AE automático)
     * @param frameDurationNs Duración del frame en nanosegundos
     * @param externalMotionScore Score de movimiento externo (acelerómetro) si disponible
     */
    fun analyze(
        image: Image,
        fps: Double,
        exposureTimeNs: Long?,
        iso: Int?,
        frameDurationNs: Long?,
        externalMotionScore: Double = 0.0
    ): PpgFrame {
        val width = image.width
        val height = image.height

        // ROI central del 15%
        val roiWidth = (width * 0.15).toInt().coerceAtLeast(10)
        val roiHeight = (height * 0.15).toInt().coerceAtLeast(10)
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
        val totalPixels = roiWidth * roiHeight

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

                if (r > 250.0) saturatedCount++
                if (r < 5.0) darkCount++
            }
        }

        val redMean = redValues.average()
        val greenMean = greenValues.average()
        val blueMean = blueValues.average()

        // Calcular estadísticas
        val redMedian = median(redValues)
        val greenMedian = median(greenValues)
        val blueMedian = median(blueValues)
        val redStd = stdDev(redValues, redMean)
        val greenStd = stdDev(greenValues, greenMean)
        val blueStd = stdDev(blueValues, blueMean)

        // Calcular movimiento inter-frame basado en cambio de DC
        val motionScore = calculateInterFrameMotion(greenMean, redMean, externalMotionScore)

        // Calcular AC/DC por ventana móvil
        val (greenAc, greenDc) = calculateAcDc(greenValuesHistory, greenMean)
        val (redAc, redDc) = calculateAcDc(redValuesHistory, redMean)

        // Calcular métricas derivadas
        val redDominance = redMean / (greenMean + 1.0)
        val textureScore = greenStd
        val skinLikelihood = estimateSkinLikelihood(redMean, greenMean, blueMean, redStd, greenStd, blueStd)
        val greenPulseCandidate = greenAc / (greenDc + 0.001)

        val roiRect = PpgFrame.RoiRect(
            centerX = left + roiWidth / 2,
            centerY = top + roiHeight / 2,
            width = roiWidth,
            height = roiHeight
        )

        val clipping = ClippingInfo(
            highClipRatio = saturatedCount.toDouble() / totalPixels,
            lowClipRatio = darkCount.toDouble() / totalPixels,
            isSaturated = saturatedCount > totalPixels * 0.3,
            isDark = darkCount > totalPixels * 0.8
        )

        val exposureDiagnostics = ExposureDiagnostics(
            exposureTimeNs = exposureTimeNs,
            iso = iso,
            frameDurationNs = frameDurationNs,
            torchEnabled = true
        )

        // Calcular perfusion index
        val perfusionIndex = (greenAc / (greenDc + 0.001)) * 100.0

        // Normalizar señal óptica (0-1 aproximado)
        val normalizedSignal = (greenMean / 255.0).coerceIn(0.0, 1.0)

        return PpgFrame(
            timestampNs = image.timestamp,
            fpsEstimate = fps,
            avgRed = redMean,
            avgGreen = greenMean,
            avgBlue = blueMean,
            redDc = redDc,
            greenDc = greenDc,
            blueDc = blueMean, // Usamos avgBlue como DC aproximado
            redAc = redAc,
            greenAc = greenAc,
            blueAc = blueStd * 2.0, // Estimación desde desviación
            roiRect = roiRect,
            saturationRatio = clipping.highClipRatio,
            darknessRatio = clipping.lowClipRatio,
            skinLikelihood = skinLikelihood,
            redDominance = redDominance,
            greenPulseCandidate = greenPulseCandidate,
            textureScore = textureScore,
            motionScore = motionScore,
            rawOpticalSignal = greenMean,
            normalizedSignal = normalizedSignal,
            clipping = clipping,
            exposureDiagnostics = exposureDiagnostics,
            perfusionIndex = perfusionIndex
        )
    }

    /**
     * Calcula movimiento inter-frame basado en cambios de DC.
     */
    private fun calculateInterFrameMotion(
        currentGreen: Double,
        currentRed: Double,
        externalMotionScore: Double
    ): Double {
        val greenChange = abs(currentGreen - lastAvgGreen) / (lastAvgGreen + 1.0)
        val redChange = abs(currentRed - lastAvgRed) / (lastAvgRed + 1.0)

        lastAvgGreen = currentGreen
        lastAvgRed = currentRed

        // Score compuesto: cambio óptico + movimiento externo
        val opticalMotion = (greenChange + redChange) / 2.0
        return (opticalMotion * 0.7 + externalMotionScore * 0.3).coerceIn(0.0, 1.0)
    }

    /**
     * Calcula componentes AC y DC usando ventana móvil.
     */
    private fun calculateAcDc(buffer: ArrayDeque<Double>, currentValue: Double): Pair<Double, Double> {
        buffer.addLast(currentValue)
        if (buffer.size > 60) buffer.removeFirst()

        val dc = buffer.average()
        val ac = if (buffer.size >= 10) {
            val recent = buffer.takeLast(10)
            val max = recent.maxOrNull() ?: currentValue
            val min = recent.minOrNull() ?: currentValue
            (max - min) / 2.0
        } else {
            0.0
        }

        return Pair(ac, dc)
    }

    /**
     * Estima probabilidad de piel basada en características de color y textura.
     */
    private fun estimateSkinLikelihood(
        redMean: Double,
        greenMean: Double,
        blueMean: Double,
        redStd: Double,
        greenStd: Double,
        blueStd: Double
    ): Double {
        val redDominant = redMean > greenMean * 1.1
        val reasonableRatio = (redMean / (greenMean + 1.0)) in 0.8..2.0
        val moderateTexture = (redStd + greenStd + blueStd) / 3.0 in 3.0..50.0
        val notSaturated = redMean < 250.0 && greenMean < 250.0
        val notDark = redMean > 20.0 && greenMean > 20.0

        var score = 0.0
        if (redDominant) score += 0.25
        if (reasonableRatio) score += 0.25
        if (moderateTexture) score += 0.20
        if (notSaturated) score += 0.15
        if (notDark) score += 0.15

        return score.coerceIn(0.0, 1.0)
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val size = sorted.size
        return if (size % 2 == 0) {
            (sorted[size / 2 - 1] + sorted[size / 2]) / 2.0
        } else {
            sorted[size / 2]
        }
    }

    private fun stdDev(values: List<Double>, mean: Double): Double {
        return sqrt(values.map { (it - mean) * (it - mean) }.average())
    }

    fun reset() {
        greenValuesHistory.clear()
        redValuesHistory.clear()
        lastAvgGreen = 0.0
        lastAvgRed = 0.0
    }
}
