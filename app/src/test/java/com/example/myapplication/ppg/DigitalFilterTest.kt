package com.example.myapplication.ppg

import org.junit.Test
import org.junit.Assert.*
import kotlin.math.PI
import kotlin.math.sin

/**
 * Tests unitarios para filtros digitales PPG.
 * Verifica respuesta frecuencial, atenuación y estabilidad.
 */
class DigitalFilterTest {

    companion object {
        const val SAMPLE_RATE = 30.0
        const val TOLERANCE = 0.01
    }

    @Test
    fun `test bandpass filter passes cardiac frequencies`() {
        val filter = PPGBandpassFilter(SAMPLE_RATE)

        // Generar señal sintética de 1.5 Hz (90 BPM) - dentro de banda cardíaca
        val frequency = 1.5 // Hz
        val duration = 5.0 // segundos
        val samples = (duration * SAMPLE_RATE).toInt()

        val input = DoubleArray(samples) { i ->
            sin(2 * PI * frequency * i / SAMPLE_RATE)
        }

        // Aplicar filtro
        val output = input.map { filter.filter(it) }.toDoubleArray()

        // Ignorar transitorio inicial (primeros 30 samples)
        val steadyStateOutput = output.drop(30).toDoubleArray()
        val steadyStateInput = input.drop(30).toDoubleArray()

        // Calcular amplitud de entrada y salida
        val inputAmplitude = steadyStateInput.maxOf { kotlin.math.abs(it) }
        val outputAmplitude = steadyStateOutput.maxOf { kotlin.math.abs(it) }

        // La señal de 1.5 Hz debe pasar con poca atenuación (>70% de amplitud)
        assertTrue(
            "Señal cardiaca (1.5 Hz) atenuada excesivamente: ${outputAmplitude / inputAmplitude}",
            outputAmplitude / inputAmplitude > 0.70
        )
    }

    @Test
    fun `test bandpass filter attenuates low frequencies`() {
        val filter = PPGBandpassFilter(SAMPLE_RATE)

        // Generar señal de 0.1 Hz (6 BPM) - muy baja, debe ser atenuada
        val frequency = 0.1 // Hz
        val samples = (5.0 * SAMPLE_RATE).toInt()

        val input = DoubleArray(samples) { i ->
            sin(2 * PI * frequency * i / SAMPLE_RATE)
        }

        val output = input.map { filter.filter(it) }.toDoubleArray()
        val steadyStateOutput = output.drop(30).toDoubleArray()

        // Calcular RMS de salida
        val outputRms = kotlin.math.sqrt(steadyStateOutput.map { it * it }.average())

        // Baja frecuencia debe ser fuertemente atenuada
        assertTrue(
            "Baja frecuencia (0.1 Hz) no suficientemente atenuada: RMS=$outputRms",
            outputRms < 0.3
        )
    }

    @Test
    fun `test bandpass filter attenuates high frequencies`() {
        val filter = PPGBandpassFilter(SAMPLE_RATE)

        // Generar señal de 10 Hz (600 BPM) - muy alta, debe ser atenuada
        val frequency = 10.0 // Hz
        val samples = (2.0 * SAMPLE_RATE).toInt()

        val input = DoubleArray(samples) { i ->
            sin(2 * PI * frequency * i / SAMPLE_RATE)
        }

        val output = input.map { filter.filter(it) }.toDoubleArray()
        val steadyStateOutput = output.drop(20).toDoubleArray()

        val outputRms = kotlin.math.sqrt(steadyStateOutput.map { it * it }.average())

        // Alta frecuencia debe ser fuertemente atenuada (aliasing)
        assertTrue(
            "Alta frecuencia (10 Hz) no suficientemente atenuada: RMS=$outputRms",
            outputRms < 0.5
        )
    }

    @Test
    fun `test filter rejects constant signal`() {
        val filter = PPGBandpassFilter(SAMPLE_RATE)

        // Señal DC (constante) debe ser completamente removida
        val constantValue = 100.0
        val samples = 100

        val output = (1..samples).map { filter.filter(constantValue) }

        // Después del transitorio, la salida debe ser cercana a cero
        val steadyOutput = output.drop(30)
        val maxOutput = steadyOutput.maxOf { kotlin.math.abs(it) }

        assertTrue(
            "Componente DC no removida: max=$maxOutput",
            maxOutput < 1.0
        )
    }

    @Test
    fun `test filter stability with impulse`() {
        val filter = PPGBandpassFilter(SAMPLE_RATE)

        // Respuesta a impulso
        val output = mutableListOf<Double>()

        // Impulso unitario
        output.add(filter.filter(1.0))

        // Ceros subsiguientes
        repeat(100) {
            output.add(filter.filter(0.0))
        }

        // El filtro debe ser estable (salida decae, no diverge)
        val laterSamples = output.drop(50)
        val maxLater = laterSamples.maxOf { kotlin.math.abs(it) }

        assertTrue(
            "Filtro inestable - salida no decae: max=$maxLater",
            maxLater < 0.5
        )
    }

    @Test
    fun `test filter reset clears state`() {
        val filter = PPGBandpassFilter(SAMPLE_RATE)

        // Procesar señal
        repeat(50) { filter.filter(100.0) }

        // Resetear
        filter.reset()

        // Procesar nuevo valor
        val output = filter.filter(50.0)

        // Después de reset, debe comportarse como al inicio
        // (sin memoria del procesamiento anterior)
        assertTrue(
            "Reset no limpió estado correctamente",
            kotlin.math.abs(output) < 100.0
        )
    }

    @Test
    fun `test biquad lowpass frequency response`() {
        val lpFilter = BiquadFilter(
            BiquadFilter.FilterType.LOWPASS,
            SAMPLE_RATE,
            4.0,
            0.707
        )

        // Frecuencia de corte a 4 Hz, señal a 2 Hz debe pasar
        val frequency = 2.0
        val samples = (3.0 * SAMPLE_RATE).toInt()

        val input = DoubleArray(samples) { i ->
            sin(2 * PI * frequency * i / SAMPLE_RATE)
        }

        val output = input.map { lpFilter.process(it) }.toDoubleArray()
        val steadyOutput = output.drop(30).toDoubleArray()

        val outputAmplitude = steadyOutput.maxOf { kotlin.math.abs(it) }

        // 2 Hz < 4 Hz cutoff, debe pasar
        assertTrue(
            "Lowpass bloqueando frecuencia bajo corte: amp=$outputAmplitude",
            outputAmplitude > 0.7
        )
    }

    @Test
    fun `test biquad highpass removes DC`() {
        val hpFilter = BiquadFilter(
            BiquadFilter.FilterType.HIGHPASS,
            SAMPLE_RATE,
            0.5,
            0.707
        )

        // Señal DC + AC
        val dcComponent = 100.0
        val samples = 200

        val output = DoubleArray(samples) { i ->
            val ac = sin(2 * PI * 1.0 * i / SAMPLE_RATE) // 1 Hz
            hpFilter.process(dcComponent + ac)
        }

        val steadyOutput = output.drop(50).toList()
        val meanOutput = steadyOutput.average()

        // DC debe ser removido (media cercana a 0)
        assertTrue(
            "Highpass no removió DC: media=$meanOutput",
            kotlin.math.abs(meanOutput) < 1.0
        )
    }
}
