package com.example.myapplication.ppg

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests unitarios para PeakDetectorElgendi.
 * Usa datos sintéticos para validar el algoritmo, pero el detector
 * solo se usa con datos reales de cámara en producción.
 */
class PeakDetectorElgendiTest {

    private lateinit var detector: PeakDetectorElgendi
    private val samplingRate = 30.0

    @Before
    fun setup() {
        detector = PeakDetectorElgendi(samplingRate)
    }

    @Test
    fun testInitialization() {
        assertNotNull(detector)
        assertEquals(0.0, detector.getCurrentBpm(), 0.001)
    }

    @Test
    fun testReset() {
        detector.reset()
        assertEquals(0.0, detector.getCurrentBpm(), 0.001)
    }

    @Test
    fun testDetectPeakWithSyntheticSignal() {
        // Generar señal sintética con picos conocidos
        val signal = generateSyntheticPpgSignal()
        var peakCount = 0

        signal.forEach { value ->
            val beat = detector.processSample(value, System.nanoTime(), 0.9)
            if (beat != null) {
                peakCount++
            }
        }

        // Debería detectar aproximadamente 5 picos (60 BPM a 30 FPS por 5 segundos)
        assertTrue("Debería detectar picos", peakCount > 0)
        assertTrue("No debería detectar demasiados picos", peakCount < 15)
    }

    @Test
    fun testRefractoryPeriod() {
        // Probar período refractario: no debería detectar picos muy cercanos
        val signal = generateSyntheticPpgSignal()
        var lastPeakTime = 0L
        var tooClosePeaks = 0

        signal.forEach { value ->
            val beat = detector.processSample(value, System.nanoTime(), 0.9)
            if (beat != null) {
                val timeSinceLastPeak = beat.timestampNs - lastPeakTime
                if (lastPeakTime > 0 && timeSinceLastPeak < 200_000_000L) { // Menos de 200ms
                    tooClosePeaks++
                }
                lastPeakTime = beat.timestampNs
            }
        }

        assertEquals(0, tooClosePeaks)
    }

    @Test
    fun testLowQualitySignal() {
        // Con SQI bajo, no debería detectar picos
        val signal = generateSyntheticPpgSignal()
        var peakCount = 0

        signal.forEach { value ->
            val beat = detector.processSample(value, System.nanoTime(), 0.3) // SQI bajo
            if (beat != null) {
                peakCount++
            }
        }

        assertEquals(0, peakCount)
    }

    @Test
    fun testGetCurrentBpm() {
        val signal = generateSyntheticPpgSignal()

        signal.forEach { value ->
            detector.processSample(value, System.nanoTime(), 0.9)
        }

        val bpm = detector.getCurrentBpm()
        assertTrue("BPM debería ser positivo", bpm > 0)
        assertTrue("BPM debería ser razonable", bpm in 40.0..200.0)
    }

    /**
     * Genera señal PPG sintética con picos simulados.
     * SOLO PARA TESTS - No usar en producción.
     */
    private fun generateSyntheticPpgSignal(): List<Double> {
        val signal = ArrayList<Double>()
        val samplesPerBeat = 30 // 1 segundo a 30 Hz = 60 BPM
        val totalBeats = 5

        for (beat in 0 until totalBeats) {
            // Base line
            for (i in 0 until 10) {
                signal.add(50.0)
            }
            // Ascenso
            for (i in 0 until 5) {
                signal.add(50.0 + i * 10.0)
            }
            // Pico
            signal.add(100.0)
            // Descenso
            for (i in 0 until 8) {
                signal.add(100.0 - i * 8.0)
            }
            // Base line
            for (i in 0 until 7) {
                signal.add(50.0)
            }
        }

        return signal
    }
}
