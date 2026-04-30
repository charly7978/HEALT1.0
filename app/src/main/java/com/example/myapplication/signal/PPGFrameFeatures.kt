package com.example.myapplication.signal

import android.graphics.Rect

data class PPGFrameFeatures(
    val timestampNs: Long,
    val redMean: Double,
    val greenMean: Double,
    val blueMean: Double,
    val redStd: Double,
    val greenStd: Double,
    val blueStd: Double,
    val redMedian: Double,
    val greenMedian: Double,
    val blueMedian: Double,
    val clippedHighRatio: Double,
    val clippedLowRatio: Double,
    val validPixelRatio: Double,
    val brightness: Double,
    val redDominance: Double,
    val perfusionIndexGreen: Double,
    val perfusionIndexRed: Double,
    val roi: Rect,
    val fingerState: FingerState,
    val pressureState: PressureState,
    val qualityHints: List<String> = emptyList()
)

enum class FingerState {
    NO_FINGER,
    FINGER_DETECTED,
    LOW_LIGHT,
    SATURATED,
    UNSTABLE,
    STABLE
}

enum class PressureState {
    TOO_LOW,
    TOO_HIGH,
    OPTIMAL,
    UNKNOWN
}
