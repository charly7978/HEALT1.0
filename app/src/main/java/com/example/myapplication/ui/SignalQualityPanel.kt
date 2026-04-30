package com.example.myapplication.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Panel de calidad de señal con indicadores visuales.
 * Muestra SQI, Perfusion Index, Motion Score y estado.
 */
@Composable
fun SignalQualityPanel(
    sqi: Double,
    perfusionIndex: Double,
    motionScore: Double,
    state: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "CALIDAD DE SEÑAL",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )

        // SQI Bar
        QualityBar(
            label = "SQI",
            value = sqi,
            maxValue = 1.0,
            color = getSqiColor(sqi)
        )

        // Perfusion Index Bar
        QualityBar(
            label = "PI",
            value = perfusionIndex,
            maxValue = 10.0,
            color = getPerfusionColor(perfusionIndex)
        )

        // Motion Score Bar (invertido: menor es mejor)
        QualityBar(
            label = "MOT",
            value = 1.0 - motionScore,
            maxValue = 1.0,
            color = getMotionColor(motionScore)
        )

        // Estado textual
        Text(
            state,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 10.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun QualityBar(
    label: String,
    value: Double,
    maxValue: Double,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(30.dp)
        )
        
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((value / maxValue).toFloat().coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(color)
            )
        }
        
        Text(
            "%.2f".format(value),
            color = Color.White,
            fontSize = 10.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            modifier = Modifier.width(40.dp)
        )
    }
}

private fun getSqiColor(sqi: Double): Color {
    return when {
        sqi >= 0.8 -> Color(0xFF22C55E) // Verde
        sqi >= 0.6 -> Color(0xFF22C55E).copy(alpha = 0.7f)
        sqi >= 0.4 -> Color(0xFFF59E0B) // Amarillo
        else -> Color(0xFFEF4444) // Rojo
    }
}

private fun getPerfusionColor(pi: Double): Color {
    return when {
        pi in 0.5..3.0 -> Color(0xFF22C55E)
        pi in 0.1..5.0 -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }
}

private fun getMotionColor(motionScore: Double): Color {
    return when {
        motionScore < 0.1 -> Color(0xFF22C55E)
        motionScore < 0.3 -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }
}
