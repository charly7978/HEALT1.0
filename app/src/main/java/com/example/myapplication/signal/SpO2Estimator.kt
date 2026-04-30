package com.example.myapplication.signal

import kotlin.math.log10

/**
 * Estimador de SpO2 mediante el método de Ratio-of-Ratios (R = (ACred/DCred) / (ACir/DCir)).
 * Como no tenemos sensor IR, usamos el canal Verde o Azul como referencia para el componente de absorción pulsátil.
 */
class Spo2Estimator {

    data class Spo2Result(
        val spo2: Double,
        val confidence: Double
    )

    /**
     * Calcula SpO2 basado en componentes AC/DC de canales Rojo y Verde.
     */
    fun estimate(
        redAc: Double, redDc: Double,
        greenAc: Double, greenDc: Double,
        sqi: Double
    ): Spo2Result {
        if (redDc == 0.0 || greenDc == 0.0 || sqi < 30.0) {
            return Spo2Result(0.0, 0.0)
        }

        // R = (AC_red / DC_red) / (AC_green / DC_green)
        val r = (redAc / redDc) / (greenAc / greenDc)

        // Fórmula empírica lineal aproximada: SpO2 = A - B*R
        // Estos coeficientes requieren calibración por dispositivo.
        val a = 110.0
        val b = 25.0
        
        var spo2 = a - b * r
        
        // Limitar a rangos fisiológicos
        spo2 = spo2.coerceIn(70.0, 100.0)

        // La confianza depende del SQI y de que R esté en un rango razonable
        val confidence = (sqi / 100.0) * (if (r in 0.3..2.5) 1.0 else 0.5)

        return Spo2Result(spo2, confidence)
    }
}
