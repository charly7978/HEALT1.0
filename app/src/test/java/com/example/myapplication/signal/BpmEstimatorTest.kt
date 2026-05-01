package com.example.myapplication.signal

import com.example.myapplication.ppg.BeatEvent
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

/**
 * Tests unitarios para el estimador de BPM.
 * Verifica cálculo de BPM, suavizado EMA y confianza.
 */
class BpmEstimatorTest {

    private lateinit var bpmEstimator: BpmEstimator

    @Before
    fun setup() {
        bpmEstimator = BpmEstimator()
    }

    companion object {
        // Intervalos RR para diferentes frecuencias cardíacas
        const val RR_60_BPM = 1000L // 1000ms = 60 BPM
        const val RR_80_BPM = 750L  // 750ms = 80 BPM
        const val RR_100_BPM = 600L // 600ms = 100 BPM
        const val RR_120_BPM = 500L // 500ms = 120 BPM
    }

    /**
     * Crea un BeatEvent sintético para testing.
     */
    private fun createBeatEvent(
        timestampNs: Long,
        rrMs: Double? = null,
        bpm: Double? = null,
        confidence: Double = 0.8
    ): BeatEvent {
        return BeatEvent(
            timestampNs = timestampNs,
            rrMs = rrMs,
            instantaneousBpm = bpm,
            confidence = confidence,
            amplitude = 1.0,
            prominence = 0.5,
            sourceChannel = "green"
        )
    }

    @Test
    fun `test no BPM with insufficient beats`() {
        // Agregar menos de 5 latidos
        repeat(3) { i ->
            bpmEstimator.addBeat(createBeatEvent(
                timestampNs = i * 1_000_000_000L,
                rrMs = RR_60_BPM.toDouble()
            ))
        }

        // No debe reportar BPM con < 5 latidos
        assertNull(bpmEstimator.getCurrentBpm())
        assertEquals(0.0, bpmEstimator.getConfidence(), 0.01)
        assertFalse(bpmEstimator.hasEnoughData())
    }

    @Test
    fun `test BPM calculation with regular 60 BPM`() {
        // Agregar 10 latidos regulares a 60 BPM
        repeat(10) { i ->
            bpmEstimator.addBeat(createBeatEvent(
                timestampNs = i * 1_000_000_000L,
                rrMs = RR_60_BPM.toDouble()
            ))
        }

        // Debe reportar BPM cercano a 60
        val bpm = bpmEstimator.getCurrentBpm()
        assertNotNull(bpm)
        assertTrue(
            "BPM fuera de rango esperado: $bpm",
            bpm!! in 58.0..62.0
        )
        assertTrue(bpmEstimator.hasEnoughData())
    }

    @Test
    fun `test BPM calculation with regular 80 BPM`() {
        // Agregar 10 latidos a 80 BPM
        repeat(10) { i ->
            bpmEstimator.addBeat(createBeatEvent(
                timestampNs = i * 750_000_000L,
                rrMs = RR_80_BPM.toDouble()
            ))
        }

        val bpm = bpmEstimator.getCurrentBpm()
        assertNotNull(bpm)
        assertTrue(
            "BPM fuera de rango esperado: $bpm",
            bpm!! in 78.0..82.0
        )
    }

    @Test
    fun `test EMA smoothing with changing heart rate`() {
        // Primero 60 BPM
        repeat(5) { i ->
            bpmEstimator.addBeat(createBeatEvent(
                timestampNs = i * 1_000_000_000L,
                rrMs = RR_60_BPM.toDouble()
            ))
        }

        val bpm60 = bpmEstimator.getCurrentBpm()

        // Luego cambiar a 100 BPM (repentinamente)
        repeat(5) { i ->
            bpmEstimator.addBeat(createBeatEvent(
                timestampNs = (5 + i) * 600_000_000L,
                rrMs = RR_100_BPM.toDouble()
            ))
        }

        val bpmAfterChange = bpmEstimator.getCurrentBpm()

        // El EMA debe suavizar el cambio - no debe saltar inmediatamente a 100
        assertTrue(
            "EMA no suavizó el cambio: $bpm60 -> $bpmAfterChange",
            bpmAfterChange!! > bpm60!! && bpmAfterChange < 95.0
        )
    }

    @Test
    fun `test confidence increases with regular rhythm`() {
        // Latidos muy regulares
        repeat(15) { i ->
            bpmEstimator.addBeat(createBeatEvent(
                timestampNs = i * 1_000_000_000L,
                rrMs = RR_60_BPM.toDouble()
            ))
        }

        val confidence = bpmEstimator.getConfidence()

        // Con ritmo regular, confianza debe ser alta
        assertTrue(
            "Confianza baja con ritmo regular: $confidence",
            confidence > 0.6
        )
    }

    @Test
    fun `test confidence decreases with irregular rhythm`() {
        // Agregar latidos irregulares (variación de ±200ms)
        val rrValues = listOf(800L, 1100L, 900L, 1200L, 850L, 1150L, 800L, 1100L, 950L, 1050L)

        rrValues.forEachIndexed { i, rr ->
            bpmEstimator.addBeat(createBeatEvent(
                timestampNs = i * 1_000_000_000L,
                rrMs = rr.toDouble()
            ))
        }

        val confidence = bpmEstimator.getConfidence()

        // Con ritmo irregular, confianza debe ser baja
        assertTrue(
            "Confianza alta con ritmo irregular: $confidence",
            confidence < 0.5
        )
    }

