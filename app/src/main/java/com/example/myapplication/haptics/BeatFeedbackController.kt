package com.example.myapplication.haptics

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.myapplication.ppg.BeatEvent
import com.example.myapplication.ppg.PpgPhysiologyClassifier

/**
 * Controlador de retroalimentación háptica y sonora por latido.
 * REGLA CRÍTICA: Solo emite beep/vibración cuando hay PPG_VALID.
 * No genera feedback sobre objetos, sábanas o señal no fisiológica.
 */
class BeatFeedbackController(context: Context) {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private var toneGenerator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 80)
    } catch (e: Exception) {
        Log.e("BeatFeedback", "Error creating ToneGenerator", e)
        null
    }

    var isBeepEnabled: Boolean = true
        set(value) {
            field = value
            Log.d("BeatFeedback", "Beep ${if (value) "enabled" else "disabled"}")
        }
    
    var isVibrationEnabled: Boolean = true
        set(value) {
            field = value
            Log.d("BeatFeedback", "Vibration ${if (value) "enabled" else "disabled"}")
        }
    
    // Tracking para evitar doble feedback
    private var lastFeedbackTimestampNs: Long = 0
    private val minFeedbackIntervalNs = 200_000_000L // 200ms entre feedbacks
    
    // Contadores para telemetría
    private var totalTriggers: Int = 0
    private var rejectedInvalidState: Int = 0
    private var rejectedRateLimited: Int = 0

    /**
     * Emite retroalimentación por latido detectado.
     * 
     * REQUISITOS OBLIGATORIOS:
     * - validityState debe ser PPG_VALID
     * - beat.confidence >= umbral mínimo
     * - Intervalo mínimo desde último feedback cumplido
     * 
     * @param beat Evento de latido detectado
     * @param validityState Estado de validez PPG actual
     */
    fun triggerOnBeat(
        beat: BeatEvent,
        validityState: PpgPhysiologyClassifier.PpgValidityState
    ) {
        // ========== VALIDACIÓN DE ESTADO FISIOLÓGICO ==========
        // REGLA CRÍTICA: Solo emitir feedback si hay PPG real
        if (validityState != PpgPhysiologyClassifier.PpgValidityState.PPG_VALID) {
            rejectedInvalidState++
            if (rejectedInvalidState % 30 == 1) { // Log cada ~1 segundo a 30 FPS
                Log.d("BeatFeedback", "Feedback suprimido: estado=$validityState (no PPG_VALID)")
            }
            return
        }
        
        // ========== VALIDACIÓN DE CONFIANZA ==========
        if (beat.confidence < 0.5) {
            return
        }
        
        // ========== RATE LIMITING ==========
        val now = System.nanoTime()
        val timeSinceLast = now - lastFeedbackTimestampNs
        if (timeSinceLast < minFeedbackIntervalNs) {
            rejectedRateLimited++
            return
        }
        
        // ========== EMITIR BEEP ==========
        if (isBeepEnabled) {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_PIP, 40)
            } catch (e: Exception) {
                Log.e("BeatFeedback", "Beep error", e)
            }
        }
        
        // ========== EMITIR VIBRACIÓN ==========
        if (isVibrationEnabled) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(30)
                }
            } catch (e: Exception) {
                Log.e("BeatFeedback", "Vibration error", e)
            }
        }
        
        lastFeedbackTimestampNs = now
        totalTriggers++
        
        Log.d("BeatFeedback", "Feedback emitted #${totalTriggers} (BPM=${"%.1f".format(beat.instantaneousBpm ?: 0.0)}, conf=${"%.2f".format(beat.confidence)})")
    }
    
    /**
     * Obtiene estadísticas de feedback.
     */
    fun getStats(): FeedbackStats {
        return FeedbackStats(
            totalTriggers = totalTriggers,
            rejectedInvalidState = rejectedInvalidState,
            rejectedRateLimited = rejectedRateLimited,
            beepEnabled = isBeepEnabled,
            vibrationEnabled = isVibrationEnabled
        )
    }

    fun release() {
        toneGenerator?.release()
        toneGenerator = null
        Log.d("BeatFeedback", "Controller released. Total triggers: $totalTriggers")
    }
    
    fun reset() {
        lastFeedbackTimestampNs = 0
        totalTriggers = 0
        rejectedInvalidState = 0
        rejectedRateLimited = 0
    }
}

/**
 * Estadísticas de retroalimentación.
 */
data class FeedbackStats(
    val totalTriggers: Int,
    val rejectedInvalidState: Int,
    val rejectedRateLimited: Int,
    val beepEnabled: Boolean,
    val vibrationEnabled: Boolean
)
