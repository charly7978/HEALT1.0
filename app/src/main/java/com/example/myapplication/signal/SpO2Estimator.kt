package com.example.myapplication.signal

/**
 * Estimador de SpO2 experimental.
 * Requiere calibración por dispositivo para mostrar valores médicos.
 */
class SpO2Estimator {

    data class SpO2Result(
        val value: Double,
        val isExperimental: Boolean,
        val confidence: Double
    )

    /**
     * Calcula SpO2 usando el método de Ratio-of-Ratios entre Rojo y Verde.
     */
    fun estimate(
        redAC: Double, redDC: Double,
        greenAC: Double, greenDC: Double,
        isCalibrated: Boolean = false
    ): SpO2Result {
        if (redDC == 0.0 || greenDC == 0.0 || redAC == 0.0 || greenAC == 0.0) {
            return SpO2Result(0.0, true, 0.0)
        }

        val r = (redAC / redDC) / (greenAC / greenDC)
        
        // Coeficientes genéricos (deben ser calibrados por dispositivo)
        val a = 110.0
        val b = -25.0
        val spo2 = (a + b * r).coerceIn(70.0, 100.0)

        return SpO2Result(
            value = spo2,
            isExperimental = !isCalibrated,
            confidence = if (r in 0.4..1.2) 0.8 else 0.4
        )
    }
}
