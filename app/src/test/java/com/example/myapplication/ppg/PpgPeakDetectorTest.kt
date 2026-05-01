package com.example.myapplication.ppg

import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.exp

/**
 * Tests unitarios para el detector de picos PPG.
 * Usa señales sintéticas SOLO para testing - nunca en producción.
 */
class PpgPeakDetectorTest {

    private lateinit var peakDetector: PpgPeakDetector

    @Before
    fun setup() {
        peakDetector = PpgPeakDetector()
    }

    companion object {
        const val SAMPLE_RATE = 30.0
        const val BPM = 60.0 // 1 Hz = 60 BPM
        const val PERIOD_SAMPLES = 30 // 30 samples por período a 1 Hz
    }

    /**
     * Genera señal PPG sintética tipo "onda de pulso" con componente sistólica y diastólica.
     * SOLO para testing - simulación matemática, no datos reales.
     */
    private fun generateSyntheticPpgSignal(
        durationSeconds: Double,
        bpm: Double,
        amplitude: Double = 1.0,
        noiseLevel: Double = 0.0
    ): List<Pair<Long, Double>> { // timestampNs, value
        val samples = (durationSeconds * SAMPLE_RATE).toInt()
        val periodSamples = (SAMPLE_RATE / (bpm / 60.0)).toInt()

        return (0 until samples).map { i ->
            val t = i / SAMPLE_RATE
            val phase = 2 * PI * (i % periodSamples) / periodSamples

            // Forma de onda PPG aproximada: pico sistólico rápido + onda diastólica
            val systolic = exp(-((phase - PI/4) * 2).let { it * it }) // Pico rápido
            val diastolic = 0.3 * exp(-((phase - 3*PI/2) * 1.5).let { it * it }) // Onda pequeña

            val baseSignal = systolic + diastolic
            val noise = if (noiseLevel > 0) (Math.random() - 0.5) * noiseLevel else 0.0

            val timestampNs = (t * 1_000_000_000).toLong()
            val value = (baseSignal * amplitude) + noise

            Pair(timestampNs, value)
        }
    }

    @Test
    fun `test detects peaks in synthetic PPG signal`() {
        // Generar señal de 60 BPM durante 10 segundos = ~10 picos esperados
        val signal = generateSyntheticPpgSignal(10.0, 60.0, amplitude = 1.0)

        val detectedBeats = mutableListOf<BeatEvent>()

        // Procesar con estado PPG_VALID
        signal.forEach { (timestampNs, value) ->
            val beat = peakDetector.detect(
                filteredValue = value,
                timestampNs = timestampNs,
                sqi = 0.8, // Buena calidad
                validityState = PpgPhysiologyClassifier.PpgValidityState.PPG_VALID,
                acComponent = 0.5
            )
            if (beat != null) detectedBeats.add(beat)
        }

        // Debe detectar ~10 picos (60 BPM * 10s = 10 ciclos)
        // Permitir margen por transitorios iniciales
        assertTrue(
            "Picos detectados insuficientes: ${detectedBeats.size}, esperado ~10",
            detectedBeats.size in 8..12
        )
    }

    @Test
    fun `test no peaks detected without PPG_VALID state`() {
        val signal = generateSyntheticPpgSignal(5.0, 60.0, amplitude = 1.0)

        val detectedBeats = mutableListOf<BeatEvent>()

        // Procesar con estado NO_PPG_PHYSIOLOGICAL_SIGNAL
        signal.forEach { (timestampNs, value) ->
            val beat = peakDetector.detect(
                filteredValue = value,
                timestampNs = timestampNs,
                sqi = 0.8,
                validityState = PpgPhysiologyClassifier.PpgValidityState.NO_PPG_PHYSIOLOGICAL_SIGNAL,
                acComponent = 0.5
            )
            if (beat != null) detectedBeats.add(beat)
        }

        // No debe detectar picos sin PPG_VALID
        assertEquals(
            "Picos detectados sin PPG_VALID: ${detectedBeats.size}",
            0,
            detectedBeats.size
        )
    }

