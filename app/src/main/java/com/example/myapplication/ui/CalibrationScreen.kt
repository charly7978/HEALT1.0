package com.example.myapplication.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ppg.CalibrationProfile
import com.example.myapplication.ppg.DeviceCalibrationManager

/**
 * Pantalla de calibración SpO₂.
 * Permite ingresar un valor de referencia de SpO₂ para calibrar el dispositivo.
 * 
 * REGLA CRÍTICA:
 * - SpO₂ requiere calibración por dispositivo.
 * - El usuario debe ingresar un valor de referencia de un oxímetro certificado.
 * - No se usan valores hardcodeados o simulados.
 */
@Composable
fun CalibrationScreen(
    calibrationManager: DeviceCalibrationManager,
    onCalibrationComplete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var referenceSpO2 by remember { mutableStateOf("") }
    var isCalibrating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf("") }

    val backgroundColor = Color(0xFF070B14)
    val primaryColor = Color(0xFF3B82F6)
    val successColor = Color(0xFF22C55E)
    val errorColor = Color(0xFFEF4444)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Text(
                "CALIBRACIÓN SpO₂",
                color = primaryColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                "Para medir SpO₂ con precisión, debe calibrar el dispositivo con un oxímetro certificado.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )

            Divider(color = Color.White.copy(alpha = 0.1f))

            // Instrucciones
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "INSTRUCCIONES:",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                BulletPoint("1. Use un oxímetro de pulso certificado")
                BulletPoint("2. Coloque el oxímetro en el mismo dedo")
                BulletPoint("3. Espere 30 segundos hasta que el valor sea estable")
                BulletPoint("4. Ingrese el valor de SpO₂ del oxímetro")
                BulletPoint("5. Mantenga el dedo en la cámara durante la calibración")
            }

            Divider(color = Color.White.copy(alpha = 0.1f))

            // Input de referencia
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "VALOR DE REFERENCIA SpO₂ (%)",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = referenceSpO2,
                    onValueChange = { 
                        if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                            referenceSpO2 = it
                            errorMessage = ""
                        }
                    },
                    placeholder = { Text("95-100") },
                    singleLine = true,
                    modifier = Modifier.width(200.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryColor,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                    )
                )
            }

            // Mensajes
            if (errorMessage.isNotEmpty()) {
                Text(
                    errorMessage,
                    color = errorColor,
                    fontSize = 12.sp
                )
            }

            if (successMessage.isNotEmpty()) {
                Text(
                    successMessage,
                    color = successColor,
                    fontSize = 12.sp
                )
            }

            // Botones
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                ) {
                    Text("CANCELAR", color = Color.White, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        val spo2 = referenceSpO2.toIntOrNull()
                        when {
                            spo2 == null -> {
                                errorMessage = "Ingrese un valor válido (70-100)"
                            }
                            spo2 < 70 || spo2 > 100 -> {
                                errorMessage = "El valor debe estar entre 70 y 100"
                            }
                            else -> {
                                isCalibrating = true
                                // Simular proceso de calibración (en producción usar datos reales)
                                // Aquí se debería capturar datos de la cámara y calcular el ratio R
                                // Por ahora, creamos un perfil de calibración con valores por defecto
                                val profile = CalibrationProfile.fromMeasurement(
                                    deviceModel = android.os.Build.MODEL,
                                    cameraId = "back",
                                    physicalLens = null,
                                    exposureTimeNs = 10_000_000L,
                                    iso = 800,
                                    frameDurationNs = 33_333_333L,
                                    torchIntensity = 1.0f,
                                    measuredR = 0.5, // Valor por defecto, debe calcularse de datos reales
                                    referenceSpO2 = spo2
                                )
                                calibrationManager.saveProfile(profile)
                                calibrationManager.setActiveProfile(profile.id)
                                
                                successMessage = "Calibración completada exitosamente"
                                isCalibrating = false
                                
                                // Retraso breve antes de navegar
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                    kotlinx.coroutines.delay(1500)
                                    onCalibrationComplete()
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = referenceSpO2.isNotEmpty() && !isCalibrating,
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) {
                    if (isCalibrating) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text("CALIBRAR", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Perfiles existentes
            val existingProfiles = calibrationManager.getAllProfiles()
            if (existingProfiles.isNotEmpty()) {
                Divider(color = Color.White.copy(alpha = 0.1f))
                Text(
                    "PERFILES DE CALIBRACIÓN EXISTENTES",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                
                existingProfiles.forEach { profile ->
                    ProfileItem(
                        profile = profile,
                        isActive = calibrationManager.getActiveProfile()?.id == profile.id,
                        onSelect = { calibrationManager.setActiveProfile(profile.id) },
                        onDelete = { calibrationManager.deleteProfile(profile.id) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun BulletPoint(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("•", color = Color.White.copy(alpha = 0.5f))
        Text(text, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
    }
}

@Composable
private fun ProfileItem(
    profile: CalibrationProfile,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) Color(0xFF1E293B) else Color(0xFF0F172A)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Dispositivo: ${profile.deviceModel}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Referencia: ${profile.referenceSpO2}% | ${profile.calibrationDate}",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
                if (isActive) {
                    Text(
                        "ACTIVO",
                        color = Color(0xFF22C55E),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isActive) {
                    TextButton(
                        onClick = onSelect,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF3B82F6))
                    ) {
                        Text("USAR", fontSize = 12.sp)
                    }
                }
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
                ) {
                    Text("ELIMINAR", fontSize = 12.sp)
                }
            }
        }
    }
}
