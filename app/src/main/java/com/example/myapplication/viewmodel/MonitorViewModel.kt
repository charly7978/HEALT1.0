package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.camera.Camera2Controller
import com.example.myapplication.camera.CameraDiagnostics
import com.example.myapplication.signal.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class MeasurementState {
    object Idle : MeasurementState()
    object CameraStarting : MeasurementState()
    object WaitingForFinger : MeasurementState()
    object Warmup : MeasurementState()
    object Stabilizing : MeasurementState()
    object Measuring : MeasurementState()
    data class Warning(val message: String) : MeasurementState()
    data class Error(val message: String) : MeasurementState()
}

class MonitorViewModel(
    private val cameraController: Camera2Controller,
    private val motionDetector: MotionArtifactDetector
) : ViewModel() {

    private val _uiState = MutableStateFlow(MonitorUiState())
    val uiState = _uiState.asStateFlow()

    private val signalProcessor = PPGSignalProcessor(30.0)
    private val peakDetector = PeakDetectionEngine(30.0)
    private val fingerDetectionEngine = FingerDetectionEngine()
    private val rhythmAnalyzer = RhythmAnalyzer()
    private val signalBuffer = PPGSignalBuffer()

    private val waveformPoints = mutableListOf<Float>()
    private val maxWaveformPoints = 200

    private var lastPeakTimestampNs = 0L
    private var stableStartTimeNs = 0L

    init {
        cameraController.onFrameProcessed = { features ->
            viewModelScope.launch {
                processFrame(features)
            }
        }
        cameraController.onDiagnosticsUpdated = { diag ->
            _uiState.update { it.copy(diagnostics = diag) }
        }
    }

    private fun processFrame(features: PPGFrameFeatures) {
        val fingerEval = fingerDetectionEngine.evaluate(features)
        val isMoving = motionDetector.isMovingSignificant
        
        _uiState.update { it.copy(
            fingerState = fingerEval.state,
            pressureState = fingerEval.pressure,
            isMoving = isMoving
        ) }

        if (fingerEval.state == FingerState.NO_FINGER || isMoving) {
            handleIncompleteSignal(fingerEval.state, isMoving)
            return
        }

        // Señal estable detectada
        if (stableStartTimeNs == 0L) stableStartTimeNs = features.timestampNs
        val stableDuration = (features.timestampNs - stableStartTimeNs) / 1_000_000_000.0

        // Pipeline de procesamiento
        val sigResult = signalProcessor.process(features.greenMean)
        val peakResult = peakDetector.process(sigResult.filteredValue, features.timestampNs)

        if (peakResult.isPeak) {
            if (lastPeakTimestampNs != 0L) {
                val ppi = (features.timestampNs - lastPeakTimestampNs) / 1_000_000
                rhythmAnalyzer.addInterval(ppi)
            }
            lastPeakTimestampNs = features.timestampNs
        }

        // Actualizar UI
        updateWaveform(sigResult.normalizedValue.toFloat())
        
        val rhythmMetrics = rhythmAnalyzer.analyze()
        
        val currentState = when {
            stableDuration < 3.0 -> MeasurementState.Warmup
            stableDuration < 8.0 -> MeasurementState.Stabilizing
            else -> MeasurementState.Measuring
        }

        _uiState.update { it.copy(
            measurementState = currentState,
            currentBpm = peakResult.bpm,
            sqi = sigResult.sqi,
            perfusionIndex = sigResult.perfusionIndex,
            rhythmState = rhythmMetrics.state,
            rmssd = rhythmMetrics.rmssd,
            statusText = getStatusText(currentState, stableDuration)
        ) }
    }

    private fun handleIncompleteSignal(state: FingerState, isMoving: Boolean) {
        stableStartTimeNs = 0L
        lastPeakTimestampNs = 0L
        peakDetector.reset()
        rhythmAnalyzer.reset()
        
        val msg = when {
            isMoving -> "MOVIMIENTO DETECTADO - MANTENGA QUIETO"
            state == FingerState.NO_FINGER -> "POSICIONE EL DEDO SOBRE LA CÁMARA"
            state == FingerState.LOW_LIGHT -> "LUZ INSUFICIENTE"
            state == FingerState.SATURATED -> "DEMASIADA PRESIÓN - RELAJE EL DEDO"
            else -> "ESPERANDO SEÑAL ESTABLE"
        }

        _uiState.update { it.copy(
            measurementState = MeasurementState.WaitingForFinger,
            statusText = msg,
            currentBpm = 0
        ) }
    }

    private fun updateWaveform(value: Float) {
        waveformPoints.add(value)
        if (waveformPoints.size > maxWaveformPoints) waveformPoints.removeAt(0)
        _uiState.update { it.copy(ppgWaveform = waveformPoints.toList()) }
    }

    private fun getStatusText(state: MeasurementState, duration: Double): String {
        return when (state) {
            MeasurementState.Warmup -> "INICIANDO... (%.1fs)".format(duration)
            MeasurementState.Stabilizing -> "ESTABILIZANDO... (%.1fs)".format(duration)
            MeasurementState.Measuring -> "MEDICIÓN ACTIVA"
            else -> ""
        }
    }

    fun startMonitoring() {
        _uiState.update { it.copy(measurementState = MeasurementState.CameraStarting) }
        cameraController.start()
    }

    fun stopMonitoring() {
        cameraController.stop()
        _uiState.update { MonitorUiState() }
        stableStartTimeNs = 0L
    }
}

data class MonitorUiState(
    val measurementState: MeasurementState = MeasurementState.Idle,
    val fingerState: FingerState = FingerState.NO_FINGER,
    val pressureState: PressureState = PressureState.UNKNOWN,
    val isMoving: Boolean = false,
    val currentBpm: Int = 0,
    val sqi: Double = 0.0,
    val perfusionIndex: Double = 0.0,
    val ppgWaveform: List<Float> = emptyList(),
    val rhythmState: RhythmAnalyzer.RhythmState = RhythmAnalyzer.RhythmState.INSUFFICIENT_DATA,
    val rmssd: Double = 0.0,
    val statusText: String = "INICIAR",
    val diagnostics: CameraDiagnostics = CameraDiagnostics()
)
