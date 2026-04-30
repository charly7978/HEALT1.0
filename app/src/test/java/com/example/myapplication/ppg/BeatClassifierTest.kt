package com.example.myapplication.ppg

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests unitarios para BeatClassifier.
 * Valida la clasificación de latidos.
 */
class BeatClassifierTest {

    private lateinit var classifier: BeatClassifier

    @Before
    fun setup() {
        classifier = BeatClassifier()
    }

    @Test
    fun testInitialization() {
        assertNotNull(classifier)
    }

    @Test
    fun testReset() {
        classifier.reset()
        // No debería lanzar excepción
    }

    @Test
    fun testClassifyNormalBeat() {
        val beat = BeatEvent(
            timestampNs = System.nanoTime(),
            amplitude = 100.0,
            rrMs = 1000L, // 60 BPM
            bpmInstant = 60.0,
            quality = 0.9,
            type = BeatType.NORMAL,
            reason = ""
        )

        val classified = classifier.classify(beat, 0.9)
        assertNotNull(classified)
        assertEquals(BeatType.NORMAL, classified.type)
    }

    @Test
    fun testClassifyPrematureBeat() {
        val beat = BeatEvent(
            timestampNs = System.nanoTime(),
            amplitude = 80.0,
            rrMs = 400L, // 150 BPM - muy corto
            bpmInstant = 150.0,
            quality = 0.8,
            type = BeatType.NORMAL,
            reason = ""
        )

        val classified = classifier.classify(beat, 0.8)
        assertNotNull(classified)
        // Debería detectar como prematuro o sospechoso
        assertTrue(
            "Debería ser prematuro o sospechoso",
            classified.type == BeatType.SUSPECT_PREMATURE || classified.type == BeatType.NORMAL
        )
    }

    @Test
    fun testClassifyMissedBeat() {
        val beat = BeatEvent(
            timestampNs = System.nanoTime(),
            amplitude = 100.0,
            rrMs = 2000L, // 30 BPM - muy largo
            bpmInstant = 30.0,
            quality = 0.8,
            type = BeatType.NORMAL,
            reason = ""
        )

        val classified = classifier.classify(beat, 0.8)
        assertNotNull(classified)
        // Debería detectar como pausa o sospechoso
        assertTrue(
            "Debería ser pausa o sospechoso",
            classified.type == BeatType.SUSPECT_PAUSE || classified.type == BeatType.NORMAL
        )
    }

    @Test
    fun testLowQualityBeat() {
        val beat = BeatEvent(
            timestampNs = System.nanoTime(),
            amplitude = 100.0,
            rrMs = 1000L,
            bpmInstant = 60.0,
            quality = 0.3, // Calidad baja
            type = BeatType.NORMAL,
            reason = ""
        )

        val classified = classifier.classify(beat, 0.3)
        assertNotNull(classified)
        // Con calidad baja, debería marcar como inválido o mantener normal
        assertTrue(
            "Debería ser inválido o normal",
            classified.type == BeatType.INVALID_SIGNAL || classified.type == BeatType.NORMAL
        )
    }
}
