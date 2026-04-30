package com.example.myapplication.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.myapplication.signal.RhythmAnalysisEngine

@Composable
fun PPGWaveformCanvas(
    waveform: List<Float>,
    rhythmStatus: RhythmAnalysisEngine.RhythmStatus,
    modifier: Modifier = Modifier
) {
    val lineColor = when (rhythmStatus) {
        RhythmAnalysisEngine.RhythmStatus.NORMAL -> Color(0xFF22C55E) // Verde médico
        RhythmAnalysisEngine.RhythmStatus.IRREGULAR,
        RhythmAnalysisEngine.RhythmStatus.SUSPICIOUS_ARRHYTHMIA -> Color(0xFFEF4444) // Rojo alerta
        else -> Color(0xFF3B82F6) // Azul técnica
    }

    Canvas(modifier = modifier
        .fillMaxSize()
        .background(Color(0xFF070B14))
    ) {
        if (waveform.size < 2) return@Canvas

        val width = size.width
        val height = size.height
        val path = Path()
        
        // Normalización visual básica para el Canvas
        val maxVal = waveform.maxOrNull() ?: 1f
        val minVal = waveform.minOrNull() ?: 0f
        val range = (maxVal - minVal).coerceAtLeast(0.1f)

        val stepX = width / (waveform.size - 1)
        
        waveform.forEachIndexed { index, value ->
            val x = index * stepX
            // Invertir Y (Canvas 0 es arriba) y normalizar
            val y = height - ((value - minVal) / range * height * 0.8f + height * 0.1f).toFloat()
            
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx())
        )
        
        // Dibujar grid técnico sutil
        val gridColor = Color.White.copy(alpha = 0.05f)
        val gridStep = 50.dp.toPx()
        
        for (x in 0..(width / gridStep).toInt()) {
            drawLine(gridColor, Offset(x * gridStep, 0f), Offset(x * gridStep, height))
        }
        for (y in 0..(height / gridStep).toInt()) {
            drawLine(gridColor, Offset(0f, y * gridStep), Offset(width, y * gridStep))
        }
    }
}
