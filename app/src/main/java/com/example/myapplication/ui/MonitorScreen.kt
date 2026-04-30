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
import com.example.myapplication.ppg.DeviceCalibrationManager
import com.example.myapplication.viewmodel.MonitorViewModel

@Composable
fun MonitorScreen(viewModel: MonitorViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val calibrationManager = remember { DeviceCalibrationManager(context) }

    val backgroundColor = Color(0xFF070B14)
    val normalSignalColor = Color(0xFF22C55E)
    val alertColor = Color(0xFFEF4444)
    val peakColor = Color(0xFF3B82F6)
    val warningColor = Color(0xFFF59E0B)

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        
        // 1. WAVEFORM DOMINANTE (Fondo)
        PpgWaveformCanvas(
            waveform = uiState.waveform,
            state = uiState.state,
            beatEvents = uiState.beatEvents,
            isIrregular = uiState.isIrregular
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

            // Footer: Paneles técnicos
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SignalQualityPanel(
                    sqi = uiState.sqi,
                    perfusionIndex = uiState.perfusionIndex,
                    motionScore = uiState.motionScore,
                    state = uiState.statusMessage,
                    modifier = Modifier.weight(1f)
                )
                
                CameraDiagnosticsPanel(
                    fps = uiState.actualFps,
                    exposureTimeNs = uiState.exposureTimeNs,
                    iso = uiState.iso,
                    frameDurationNs = uiState.frameDurationNs,
                    cameraId = "back",
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Mensajes de estado
            if (uiState.spo2Message.isNotEmpty()) {
                Text(uiState.spo2Message, color = Color.Gray, fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            }

            if (uiState.irregularityMessage.isNotEmpty()) {
                Text(uiState.irregularityMessage, color = warningColor, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            }

            Spacer(modifier = Modifier.height(8.dp))
            
            // Botones de control
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
                Button(
                    onClick = { viewModel.showCalibrationScreen() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                ) {
                    Text("CALIBRAR", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Pantalla de calibración (overlay)
    if (uiState.showCalibrationScreen) {
        CalibrationScreen(
            calibrationManager = calibrationManager,
            onCalibrationComplete = {
                viewModel.hideCalibrationScreen()
            },
            onCancel = {
                viewModel.hideCalibrationScreen()
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun MetricDisplay(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(value, color = color, fontSize = 90.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-4).sp)
    }
}
