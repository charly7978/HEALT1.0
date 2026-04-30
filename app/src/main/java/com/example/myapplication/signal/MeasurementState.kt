package com.example.myapplication.signal

enum class MeasurementState {
    NO_CAMERA,
    CAMERA_READY,
    SEARCHING_FINGER,
    CONTACT_CANDIDATE,
    LOCKING_SIGNAL,
    PPG_LOCKED,
    MEASURING,
    LOW_QUALITY,
    MOTION_ARTIFACT,
    SATURATED,
    INVALID_SIGNAL
}
