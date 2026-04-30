package com.example.myapplication.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.signal.PpgSignalQuality
import com.example.myapplication.viewmodel.MonitorViewModel

@Composable
fun MonitorScreen(viewModel: MonitorViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. MONITOR DE ONDAS (Fondo completo)
        WaveformDisplay(
            waveform = uiState.filteredWaveform,
            isValid = uiState.isPhysiological
        )

        // 2. MÉTRICAS FLOTANTES (Overlay)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Cabecera: Título y Status
            Column {
                Text(
                    "PPG CONTINUOUS MONITOR",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    uiState.statusMessage,
                    color = if (uiState.isPhysiological) Color.Green else Color.Red,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Centro: Métricas principales
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetricDisplay("BPM", uiState.bpm.toString(), Color.Red)
                MetricDisplay("SpO2", if (uiState.spo2 > 0) "${uiState.spo2}%" else "--", Color.Cyan)
            }

            // Pie: Telemetría técnica
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(8.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SmallMetric("FPS", "%.1f".format(uiState.actualFps))
                    SmallMetric("SQI", "%.1f".format(uiState.sqi))
                    SmallMetric("RHYTHM", if (uiState.isArhythmic) "IRREGULAR" else "REGULAR")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // BOTÓN DE INICIO/PARADA
                Button(
                    onClick = { viewModel.start() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("INICIAR MEDICIÓN / ENCENDER FLASH", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun WaveformDisplay(waveform: List<Double>, isValid: Boolean) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Rejilla técnica
        val gridStep = size.height / 10
        for (i in 0..10) {
            drawLine(
                color = Color.DarkGray.copy(alpha = 0.3f),
                start = Offset(0f, i * gridStep),
                end = Offset(size.width, i * gridStep),
                strokeWidth = 1f
            )
        }

        if (waveform.size < 2) return@Canvas

        val path = Path()
        val stepX = size.width / 300f // Buffer de 300 puntos
        
        // Normalización dinámica para visualización
        val min = waveform.minOrNull() ?: 0.0
        val max = waveform.maxOrNull() ?: 1.0
        val range = if (max - min > 0) max - min else 1.0

        waveform.forEachIndexed { index, value ->
            val x = index * stepX
            val normalized = (value - min) / range
            val y = size.height * 0.7f - (normalized.toFloat() * size.height * 0.4f)
            
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = if (isValid) Color.Green else Color.Gray,
            style = Stroke(width = 3.dp.toPx())
        )
    }
}

@Composable
fun MetricDisplay(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp)
        Text(
            value,
            color = color,
            fontSize = 64.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
fun SmallMetric(label: String, value: String) {
    Row {
        Text("$label: ", color = Color.Gray, fontSize = 10.sp)
        Text(value, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
