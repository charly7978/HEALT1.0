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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.signal.PpgPhysiologyClassifier
import com.example.myapplication.signal.RhythmAnalyzer
import com.example.myapplication.viewmodel.MonitorViewModel

/**
 * Monitor PPG full-screen estilo osciloscopio biomédico profesional.
 * Ocupa el 100% de la pantalla con métricas flotantes translúcidas.
 */
@Composable
fun MonitorScreen(viewModel: MonitorViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    val backgroundColor = Color(0xFF070B14)
    val normalSignalColor = Color(0xFF22C55E)
    val alertColor = Color(0xFFEF4444)
    val warningColor = Color(0xFFF59E0B)
    val infoColor = Color(0xFF3B82F6)

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        
        // 1. WAVEFORM DOMINANTE (Fondo - 100% de pantalla)
        PpgWaveformCanvas(
            waveform = uiState.waveform,
            rawWaveform = uiState.rawWaveform,
            physiologyState = uiState.physiologyState,
            confirmedBeats = uiState.confirmedBeats,
            isIrregular = uiState.isIrregular,
            showRaw = false
        )

        // 2. OVERLAY TRANSLÚCIDO DE MÉTRICAS
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Estado y Diagnóstico (translúcido)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "ESTADO: ${uiState.physiologyState.name}",
                        color = when (uiState.physiologyState) {
                            PpgPhysiologyClassifier.PpgValidityState.BIOMETRIC_VALID -> normalSignalColor
                            PpgPhysiologyClassifier.PpgValidityState.PPG_VALID -> infoColor
                            PpgPhysiologyClassifier.PpgValidityState.PPG_CANDIDATE -> warningColor
                            PpgPhysiologyClassifier.PpgValidityState.NO_PHYSIOLOGICAL_SIGNAL -> alertColor
                            PpgPhysiologyClassifier.PpgValidityState.RAW_OPTICAL_ONLY -> Color.Gray
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        uiState.classificationReason,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 9.sp
                    )
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "FPS: ${"%.1f".format(uiState.actualFps)}",
                        color = if (uiState.actualFps >= 25.0) normalSignalColor else alertColor,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "SQI: ${"%.2f".format(uiState.sqi)}",
                        color = if (uiState.sqi >= 0.7) normalSignalColor else warningColor,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "SNR: ${"%.1f".format(uiState.snr)}",
                        color = if (uiState.snr >= 3.0) normalSignalColor else warningColor,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Espacio para la onda (no bloquear vista)
            Spacer(modifier = Modifier.weight(1f))

            // Métricas Centrales (translúcidas)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetricDisplay("BPM", uiState.bpm?.let { "%.0f".format(it) } ?: "--", if (uiState.isIrregular) alertColor else normalSignalColor)
                
                val spo2Val = uiState.spo2?.let { "%.0f".format(it) } ?: "--"
                val spo2Color = when (uiState.spo2Status) {
                    "VALID" -> Color.Cyan
                    "NO_CALIBRATION" -> warningColor
                    else -> Color.Gray
                }
                MetricDisplay("SpO₂", spo2Val, spo2Color)
            }

            // Footer: Diagnósticos y controles (translúcido)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Línea de diagnóstico
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "PI: ${"%.2f".format(uiState.perfusionIndex)}",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "MOT: ${"%.2f".format(uiState.motionScore)}",
                        color = if (uiState.motionScore < 0.3) normalSignalColor else alertColor,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "R: ${"%.3f".format(uiState.spo2RatioR)}",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "BEATS: ${uiState.confirmedBeats.size}",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Línea de ritmo
                if (uiState.rhythmState != RhythmAnalyzer.RhythmState.INSUFFICIENT_DATA) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "RITMO: ${uiState.rhythmState.name}",
                            color = if (uiState.isIrregular) alertColor else normalSignalColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "RMSSD: ${"%.1f".format(uiState.rmssd)}",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            "CV: ${"%.1f".format(uiState.cv)}%",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Botones de control
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button(
                        onClick = { viewModel.start() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Text("INICIAR", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { viewModel.stop() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Text("DETENER", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { viewModel.setSpO2Calibration(110.0, -20.0) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Text("CALIBRAR", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun PpgWaveformCanvas(
    waveform: List<Double>,
    rawWaveform: List<Double>,
    physiologyState: PpgPhysiologyClassifier.PpgValidityState,
    confirmedBeats: List<RhythmAnalyzer.ConfirmedBeat>,
    isIrregular: Boolean,
    showRaw: Boolean
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Colores según estado
        val signalColor = when (physiologyState) {
            PpgPhysiologyClassifier.PpgValidityState.BIOMETRIC_VALID -> Color(0xFF22C55E)
            PpgPhysiologyClassifier.PpgValidityState.PPG_VALID -> Color(0xFF3B82F6)
            PpgPhysiologyClassifier.PpgValidityState.PPG_CANDIDATE -> Color(0xFFF59E0B)
            PpgPhysiologyClassifier.PpgValidityState.NO_PHYSIOLOGICAL_SIGNAL -> Color(0xFFEF4444)
            PpgPhysiologyClassifier.PpgValidityState.RAW_OPTICAL_ONLY -> Color.Gray
        }

        // 1. Retícula tipo osciloscopio
        val gridColor = Color.White.copy(alpha = 0.03f)
        for (i in 0..20) {
            drawLine(gridColor, Offset(i * width / 20, 0f), Offset(i * width / 20, height), 1f)
            drawLine(gridColor, Offset(0f, i * height / 20), Offset(width, i * height / 20), 1f)
        }

        // 2. Dibujar onda
        val points = if (waveform.size > 400) waveform.takeLast(400) else waveform
        if (points.isNotEmpty()) {
            val min = points.minOrNull() ?: -1.0
            val max = points.maxOrNull() ?: 1.0
            val range = (max - min).coerceAtLeast(0.0001)

            val path = Path()
            val stepX = width / 400

            points.forEachIndexed { index, value ->
                val x = index * stepX
                val norm = (value - min) / range
                val y = (height * 0.5f) - (norm.toFloat() * height * 0.4f)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            drawPath(path, signalColor, style = Stroke(width = 2.dp.toPx()))

            // Sombreado
            val fillPath = Path().apply {
                addPath(path)
                lineTo(points.size * stepX, height * 0.5f)
                lineTo(0f, height * 0.5f)
                close()
            }
            drawPath(fillPath, Brush.verticalGradient(listOf(signalColor.copy(alpha = 0.1f), Color.Transparent)))
        }

        // 3. Marcadores de latidos confirmados
        if (confirmedBeats.isNotEmpty()) {
            val recentBeats = confirmedBeats.takeLast(20)
            
            recentBeats.forEach { beat ->
                val markerColor = if (isIrregular) Color(0xFFEF4444) else Color(0xFF22C55E)
                
                // Línea vertical
                val x = (beat.timestampNs % 13_333_333_333L).toFloat() / 13_333_333_333f * width // Posición aproximada
                drawLine(
                    color = markerColor,
                    start = Offset(x, height * 0.1f),
                    end = Offset(x, height * 0.9f),
                    strokeWidth = 1.dp.toPx()
                )
                
                // Círculo en el pico
                drawCircle(
                    color = markerColor,
                    radius = 4.dp.toPx(),
                    center = Offset(x, height * 0.5f)
                )
            }
        }
    }
}

@Composable
fun MetricDisplay(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(value, color = color, fontSize = 70.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-2).sp)
    }
}
