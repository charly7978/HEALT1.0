package com.example.myapplication.ppg

import com.example.myapplication.signal.PpgFrame
import com.example.myapplication.signal.ClippingInfo
import com.example.myapplication.signal.ExposureDiagnostics
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import kotlin.math.PI
import kotlin.math.sin

/**
 * Tests unitarios para el clasificador fisiológico PPG.
 * Verifica correcta clasificación de señales válidas vs no fisiológicas.
 */
class PpgPhysiologyClassifierTest {

    private lateinit var classifier: PpgPhysiologyClassifier

    @Before
    fun setup() {
        classifier = PpgPhysiologyClassifier()
    }

    companion object {
        const val SAMPLE_RATE = 30.0

        // Umbrales del clasificador (deben coincidir con implementación)
        const val MIN_PERFUSION = 0.02
        const val MIN_SQI = 0.3
    }

    /**
     * Crea un PpgFrame de prueba con parámetros controlados.
     */
    private fun createTestFrame(
        avgRed: Double = 120.0,
        avgGreen: Double = 80.0,
        avgBlue: Double = 40.0,
        redAc: Double = 0.5,
        greenAc: Double = 0.3,
        redDc: Double = 120.0,
        greenDc: Double = 80.0,
        clipping: ClippingInfo = ClippingInfo(0.0, 0.0, false, false),
        motionScore: Double = 0.0,
        timestampNs: Long = System.nanoTime()
    ): PpgFrame {
        return PpgFrame(
            timestampNs = timestampNs,
            fpsEstimate = SAMPLE_RATE,
            avgRed = avgRed,
            avgGreen = avgGreen,
            avgBlue = avgBlue,
            redDc = redDc,
            greenDc = greenDc,
            blueDc = avgBlue,
            redAc = redAc,
            greenAc = greenAc,
            blueAc = 0.1,
            roiRect = PpgFrame.RoiRect(160, 120, 48, 36),
            saturationRatio = clipping.highClipRatio,
            darknessRatio = clipping.lowClipRatio,
            skinLikelihood = 0.8,
            redDominance = avgRed / (avgGreen + 1.0),
            greenPulseCandidate = greenAc / (greenDc + 0.001),
            textureScore = 15.0,
            motionScore = motionScore,
            rawOpticalSignal = avgGreen,
            normalizedSignal = avgGreen / 255.0,
            clipping = clipping,
            exposureDiagnostics = ExposureDiagnostics(
                10_000_000L, 800, 33_000_000L, true
            ),
            perfusionIndex = (greenAc / greenDc) * 100.0
        )
    }

    @Test
    fun `test saturated signal returns SATURATED state`() {
        val frame = createTestFrame(
            avgRed = 252.0,
            avgGreen = 250.0,
            clipping = ClippingInfo(0.35, 0.0, true, false) // 35% saturado
        )

        val result = classifier.classify(
            frame = frame,
            filteredSignal = 0.0,
            acRed = 0.5,
            dcRed = 252.0,
            acGreen = 0.3,
            dcGreen = 250.0,
            sqi = 0.8,
            samplingRate = SAMPLE_RATE
        )

        assertEquals(
            PpgPhysiologyClassifier.PpgValidityState.SATURATED,
            result.state
        )
        assertFalse(result.isPhysiologicalSignal)
    }

    @Test
    fun `test dark signal returns MEASURING_RAW_OPTICAL state`() {
        val frame = createTestFrame(
            avgRed = 3.0,
            avgGreen = 2.0,
            clipping = ClippingInfo(0.0, 0.85, false, true) // 85% oscuro
        )

        val result = classifier.classify(
            frame = frame,
            filteredSignal = 0.0,
            acRed = 0.0,
            dcRed = 3.0,
            acGreen = 0.0,
            dcGreen = 2.0,
            sqi = 0.8,
            samplingRate = SAMPLE_RATE
        )

        assertEquals(
            PpgPhysiologyClassifier.PpgValidityState.MEASURING_RAW_OPTICAL,
            result.state
        )
    }

