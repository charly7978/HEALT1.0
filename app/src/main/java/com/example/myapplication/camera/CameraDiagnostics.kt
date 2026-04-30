package com.example.myapplication.camera

import android.util.Range

data class CameraDiagnostics(
    val selectedFpsRange: Range<Int>? = null,
    val exposureTimeNs: Long? = null,
    val iso: Int? = null,
    val frameDurationNs: Long? = null,
    val torchEnabled: Boolean = false,
    val droppedFrames: Int = 0,
    val frameIntervalMs: Long = 0,
    val actualFps: Double = 0.0,
    val cameraMode: CameraMode = CameraMode.AUTO_FALLBACK
)

enum class CameraMode {
    MANUAL,
    AE_LOCKED,
    AUTO_FALLBACK
}
