package com.example.myapplication.signal

import android.graphics.Rect
import android.media.Image

/**
 * Analiza un frame de cámara (YUV_420_888) para extraer características ópticas PPG.
 * No simula datos, extrae promedios RGB reales y estadísticas de la ROI.
 */
class PpgFrameAnalyzer {

    fun analyze(image: Image, fps: Double): PpgSample {
        val width = image.width
        val height = image.height
        
        // ROI central del 20%
        val roiWidth = (width * 0.2).toInt()
        val roiHeight = (height * 0.2).toInt()
        val left = (width - roiWidth) / 2
        val top = (height - roiHeight) / 2
        val roi = Rect(left, top, left + roiWidth, top + roiHeight)

        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val yRowStride = image.planes[0].rowStride
        val uvRowStride = image.planes[1].rowStride
        val uvPixelStride = image.planes[1].pixelStride

        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var sqSumR = 0.0
        var saturatedCount = 0
        var darkCount = 0
        val totalPixels = roiWidth * roiHeight

        // Extraer RGB de la ROI
        for (y in roi.top until roi.bottom) {
            for (x in roi.left until roi.right) {
                val yIndex = y * yRowStride + x
                val uvIndex = (y / 2) * uvRowStride + (x / 2) * uvPixelStride

                val yVal = (yBuffer[yIndex].toInt() and 0xFF)
                val uVal = (uBuffer[uvIndex].toInt() and 0xFF) - 128
                val vVal = (vBuffer[uvIndex].toInt() and 0xFF) - 128

                // Conversión YUV a RGB (Estándar BT.601)
                val r = (yVal + 1.370705 * vVal).coerceIn(0.0, 255.0)
                val g = (yVal - 0.337633 * uVal - 0.698001 * vVal).coerceIn(0.0, 255.0)
                val b = (yVal + 1.732446 * uVal).coerceIn(0.0, 255.0)

                sumR += r
                sumG += g
                sumB += b
                sqSumR += r * r

                if (r > 252) saturatedCount++
                if (r < 3) darkCount++
            }
        }

        val avgR = sumR / totalPixels
        val avgG = sumG / totalPixels
        val avgB = sumB / totalPixels
        
        // Desviación estándar para SNR
        val varianceR = (sqSumR / totalPixels) - (avgR * avgR)
        val stdDevR = Math.sqrt(Math.max(0.0, varianceR))

        return PpgSample(
            timestampNs = image.timestamp,
            red = avgR,
            green = avgG,
            blue = avgB,
            actualFps = fps,
            roiStats = RoiStats(
                meanRed = avgR,
                stdDevRed = stdDevR,
                meanGreen = avgG,
                meanBlue = avgB
            ),
            diagnostics = SampleDiagnostics(
                clippingHigh = (saturatedCount.toDouble() / totalPixels) > 0.1,
                clippingLow = (darkCount.toDouble() / totalPixels) > 0.1,
                lowLight = avgR < 10.0
            )
        )
    }
}
