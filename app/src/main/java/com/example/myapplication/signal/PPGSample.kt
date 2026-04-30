package com.example.myapplication.signal

data class PPGSample(
    val timestamp: Long,
    val red: Double,
    val green: Double,
    val blue: Double,
    val filteredValue: Double = 0.0,
    val isPeak: Boolean = false,
    val sqi: Double = 0.0
)