    @Test
    fun `test low perfusion returns LOW_PERFUSION state`() {
        // Sin componente AC (sin pulsos)
        val frame = createTestFrame(
            avgRed = 100.0,
            avgGreen = 80.0,
            redAc = 0.001, // Muy bajo
            greenAc = 0.001
        )

        val result = classifier.classify(
            frame = frame,
            filteredSignal = 0.0,
            acRed = 0.001,
            dcRed = 100.0,
            acGreen = 0.001,
            dcGreen = 80.0,
            sqi = 0.8,
            samplingRate = SAMPLE_RATE
        )

        assertEquals(
            PpgPhysiologyClassifier.PpgValidityState.LOW_PERFUSION,
            result.state
        )
        assertFalse(result.isPhysiologicalSignal)
    }

    @Test
    fun `test no red dominance returns NO_PPG_PHYSIOLOGICAL_SIGNAL`() {
        // Objeto no rojo (ej: sábana verde)
        val frame = createTestFrame(
            avgRed = 50.0,
            avgGreen = 100.0, // Verde > Rojo
            avgBlue = 40.0
        )

        val result = classifier.classify(
            frame = frame,
            filteredSignal = 0.0,
            acRed = 0.5,
            dcRed = 50.0,
            acGreen = 0.3,
            dcGreen = 100.0,
            sqi = 0.8,
            samplingRate = SAMPLE_RATE
        )

        assertEquals(
            PpgPhysiologyClassifier.PpgValidityState.NO_PPG_PHYSIOLOGICAL_SIGNAL,
            result.state
        )
        assertFalse(result.isPhysiologicalSignal)
    }

    @Test
    fun `test no AC pulsatility returns NO_PPG_PHYSIOLOGICAL_SIGNAL`() {
        // Objeto rojo pero estático (sin AC) - sábana roja
        val frame = createTestFrame(
            avgRed = 200.0,
            avgGreen = 50.0,
            redAc = 0.0, // Sin pulsos
            greenAc = 0.0
        )

        val result = classifier.classify(
            frame = frame,
            filteredSignal = 0.0,
            acRed = 0.0,
            dcRed = 200.0,
            acGreen = 0.0,
            dcGreen = 50.0,
            sqi = 0.8,
            samplingRate = SAMPLE_RATE
        )

        assertEquals(
            PpgPhysiologyClassifier.PpgValidityState.NO_PPG_PHYSIOLOGICAL_SIGNAL,
            result.state
        )
        assertFalse(result.isPhysiologicalSignal)
    }

    @Test
    fun `test high motion returns MOTION_ARTIFACT state`() {
        val frame = createTestFrame(
            motionScore = 0.6 // Alto movimiento
        )

        val result = classifier.classify(
            frame = frame,
            filteredSignal = 0.0,
            acRed = 0.5,
            dcRed = 120.0,
            acGreen = 0.3,
            dcGreen = 80.0,
            sqi = 0.8,
            samplingRate = SAMPLE_RATE
        )

        assertEquals(
            PpgPhysiologyClassifier.PpgValidityState.MOTION_ARTIFACT,
            result.state
        )
        assertFalse(result.isPhysiologicalSignal)
    }

    @Test
    fun `test low SQI returns PPG_CANDIDATE or NO_PPG`() {
        val frame = createTestFrame()

        val result = classifier.classify(
            frame = frame,
            filteredSignal = 0.0,
            acRed = 0.5,
            dcRed = 120.0,
            acGreen = 0.3,
            dcGreen = 80.0,
            sqi = 0.2, // Muy bajo
            samplingRate = SAMPLE_RATE
        )

        // Con SQI < 0.3 no debe ser PPG_VALID
        assertNotEquals(
            PpgPhysiologyClassifier.PpgValidityState.PPG_VALID,
            result.state
        )
    }

