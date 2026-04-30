package com.example.myapplication.forensic

import java.util.Date

/**
 * Evento de medición para auditoría forense.
 */
data class MeasurementEvent(
    val timestamp: Date,
    val type: EventType,
    val description: String,
    val metadata: Map<String, Any> = emptyMap()
)

enum class EventType {
    SESSION_STARTED,
    SESSION_ENDED,
    CONTACT_LOST,
    CONTACT_RESTORED,
    MOTION_DETECTED,
    MOTION_RESOLVED,
    CLIPPING_DETECTED,
    CLIPPING_RESOLVED,
    SIGNAL_DEGRADED,
    SIGNAL_RESTORED,
    BEAT_DETECTED,
    ARRHYTHMIA_DETECTED,
    CALIBRATION_APPLIED,
    CALIBRATION_MISSING,
    CAMERA_ERROR,
    SENSOR_ERROR
}
