package com.example.myapplication.signal

data class CalibrationProfile(
    val deviceModel: String,
    val userId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val spo2A: Double = 110.0,
    val spo2B: Double = -25.0,
    val bpSystolicRef: Int? = null,
    val bpDiastolicRef: Int? = null,
    val isCalibrated: Boolean = false
)
