package com.example.myapplication.ppg

import java.util.Date

/**
 * Perfil de calibración para estimación de SpO₂.
 * Contiene coeficientes específicos por dispositivo/cámara/flash.
 * 
 * CRÍTICO: SpO₂ requiere calibración por dispositivo.
 * No usar fórmulas universales sin validación.
 */
data class CalibrationProfile(
    val id: String,
    val deviceModel: String,
    val cameraId: String,
    val physicalLens: String?,
    val exposureTimeNs: Long,
    val iso: Int,
    val frameDurationNs: Long,
    val torchIntensity: Float?,
    val coefficientA: Double,  // Coeficiente para ratio R
    val coefficientB: Double,  // Intercepto
    val calibrationDate: Date,
    val referenceSpO2: Int,    // Valor de referencia usado para calibrar
    val algorithmVersion: String,
    val isValid: Boolean
) {
    companion object {
        /**
         * Crea un perfil de calibración desde datos de medición.
         */
        fun fromMeasurement(
            deviceModel: String,
            cameraId: String,
            physicalLens: String?,
            exposureTimeNs: Long,
            iso: Int,
            frameDurationNs: Long,
            torchIntensity: Float?,
            measuredR: Double,
            referenceSpO2: Int
        ): CalibrationProfile {
            // Calibración lineal simple: SpO2 = A - B * R
            // R = (AC_red/DC_red) / (AC_green/DC_green)
            // Ajustar coeficientes basado en el punto de calibración
            val coefficientB = 20.0 // Pendiente típica de la curva de calibración
            val coefficientA = referenceSpO2.toDouble() + coefficientB * measuredR

            return CalibrationProfile(
                id = "${deviceModel}_${cameraId}_${Date().time}",
                deviceModel = deviceModel,
                cameraId = cameraId,
                physicalLens = physicalLens,
                exposureTimeNs = exposureTimeNs,
                iso = iso,
                frameDurationNs = frameDurationNs,
                torchIntensity = torchIntensity,
                coefficientA = coefficientA,
                coefficientB = coefficientB,
                calibrationDate = Date(),
                referenceSpO2 = referenceSpO2,
                algorithmVersion = "1.0",
                isValid = true
            )
        }
    }

    /**
     * Estima SpO₂ usando este perfil de calibración.
     * Retorna null si el perfil no es válido.
     */
    fun estimateSpO2(r: Double): Int? {
        if (!isValid) return null

        val estimated = coefficientA - coefficientB * r
        return estimated.toInt().coerceIn(70, 100)
    }

    /**
     * Verifica si este perfil es compatible con los parámetros actuales.
     */
    fun isCompatible(
        currentDeviceModel: String,
        currentCameraId: String,
        currentExposureNs: Long,
        currentIso: Int
    ): Boolean {
        if (!isValid) return false
        if (deviceModel != currentDeviceModel) return false
        if (cameraId != currentCameraId) return false
        
        // Tolerancia del 20% para exposición e ISO
        val exposureTolerance = exposureTimeNs * 0.2
        if (abs(currentExposureNs - exposureTimeNs) > exposureTolerance) return false
        
        val isoTolerance = iso * 0.2
        if (abs(currentIso - iso) > isoTolerance) return false

        return true
    }

    private fun abs(value: Long): Long = if (value < 0) -value else value
    private fun abs(value: Int): Int = if (value < 0) -value else value
}