    @Test
    fun `test instant BPM calculation`() {
        bpmEstimator.addBeat(createBeatEvent(
            timestampNs = 0L,
            rrMs = RR_60_BPM.toDouble(),
            bpm = 60.0
        ))

        val instantBpm = bpmEstimator.getInstantBpm()

        assertNotNull(instantBpm)
        assertEquals(60.0, instantBpm!!, 0.1)
    }

    @Test
    fun `test beat count tracking`() {
        repeat(7) { i ->
            bpmEstimator.addBeat(createBeatEvent(
                timestampNs = i * 1_000_000_000L,
                rrMs = RR_60_BPM.toDouble()
            ))
        }

        assertEquals(7, bpmEstimator.getBeatCount())
    }

    @Test
    fun `test freeze preserves last values`() {
        // Agregar latidos regulares
        repeat(10) { i ->
            bpmEstimator.addBeat(createBeatEvent(
                timestampNs = i * 1_000_000_000L,
                rrMs = RR_60_BPM.toDouble()
            ))
        }

        val bpmBefore = bpmEstimator.getCurrentBpm()
        val confidenceBefore = bpmEstimator.getConfidence()

        // Congelar
        val frozen = bpmEstimator.freeze()

        // Verificar valores congelados
        assertEquals(bpmBefore, frozen.bpm, 0.1)
        assertEquals(confidenceBefore, frozen.confidence, 0.01)
        assertEquals(10, frozen.beatCount)
    }

    @Test
    fun `test reset clears all state`() {
        // Agregar latidos
        repeat(10) { i ->
            bpmEstimator.addBeat(createBeatEvent(
                timestampNs = i * 1_000_000_000L,
                rrMs = RR_60_BPM.toDouble()
            ))
        }

        // Resetear
        bpmEstimator.reset()

        // Todo debe volver a cero
        assertNull(bpmEstimator.getCurrentBpm())
        assertEquals(0.0, bpmEstimator.getConfidence(), 0.01)
        assertEquals(0, bpmEstimator.getBeatCount())
        assertFalse(bpmEstimator.hasEnoughData())
    }

    @Test
    fun `test RR statistics calculation`() {
        // Agregar latidos a 60 BPM
        repeat(10) { i ->
            bpmEstimator.addBeat(createBeatEvent(
                timestampNs = i * 1_000_000_000L,
                rrMs = RR_60_BPM.toDouble()
            ))
        }

        val stats = bpmEstimator.getRRStats()

        // Estadísticas deben ser razonables
        assertTrue(stats.meanRR in 950.0..1050.0) // ~1000ms
        assertTrue(stats.stdDev < 50.0) // Baja desviación
        assertTrue(stats.cv < 5.0) // Bajo coeficiente de variación
    }

    @Test
    fun `test reliable BPM tracking`() {
        // Agregar latidos con alta confianza
        repeat(12) { i ->
            bpmEstimator.addBeat(createBeatEvent(
                timestampNs = i * 1_000_000_000L,
                rrMs = RR_60_BPM.toDouble(),
                confidence = 0.9
            ))
        }

        val lastReliable = bpmEstimator.getLastReliableBpm()

        // Con buena confianza, debe haber BPM confiable
        assertNotNull(lastReliable)
        assertTrue(lastReliable!! in 58.0..62.0)
    }

    @Test
    fun `test no reliable BPM with low confidence`() {
        // Agregar latidos con baja confianza
        repeat(12) { i ->
            bpmEstimator.addBeat(createBeatEvent(
                timestampNs = i * 1_000_000_000L,
                rrMs = RR_60_BPM.toDouble(),
                confidence = 0.3
            ))
        }

        val lastReliable = bpmEstimator.getLastReliableBpm()

        // Con baja confianza, no debe haber BPM confiable
        assertNull(lastReliable)
    }

    @Test
    fun `test handles bradycardia correctly`() {
        // Latidos muy lentos (45 BPM = 1333ms)
        repeat(8) { i ->
            bpmEstimator.addBeat(createBeatEvent(
                timestampNs = i * 1_333_000_000L,
                rrMs = 1333.0
            ))
        }

        val bpm = bpmEstimator.getCurrentBpm()
        assertNotNull(bpm)
        assertTrue(
            "No detectó bradicardia correctamente: $bpm",
            bpm!! in 43.0..47.0
        )
    }

    @Test
    fun `test handles tachycardia correctly`() {
        // Latidos rápidos (150 BPM = 400ms)
        repeat(8) { i ->
            bpmEstimator.addBeat(createBeatEvent(
                timestampNs = i * 400_000_000L,
                rrMs = 400.0
            ))
        }

        val bpm = bpmEstimator.getCurrentBpm()
        assertNotNull(bpm)
        assertTrue(
            "No detectó taquicardia correctamente: $bpm",
            bpm!! in 148.0..152.0
        )
    }
}
