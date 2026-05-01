package com.example.myapplication.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.signal.MeasurementState

@Composable
fun PpgWaveformCanvas(
    waveform: List<Double>,
    state: MeasurementState,
    beatEvents: List<Any>, // Simplificado por ahora
    isIrregular: Boolean
) {
    val signalColor = if (isIrregular) Color(0xFFEF4444) else Color(0xFF22C55E)
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (waveform.size < 2) return@Canvas
        
        val width = size.width
        val height = size.height
        val step = width / 300f // maxWaveformSize
        
        // Retícula técnica
        for (i in 0..10) {
            val x = (width / 10) * i
            drawLine(Color.White.copy(alpha = 0.05f), Offset(x, 0f), Offset(x, height), 1f)
            val y = (height / 10) * i
            drawLine(Color.White.copy(alpha = 0.05f), Offset(0f, y), Offset(width, y), 1f)
        }

        val path = Path()
        val min = waveform.minOrNull() ?: 0.0
        val max = waveform.maxOrNull() ?: 255.0
        val range = (max - min).coerceAtLeast(1.0)

        waveform.forEachIndexed { index, value ->
            val x = index * step
            val normalizedY = ((value - min) / range).toFloat()
            val y = height - (normalizedY * height * 0.6f) - (height * 0.2f)
            
            if (index == 0) path.moveTo(x, y)
            else path.lineTo(x, y)
        }

        drawPath(path, signalColor, style = Stroke(width = 3f))
    }
}

@Composable
fun SignalQualityPanel(
    sqi: Double,
    perfusionIndex: Double,
    motionScore: Double,
    state: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color(0xFF1E293B).copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.small
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("CALIDAD DE SEÑAL", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = sqi.toFloat(),
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = if (sqi > 0.7) Color.Green else if (sqi > 0.4) Color.Yellow else Color.Red,
                trackColor = Color.White.copy(alpha = 0.1f)
            )
            Text("PI: %.2f%%".format(perfusionIndex), color = Color.White, fontSize = 10.sp)
            Text("MOT: %.2f".format(motionScore), color = Color.White, fontSize = 10.sp)
        }
    }
}

@Composable
fun CameraDiagnosticsPanel(
    fps: Double,
    exposureTimeNs: Long?,
    iso: Int?,
    frameDurationNs: Long?,
    cameraId: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color(0xFF1E293B).copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.small
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("CÁMARA DIAG", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("FPS: %.1f".format(fps), color = Color.White, fontSize = 10.sp)
            Text("ISO: ${iso ?: "AUTO"}", color = Color.White, fontSize = 10.sp)
            val expMs = (exposureTimeNs ?: 0L) / 1_000_000.0
            Text("EXP: %.1f ms".format(expMs), color = Color.White, fontSize = 10.sp)
        }
    }
}

@Composable
fun CalibrationScreen(
    calibrationManager: Any,
    onCalibrationComplete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(Color.Black.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("MODO CALIBRACIÓN", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Esta función requiere equipo de referencia médico.", color = Color.Gray, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onCancel) {
                Text("CERRAR")
            }
        }
    }
}
