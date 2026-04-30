package com.example.myapplication.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.myapplication.domain.MeasurementState
import com.example.myapplication.ppg.BeatEvent

/**
 * Canvas profesional para visualización de onda PPG con marcadores de latidos.
 * Estilo osciloscopio forense.
 */
@Composable
fun PpgWaveformCanvas(
    waveform: List<Double>,
    state: MeasurementState,
    beatEvents: List<BeatEvent>,
    isIrregular: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Colores
        val gridColor = Color.White.copy(alpha = 0.05f)
        val normalColor = Color(0xFF22C55E)
        val alertColor = Color(0xFFEF4444)
        val warningColor = Color(0xFFF59E0B)
        val signalColor = if (isIrregular) alertColor else normalColor

        // 1. Retícula tipo osciloscopio
        for (i in 0..10) {
            drawLine(gridColor, Offset(i * width / 10, 0f), Offset(i * width / 10, height), 1f)
            drawLine(gridColor, Offset(0f, i * height / 10), Offset(width, i * height / 10), 1f)
        }

        // 2. Línea de base cuando no hay señal
        if (state != MeasurementState.MEASURING || waveform.isEmpty()) {
            drawLine(Color.Gray.copy(alpha = 0.3f), Offset(0f, height / 2), Offset(width, height / 2), 2f)
            return@Canvas
        }

        // 3. Dibujar onda PPG
        val points = if (waveform.size > 400) waveform.takeLast(400) else waveform
        val min = points.minOrNull() ?: -1.0
        val max = points.maxOrNull() ?: 1.0
        val range = (max - min).coerceAtLeast(0.0001)

        val path = Path()
        val stepX = width / 400

        points.forEachIndexed { index, value ->
            val x = index * stepX
            val norm = (value - min) / range
            val y = (height * 0.5f) - (norm.toFloat() * height * 0.35f)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(path, signalColor, style = Stroke(width = 3.dp.toPx()))

        // 4. Sombreado dinámico
        val fillPath = Path().apply {
            addPath(path)
            lineTo(points.size * stepX, height * 0.5f)
            lineTo(0f, height * 0.5f)
            close()
        }
        drawPath(fillPath, Brush.verticalGradient(listOf(signalColor.copy(alpha = 0.15f), Color.Transparent)))

        // 5. Marcadores de latidos
        if (beatEvents.isNotEmpty()) {
            val recentBeats = beatEvents.takeLast(20)
            val waveformTimestamps = generateTimestamps(waveform.size)
            
            recentBeats.forEach { beat ->
                // Encontrar posición aproximada en la onda
                val relativePosition = if (waveformTimestamps.isNotEmpty()) {
                    val timestamp = beat.timestampNs
                    val startIndex = waveformTimestamps.indexOfFirst { it >= timestamp - 5_000_000_000L } // 5 segundos de ventana
                    if (startIndex >= 0) startIndex else 0
                } else {
                    0
                }

                if (relativePosition < points.size) {
                    val x = relativePosition * stepX
                    val markerColor = when (beat.type) {
                        com.example.myapplication.ppg.BeatType.NORMAL -> normalColor
                        com.example.myapplication.ppg.BeatType.SUSPECT_PREMATURE -> warningColor
                        com.example.myapplication.ppg.BeatType.SUSPECT_PAUSE -> warningColor
                        com.example.myapplication.ppg.BeatType.SUSPECT_MISSED -> alertColor
                        com.example.myapplication.ppg.BeatType.IRREGULAR -> alertColor
                        com.example.myapplication.ppg.BeatType.INVALID_SIGNAL -> Color.Gray
                    }

                    // Línea vertical marcadora
                    drawLine(
                        markerColor,
                        Offset(x, height * 0.1f),
                        Offset(x, height * 0.9f),
                        Stroke(width = 2.dp.toPx())
                    )

                    // Círculo en el pico
                    val norm = (beat.amplitude - min) / range
                    val y = (height * 0.5f) - (norm.toFloat() * height * 0.35f)
                    drawCircle(
                        markerColor,
                        radius = 6.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }
        }
    }

    // Genera timestamps simulados para la onda (en producción usar timestamps reales)
    private fun generateTimestamps(size: Int): List<Long> {
        val now = System.nanoTime()
        return (0 until size).map { now - (size - it) * 33_333_333L } // ~30 FPS
    }
}
