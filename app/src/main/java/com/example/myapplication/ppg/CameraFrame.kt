package com.example.myapplication.ppg

/**
 * Frame de cámara con características ópticas extraídas.
 * Todos los valores provienen de procesamiento real de píxeles.
 */
data class CameraFrame(
    val timestampNs: Long,
    val width: Int,
    val height: Int,
    val format: Int,
    val cameraId: String,
    val exposureTimeNs: Long?,
    val iso: Int?,
    val frameDurationNs: Long?,
    val redMean: Double,
    val greenMean: Double,
    val blueMean: Double,
    val redAcDc: Double,
    val greenAcDc: Double,
    val blueAcDc: Double,
    val clipHighRatio: Double,
    val clipLowRatio: Double,
    val roiCoverage: Double
)
