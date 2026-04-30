package com.example.myapplication.signal

import android.graphics.Color
import kotlin.math.abs

/**
 * Validador fisiológico de contacto (Anti-objetos).
 * Detecta si la señal proviene de tejido humano iluminado por flash.
 */
class PhysiologicalContactGate {

    data class ContactFeatures(
        val redMean: Double,
        val greenMean: Double,
        val blueMean: Double,
        val lumaMean: Double,
        val saturationRatio: Double,
        val clippedPixelRatio: Double,
        val skinLikeScore: Double
    )

    fun evaluate(features: ContactFeatures): MeasurementState {
        // 1. Detección de saturación extrema (Flash quemado o sensor expuesto directamente)
        if (features.clippedPixelRatio > 0.5 || features.lumaMean > 250.0) {
            return MeasurementState.SATURATED
        }

        // 2. Detección de oscuridad (No hay flash o lente tapado con algo opaco no traslúcido)
        if (features.lumaMean < 5.0) {
            return MeasurementState.SEARCHING_FINGER
        }

        // 3. Dominancia cromática (Tejido humano traslúcido: Rojo >> Verde > Azul)
        // En PPG por cámara, el canal rojo suele estar casi saturado mientras que el verde/azul absorben
        val isRedDominant = features.redMean > (features.greenMean * 1.5) && features.redMean > (features.blueMean * 2.0)
        
        // 4. Skin-like score (Simplificado: R debe ser alto, G y B bajos pero presentes)
        val isSkinLike = isRedDominant && features.redMean > 100.0 && features.greenMean < 180.0

        if (!isSkinLike) {
            return MeasurementState.INVALID_SIGNAL
        }

        // 5. Estabilidad básica
        // (La estabilidad temporal se maneja en el SignalProcessor, aquí validamos estática del frame)
        
        return MeasurementState.CONTACT_CANDIDATE
    }
}
