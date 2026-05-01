package com.example.myapplication.ppg

import kotlin.math.max

/**
 * Estimador de SpO2 con calibración por dispositivo.
 * REGLA CRÍTICA: No muestra SpO2 si no hay PPG_VALID sostenido.
 * No usa fórmulas hardcodeadas sin calibración específica.
 */
class Spo2Estimator {

    enum class Spo2Status {
        NOT_AVAILABLE,          // No hay datos suficientes
        INSUFFICIENT_QUALITY,   // SQI insuficiente
        NO_CALIBRATION,         // Requiere calibración por dispositivo
        UNSTABLE_SIGNAL,        // Señal inestable
        CALIBRATING,            // Acumulando datos para calibrar
        VALID_ESTIMATE          // Estimación válida (puede ser no calibrada)
    }

    data class Spo2Result(
        val value: Int?,               // Valor SpO2 estimado (null si no disponible)
        val status: Spo2Status,        // Estado de la estimación
        val confidence: Double,        // Confianza 0.0-1.0
        val ratioR: Double,            // Ratio de ratios calculado
        val perfusionIndex: Double,    // Perfusion index usado
        val calibrationType: CalibrationType,
        val isCalibrated: Boolean,     // true si tiene calibración específica
        val warningMessage: String?    // Advertencia para el usuario
    )

    enum class CalibrationType {
        NONE,           // Sin calibración
        GENERIC,        // Calibración genérica (no confiable)
        DEVICE_SPECIFIC // Calibración específica del dispositivo
    }

    // Perfil de calibración (debe ser establecido por dispositivo)
    data class CalibrationProfile(
        val deviceModel: String,
        val coefficientA: Double,  // Offset
        val coefficientB: Double,  // Lineal
        val coefficientC: Double = 0.0, // Cuadrático
        val validRangeMin: Int = 70,
        val validRangeMax: Int = 100,
        val source: String = "unknown"
    )

    private var calibrationProfile: CalibrationProfile? = null
    
    // Buffer para estabilización temporal
    private val ratioBuffer = ArrayDeque<Double>(30)
    private val piBuffer = ArrayDeque<Double>(30)
    
    // Contadores de calidad
    private var validSampleCount: Int = 0
    private var insufficientCount: Int = 0

    /**
     * Establece perfil de calibración específico del dispositivo.
     * La calibración debe obtenerse mediante validación clínica.
     */
    fun setCalibrationProfile(profile: CalibrationProfile) {
        calibrationProfile = profile
    }

    /**
     * Establece calibración simple (para compatibilidad).
     * Modelo: SpO2 = A + B*R + C*R^2
     */
    fun setCalibration(a: Double, b: Double, c: Double = 0.0, deviceModel: String = "generic") {
        calibrationProfile = CalibrationProfile(
            deviceModel = deviceModel,
            coefficientA = a,
            coefficientB = b,
            coefficientC = c,
            source = "manual"
        )
    }

    /**
     * Limpia calibración y restablece estado.
     */
    fun clearCalibration() {
        calibrationProfile = null
        ratioBuffer.clear()
        piBuffer.clear()
        validSampleCount = 0
        insufficientCount = 0
    }

