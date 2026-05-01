package com.example.myapplication.ui

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Vibration
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
import com.example.myapplication.ppg.PpgPhysiologyClassifier
import com.example.myapplication.ppg.RhythmAnalyzer
import com.example.myapplication.ppg.Spo2Estimator
import com.example.myapplication.viewmodel.MonitorViewModel

/**
 * Monitor PPG profesional con separación clara entre datos ópticos crudos y señal fisiológica.
 * REGLA CRÍTICA: La UI nunca dibuja ondas "cardíacas" sin PPG_VALID.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorScreen(viewModel: MonitorViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var showDebugPanel by remember { mutableStateOf(false) }

    val backgroundColor = Color(0xFF070B14)
    val normalSignalColor = Color(0xFF22C55E)
    val alertColor = Color(0xFFEF4444)
    val warningColor = Color(0xFFF59E0B)
    val infoColor = Color(0xFF3B82F6)
    val rawOpticalColor = Color(0xFF6B7280) // Gris para datos ópticos crudos

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        
        // ========== CANVAS DE ONDAS ==========
        // Dibujar onda RAW siempre (datos ópticos crudos)
        // Dibujar onda PPG filtrada SOLO si hay señal fisiológica
        PpgWaveformCanvas(
            rawWaveform = uiState.rawWaveform,
            filteredWaveform = uiState.filteredWaveform,
            showFilteredWaveform = uiState.showFilteredWaveform,
            validityState = uiState.validityState,
            isPhysiologicalSignal = uiState.isPhysiologicalSignal
        )

        // ========== OVERLAY DE MÉTRICAS ==========
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            
            // ---- HEADER: Estado y Telemetría ----
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.6f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Estado principal
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val stateColor = when (uiState.validityState) {
                                PpgPhysiologyClassifier.PpgValidityState.PPG_VALID -> normalSignalColor
                                PpgPhysiologyClassifier.PpgValidityState.PPG_DEGRADED -> warningColor
                                PpgPhysiologyClassifier.PpgValidityState.PPG_CANDIDATE -> infoColor
                                PpgPhysiologyClassifier.PpgValidityState.SEARCHING_PPG -> infoColor
                                PpgPhysiologyClassifier.PpgValidityState.NO_PPG_PHYSIOLOGICAL_SIGNAL -> alertColor
                                PpgPhysiologyClassifier.PpgValidityState.SATURATED -> alertColor
                                PpgPhysiologyClassifier.PpgValidityState.MOTION_ARTIFACT -> warningColor
                                PpgPhysiologyClassifier.PpgValidityState.LOW_PERFUSION -> warningColor
                                else -> rawOpticalColor
                            }
                            
                            Text(
                                uiState.statusMessage,
                                color = stateColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                uiState.classificationReason,
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 10.sp,
                                lineHeight = 12.sp
                            )
                        }
                        
                        // FPS y calidad
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "${"%.1f".format(uiState.actualFps)} FPS",
                                color = if (uiState.actualFps >= 25) normalSignalColor else alertColor,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                "SQI: ${"%.2f".format(uiState.sqi)}",
                                color = when {
                                    uiState.sqi >= 0.7 -> normalSignalColor
                                    uiState.sqi >= 0.4 -> warningColor
                                    else -> alertColor
                                },
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    
                    // Métricas secundarias
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TelemetryItem("PI", "${"%.2f".format(uiState.perfusionIndex)}", uiState.perfusionIndex >= 0.5)
                        TelemetryItem("SNR", "${"%.1f".format(uiState.snr)}", uiState.snr >= 3.0)
                        TelemetryItem("MOT", "${"%.2f".format(uiState.motionScore)}", uiState.motionScore < 0.3)
                        TelemetryItem("LAT", "${"%.1f".format(uiState.latencyMs)}ms", uiState.latencyMs < 50)
                    }
                }
            }

            // ---- ESPACIO PARA ONDA ----
            Spacer(modifier = Modifier.weight(1f))

            // ---- MÉTRICAS PRINCIPALES ----
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // BPM
                    val bpmColor = when {
                        !uiState.isPhysiologicalSignal -> Color.Gray
                        uiState.bpm == null -> Color.Gray
                        uiState.isIrregular -> alertColor
                        uiState.bpmConfidence < 0.5 -> warningColor
                        else -> normalSignalColor
                    }
                    MetricDisplay(
                        label = "BPM",
                        value = uiState.bpm?.let { "%.0f".format(it) } ?: "--",
                        confidence = if (uiState.isPhysiologicalSignal) uiState.bpmConfidence else 0.0,
                        color = bpmColor
                    )
                    
                    // SpO2
                    val spo2Color = when (uiState.spo2Status) {
                        Spo2Estimator.Spo2Status.VALID_ESTIMATE -> Color.Cyan
                        Spo2Estimator.Spo2Status.CALIBRATING -> warningColor
                        Spo2Estimator.Spo2Status.NO_CALIBRATION -> infoColor
                        else -> Color.Gray
                    }
                    MetricDisplay(
                        label = "SpO₂",
                        value = uiState.spo2?.let { "%.0f".format(it) } ?: 
                               if (uiState.spo2Status == Spo2Estimator.Spo2Status.CALIBRATING) "..." else "--",
                        confidence = uiState.spo2Confidence,
                        color = spo2Color,
                        warning = uiState.spo2Warning
                    )
                    
                    // Ritmo
                    if (uiState.rhythmState != RhythmAnalyzer.RhythmState.INSUFFICIENT_DATA) {
                        MetricDisplay(
                            label = "RITMO",
                            value = when (uiState.rhythmState) {
                                RhythmAnalyzer.RhythmState.REGULAR -> "OK"
                                RhythmAnalyzer.RhythmState.IRREGULAR -> "IRR"
                                RhythmAnalyzer.RhythmState.POSSIBLE_AF_PATTERN_EXPERIMENTAL -> "AF?"
                                RhythmAnalyzer.RhythmState.POSSIBLE_ECTOPIC_BEATS -> "ECT"
                                else -> "--"
                            },
                            confidence = 0.0,
                            color = if (uiState.isIrregular) alertColor else normalSignalColor
                        )
                    }
                }
            }

            // ---- RITMO DETALLADO (si hay datos) ----
            AnimatedVisibility(
                visible = uiState.beatCount >= 10,
                enter = fadeIn() + expandVertically()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Black.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        HrvMetric("RMSSD", "${"%.0f".format(uiState.rmssd)}ms", uiState.rmssd in 20.0..100.0)
                        HrvMetric("SDNN", "${"%.0f".format(uiState.sdnn)}ms", uiState.sdnn in 30.0..150.0)
                        HrvMetric("CV", "${"%.1f".format(uiState.cv)}%", uiState.cv in 3.0..15.0)
                        HrvMetric("LATIDOS", "${uiState.beatCount}", true)
                    }
                }
            }

            // ---- CONTROLES ----
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { viewModel.setBeepEnabled(!uiState.beepEnabled) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Beep",
                                tint = if (uiState.beepEnabled) normalSignalColor else Color.Gray
                            )
                        }
                        
                        IconButton(
                            onClick = { viewModel.setVibrationEnabled(!uiState.vibrationEnabled) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Vibration,
                                contentDescription = "Vibration",
                                tint = if (uiState.vibrationEnabled) normalSignalColor else Color.Gray
                            )
                        }
                        
                        IconButton(
                            onClick = { showDebugPanel = !showDebugPanel },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Debug",
                                tint = if (showDebugPanel) infoColor else Color.Gray
                            )
                        }
                        
                        Button(
                            onClick = { if (uiState.cameraRunning) viewModel.stop() else viewModel.start() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.cameraRunning) alertColor else normalSignalColor
                            ),
                            modifier = Modifier.weight(2f)
                        ) {
                            Text(
                                if (uiState.cameraRunning) "DETENER" else "INICIAR",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    // Botón de calibración (solo visible con PPG válido)
                    AnimatedVisibility(
                        visible = uiState.isPhysiologicalSignal,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.setSpO2Calibration(110.0, -20.0, 0.0) },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = infoColor
                            )
                        ) {
                            Text("CALIBRAR SpO2 (Genérica)", fontSize = 11.sp)
                        }
                    }
                }
            }

            // ---- PANEL DEBUG ----
            AnimatedVisibility(
                visible = showDebugPanel,
                enter = slideInVertically { it } + fadeIn()
            ) {
                DebugTelemetryPanel(debugText = uiState.debugText)
            }
        }
    }
}

@Composable
private fun TelemetryItem(label: String, value: String, isGood: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$label: ",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            value,
            color = if (isGood) Color(0xFF22C55E) else Color(0xFFEF4444),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun HrvMetric(label: String, value: String, isNormal: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
        Text(
            value,
            color = if (isNormal) Color(0xFF22C55E) else Color(0xFFF59E0B),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun MetricDisplay(
    label: String,
    value: String,
    confidence: Double,
    color: Color,
    warning: String? = null
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            value,
            color = color,
            fontSize = if (label == "RITMO") 40.sp else 60.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-2).sp
        )
        if (confidence > 0) {
            LinearProgressIndicator(
                progress = { confidence.toFloat() },
                modifier = Modifier.width(60.dp).padding(top = 4.dp),
                color = color,
                trackColor = Color.White.copy(alpha = 0.1f)
            )
        }
        if (warning != null) {
            Text(
                warning,
                color = Color(0xFFF59E0B),
                fontSize = 8.sp,
                modifier = Modifier.width(100.dp),
                lineHeight = 10.sp
            )
        }
    }
}

@Composable
private fun DebugTelemetryPanel(debugText: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .padding(top = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.8f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(8.dp)
        ) {
            Text(
                "DEBUG TELEMETRY",
                color = Color(0xFF3B82F6),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                debugText,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 12.sp
            )
        }
    }
}

/**
 * Canvas para dibujar ondas PPG.
 * REGLA: Dibuja onda RAW siempre, onda PPG filtrada SOLO con PPG_VALID.
 */
