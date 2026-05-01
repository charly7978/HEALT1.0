package com.example.myapplication.ppg

/**
 * Filtros digitales IIR Biquad Butterworth para procesamiento de señal.
 * Implementación genérica reutilizable.
 */
class BiquadFilter(
    private val filterType: FilterType,
    private val sampleRate: Double,
    private val cutoffFreq: Double,
    private val q: Double = 0.707
) {
    enum class FilterType {
        LOWPASS, HIGHPASS, BANDPASS, NOTCH
    }

    private var b0 = 0.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0

    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    init {
        calculateCoefficients()
    }

    private fun calculateCoefficients() {
        val w0 = 2.0 * Math.PI * cutoffFreq / sampleRate
        val alpha = Math.sin(w0) / (2.0 * q)
        val cosw0 = Math.cos(w0)

        when (filterType) {
            FilterType.LOWPASS -> {
                b0 = (1.0 - cosw0) / 2.0
                b1 = 1.0 - cosw0
                b2 = (1.0 - cosw0) / 2.0
                a0 = 1.0 + alpha
                a1 = -2.0 * cosw0
                a2 = 1.0 - alpha
            }
            FilterType.HIGHPASS -> {
                b0 = (1.0 + cosw0) / 2.0
                b1 = -(1.0 + cosw0)
                b2 = (1.0 + cosw0) / 2.0
                a0 = 1.0 + alpha
                a1 = -2.0 * cosw0
                a2 = 1.0 - alpha
            }
            FilterType.BANDPASS -> {
                b0 = alpha
                b1 = 0.0
                b2 = -alpha
                a0 = 1.0 + alpha
                a1 = -2.0 * cosw0
                a2 = 1.0 - alpha
            }
            FilterType.NOTCH -> {
                b0 = 1.0
                b1 = -2.0 * cosw0
                b2 = 1.0
                a0 = 1.0 + alpha
                a1 = -2.0 * cosw0
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

    private var a0 = 1.0

    fun process(input: Double): Double {
        val output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = input
        y2 = y1
        y1 = output
        return output
    }

    fun reset() {
        x1 = 0.0
        x2 = 0.0
        y1 = 0.0
        y2 = 0.0
    }
}

/**
 * Filtro band-pass PPG optimizado para rango cardíaco humano (0.5 - 4.0 Hz).
 */
class PPGBandpassFilter(sampleRate: Double) {
    private val hp = BiquadFilter(BiquadFilter.FilterType.HIGHPASS, sampleRate, 0.5, 0.707)
    private val lp = BiquadFilter(BiquadFilter.FilterType.LOWPASS, sampleRate, 4.0, 0.707)

    fun filter(sample: Double): Double {
        return lp.process(hp.process(sample))
    }

    fun reset() {
        hp.reset()
        lp.reset()
    }
}