    @Test
    fun `test valid signal progresses through states`() {
        // Simular acumulación de frames válidos
        val baseFrame = createTestFrame(
            avgRed = 150.0,
            avgGreen = 80.0,
            redAc = 1.0,
            greenAc = 0.5
        )

        val results = mutableListOf<PpgPhysiologyClassifier.ClassificationResult>()

        // Procesar 300 frames (10 segundos) de señal válida
        repeat(300) { i ->
            // Simular señal periódica (1 Hz = 60 BPM)
            val phase = 2 * PI * (i % 30) / 30.0
            val signalValue = sin(phase) * 0.5

            val frame = baseFrame.copy(
                timestampNs = i * 33_333_333L,
                avgGreen = 80.0 + signalValue * 10,
                greenAc = kotlin.math.abs(signalValue) * 0.5 + 0.1
            )

            val result = classifier.classify(
                frame = frame,
                filteredSignal = signalValue,
                acRed = frame.redAc,
                dcRed = frame.redDc,
                acGreen = frame.greenAc,
                dcGreen = frame.greenDc,
                sqi = 0.8,
                samplingRate = SAMPLE_RATE
            )

            results.add(result)
        }

        // Verificar progresión de estados
        val initialStates = results.take(10).map { it.state }
        val finalStates = results.takeLast(50).map { it.state }

        // Al inicio debe estar buscando
        assertTrue(
            "Estado inicial incorrecto",
            initialStates.any {
                it == PpgPhysiologyClassifier.PpgValidityState.SEARCHING_PPG ||
                it == PpgPhysiologyClassifier.PpgValidityState.MEASURING_RAW_OPTICAL
            }
        )

        // Al final debe ser válido o candidato
        assertTrue(
            "No alcanzó estado válido: ${finalStates.distinct()}",
            finalStates.any {
                it == PpgPhysiologyClassifier.PpgValidityState.PPG_VALID ||
                it == PpgPhysiologyClassifier.PpgValidityState.PPG_CANDIDATE
            }
        )
    }

    @Test
    fun `test perfusion index calculation`() {
        val frame = createTestFrame(
            greenAc = 0.5,
            greenDc = 100.0 // PI = (0.5/100)*100 = 0.5%
        )

        val result = classifier.classify(
            frame = frame,
            filteredSignal = 0.0,
            acRed = 0.5,
            dcRed = 120.0,
            acGreen = 0.5,
            dcGreen = 100.0,
            sqi = 0.8,
            samplingRate = SAMPLE_RATE
        )

        // PI = 0.5% es aceptable
        assertNotEquals(
            PpgPhysiologyClassifier.PpgValidityState.LOW_PERFUSION,
            result.state
        )
    }

    @Test
    fun `test classifier reset clears state`() {
        // Procesar señal
        val frame = createTestFrame()
        repeat(100) {
            classifier.classify(
                frame = frame,
                filteredSignal = 0.0,
                acRed = 0.5,
                dcRed = 120.0,
                acGreen = 0.3,
                dcGreen = 80.0,
                sqi = 0.8,
                samplingRate = SAMPLE_RATE
            )
        }

        // Resetear
        classifier.reset()

        // Primera clasificación después de reset debe ser inicial
        val result = classifier.classify(
            frame = frame,
            filteredSignal = 0.0,
            acRed = 0.5,
            dcRed = 120.0,
            acGreen = 0.3,
            dcGreen = 80.0,
            sqi = 0.8,
            samplingRate = SAMPLE_RATE
        )

        // Después de reset, debe estar buscando
        assertEquals(
            PpgPhysiologyClassifier.PpgValidityState.SEARCHING_PPG,
            result.state
        )
    }

    @Test
    fun `test confidence is within valid range`() {
        val frame = createTestFrame()

        val result = classifier.classify(
            frame = frame,
            filteredSignal = 0.0,
            acRed = 0.5,
            dcRed = 120.0,
            acGreen = 0.3,
            dcGreen = 80.0,
            sqi = 0.8,
            samplingRate = SAMPLE_RATE
        )

        assertTrue(
            "Confianza fuera de rango [0,1]: ${result.confidence}",
            result.confidence in 0.0..1.0
        )
    }
}
