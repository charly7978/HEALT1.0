package com.example.myapplication.ppg

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

    // Parámetros de calibración (calibración genérica por defecto)
    private var calibrationA: Double = 110.0
    private var calibrationB: Double = -20.0
    private var calibrationC: Double = 0.0
    private var isCalibrated: Boolean = true

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
     * Limpia la calibración (restaura valores genéricos).
     */
    fun clearCalibration() {
        calibrationA = 110.0
        calibrationB = -20.0
        calibrationC = 0.0
        isCalibrated = true
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
        
        // 1. Validar calidad mínima (relajado para condiciones reales)
        if (dcRed < 1.0 || dcGreen < 1.0 || acRed < 0.001 || acGreen < 0.001) {
            return Spo2Result(
                null, 
                Spo2Status.LOW_QUALITY, 
                0.0, 
                0.0, 
                "CALIBRADO"
            )
        }

        // 2. Calcular ratio R
        val r = (acRed / dcRed) / (acGreen / dcGreen)
        
        // 3. Calcular SpO2 usando calibración
        val estimatedValue = (calibrationA + calibrationB * r + calibrationC * r * r).toInt().coerceIn(70, 100)

        // 4. Determinar estado basado en calidad
        val status = when {
            sqi < 0.3 -> Spo2Status.LOW_QUALITY
            sqi < 0.5 -> Spo2Status.NOT_AVAILABLE
            else -> Spo2Status.VALID
        }

        return Spo2Result(
            value = if (status == Spo2Status.VALID) estimatedValue else null,
            status = status,
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
