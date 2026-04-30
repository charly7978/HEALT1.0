package com.example.myapplication.forensic

import android.content.Context
import android.os.Environment
import com.example.myapplication.ppg.BeatEvent
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exportador de sesiones de medición.
 * Exporta datos en JSON y CSV para análisis forense.
 */
class SessionExporter(private val context: Context) {

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    /**
     * Exporta una sesión completa a JSON y CSV.
     * Retorna la ruta del archivo JSON principal.
     */
    fun exportSession(session: MeasurementSession): String? {
        val timestamp = dateFormat.format(Date())
        val baseFileName = "ppg_session_${session.sessionId}_$timestamp"

        // Crear directorio de exportación
        val exportDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "ppg_sessions"
        )
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }

        // Exportar JSON completo
        val jsonFile = File(exportDir, "$baseFileName.json")
        if (!exportJson(session, jsonFile)) {
            return null
        }

        // Exportar CSV de latidos
        val beatsCsvFile = File(exportDir, "$baseFileName\_beats.csv")
        exportBeatsCsv(session.getBeats(), beatsCsvFile)

        // Exportar CSV de eventos
        val eventsCsvFile = File(exportDir, "$baseFileName\_events.csv")
        exportEventsCsv(session.getEvents(), eventsCsvFile)

        // Exportar reporte resumido
        val summaryFile = File(exportDir, "$baseFileName\_summary.txt")
        exportSummary(session.getSummary(), summaryFile)

        return jsonFile.absolutePath
    }

    /**
     * Exporta sesión a JSON.
     */
    private fun exportJson(session: MeasurementSession, file: File): Boolean {
        return try {
            val json = gson.toJson(session)
            FileWriter(file).use { writer ->
                writer.write(json)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Exporta latidos a CSV.
     */
    private fun exportBeatsCsv(beats: List<BeatEvent>, file: File): Boolean {
        return try {
            FileWriter(file).use { writer ->
                writer.write("timestamp_ns,amplitude,rr_ms,bpm_instant,quality,type,reason\n")
                beats.forEach { beat ->
                    writer.write("${beat.timestampNs},${beat.amplitude},${beat.rrMs ?: ""},${beat.bpmInstant ?: ""},${beat.quality},${beat.type},${beat.reason}\n")
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Exporta eventos a CSV.
     */
    private fun exportEventsCsv(events: List<MeasurementEvent>, file: File): Boolean {
        return try {
            FileWriter(file).use { writer ->
                writer.write("timestamp,type,description,metadata\n")
                events.forEach { event ->
                    val metadataStr = event.metadata.entries.joinToString(";") { "${it.key}=${it.value}" }
                    writer.write("${event.timestamp},${event.type},${event.description},\"$metadataStr\"\n")
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Exporta resumen de sesión a texto plano.
     */
    private fun exportSummary(summary: SessionSummary, file: File): Boolean {
        return try {
            FileWriter(file).use { writer ->
                writer.write("=== RESUMEN DE SESIÓN PPG ===\n\n")
                writer.write("ID de Sesión: ${summary.sessionId}\n")
                writer.write("Duración: ${summary.durationMs / 1000} segundos\n")
                writer.write("Frames Totales: ${summary.totalFrames}\n")
                writer.write("Frames Rechazados: ${summary.rejectedFrames} (${(summary.rejectionRate * 100).toInt()}%)\n")
                writer.write("FPS Promedio: ${"%.2f".format(summary.averageFps)}\n")
                writer.write("Jitter: ${"%.2f".format(summary.jitterMs)} ms\n")
                writer.write("\n--- LATIDOS ---\n")
                writer.write("Total: ${summary.totalBeats}\n")
                writer.write("Válidos: ${summary.validBeats}\n")
                writer.write("Anómalos: ${summary.abnormalBeats}\n")
                writer.write("\n--- EVENTOS ---\n")
                writer.write("Pérdida de Contacto: ${summary.contactLostEvents}\n")
                writer.write("Movimiento Detectado: ${summary.motionEvents}\n")
                writer.write("Clipping Detectado: ${summary.clippingEvents}\n")
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Obtiene el directorio de exportación.
     */
    fun getExportDirectory(): File {
        return File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "ppg_sessions"
        )
    }
}
