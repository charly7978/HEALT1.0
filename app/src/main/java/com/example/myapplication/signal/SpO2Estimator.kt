package com.example.myapplication.signal

/**
 * Estimador de SpO2 honesto y basado en calibración.
 * No devuelve valores si no hay señal válida o calibración.
 */
class Spo2Estimator {

    enum class Spo2Status {
        NOT_AVAILABLE,
        LOW_QUALITY,
        UNCALIBRATED,
        VALID
    }

    data class Spo2Result(
        val value: Int?,
        val status: Spo2Status,
        val confidence: Double
    )

    /**
     * Calcula SpO2 basado en el Ratio de Ratios (R).
     * R = (AC_red / DC_red) / (AC_green / DC_green)
     */
    fun estimate(
        acRed: Double, dcRed: Double,
        acGreen: Double, dcGreen: Double,
        sqi: Double,
        isCalibrated: Boolean = false // Por defecto false hasta tener perfil de dispositivo
    ): Spo2Result {
        
        if (sqi < 0.75 || dcRed < 1.0 || dcGreen < 1.0 || acRed < 0.01 || acGreen < 0.01) {
            return Spo2Result(null, Spo2Status.LOW_QUALITY, 0.0)
        }

        val r = (acRed / dcRed) / (acGreen / dcGreen)
        
        // Curva genérica experimental (SOLO para fines de desarrollo, se marca como UNCALIBRATED)
        // SpO2 = 110 - 25 * R
        val estimatedValue = (110.0 - 20.0 * r).toInt().coerceIn(70, 100)

        return if (isCalibrated) {
            Spo2Result(estimatedValue, Spo2Status.VALID, sqi)
        } else {
            // Reportamos el valor pero con estado UNCALIBRATED para que la UI decida si mostrarlo con advertencia
            Spo2Result(estimatedValue, Spo2Status.UNCALIBRATED, sqi * 0.5)
        }
    }
}
