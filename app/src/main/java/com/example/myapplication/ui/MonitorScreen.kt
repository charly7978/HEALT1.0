package com.example.myapplication.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.example.myapplication.signal.PpgValidityState
import com.example.myapplication.signal.RhythmAnalyzer
import com.example.myapplication.viewmodel.MonitorViewModel

@Composable
fun MonitorScreen(viewModel: MonitorViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "CARDIO MONITOR PRO",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Estado de Validez
        StatusIndicator(uiState.validityState, uiState.statusMessage)

        Spacer(modifier = Modifier.height(16.dp))

        // Pantalla de Ondas
        WaveformPanel(
            ppgWaveform = uiState.ppgWaveform,
            rawWaveform = uiState.rawWaveform,
            isValid = uiState.validityState == PpgValidityState.PPG_VALID || uiState.validityState == PpgValidityState.PPG_CANDIDATE
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Métricas Principales
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MetricCard("BPM", uiState.currentBpm.takeIf { it > 0 }?.toString() ?: "--", Color.Red)
            MetricCard("SpO2", uiState.currentSpo2?.let { "%.1f%%".format(it) } ?: "--", Color.Cyan)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Análisis de Ritmo
        RhythmCard(uiState.rhythmState)

        Spacer(modifier = Modifier.height(24.dp))

        // Telemetría Técnica (Debug)
        TelemetryPanel(uiState)

        Spacer(modifier = Modifier.height(24.dp))

        // Controles
        ControlPanel(
            isRunning = uiState.isRunning,
            beepEnabled = uiState.beepEnabled,
            vibrationEnabled = uiState.vibrationEnabled,
            onToggleStart = { if (uiState.isRunning) viewModel.stop() else viewModel.start() },
            onToggleBeep = viewModel::toggleBeep,
            onToggleVibration = viewModel::toggleVibration
        )
    }
}

@Composable
fun StatusIndicator(state: PpgValidityState, message: String) {
    val color = when(state) {
        PpgValidityState.PPG_VALID -> Color.Green
        PpgValidityState.PPG_CANDIDATE -> Color.Yellow
        PpgValidityState.NO_PPG_PHYSIOLOGICAL_SIGNAL -> Color.Red
        PpgValidityState.SATURATED -> Color.Magenta
        else -> Color.Gray
    }
    
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            color = color,
            modifier = Modifier.padding(12.dp),
            fontWeight = FontWeight.Medium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun WaveformPanel(ppgWaveform: List<Float>, rawWaveform: List<Float>, isValid: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Rejilla de fondo
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = size.width / 10
                for (i in 0..10) {
                    drawLine(Color.DarkGray, Offset(i * step, 0f), Offset(i * step, size.height), 0.5f)
                }
            }

            // Onda Cruda (Siempre visible, tenue)
            PpgWaveformCanvas(rawWaveform, Color.Gray.copy(alpha = 0.4f), label = "RAW OPTICAL")

            // Onda PPG Filtrada (Solo si hay señal candidato/válida)
            if (isValid) {
                PpgWaveformCanvas(ppgWaveform, Color.Green, label = "PPG FILTERED")
            }
        }
    }
}

@Composable
fun PpgWaveformCanvas(points: List<Float>, color: Color, label: String) {
    Box(modifier = Modifier.fillMaxSize()) {
        Text(label, color = color, fontSize = 10.sp, modifier = Modifier.padding(4.dp))
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (points.size < 2) return@Canvas
            
            val path = Path()
            val stepX = size.width / 200f
            
            points.forEachIndexed { index, value ->
                val x = index * stepX
                val y = size.height - (value * size.height)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            
            drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

@Composable
fun MetricCard(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.LightGray, fontSize = 12.sp)
        Text(value, color = color, fontSize = 32.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun RhythmCard(state: RhythmAnalyzer.RhythmState) {
    val text = when(state) {
        RhythmAnalyzer.RhythmState.REGULAR -> "RITMO REGULAR"
        RhythmAnalyzer.RhythmState.IRREGULAR -> "RITMO IRREGULAR"
        RhythmAnalyzer.RhythmState.POSSIBLE_ECTOPIC_BEATS -> "LATIDOS ECTÓPICOS DETECTADOS"
        RhythmAnalyzer.RhythmState.POSSIBLE_AF_PATTERN_EXPERIMENTAL -> "SOSPECHA DE ARRITMIA (ANÁLISIS REQUERIDO)"
        RhythmAnalyzer.RhythmState.INSUFFICIENT_DATA -> "ANALIZANDO RITMO..."
    }
    Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun TelemetryPanel(uiState: com.example.myapplication.viewmodel.MonitorUiState) {
    Column(modifier = Modifier.fillMaxWidth().background(Color.DarkGray.copy(alpha = 0.3f)).padding(8.dp)) {
        Text("TELEMETRÍA:", color = Color.Yellow, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("FPS: %.1f".format(uiState.fps), color = Color.White, fontSize = 10.sp)
            Text("SQI: %.1f%%".format(uiState.sqi), color = Color.White, fontSize = 10.sp)
            Text("PI: %.3f".format(uiState.perfusionIndex), color = Color.White, fontSize = 10.sp)
        }
    }
}

@Composable
fun ControlPanel(
    isRunning: Boolean,
    beepEnabled: Boolean,
    vibrationEnabled: Boolean,
    onToggleStart: () -> Unit,
    onToggleBeep: (Boolean) -> Unit,
    onToggleVibration: (Boolean) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = onToggleStart,
            colors = ButtonDefaults.buttonColors(containerColor = if (isRunning) Color.Red else Color(0xFF2E7D32)),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(if (isRunning) "DETENER" else "INICIAR MONITOR", fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = beepEnabled, onCheckedChange = onToggleBeep, colors = CheckboxDefaults.colors(checkmarkColor = Color.Black, uncheckedColor = Color.Gray))
            Text("Beep", color = Color.White)
            Spacer(modifier = Modifier.width(16.dp))
            Checkbox(checked = vibrationEnabled, onCheckedChange = onToggleVibration, colors = CheckboxDefaults.colors(checkmarkColor = Color.Black, uncheckedColor = Color.Gray))
            Text("Vibración", color = Color.White)
        }
    }
}
