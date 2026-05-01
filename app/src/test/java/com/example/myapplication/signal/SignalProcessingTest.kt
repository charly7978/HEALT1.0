package com.example.myapplication.signal

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests unitarios para procesamiento de señal PPG.
 * Usa datos sintéticos SOLO para validación de algoritmos.
 */
class SignalProcessingTest {

    @Test
    fun testSpo2Estimator_withoutCalibration_returnsNoCalibration() {
        val estimator = Spo2Estimator()
        val result = estimator.estimate(
            acRed = 0.5, dcRed = 100.0,
            acGreen = 0.3, dcGreen = 100.0,
            sqi = 0.8
        )
        
        assertEquals(Spo2Estimator.Spo2Status.NO_CALIBRATION, result.status)
        assertNull(result.value)
    }

    @Test
    fun testSpo2Estimator_withCalibration_returnsValue() {
        val estimator = Spo2Estimator()
        estimator.setCalibration(110.0, -20.0)
        
        val result = estimator.estimate(
            acRed = 0.5, dcRed = 100.0,
            acGreen = 0.3, dcGreen = 100.0,
            sqi = 0.8
        )
        
        assertEquals(Spo2Estimator.Spo2Status.VALID, result.status)
        assertNotNull(result.value)
        assertTrue(result.value!! in 70..100)
    }

    @Test
    fun testSpo2Estimator_lowQuality_returnsLowQuality() {
        val estimator = Spo2Estimator()
        estimator.setCalibration(110.0, -20.0)
        
        val result = estimator.estimate(
            acRed = 0.01, dcRed = 1.0,
            acGreen = 0.01, dcGreen = 1.0,
            sqi = 0.5
        )
        
        assertEquals(Spo2Estimator.Spo2Status.LOW_QUALITY, result.status)
        assertNull(result.value)
    }

    @Test
    fun testPpgPeakDetector_lowSqi_returnsNull() {
        val detector = PpgPeakDetector()
        val beat = detector.detect(
            filteredValue = 1.0,
            timestampNs = 1_000_000_000L,
            sqi = 0.3,
            acGreen = 0.1,
            dcGreen = 100.0
        )
        
        assertNull(beat)
    }

    @Test
    fun testPpgPeakDetector_insufficientAC_returnsNull() {
        val detector = PpgPeakDetector()
        val beat = detector.detect(
            filteredValue = 1.0,
            timestampNs = 1_000_000_000L,
            sqi = 0.8,
            acGreen = 0.01,
            dcGreen = 100.0
        )
        
        assertNull(beat)
    }

    @Test
    fun testRhythmAnalyzer_insufficientData_returnsInsufficient() {
        val analyzer = RhythmAnalyzer()
        val metrics = analyzer.analyze()
        
        assertEquals(RhythmAnalyzer.RhythmState.INSUFFICIENT_DATA, metrics.state)
        assertFalse(analyzer.isArhythmic)
    }

    @Test
    fun testRhythmAnalyzer_withBeats_calculatesMetrics() {
        val analyzer = RhythmAnalyzer()
        
        // Agregar latidos sintéticos (solo para test)
        val baseTime = System.nanoTime()
        repeat(10) { i ->
            analyzer.addConfirmedBeat(
                RhythmAnalyzer.ConfirmedBeat(
                    timestampNs = baseTime + i * 1_000_000_000L,
                    amplitude = 1.0,
                    rrMs = 1000L,
                    confidence = 0.9,
                    sourceChannel = "green"
                )
            )
        }
        
        val metrics = analyzer.analyze()
        
        assertEquals(RhythmAnalyzer.RhythmState.REGULAR, metrics.state)
        assertEquals(10, metrics.beatCount)
        assertEquals(1000.0, metrics.meanRR, 0.1)
    }

    @Test
    fun testPpgPhysiologyClassifier_noRedDominance_returnsNoPhysiological() {
        val classifier = PpgPhysiologyClassifier()
        
        val sample = PpgSample(
            timestampNs = 0L,
            rawRed = 50.0,
            rawGreen = 100.0,
            rawBlue = 50.0,
            roiStats = RoiStats(0, 0, 10, 10, 100, 50.0, 100.0, 50.0, 5.0, 10.0, 5.0),
            clipping = ClippingInfo(0.0, 0.0, false, false),
            motionScore = 0.1,
            exposureDiagnostics = ExposureDiagnostics(null, null, null, true)
        )
        
        val result = classifier.classify(
            sample = sample,
            filteredSignal = 0.5,
            acRed = 0.1,
            acGreen = 0.1,
            sqi = 0.5
        )
        
        assertEquals(PpgPhysiologyClassifier.PpgValidityState.NO_PHYSIOLOGICAL_SIGNAL, result.state)
    }

    @Test
    fun testPpgSignalQuality_calculatesSqi() {
        val quality = PpgSignalQuality()
        
        val sample = PpgSample(
            timestampNs = 0L,
            rawRed = 150.0,
            rawGreen = 100.0,
            rawBlue = 50.0,
            roiStats = RoiStats(0, 0, 10, 10, 100, 150.0, 100.0, 50.0, 10.0, 5.0, 5.0),
            clipping = ClippingInfo(0.0, 0.0, false, false),
            motionScore = 0.1,
            exposureDiagnostics = ExposureDiagnostics(null, null, null, true)
        )
        
        val result = quality.compute(
            sample = sample,
            acRed = 0.5,
            acGreen = 0.3,
            filteredSignal = 0.5,
            isPeriodical = true
        )
        
        assertTrue(result.totalSqi > 0)
        assertTrue(result.perfusionIndex > 0)
    }
}