@Composable
fun PpgWaveformCanvas(
    rawWaveform: List<Double>,
    filteredWaveform: List<Double>,
    showFilteredWaveform: Boolean,
    validityState: PpgPhysiologyClassifier.PpgValidityState,
    isPhysiologicalSignal: Boolean
) {
    val rawOpticalColor = Color.Gray.copy(alpha = 0.4f)
    val ppgValidColor = Color(0xFF22C55E)
    val ppgCandidateColor = Color(0xFFF59E0B)
    val noSignalColor = Color(0xFFEF4444).copy(alpha = 0.3f)
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // 1. RETÍCULA TIPO OSCILOSCOPIO
        val gridColor = Color.White.copy(alpha = 0.03f)
        val gridSpacing = width / 20
        for (i in 0..20) {
            drawLine(gridColor, Offset(i * gridSpacing, 0f), Offset(i * gridSpacing, height), 1f)
            drawLine(gridColor, Offset(0f, i * height / 20), Offset(width, i * height / 20), 1f)
        }

        // 2. DIBUJAR ONDA RAW (datos ópticos crudos) - SIEMPRE
        if (rawWaveform.size >= 2) {
            val points = rawWaveform.takeLast(400)
            val min = points.minOrNull() ?: 0.0
            val max = points.maxOrNull() ?: 255.0
            val range = (max - min).coerceAtLeast(1.0)

            val path = Path()
            val stepX = width / points.size.coerceAtLeast(2)

            points.forEachIndexed { index, value ->
                val x = index * stepX
                val norm = (value - min) / range
                val y = (height * 0.3f) - (norm.toFloat() * height * 0.2f) // Parte superior
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            drawPath(path, rawOpticalColor, style = Stroke(width = 1.5.dp.toPx()))
            
            // Etiqueta
            // Nota: No podemos dibujar texto directamente en Canvas sin TextMeasurer
            // La etiqueta se muestra en la UI overlay
        }

        // 3. DIBUJAR ONDA PPG FILTRADA - SOLO SI HAY SEÑAL FISIOLÓGICA
        if (showFilteredWaveform && filteredWaveform.size >= 2 && isPhysiologicalSignal) {
            val points = filteredWaveform.takeLast(400)
            val min = points.minOrNull() ?: -1.0
            val max = points.maxOrNull() ?: 1.0
            val range = (max - min).coerceAtLeast(0.001)

            val path = Path()
            val stepX = width / points.size.coerceAtLeast(2)
            
            val signalColor = when (validityState) {
                PpgPhysiologyClassifier.PpgValidityState.PPG_VALID -> ppgValidColor
                PpgPhysiologyClassifier.PpgValidityState.PPG_DEGRADED -> ppgCandidateColor
                PpgPhysiologyClassifier.PpgValidityState.PPG_CANDIDATE -> ppgCandidateColor
                else -> noSignalColor
            }

            points.forEachIndexed { index, value ->
                val x = index * stepX
                val norm = (value - min) / range
                val y = (height * 0.7f) - (norm.toFloat() * height * 0.4f) // Parte inferior
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            drawPath(path, signalColor, style = Stroke(width = 2.5.dp.toPx()))
            
            // Sombreado bajo la curva
            val fillPath = Path().apply {
                addPath(path)
                lineTo(points.size * stepX, height * 0.7f)
                lineTo(0f, height * 0.7f)
                close()
            }
            drawPath(
                fillPath,
                Brush.verticalGradient(
                    listOf(signalColor.copy(alpha = 0.15f), Color.Transparent),
                    startY = height * 0.3f,
                    endY = height * 0.7f
                )
            )
        }
        
        // 4. INDICADOR VISUAL DE ESTADO
        val statusColor = when (validityState) {
            PpgPhysiologyClassifier.PpgValidityState.PPG_VALID -> ppgValidColor
            PpgPhysiologyClassifier.PpgValidityState.PPG_DEGRADED -> ppgCandidateColor
            PpgPhysiologyClassifier.PpgValidityState.PPG_CANDIDATE -> ppgCandidateColor
            PpgPhysiologyClassifier.PpgValidityState.NO_PPG_PHYSIOLOGICAL_SIGNAL -> noSignalColor
            PpgPhysiologyClassifier.PpgValidityState.SATURATED -> Color(0xFFEF4444)
            else -> rawOpticalColor
        }
        
        // Línea de estado en el borde superior
        drawLine(
            color = statusColor,
            start = Offset(0f, 0f),
            end = Offset(width, 0f),
            strokeWidth = 3.dp.toPx()
        )
    }
}
