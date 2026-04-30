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
import com.example.myapplication.domain.MeasurementState
import com.example.myapplication.viewmodel.MonitorViewModel

@Composable
fun MonitorScreen(viewModel: MonitorViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    val backgroundColor = Color(0xFF070B14)
    val normalSignalColor = Color(0xFF22C55E)
    val alertColor = Color(0xFFEF4444)
    val peakColor = Color(0xFF3B82F6)
    val warningColor = Color(0xFFF59E0B)

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        
        // 1. WAVEFORM DOMINANTE (Fondo)
        WaveformDisplay(
            waveform = uiState.waveform,
            state = uiState.state,
            isIrregular = uiState.isIrregular,
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
                    Text("MONITOR PPG FORENSE", color = peakColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(
                        uiState.statusMessage,
                        color = when (uiState.state) {
                            MeasurementState.MEASURING -> normalSignalColor
                            MeasurementState.DEGRADED, MeasurementState.INVALID -> alertColor
                            MeasurementState.WARMUP -> warningColor
                            else -> Color.White.copy(alpha = 0.6f)
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                
                if (uiState.isIrregular) {
                    Surface(color = alertColor, shape = MaterialTheme.shapes.small) {
                        Text(" IRREGULAR ", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(4.dp))
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
                    MetricDisplay("BPM", uiState.bpm?.let { "%.0f".format(it) } ?: "--", if (uiState.isIrregular) alertColor else normalSignalColor)
                    
                    val spo2Val = uiState.spo2?.let { "%.0f".format(it) } ?: "--"
                    val spo2Color = if (uiState.spo2 != null) Color.Cyan else Color.Gray
                    MetricDisplay("SpO₂", spo2Val, spo2Color)
                }
            }

            // Footer: Telemetría Forense
            Column(
                modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).padding(8.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    InfoItem("SQI", "%.2f".format(uiState.sqi))
                    InfoItem("PI", "%.2f".format(uiState.perfusionIndex))
                    InfoItem("MOT", "%.2f".format(uiState.motionScore))
                    InfoItem("FPS", "%.1f".format(uiState.actualFps))
                }
                
                if (uiState.spo2Message.isNotEmpty()) {
                    Text(uiState.spo2Message, color = Color.Gray, fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                }

                if (uiState.irregularityMessage.isNotEmpty()) {
                    Text(uiState.irregularityMessage, color = warningColor, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                }

                Spacer(modifier = Modifier.height(10.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.start() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                    ) {
                        Text("INICIAR", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { viewModel.stop() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                    ) {
                        Text("DETENER", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun WaveformDisplay(
    waveform: List<Double>,
    state: MeasurementState,
    isIrregular: Boolean,
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

        if (state != MeasurementState.MEASURING || waveform.isEmpty()) {
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
        val signalColor = if (isIrregular) alertColor else normalColor

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