    @Test
    fun `test no peaks with low SQI`() {
        val signal = generateSyntheticPpgSignal(5.0, 60.0, amplitude = 1.0)

        val detectedBeats = mutableListOf<BeatEvent>()

        // Procesar con SQI muy bajo
        signal.forEach { (timestampNs, value) ->
            val beat = peakDetector.detect(
                filteredValue = value,
                timestampNs = timestampNs,
                sqi = 0.2, // Muy bajo
                validityState = PpgPhysiologyClassifier.PpgValidityState.PPG_VALID,
                acComponent = 0.5
            )
            if (beat != null) detectedBeats.add(beat)
        }

        // No debe detectar con SQI < 0.4
        assertEquals(
            "Picos detectados con SQI bajo: ${detectedBeats.size}",
            0,
            detectedBeats.size
        )
    }

    @Test
    fun `test no peaks with low AC component`() {
        val signal = generateSyntheticPpgSignal(5.0, 60.0, amplitude = 1.0)

        val detectedBeats = mutableListOf<BeatEvent>()

        // Procesar con AC muy bajo (sin pulsos)
        signal.forEach { (timestampNs, value) ->
            val beat = peakDetector.detect(
                filteredValue = value,
                timestampNs = timestampNs,
                sqi = 0.8,
                validityState = PpgPhysiologyClassifier.PpgValidityState.PPG_VALID,
                acComponent = 0.01 // Muy bajo, < 0.02 umbral
            )
            if (beat != null) detectedBeats.add(beat)
        }

        // No debe detectar sin AC suficiente
        assertEquals(
            "Picos detectados sin AC suficiente: ${detectedBeats.size}",
            0,
            detectedBeats.size
        )
    }

    @Test
    fun `test refractory period limits detection rate`() {
        // Señal de 180 BPM (muy rápida) - debe limitarse por período refractario
        val signal = generateSyntheticPpgSignal(5.0, 180.0, amplitude = 1.0)

        val detectedBeats = mutableListOf<BeatEvent>()

        signal.forEach { (timestampNs, value) ->
            val beat = peakDetector.detect(
                filteredValue = value,
                timestampNs = timestampNs,
                sqi = 0.8,
                validityState = PpgPhysiologyClassifier.PpgValidityState.PPG_VALID,
                acComponent = 0.5
            )
            if (beat != null) detectedBeats.add(beat)
        }

        // A 180 BPM esperaríamos ~15 picos en 5s, pero el refractario limita a ~200ms mínimo
        // = max 300 BPM teórico, pero el detector debe ser conservador
        assertTrue(
            "Tasa de detección excesiva para refractario: ${detectedBeats.size}",
            detectedBeats.size <= 15
        )
    }

    @Test
    fun `test RR intervals are reasonable`() {
        val signal = generateSyntheticPpgSignal(10.0, 60.0, amplitude = 1.0)

        val detectedBeats = mutableListOf<BeatEvent>()

        signal.forEach { (timestampNs, value) ->
            val beat = peakDetector.detect(
                filteredValue = value,
                timestampNs = timestampNs,
                sqi = 0.8,
                validityState = PpgPhysiologyClassifier.PpgValidityState.PPG_VALID,
                acComponent = 0.5
            )
            if (beat != null) detectedBeats.add(beat)
        }

        // Verificar que los RR sean razonables (300ms - 2000ms)
        val rrValues = detectedBeats.drop(1).mapNotNull { it.rrMs }

        rrValues.forEach { rr ->
            assertTrue(
                "RR fuera de rango fisiológico: ${rr}ms",
                rr in 300.0..2000.0
            )
        }
    }

