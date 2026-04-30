package com.example.myapplication.ppg

/**
 * Estimador de SpO₂ con sistema de calibración obligatorio.
 * 
 * REGLA CRÍTICA:
 * - No mostrar SpO₂ sin calibración válida.
 * - No usar fórmulas universales como resultado definitivo.
 * - Indicar "REQUIERE CALIBRACIÓN" si no hay perfil.
 */
class Spo2Estimator(
    private val calibrationManager: DeviceCalibrationManager
) {

    enum class Spo2Status {
        NOT_AVAILABLE,
        LOW_QUALITY,
        CALIBRATION_REQUIRED,
        UNCALIBRATED_ESTIMATE,
        VALID
    }

    data class Spo2Result(
        val value: Int?,
        val status: Spo2Status,
        val confidence: Double,
        val message: String
    )

    /**
     * Estima SpO₂ basado en ratios AC/DC.
     * 
     * @param acRed Componente AC del canal rojo
     * @param dcRed Componente DC del canal rojo
     * @param acGreen Componente AC del canal verde
     * @param dcGreen Componente DC del canal verde
     * @param sqi Índice de calidad de señal
     * @param deviceModel Modelo del dispositivo
     * @param cameraId ID de la cámara
     * @param exposureNs Tiempo de exposición en ns
     * @param iso ISO actual
     */
    fun estimate(
        acRed: Double,
        dcRed: Double,
        acGreen: Double,
        dcGreen: Double,
        sqi: Double,
        deviceModel: String,
        cameraId: String,
        exposureNs: Long,
        iso: Int
    ): Spo2Result {
        
        // 1. Validar calidad de señal
        if (sqi < 0.75 || dcRed < 1.0 || dcGreen < 1.0 || acRed < 0.01 || acGreen < 0.01) {
            return Spo2Result(
                value = null,
                status = Spo2Status.LOW_QUALITY,
                confidence = 0.0,
                message = "Señal insuficiente"
            )
        }

        // 2. Calcular ratio R = (AC_red/DC_red) / (AC_green/DC_green)
        val r = (acRed / dcRed) / (acGreen / dcGreen)

        // 3. Buscar perfil de calibración compatible
        val profile = calibrationManager.findCompatibleProfile(
            deviceModel, cameraId, exposureNs, iso
        )

        return when {
            // 4. Si hay perfil de calibración válido, usarlo
            profile != null && profile.isValid -> {
                val estimated = profile.estimateSpO2(r)
                Spo2Result(
                    value = estimated,
                    status = Spo2Status.VALID,
                    confidence = sqi,
                    message = "Calibrado: ${profile.deviceModel}"
                )
            }
            // 5. Si hay calibración pero no compatible, advertir
            calibrationManager.hasValidCalibration() -> {
                Spo2Result(
                    value = null,
                    status = Spo2Status.CALIBRATION_REQUIRED,
                    confidence = 0.0,
                    message = "Recalibración requerida (parámetros cambiados)"
                )
            }
            // 6. Si no hay calibración, NO mostrar número definitivo
            else -> {
                // Opcional: mostrar estimación cruda con advertencia fuerte
                // pero el requisito es NO mostrar como si fuera exacto
                val rawEstimate = (110.0 - 20.0 * r).toInt().coerceIn(70, 100)
                Spo2Result(
                    value = null, // IMPORTANTE: null para no mostrar como definitivo
                    status = Spo2Status.CALIBRATION_REQUIRED,
                    confidence = 0.0,
                    message = "SpO₂ REQUIERE CALIBRACIÓN"
                )
            }
        }
    }

    /**
     * Versión simplificada para estimación sin parámetros de cámara.
     * Usa el perfil activo si existe.
     */
    fun estimateSimple(
        acRed: Double,
        dcRed: Double,
        acGreen: Double,
        dcGreen: Double,
        sqi: Double
    ): Spo2Result {
        if (sqi < 0.75 || dcRed < 1.0 || dcGreen < 1.0 || acRed < 0.01 || acGreen < 0.01) {
            return Spo2Result(
                value = null,
                status = Spo2Status.LOW_QUALITY,
                confidence = 0.0,
                message = "Señal insuficiente"
            )
        }

        val r = (acRed / dcRed) / (acGreen / dcGreen)
        val profile = calibrationManager.getActiveProfile()

        return when {
            profile != null && profile.isValid -> {
                val estimated = profile.estimateSpO2(r)
                Spo2Result(
                    value = estimated,
                    status = Spo2Status.VALID,
                    confidence = sqi,
                    message = "Calibrado"
                )
            }
            else -> {
                Spo2Result(
                    value = null,
                    status = Spo2Status.CALIBRATION_REQUIRED,
                    confidence = 0.0,
                    message = "SpO₂ REQUIERE CALIBRACIÓN"
                )
            }
        }
    }
}
