package com.example.myapplication.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.signal.MeasurementState
import com.example.myapplication.signal.Spo2Estimator
import com.example.myapplication.viewmodel.MonitorViewModel

@Composable
fun MonitorScreen(viewModel: MonitorViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    val backgroundColor = Color(0xFF070B14)
    val normalSignalColor = Color(0xFF22C55E)
    val alertColor = Color(0xFFEF4444)
    val peakColor = Color(0xFF3B82F6)

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        
        // 1. WAVEFORM DOMINANTE (Fondo)
        WaveformDisplay(
            waveform = uiState.waveform,
            state = uiState.state,
            isArhythmic = uiState.isArhythmic,
            normalColor = normalSignalColor,
            alertColor = alertColor
        )

        // 2. OVERLAY TÉCNICO
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Estado y Diagnóstico
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("BIOMEDICAL PPG MONITOR", color = peakColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(
                        uiState.statusMessage,
                        color = if (uiState.state == MeasurementState.MEASURING) normalSignalColor else Color.White.copy(alpha = 0.6f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                
                if (uiState.isArhythmic) {
                    Surface(color = alertColor, shape = MaterialTheme.shapes.small) {
                        Text(" PULSO IRREGULAR ", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(4.dp))
                    }
                }
            }

            // Centro: Métricas (Solo si hay señal)
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetricDisplay("BPM", uiState.bpm?.toString() ?: "--", if (uiState.isArhythmic) alertColor else normalSignalColor)
                    
                    val spo2Val = if (uiState.spo2Status == Spo2Estimator.Spo2Status.VALID) "${uiState.spo2}%" 
                                 else if (uiState.spo2Status == Spo2Estimator.Spo2Status.UNCALIBRATED) "${uiState.spo2}%*"
                                 else "--"
                    MetricDisplay("SpO2", spo2Val, Color.Cyan)
                }
            }

            // Footer: Telemetría Forense
            Column(
                modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).padding(8.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    InfoItem("SQI", "%.2f".format(uiState.sqi))
                    InfoItem("FPS", "%.1f".format(uiState.actualFps))
                    InfoItem("RR", if (uiState.rrIntervals.isNotEmpty()) "${uiState.rrIntervals.last()}ms" else "--")
                }
                
                if (uiState.spo2Status == Spo2Estimator.Spo2Status.UNCALIBRATED) {
                    Text("* CALIBRACIÓN NO VERIFICADA", color = Color.Gray, fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                }

                Spacer(modifier = Modifier.height(10.dp))
                
                Button(
                    onClick = { viewModel.start() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                ) {
                    Text("ACTIVAR SENSORES", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun WaveformDisplay(
    waveform: List<Double>,
    state: MeasurementState,
    isArhythmic: Boolean,
    normalColor: Color,
    alertColor: Color
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Retícula
        val gridAlpha = 0.05f
        for (i in 0..10) {
            drawLine(Color.White.copy(alpha = gridAlpha), Offset(i * width / 10, 0f), Offset(i * width / 10, height), 1f)
            drawLine(Color.White.copy(alpha = gridAlpha), Offset(0f, i * height / 10), Offset(width, i * height / 10), 1f)
        }

        if (state < MeasurementState.LOCKING_SIGNAL || waveform.isEmpty()) {
            // Línea plana técnica cuando no hay señal validada
            drawLine(Color.Gray.copy(alpha = 0.3f), Offset(0f, height / 2), Offset(width, height / 2), 2f)
            return@Canvas
        }

        val points = if (waveform.size > 400) waveform.takeLast(400) else waveform
        val min = points.minOrNull() ?: -1.0
        val max = points.maxOrNull() ?: 1.0
        val range = (max - min).coerceAtLeast(0.0001)

        val path = Path()
        val stepX = width / 400
        val signalColor = if (isArhythmic) alertColor else normalColor

        points.forEachIndexed { index, value ->
            val x = index * stepX
            val norm = (value - min) / range
            val y = (height * 0.5f) - (norm.toFloat() * height * 0.3f)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(path, signalColor, style = Stroke(width = 3.dp.toPx()))
        
        // Sombreado dinámico
        val fillPath = Path().apply {
            addPath(path)
            lineTo(points.size * stepX, height * 0.5f)
            lineTo(0f, height * 0.5f)
            close()
        }
        drawPath(fillPath, Brush.verticalGradient(listOf(signalColor.copy(alpha = 0.15f), Color.Transparent)))
    }
}

@Composable
fun MetricDisplay(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(value, color = color, fontSize = 90.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-4).sp)
    }
}

@Composable
fun InfoItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.Gray, fontSize = 10.sp)
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
    }
}
