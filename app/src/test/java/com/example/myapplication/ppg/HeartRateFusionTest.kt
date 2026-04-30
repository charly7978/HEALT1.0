package com.example.myapplication.ppg

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests unitarios para HeartRateFusion.
 * Valida la fusión de detectores y el consenso.
 */
class HeartRateFusionTest {

    private lateinit var fusion: HeartRateFusion
    private val samplingRate = 30.0

    @Before
    fun setup() {
        fusion = HeartRateFusion(samplingRate)
    }

    @Test
    fun testInitialization() {
        assertNotNull(fusion)
        assertEquals(0.0, fusion.getCurrentBpm(), 0.001)
    }

    @Test
    fun testReset() {
        fusion.reset()
        assertEquals(0.0, fusion.getCurrentBpm(), 0.001)
    }

    @Test
    fun testProcessSample() {
        val signal = generateSyntheticPpgSignal()
        var beatCount = 0

        signal.forEach { value ->
            val beat = fusion.processSample(value, System.nanoTime(), 0.9)
            if (beat != null) {
                beatCount++
            }
        }

        assertTrue("Debería detectar latidos", beatCount > 0)
    }

    @Test
    fun testGetCurrentBpm() {
        val signal = generateSyntheticPpgSignal()

        signal.forEach { value ->
            fusion.processSample(value, System.nanoTime(), 0.9)
        }

        val bpm = fusion.getCurrentBpm()
        assertTrue("BPM debería ser positivo", bpm > 0)
        assertTrue("BPM debería ser razonable", bpm in 40.0..200.0)
    }

    @Test
    fun testLowQualitySignal() {
        val signal = generateSyntheticPpgSignal()
        var beatCount = 0

        signal.forEach { value ->
            val beat = fusion.processSample(value, System.nanoTime(), 0.2) // SQI muy bajo
            if (beat != null) {
                beatCount++
            }
        }

        assertEquals(0, beatCount)
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
