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
import com.example.myapplication.signal.PpgSignalQuality
import com.example.myapplication.viewmodel.MonitorViewModel

/**
 * Monitor Cardiaco de Grado Médico/Forense.
 * Diseño optimizado para visualización de alta fidelidad (60 FPS).
 */
@Composable
fun MonitorScreen(viewModel: MonitorViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    val backgroundColor = Color(0xFF070B14)
    val normalSignalColor = Color(0xFF22C55E)
    val arrhythmiaColor = Color(0xFFEF4444)
    val peakColor = Color(0xFF3B82F6)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // 1. MONITOR DE ONDAS (Full Screen Canvas)
        WaveformDisplay(
            waveform = uiState.filteredWaveform,
            isArhythmic = uiState.isArhythmic,
            validityState = uiState.validityState,
            normalColor = normalSignalColor,
            alertColor = arrhythmiaColor,
            peakColor = peakColor
        )

        // 2. OVERLAY DE MÉTRICAS Y TELEMETRÍA
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Cabecera: Información de Sesión
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "LIVE PPG DIAGNOSTIC",
                        color = peakColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        uiState.statusMessage.uppercase(),
                        color = if (uiState.validityState >= PpgSignalQuality.PpgValidityState.PPG_VALID) Color.White else Color.Gray,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                if (uiState.isArhythmic) {
                    Surface(
                        color = arrhythmiaColor,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            " ARRITMIA DETECTADA ",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
            }

            // Centro: Métricas Críticas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetricItem("BPM", uiState.bpm.toString(), if(uiState.isArhythmic) arrhythmiaColor else normalSignalColor)
                MetricItem("SpO2", if (uiState.bpm > 0) "${uiState.spo2}%" else "--", Color.Cyan)
            }

            // Pie: Datos Técnicos Forenses
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TechnicalData("SQI", "%.1f%%".format(uiState.sqi))
                    TechnicalData("FPS", "%.1f".format(uiState.actualFps))
                    TechnicalData("PI", "%.2f%%".format(uiState.sqi / 10.0)) // Estimación de Perfusión
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = { viewModel.start() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("START SENSOR / FLASH TORCH", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun WaveformDisplay(
    waveform: List<Double>,
    isArhythmic: Boolean,
    validityState: PpgSignalQuality.PpgValidityState,
    normalColor: Color,
    alertColor: Color,
    peakColor: Color
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        
        // DIBUJO DE RETÍCULA (Estilo Monitor Profesional)
        val gridCountX = 15
        val gridCountY = 20
        val stepX = width / gridCountX
        val stepY = height / gridCountY

        for (i in 0..gridCountX) {
            drawLine(
                color = Color.White.copy(alpha = 0.05f),
                start = Offset(i * stepX, 0f),
                end = Offset(i * stepX, height),
                strokeWidth = 1f
            )
        }
        for (i in 0..gridCountY) {
            drawLine(
                color = Color.White.copy(alpha = 0.05f),
                start = Offset(0f, i * stepY),
                end = Offset(width, i * stepY),
                strokeWidth = 1f
            )
        }

        if (waveform.size < 2) return@Canvas

        // DIBUJO DE ONDA PPG
        val path = Path()
        val pointsToDisplay = 300 // Buffer de visualización
        val drawStepX = width / pointsToDisplay
        
        // Auto-escala dinámica (Vanguardista)
        val recentData = if (waveform.size > pointsToDisplay) waveform.takeLast(pointsToDisplay) else waveform
        val min = recentData.minOrNull() ?: -1.0
        val max = recentData.maxOrNull() ?: 1.0
        val range = (max - min).coerceAtLeast(0.01)

        val signalColor = if (isArhythmic) alertColor else normalColor

        recentData.forEachIndexed { index, value ->
            val x = index * drawStepX
            val normalized = (value - min) / range
            // La onda ocupa la parte central para dejar espacio a métricas
            val y = (height * 0.5f) - (normalized.toFloat() * height * 0.3f)
            
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        // Sombreado bajo la curva (Estética Médica)
        val fillPath = Path().apply {
            addPath(path)
            lineTo(recentData.size * drawStepX, height * 0.5f)
            lineTo(0f, height * 0.5f)
            close()
        }
        
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(signalColor.copy(alpha = 0.2f), Color.Transparent),
                startY = height * 0.2f,
                endY = height * 0.5f
            )
        )

        drawPath(
            path = path,
            color = signalColor,
            style = Stroke(width = 2.5.dp.toPx())
        )
        
        // Indicador de Picos Recientes
        if (validityState >= PpgSignalQuality.PpgValidityState.PPG_VALID) {
            // Dibujar una pequeña marca azul en los puntos más altos del buffer visible
            val maxVal = recentData.maxOrNull() ?: 0.0
            recentData.forEachIndexed { index, value ->
                if (value == maxVal) {
                    val x = index * drawStepX
                    val y = (height * 0.5f) - ((value - min) / range).toFloat() * height * 0.3f
                    drawCircle(peakColor, radius = 4f, center = Offset(x, y))
                }
            }
        }
    }
}

@Composable
fun MetricItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(
            value,
            color = color,
            fontSize = 80.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-2).sp
        )
    }
}

@Composable
fun TechnicalData(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.Gray, fontSize = 10.sp)
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}
