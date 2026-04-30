package com.example.myapplication.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.signal.FingerState
import com.example.myapplication.signal.PressureState
import com.example.myapplication.signal.RhythmAnalyzer
import com.example.myapplication.viewmodel.MeasurementState
import com.example.myapplication.viewmodel.MonitorViewModel

@Composable
fun FullScreenPpgMonitor(viewModel: MonitorViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070B14))
    ) {
        // 1. Fondo: Onda PPG Principal
        PPGWaveformCanvas(
            waveform = uiState.ppgWaveform,
            rhythmStatus = mapRhythmToLegacy(uiState.rhythmState),
            modifier = Modifier.fillMaxSize()
        )

        // 2. Overlay de Telemetría
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Técnico
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("BPM", color = Color.White.copy(0.6f), fontSize = 12.sp)
                    Text(
                        text = if (uiState.currentBpm > 0) uiState.currentBpm.toString() else "--",
                        color = Color(0xFF22C55E),
                        fontSize = 80.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text("FPS: %.1f".format(uiState.diagnostics.actualFps), color = Color.White.copy(0.4f), fontSize = 10.sp)
                    Text("SQI: %.0f%%".format(uiState.sqi), color = Color.White.copy(0.6f), fontSize = 14.sp)
                    Text("PI: %.2f".format(uiState.perfusionIndex), color = Color(0xFF3B82F6), fontSize = 14.sp)
                }
            }

            // Centro: Estado del Dedo / Presión
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (uiState.measurementState is MeasurementState.WaitingForFinger || uiState.fingerState != FingerState.STABLE) {
                    Text(
                        text = uiState.statusText,
                        color = if (uiState.isMoving) Color.Red else Color.Yellow,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            // Footer: Análisis de Ritmo y Estabilidad
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text("RITMO", color = Color.White.copy(0.6f), fontSize = 12.sp)
                        Text(
                            text = uiState.rhythmState.name.replace("_", " "),
                            color = when (uiState.rhythmState) {
                                RhythmAnalyzer.RhythmState.REGULAR -> Color(0xFF22C55E)
                                RhythmAnalyzer.RhythmState.INSUFFICIENT_DATA -> Color.Gray
                                else -> Color.Red
                            },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Text("RMSSD", color = Color.White.copy(0.6f), fontSize = 12.sp)
                        Text("${uiState.rmssd.toInt()} ms", color = Color.White, fontSize = 18.sp)
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Barra de progreso de estabilidad
                if (uiState.measurementState is MeasurementState.Warmup || uiState.measurementState is MeasurementState.Stabilizing) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color.White.copy(0.1f))
                    ) {
                        // Implementación visual de la duración estable
                    }
                }
            }
        }
    }
}

// Mapper temporal para mantener compatibilidad con el Canvas anterior
private fun mapRhythmToLegacy(state: RhythmAnalyzer.RhythmState): com.example.myapplication.signal.RhythmAnalysisEngine.RhythmStatus {
    return when (state) {
        RhythmAnalyzer.RhythmState.REGULAR -> com.example.myapplication.signal.RhythmAnalysisEngine.RhythmStatus.NORMAL
        RhythmAnalyzer.RhythmState.IRREGULAR,
        RhythmAnalyzer.RhythmState.POSSIBLE_ECTOPIC_BEATS,
        RhythmAnalyzer.RhythmState.POSSIBLE_AF_PATTERN_EXPERIMENTAL -> com.example.myapplication.signal.RhythmAnalysisEngine.RhythmStatus.SUSPICIOUS_ARRHYTHMIA
        else -> com.example.myapplication.signal.RhythmAnalysisEngine.RhythmStatus.INSUFFICIENT_DATA
    }
}
