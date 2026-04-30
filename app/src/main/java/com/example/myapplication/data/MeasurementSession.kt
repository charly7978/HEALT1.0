package com.example.myapplication.data

import com.example.myapplication.signal.RhythmAnalysisEngine

data class MeasurementSession(
    val id: String,
    val timestamp: Long,
    val averageBpm: Int,
    val averageSpO2: Int,
    val rhythmStatus: RhythmAnalysisEngine.RhythmStatus,
    val rmssd: Double,
    val samples: List<Float> // Opcional: guardar muestras para auditoría
)
