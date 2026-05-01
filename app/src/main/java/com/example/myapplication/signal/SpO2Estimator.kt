package com.example.myapplication.signal

/**
 * Estimador de SpO2 basado en calibración por dispositivo.
 * NO devuelve valores fijos ni hardcodeados.
 * Requiere calibración para mostrar valores válidos.
 */
class Spo2Estimator {

    enum class Spo2Status {
        NOT_AVAILABLE,
        LOW_QUALITY,
        NO_CALIBRATION,
        UNSTABLE_SIGNAL,
        VALID
    }

    data class Spo2Result(
        val value: Int?,
        val status: Spo2Status,
        val confidence: Double,
        val ratioR: Double,
        val calibrationStatus: String
    )

    // Parámetros de calibración (por defecto no calibrado)
    private var calibrationA: Double? = null
    private var calibrationB: Double? = null
    private var calibrationC: Double? = null
    private var isCalibrated: Boolean = false

    /**
     * Establece parámetros de calibración para el dispositivo.
     * Modelo: SpO2 = A + B*R + C*R^2
     */
    fun setCalibration(a: Double, b: Double, c: Double = 0.0) {
        calibrationA = a
        calibrationB = b
        calibrationC = c
        isCalibrated = true
    }

    /**
     * Limpia la calibración.
     */
    fun clearCalibration() {
        calibrationA = null
        calibrationB = null
        calibrationC = null
        isCalibrated = false
    }

    /**
     * Calcula SpO2 basado en el Ratio de Ratios (R).
     * R = (AC_red / DC_red) / (AC_green / DC_green)
     */
    fun estimate(
        acRed: Double, dcRed: Double,
        acGreen: Double, dcGreen: Double,
        sqi: Double,
        windowStability: Double = 0.0
    ): Spo2Result {
        
        // 1. Validar calidad mínima
        if (sqi < 0.7 || dcRed < 1.0 || dcGreen < 1.0 || acRed < 0.01 || acGreen < 0.01) {
            return Spo2Result(
                null, 
                Spo2Status.LOW_QUALITY, 
                0.0, 
                0.0, 
                if (isCalibrated) "CALIBRADO" else "SIN CALIBRACIÓN"
            )
        }

        // 2. Validar estabilidad de ventana
        if (windowStability < 0.5) {
            return Spo2Result(
                null, 
                Spo2Status.UNSTABLE_SIGNAL, 
                sqi, 
                0.0, 
                if (isCalibrated) "CALIBRADO" else "SIN CALIBRACIÓN"
            )
        }

        // 3. Calcular ratio R
        val r = (acRed / dcRed) / (acGreen / dcGreen)
        
        // 4. Si no hay calibración, NO devolver valor
        if (!isCalibrated) {
            return Spo2Result(
                null, 
                Spo2Status.NO_CALIBRATION, 
                sqi, 
                r, 
                "SIN CALIBRACIÓN - REQUIERE CALIBRACIÓN POR DISPOSITIVO"
            )
        }

        // 5. Calcular SpO2 usando calibración
        val a = calibrationA ?: 110.0
        val b = calibrationB ?: -20.0
        val c = calibrationC ?: 0.0
        
        val estimatedValue = (a + b * r + c * r * r).toInt().coerceIn(70, 100)

        return Spo2Result(
            value = estimatedValue,
            status = Spo2Status.VALID,
            confidence = sqi,
            ratioR = r,
            calibrationStatus = "CALIBRADO"
        )
    }

    /**
     * Obtiene el índice experimental sin calibración (SOLO para diagnóstico).
     * NO debe mostrarse como SpO2 válido al usuario.
     */
    fun getExperimentalIndex(
        acRed: Double, dcRed: Double,
        acGreen: Double, dcGreen: Double,
        sqi: Double
    ): Double? {
        if (sqi < 0.7 || dcRed < 1.0 || dcGreen < 1.0) return null
        
        val r = (acRed / dcRed) / (acGreen / dcGreen)
        // Índice experimental genérico (NO es SpO2)
        return 110.0 - 20.0 * r
    }
}
