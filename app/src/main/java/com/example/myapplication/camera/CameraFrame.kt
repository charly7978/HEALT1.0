package com.example.myapplication.camera

/**
 * Representa los datos extraídos de un frame de cámara para procesamiento PPG.
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
    val roiCoverage: Double,
    val actualFps: Double
)
