package com.example.myapplication.haptics

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Controlador para retroalimentación sonora y háptica de latidos cardíacos.
 * Optimizado para respuesta de baja latencia.
 */
class BeatFeedbackController(context: Context) {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    // Usamos STREAM_MUSIC para asegurar que el volumen sea controlable y nítido
    private var toneGenerator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    } catch (e: Exception) {
        null
    }

    var isBeepEnabled: Boolean = true
    var isVibrationEnabled: Boolean = true

    fun trigger() {
        // Ejecución inmediata en el hilo actual para evitar latencia
        if (isBeepEnabled) {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_PIP, 50)
            } catch (e: Exception) {
                Log.e("Feedback", "Beep error", e)
            }
        }
        
        if (isVibrationEnabled) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(40, 200)) // 40ms, Amplitud fuerte
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(40)
                }
            } catch (e: Exception) {
                Log.e("Feedback", "Vibration error", e)
            }
        }
    }

    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }
}
