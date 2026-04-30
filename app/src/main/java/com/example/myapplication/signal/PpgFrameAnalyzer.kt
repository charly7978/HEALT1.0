package com.example.myapplication.signal

import android.graphics.Rect
import android.media.Image
import java.nio.ByteBuffer

/**
 * Extrae características ópticas crudas del frame de la cámara.
 * No genera ondas ni calcula métricas, solo reporta lo que ve el sensor.
 */
class PpgFrameAnalyzer {

    data class FrameFeatures(
        val timestampNs: Long,
        val redMean: Double,
        val greenMean: Double,
        val blueMean: Double,
        val lumaMean: Double,
        val clippedPixelRatio: Double,
        val darkPixelRatio: Double,
        val actualFps: Double
    )

    fun analyze(image: Image, fps: Double): FrameFeatures {
        val width = image.width
        val height = image.height
        
        // ROI central del 15% para reducir ruido de bordes y mejorar performance
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

        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var sumLuma = 0.0
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

                // Conversión YUV a RGB (Estándar BT.601) optimizada
                val r = (yVal + 1.370705 * vVal).coerceIn(0.0, 255.0)
                val g = (yVal - 0.337633 * uVal - 0.698001 * vVal).coerceIn(0.0, 255.0)
                val b = (yVal + 1.732446 * uVal).coerceIn(0.0, 255.0)

                sumR += r
                sumG += g
                sumB += b
                sumLuma += yVal

                if (r > 250.0) saturatedCount++
                if (r < 5.0) darkCount++
            }
        }

        return FrameFeatures(
            timestampNs = image.timestamp,
            redMean = sumR / totalPixels,
            greenMean = sumG / totalPixels,
            blueMean = sumB / totalPixels,
            lumaMean = sumLuma / totalPixels,
            clippedPixelRatio = saturatedCount.toDouble() / totalPixels,
            darkPixelRatio = darkCount.toDouble() / totalPixels,
            actualFps = fps
        )
    }
}
