package com.example.myapplication.signal

import android.graphics.ImageFormat
import android.graphics.Rect
import android.media.Image
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.sqrt

class FingerRoiExtractor {

    fun extractFeatures(image: Image): PPGFrameFeatures {
        val width = image.width
        val height = image.height
        
        // ROI central (45% del ancho y alto)
        val roiWidth = (width * 0.45).toInt()
        val roiHeight = (height * 0.45).toInt()
        val left = (width - roiWidth) / 2
        val top = (height - roiHeight) / 2
        val roi = Rect(left, top, left + roiWidth, top + roiHeight)

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yStride = yPlane.rowStride
        val uvStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var sumSqR = 0.0
        var sumSqG = 0.0
        var sumSqB = 0.0
        
        var clippedHighCount = 0
        var clippedLowCount = 0
        var totalPixels = 0
        
        val rValues = mutableListOf<Double>()
        val gValues = mutableListOf<Double>()
        val bValues = mutableListOf<Double>()

        // Procesamiento con stride para optimizar
        val stride = 2
        for (y in roi.top until roi.bottom step stride) {
            for (x in roi.left until roi.right step stride) {
                val yIdx = y * yStride + x
                val uvIdx = (y / 2) * uvStride + (x / 2) * uvPixelStride

                val yVal = (yBuffer.get(yIdx).toInt() and 0xFF).toDouble()
                val uVal = (uBuffer.get(uvIdx).toInt() and 0xFF).toDouble() - 128.0
                val vVal = (vBuffer.get(uvIdx).toInt() and 0xFF).toDouble() - 128.0

                // Conversión YUV a RGB (Estándar BT.601)
                val r = (yVal + 1.402 * vVal).coerceIn(0.0, 255.0)
                val g = (yVal - 0.344136 * uVal - 0.714136 * vVal).coerceIn(0.0, 255.0)
                val b = (yVal + 1.772 * uVal).coerceIn(0.0, 255.0)

                sumR += r
                sumG += g
                sumB += b
                sumSqR += r * r
                sumSqG += g * g
                sumSqB += b * b

                if (r > 248 || g > 248 || b > 248) clippedHighCount++
                if (r < 5 && g < 5 && b < 5) clippedLowCount++

                rValues.add(r)
                gValues.add(g)
                bValues.add(b)
                totalPixels++
            }
        }

        val count = totalPixels.toDouble()
        val meanR = sumR / count
        val meanG = sumG / count
        val meanB = sumB / count

        val stdR = sqrt(max(0.0, sumSqR / count - meanR * meanR))
        val stdG = sqrt(max(0.0, sumSqG / count - meanG * meanG))
        val stdB = sqrt(max(0.0, sumSqB / count - meanB * meanB))

        rValues.sort()
        gValues.sort()
        bValues.sort()
        
        val medianR = if (rValues.isNotEmpty()) rValues[rValues.size / 2] else 0.0
        val medianG = if (gValues.isNotEmpty()) gValues[gValues.size / 2] else 0.0
        val medianB = if (bValues.isNotEmpty()) bValues[bValues.size / 2] else 0.0

        val brightness = (meanR * 0.299 + meanG * 0.587 + meanB * 0.114)
        val redDominance = meanR / max(meanG, 1.0)

        // Detección preliminar de estado (se refina en FingerDetectionEngine)
        val fingerState = when {
            count == 0.0 -> FingerState.NO_FINGER
            brightness < 20 -> FingerState.LOW_LIGHT
            clippedHighCount / count > 0.2 -> FingerState.SATURATED
            redDominance < 1.5 -> FingerState.NO_FINGER
            else -> FingerState.FINGER_DETECTED
        }

        return PPGFrameFeatures(
            timestampNs = image.timestamp,
            redMean = meanR,
            greenMean = meanG,
            blueMean = meanB,
            redStd = stdR,
            greenStd = stdG,
            blueStd = stdB,
            redMedian = medianR,
            greenMedian = medianG,
            blueMedian = medianB,
            clippedHighRatio = clippedHighCount / count,
            clippedLowRatio = clippedLowCount / count,
            validPixelRatio = 1.0 - (clippedHighCount + clippedLowCount) / count,
            brightness = brightness,
            redDominance = redDominance,
            perfusionIndexGreen = 0.0, // Calculado por ventana en SignalProcessor
            perfusionIndexRed = 0.0,
            roi = roi,
            fingerState = fingerState,
            pressureState = PressureState.UNKNOWN
        )
    }
}