    @Test
    fun `test flat signal produces no peaks`() {
        // Señal plana sin pulsos (simula objeto estático)
        val samples = (5.0 * SAMPLE_RATE).toInt()
        val flatValue = 100.0

        val detectedBeats = mutableListOf<BeatEvent>()

        repeat(samples) { i ->
            val timestampNs = (i / SAMPLE_RATE * 1_000_000_000).toLong()
            val beat = peakDetector.detect(
                filteredValue = flatValue,
                timestampNs = timestampNs,
                sqi = 0.8,
                validityState = PpgPhysiologyClassifier.PpgValidityState.PPG_VALID,
                acComponent = 0.5
            )
            if (beat != null) detectedBeats.add(beat)
        }

        // Señal plana no debe producir picos
        assertEquals(
            "Picos detectados en señal plana: ${detectedBeats.size}",
            0,
            detectedBeats.size
        )
    }

    @Test
    fun `test random noise produces no peaks`() {
        // Ruido aleatorio sin estructura periódica
        val samples = (5.0 * SAMPLE_RATE).toInt()

        val detectedBeats = mutableListOf<BeatEvent>()

        repeat(samples) { i ->
            val timestampNs = (i / SAMPLE_RATE * 1_000_000_000).toLong()
            val noise = (Math.random() - 0.5) * 0.5 // Ruido de amplitud limitada

            val beat = peakDetector.detect(
                filteredValue = noise,
                timestampNs = timestampNs,
                sqi = 0.8,
                validityState = PpgPhysiologyClassifier.PpgValidityState.PPG_VALID,
                acComponent = 0.5
            )
            if (beat != null) detectedBeats.add(beat)
        }

        // Ruido aleatorio no debe producir picos fisiológicos
        assertTrue(
            "Demasiados picos en ruido aleatorio: ${detectedBeats.size}",
            detectedBeats.size <= 2 // Máximo 2 falsos positivos tolerables
        )
    }

    @Test
    fun `test detector reset clears state`() {
        // Procesar señal normal
        val signal1 = generateSyntheticPpgSignal(5.0, 60.0, amplitude = 1.0)
        signal1.forEach { (timestampNs, value) ->
            peakDetector.detect(
                filteredValue = value,
                timestampNs = timestampNs,
                sqi = 0.8,
                validityState = PpgPhysiologyClassifier.PpgValidityState.PPG_VALID,
                acComponent = 0.5
            )
        }

        // Resetear
        peakDetector.reset()

        // Procesar nueva señal
        val signal2 = generateSyntheticPpgSignal(5.0, 60.0, amplitude = 1.0)
        val detectedAfterReset = mutableListOf<BeatEvent>()

        signal2.forEach { (timestampNs, value) ->
            val beat = peakDetector.detect(
                filteredValue = value,
                timestampNs = timestampNs,
                sqi = 0.8,
                validityState = PpgPhysiologyClassifier.PpgValidityState.PPG_VALID,
                acComponent = 0.5
            )
            if (beat != null) detectedAfterReset.add(beat)
        }

        // Después de reset debe detectar normalmente
        assertTrue(
            "Detector no funciona correctamente después de reset: ${detectedAfterReset.size}",
            detectedAfterReset.size in 4..7
        )
    }

    @Test
    fun `test beat confidence is reasonable`() {
        val signal = generateSyntheticPpgSignal(5.0, 60.0, amplitude = 1.0)

        val detectedBeats = mutableListOf<BeatEvent>()

        signal.forEach { (timestampNs, value) ->
            val beat = peakDetector.detect(
                filteredValue = value,
                timestampNs = timestampNs,
                sqi = 0.8,
                validityState = PpgPhysiologyClassifier.PpgValidityState.PPG_VALID,
                acComponent = 0.5
            )
            if (beat != null) detectedBeats.add(beat)
        }

        // Verificar que la confianza esté en rango válido
        detectedBeats.forEach { beat ->
            assertTrue(
                "Confianza fuera de rango: ${beat.confidence}",
                beat.confidence in 0.0..1.0
            )
        }

        // Picos detectados deben tener confianza razonable
        val avgConfidence = detectedBeats.map { it.confidence }.average()
        assertTrue(
            "Confianza promedio demasiado baja: $avgConfidence",
            avgConfidence > 0.3
        )
    }
}
