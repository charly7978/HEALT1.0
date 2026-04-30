package com.example.myapplication.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Panel de diagnóstico de cámara.
 * Muestra FPS, exposición, ISO, frame duration y estado del torch.
 */
@Composable
fun CameraDiagnosticsPanel(
    fps: Double,
    exposureTimeNs: Long?,
    iso: Int?,
    frameDurationNs: Long?,
    cameraId: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "DIAGNÓSTICO CÁMARA",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )

        DiagnosticRow("FPS", "%.1f".format(fps), getFpsColor(fps))
        DiagnosticRow("CÁMARA", cameraId, Color.White)
        
        exposureTimeNs?.let {
            val exposureMs = it / 1_000_000.0
            DiagnosticRow("EXP", "%.2fms".format(exposureMs), Color.White)
        }
        
        iso?.let {
            DiagnosticRow("ISO", "$it", Color.White)
        }
        
        frameDurationNs?.let {
            val durationMs = it / 1_000_000.0
            DiagnosticRow("FRAME", "%.2fms".format(durationMs), Color.White)
        }
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            value,
            color = color,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun getFpsColor(fps: Double): Color {
    return when {
        fps >= 25.0 -> Color(0xFF22C55E) // Verde
        fps >= 20.0 -> Color(0xFFF59E0B) // Amarillo
        else -> Color(0xFFEF4444) // Rojo
    }
}
