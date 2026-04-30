package com.example.myapplication.forensic

import com.example.myapplication.ppg.BeatEvent
import java.util.Date

/**
 * Sesión de medición forense.
 * Registra todos los eventos y métricas para auditoría.
 */
data class MeasurementSession(
    val sessionId: String,
    val startTime: Date,
    var endTime: Date? = null,
    val deviceModel: String,
    val androidVersion: String,
    val cameraId: String,
    val exposureTimeNs: Long?,
    val iso: Int?,
    val frameDurationNs: Long?,
    val torchEnabled: Boolean,
    val calibrationProfileId: String?,
    val algorithmVersion: String
) {
    private val events = ArrayList<MeasurementEvent>()
    private val beats = ArrayList<BeatEvent>()

    var totalFrames: Int = 0
    var rejectedFrames: Int = 0
    var averageFps: Double = 0.0
    var jitterMs: Double = 0.0

    /**
     * Agrega un evento a la sesión.
     */
    fun addEvent(event: MeasurementEvent) {
        events.add(event)
    }

    /**
     * Agrega un latido detectado.
     */
    fun addBeat(beat: BeatEvent) {
        beats.add(beat)
    }

    /**
     * Obtiene todos los eventos.
     */
    fun getEvents(): List<MeasurementEvent> = events.toList()

    /**
     * Obtiene todos los latidos.
     */
    fun getBeats(): List<BeatEvent> = beats.toList()

    /**
     * Finaliza la sesión.
     */
    fun end() {
        endTime = Date()
    }

    /**
     * Genera un resumen de la sesión.
     */
    fun getSummary(): SessionSummary {
        val validBeats = beats.count { it.type.name != "INVALID_SIGNAL" }
        val abnormalBeats = beats.count { 
            it.type.name.contains("SUSPECT") || it.type.name == "IRREGULAR"
        }
        val contactLostEvents = events.count { it.type == EventType.CONTACT_LOST }
        val motionEvents = events.count { it.type == EventType.MOTION_DETECTED }
        val clippingEvents = events.count { it.type == EventType.CLIPPING_DETECTED }

        return SessionSummary(
            sessionId = sessionId,
            durationMs = (endTime?.time ?: startTime.time) - startTime.time,
            totalFrames = totalFrames,
            rejectedFrames = rejectedFrames,
            rejectionRate = if (totalFrames > 0) rejectedFrames.toDouble() / totalFrames else 0.0,
            averageFps = averageFps,
            jitterMs = jitterMs,
            totalBeats = beats.size,
            validBeats = validBeats,
            abnormalBeats = abnormalBeats,
            contactLostEvents = contactLostEvents,
            motionEvents = motionEvents,
            clippingEvents = clippingEvents
        )
    }
}

data class SessionSummary(
    val sessionId: String,
    val durationMs: Long,
    val totalFrames: Int,
    val rejectedFrames: Int,
    val rejectionRate: Double,
    val averageFps: Double,
    val jitterMs: Double,
    val totalBeats: Int,
    val validBeats: Int,
    val abnormalBeats: Int,
    val contactLostEvents: Int,
    val motionEvents: Int,
    val clippingEvents: Int
)
