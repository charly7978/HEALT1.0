package com.example.myapplication.forensic

import java.security.MessageDigest

/**
 * Utilidad para generar hashes de integridad de datos.
 */
object IntegrityHasher {

    /**
     * Genera hash SHA-256 de datos.
     */
    fun sha256(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Genera hash SHA-256 de bytes.
     */
    fun sha256Bytes(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Verifica que un hash coincida con los datos.
     */
    fun verify(data: String, expectedHash: String): Boolean {
        return sha256(data) == expectedHash
    }
}
