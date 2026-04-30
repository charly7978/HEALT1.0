package com.example.myapplication.ppg

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests unitarios para PeakDetectorDerivative.
 * Usa datos sintéticos para validar el algoritmo.
 */
class PeakDetectorDerivativeTest {

    private lateinit var detector: PeakDetectorDerivative
    private val samplingRate = 30.0

    @Before
    fun setup() {
        detector = PeakDetectorDerivative(samplingRate)
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
        val signal = generateSyntheticPpgSignal()
        var peakCount = 0

        signal.forEach { value ->
            val beat = detector.processSample(value, System.nanoTime(), 0.9)
            if (beat != null) {
                peakCount++
            }
        }

        assertTrue("Debería detectar picos", peakCount > 0)
        assertTrue("No debería detectar demasiados picos", peakCount < 15)
    }

    @Test
    fun testLowQualitySignal() {
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

    private fun generateSyntheticPpgSignal(): List<Double> {
        val signal = ArrayList<Double>()
        val totalBeats = 5

        for (beat in 0 until totalBeats) {
            for (i in 0 until 10) signal.add(50.0)
            for (i in 0 until 5) signal.add(50.0 + i * 10.0)
            signal.add(100.0)
            for (i in 0 until 8) signal.add(100.0 - i * 8.0)
            for (i in 0 until 7) signal.add(50.0)
        }

        return signal
    }
}
