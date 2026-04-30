package com.example.myapplication.domain

/**
 * Lectura de signos vitales procesada.
 * Solo contiene valores derivados de señales reales.
 */
data class VitalReading(
    val bpm: Double?,
    val bpmConfidence: Double,
    val spo2: Double?,
    val spo2Confidence: Double,
    val sqi: Double,
    val state: MeasurementState,
    val message: String
)
