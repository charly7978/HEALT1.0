package com.example.myapplication.forensic

import java.security.MessageDigest
import java.util.Date

/**
 * Rastro de auditoría para integridad de datos.
 * Genera hashes para verificar que los datos no fueron modificados.
 */
class AuditTrail {

    private val entries = ArrayList<AuditEntry>()

    data class AuditEntry(
        val timestamp: Date,
        val eventType: String,
        val dataHash: String,
        val description: String
    )

    /**
     * Registra un evento en el rastro de auditoría.
     */
    fun logEvent(eventType: String, data: String, description: String) {
        val hash = calculateHash(data)
        entries.add(
            AuditEntry(
                timestamp = Date(),
                eventType = eventType,
                dataHash = hash,
                description = description
            )
        )
    }

    /**
     * Calcula hash SHA-256 de datos.
     */
    private fun calculateHash(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Verifica la integridad de datos comparando con hash almacenado.
     */
    fun verifyIntegrity(data: String, expectedHash: String): Boolean {
        val currentHash = calculateHash(data)
        return currentHash == expectedHash
    }

    /**
     * Obtiene todas las entradas de auditoría.
     */
    fun getEntries(): List<AuditEntry> = entries.toList()

    /**
     * Genera un reporte de auditoría en formato JSON.
     */
    fun generateAuditReport(): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"auditEntries\": [\n")
        
        entries.forEachIndexed { index, entry ->
            sb.append("    {\n")
            sb.append("      \"timestamp\": \"${entry.timestamp}\",\n")
            sb.append("      \"eventType\": \"${entry.eventType}\",\n")
            sb.append("      \"dataHash\": \"${entry.dataHash}\",\n")
            sb.append("      \"description\": \"${entry.description}\"\n")
            sb.append("    }")
            if (index < entries.size - 1) sb.append(",")
            sb.append("\n")
        }
        
        sb.append("  ]\n")
        sb.append("}")
        return sb.toString()
    }

    fun clear() {
        entries.clear()
    }
}
