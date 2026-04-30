package com.example.myapplication.signal

import kotlin.math.PI
import kotlin.math.tan

/**
 * Filtro IIR de segundo orden (Biquad) para procesamiento en tiempo real.
 * Implementación causal de bajo retardo.
 */
class BiquadFilter(
    private val type: FilterType,
    private val samplingRate: Double,
    private val centerFreq: Double,
    private val q: Double = 0.707
) {
    enum class FilterType { LOWPASS, HIGHPASS, BANDPASS }

    private var a0: Double = 1.0
    private var a1: Double = 0.0
    private var a2: Double = 0.0
    private var b0: Double = 0.0
    private var b1: Double = 0.0
    private var b2: Double = 0.0

    private var x1: Double = 0.0
    private var x2: Double = 0.0
    private var y1: Double = 0.0
    private var y2: Double = 0.0

    init {
        updateCoefficients()
    }

    private fun updateCoefficients() {
        val omega = 2.0 * PI * centerFreq / samplingRate
        val sn = Math.sin(omega)
        val cs = Math.cos(omega)
        val alpha = sn / (2.0 * q)

        when (type) {
            FilterType.LOWPASS -> {
                b0 = (1.0 - cs) / 2.0
                b1 = 1.0 - cs
                b2 = (1.0 - cs) / 2.0
                a0 = 1.0 + alpha
                a1 = -2.0 * cs
                a2 = 1.0 - alpha
            }
            FilterType.HIGHPASS -> {
                b0 = (1.0 + cs) / 2.0
                b1 = -(1.0 + cs)
                b2 = (1.0 + cs) / 2.0
                a0 = 1.0 + alpha
                a1 = -2.0 * cs
                a2 = 1.0 - alpha
            }
            FilterType.BANDPASS -> {
                b0 = alpha
                b1 = 0.0
                b2 = -alpha
                a0 = 1.0 + alpha
                a1 = -2.0 * cs
                a2 = 1.0 - alpha
            }
        }

        // Normalizar
        b0 /= a0
        b1 /= a0
        b2 /= a0
        a1 /= a0
        a2 /= a0
    }

    fun process(sample: Double): Double {
        val out = b0 * sample + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = sample
        y2 = y1
        y1 = out
        return out
    }
}

/**
 * Filtro Paso-Banda compuesto para PPG (0.5Hz a 5Hz aprox).
 * Elimina DC/Modulación respiratoria y ruido de alta frecuencia.
 */
class PPGBandpassFilter(samplingRate: Double) {
    private val hp = BiquadFilter(BiquadFilter.FilterType.HIGHPASS, samplingRate, 0.5, 0.707)
    private val lp = BiquadFilter(BiquadFilter.FilterType.LOWPASS, samplingRate, 5.0, 0.707)

    fun filter(sample: Double): Double {
        return lp.process(hp.process(sample))
    }
}
