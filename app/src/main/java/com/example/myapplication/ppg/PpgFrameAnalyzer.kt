package com.example.myapplication.ppg

import android.media.Image
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Analiza frames de cámara para extraer PpgSample con datos ópticos completos.
 * Calcula estadísticas ROI, clipping, movimiento y diagnóstico de exposición.
 */
class PpgFrameAnalyzer {

    fun analyze(image: Image, fps: Double, exposureTimeNs: Long?, iso: Int?, frameDurationNs: Long?): PpgSample {
        val width = image.width
        val height = image.height
        
        // ROI central del 15%
        val roiWidth = (width * 0.15).toInt()
        val roiHeight = (height * 0.15).toInt()
        val left = (width - roiWidth) / 2
        val top = (height - roiHeight) / 2

        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val yRowStride = image.planes[0].rowStride
        val uvRowStride = image.planes[1].rowStride
        val uvPixelStride = image.planes[1].pixelStride

        val redValues = ArrayList<Double>()
        val greenValues = ArrayList<Double>()
        val blueValues = ArrayList<Double>()
        var saturatedCount = 0
        var darkCount = 0
        val totalPixels = roiWidth * roiHeight

        for (y in top until (top + roiHeight)) {
            for (x in left until (left + roiWidth)) {
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

        // Calcular estadísticas ROI
        val redMedian = median(redValues)
        val greenMedian = median(greenValues)
        val blueMedian = median(blueValues)
        val redStd = stdDev(redValues, redMean)
        val greenStd = stdDev(greenValues, greenMean)
        val blueStd = stdDev(blueValues, blueMean)

        // Calcular movimiento - requiere datos reales de sensores o análisis inter-frame real
        // TODO: Implementar análisis de movimiento real usando sensores o flujo óptico
        val motionScore = 0.0 // Placeholder - no es simulación, es ausencia de implementación

        val roiStats = RoiStats(
            centerX = left + roiWidth / 2,
            centerY = top + roiHeight / 2,
            width = roiWidth,
            height = roiHeight,
            pixelCount = totalPixels,
            medianRed = redMedian,
            medianGreen = greenMedian,
            medianBlue = blueMedian,
            stdRed = redStd,
            stdGreen = greenStd,
            stdBlue = blueStd
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

        return PpgSample(
            timestampNs = image.timestamp,
            rawRed = redMean,
            rawGreen = greenMean,
            rawBlue = blueMean,
            roiStats = roiStats,
            clipping = clipping,
            motionScore = motionScore,
            exposureDiagnostics = exposureDiagnostics
        )
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

}