    /**
     * Estima SpO2 basado en ratio-of-ratios de canales rojo/verde.
     * 
     * REQUISITOS:
     * - validityState debe ser PPG_VALID o PPG_DEGRADED
     * - sqi >= 0.5
     * - Mínimo 5 segundos de ventana con PPG válido
     * 
     * @param acRed Componente AC (pulsátil) del canal rojo
     * @param dcRed Componente DC (baseline) del canal rojo
     * @param acGreen Componente AC del canal verde
     * @param dcGreen Componente DC del canal verde
     * @param sqi Signal Quality Index
     * @param validityState Estado de validez PPG
     * @param secondsWithValidPPG Segundos acumulados con PPG_VALID
     */
    fun estimate(
        acRed: Double, dcRed: Double,
        acGreen: Double, dcGreen: Double,
        sqi: Double,
        validityState: PpgPhysiologyClassifier.PpgValidityState,
        secondsWithValidPPG: Double = 0.0
    ): Spo2Result {
        
        // ========== VALIDACIÓN DE ESTADO FISIOLÓGICO ==========
        if (validityState != PpgPhysiologyClassifier.PpgValidityState.PPG_VALID &&
            validityState != PpgPhysiologyClassifier.PpgValidityState.PPG_DEGRADED) {
            return Spo2Result(
                value = null,
                status = Spo2Status.NOT_AVAILABLE,
                confidence = 0.0,
                ratioR = 0.0,
                perfusionIndex = 0.0,
                calibrationType = CalibrationType.NONE,
                isCalibrated = false,
                warningMessage = "SpO2 requiere PPG válido"
            )
        }
        
        // ========== VALIDACIÓN DE CALIDAD ==========
        if (sqi < 0.5) {
            insufficientCount++
            return Spo2Result(
                value = null,
                status = Spo2Status.INSUFFICIENT_QUALITY,
                confidence = sqi,
                ratioR = 0.0,
                perfusionIndex = 0.0,
                calibrationType = CalibrationType.NONE,
                isCalibrated = false,
                warningMessage = "Calidad insuficiente (SQI=${"%.2f".format(sqi)})"
            )
        }
        
        // ========== VALIDACIÓN DE COMPONENTES ==========
        if (dcRed < 0.1 || dcGreen < 0.1 || acRed <= 0 || acGreen <= 0) {
            return Spo2Result(
                value = null,
                status = Spo2Status.UNSTABLE_SIGNAL,
                confidence = 0.0,
                ratioR = 0.0,
                perfusionIndex = 0.0,
                calibrationType = CalibrationType.NONE,
                isCalibrated = false,
                warningMessage = "Componentes AC/DC insuficientes"
            )
        }
        
        // ========== CALCULAR PERFIL DE PERFUSIÓN ==========
        val piRed = (acRed / dcRed) * 100.0
        val piGreen = (acGreen / dcGreen) * 100.0
        val perfusionIndex = max(piRed, piGreen)
        
        // Perfusión mínima requerida para SpO2 confiable
        if (perfusionIndex < 0.5) {
            return Spo2Result(
                value = null,
                status = Spo2Status.UNSTABLE_SIGNAL,
                confidence = sqi * 0.5,
                ratioR = 0.0,
                perfusionIndex = perfusionIndex,
                calibrationType = CalibrationType.NONE,
                isCalibrated = false,
                warningMessage = "Perfusión insuficiente (PI=${"%.2f".format(perfusionIndex)})"
            )
        }
        
        // ========== CALCULAR RATIO R ==========
        // R = (AC_red / DC_red) / (AC_green / DC_green)
        val ratioR = (acRed / dcRed) / (acGreen / dcGreen)
        
        // Acumular en buffer para estabilización
        ratioBuffer.addLast(ratioR)
        piBuffer.addLast(perfusionIndex)
        if (ratioBuffer.size > 30) {
            ratioBuffer.removeFirst()
            piBuffer.removeFirst()
        }
        
        // Requerir mínimo de ventana temporal
        if (secondsWithValidPPG < 5.0) {
            return Spo2Result(
                value = null,
                status = Spo2Status.CALIBRATING,
                confidence = sqi * 0.3,
                ratioR = ratioR,
                perfusionIndex = perfusionIndex,
                calibrationType = CalibrationType.NONE,
                isCalibrated = false,
                warningMessage = "Acumulando datos (${"%.0f".format(secondsWithValidPPG)}/5s)..."
            )
        }
        
        // ========== CALCULAR SpO2 ==========
        val (estimatedSpO2, calibrationType, isCalibrated) = calculateSpO2(ratioR)
        
        // ========== DETERMINAR ESTADO FINAL ==========
        val finalStatus = when {
            !isCalibrated -> Spo2Status.NO_CALIBRATION
            ratioBuffer.size < 20 -> Spo2Status.CALIBRATING
            else -> Spo2Status.VALID_ESTIMATE
        }
        
        val warning = when {
            !isCalibrated -> "Estimación NO CALIBRADA - usar solo como referencia"
            perfusionIndex < 1.0 -> "Perfusión baja - resultado puede ser inexacto"
            sqi < 0.7 -> "Señal degradada - verificar posición del dedo"
            else -> null
        }
        
        validSampleCount++
        
        return Spo2Result(
            value = if (finalStatus == Spo2Status.VALID_ESTIMATE || 
                       finalStatus == Spo2Status.NO_CALIBRATION) estimatedSpO2 else null,
            status = finalStatus,
            confidence = calculateConfidence(sqi, perfusionIndex, ratioBuffer.size),
            ratioR = ratioBuffer.average(), // Usar promedio del buffer
            perfusionIndex = perfusionIndex,
            calibrationType = calibrationType,
            isCalibrated = isCalibrated,
            warningMessage = warning
        )
    }
    
    private fun calculateSpO2(ratioR: Double): Triple<Int?, CalibrationType, Boolean> {
        val profile = calibrationProfile
        
        return if (profile != null) {
            // Usar calibración específica
            val value = profile.coefficientA + 
                       profile.coefficientB * ratioR + 
                       profile.coefficientC * ratioR * ratioR
            val clamped = value.toInt().coerceIn(profile.validRangeMin, profile.validRangeMax)
            Triple(clamped, CalibrationType.DEVICE_SPECIFIC, true)
        } else {
            // Sin calibración - NO mostrar valor clínico
            // Retornar null o un índice experimental marcado como no calibrado
            Triple(null, CalibrationType.NONE, false)
        }
    }
    
    private fun calculateConfidence(sqi: Double, perfusionIndex: Double, sampleCount: Int): Double {
        val sqiScore = sqi.coerceIn(0.0, 1.0)
        val piScore = (perfusionIndex / 5.0).coerceIn(0.0, 1.0) // Normalizar a 5% PI
        val sampleScore = (sampleCount / 30.0).coerceIn(0.0, 1.0)
        
        return sqiScore * 0.5 + piScore * 0.3 + sampleScore * 0.2
    }
    
    /**
     * Obtiene información del ratio R para diagnóstico (sin convertir a SpO2).
     */
    fun getRawRatioInfo(): RawRatioInfo? {
        if (ratioBuffer.isEmpty()) return null
        
        return RawRatioInfo(
            currentRatio = ratioBuffer.last(),
            averageRatio = ratioBuffer.average(),
            sampleCount = ratioBuffer.size,
            stability = calculateRatioStability()
        )
    }
    
    private fun calculateRatioStability(): Double {
        if (ratioBuffer.size < 5) return 0.0
        val values = ratioBuffer.toList()
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        val stdDev = kotlin.math.sqrt(variance)
        val cv = if (mean > 0) stdDev / mean else 0.0
        return (1.0 - cv).coerceIn(0.0, 1.0) // Mayor estabilidad = mayor score
    }
    
    fun reset() {
        ratioBuffer.clear()
        piBuffer.clear()
        validSampleCount = 0
        insufficientCount = 0
    }
    
    /**
     * Información del ratio R crudo.
     */
    data class RawRatioInfo(
        val currentRatio: Double,
        val averageRatio: Double,
        val sampleCount: Int,
        val stability: Double
    )
}
