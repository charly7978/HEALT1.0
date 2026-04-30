package com.example.myapplication.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.camera.Camera2PpgController
import com.example.myapplication.haptics.BeatFeedbackController
import com.example.myapplication.signal.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel central que orquestra el pipeline PPG profesional.
 */
class MonitorViewModel(application: Application) : AndroidViewModel(application) {

    private val cameraController = Camera2PpgController(application)
    private val beatFeedback = BeatFeedbackController(application)
    private val signalProcessor = PpgSignalProcessor(30.0)
    private val physiologyClassifier = PpgPhysiologyClassifier()
    private val peakDetector = PpgPeakDetector(30.0)
    private val rhythmAnalyzer = RhythmAnalyzer()
    private val spo2Estimator = Spo2Estimator()

    private val _uiState = MutableStateFlow(MonitorUiState())
    val uiState = _uiState.asStateFlow()

    private val waveformPoints = mutableListOf<Float>()
    private val rawPoints = mutableListOf<Float>()
    private val maxPoints = 200

    private var lastPeakTimestampNs = 0L

    init {
        cameraController.onFrameAvailable = { frame ->
            viewModelScope.launch {
                processPpgFrame(frame)
            }
        }
    }

    private fun processPpgFrame(frame: PpgFrame) {
        val sigResult = signalProcessor.process(frame.avgGreen)
        val validity = physiologyClassifier.classify(frame, sigResult.sqi, sigResult.perfusionIndex)
        
        var peakResult: PpgPeakDetector.PeakResult? = null
        if (validity == PpgValidityState.PPG_CANDIDATE || validity == PpgValidityState.PPG_VALID) {
            peakResult = peakDetector.process(sigResult.filteredValue, frame.timestampNs)
        } else {
            peakDetector.reset()
        }

        // 4. Manejo de Latidos y Retroalimentación
        if (peakResult?.isPeak == true && validity == PpgValidityState.PPG_VALID) {
            if (lastPeakTimestampNs != 0L) {
                val ppi = (frame.timestampNs - lastPeakTimestampNs) / 1_000_000
                rhythmAnalyzer.addInterval(ppi)
            }
            lastPeakTimestampNs = frame.timestampNs
            beatFeedback.playBeatFeedback()
        }

        // 5. Estimación de SpO2
        val spo2Res = if (validity == PpgValidityState.PPG_VALID) {
            spo2Estimator.estimate(sigResult.ac, sigResult.dc, sigResult.ac, sigResult.dc, sigResult.sqi)
        } else null

        // 6. Actualizar UI
        updateWaveforms(sigResult.normalizedValue.toFloat(), frame.rawOpticalSignal.toFloat())
        
        val rhythmMetrics = rhythmAnalyzer.analyze()

        _uiState.update { it.copy(
            validityState = validity,
            currentBpm = peakResult?.bpm ?: 0,
            currentSpo2 = spo2Res?.spo2,
            sqi = sigResult.sqi,
            perfusionIndex = sigResult.perfusionIndex,
            rhythmState = rhythmMetrics.state,
            fps = frame.fpsEstimate,
            statusMessage = getValidityMessage(validity)
        ) }
    }

    private fun updateWaveforms(ppg: Float, raw: Float) {
        waveformPoints.add(ppg)
        if (waveformPoints.size > maxPoints) waveformPoints.removeAt(0)
        
        rawPoints.add(raw)
        if (rawPoints.size > maxPoints) rawPoints.removeAt(0)
        
        _uiState.update { it.copy(
            ppgWaveform = waveformPoints.toList(),
            rawWaveform = rawPoints.toList()
        ) }
    }

    private fun getValidityMessage(state: PpgValidityState): String = when(state) {
        PpgValidityState.MEASURING_RAW_OPTICAL -> "MIDIENDO DATOS ÓPTICOS..."
        PpgValidityState.NO_PPG_PHYSIOLOGICAL_SIGNAL -> "SEÑAL NO FISIOLÓGICA (OBJETO DETECTADO)"
        PpgValidityState.SEARCHING_PPG -> "BUSCANDO PULSO PPG..."
        PpgValidityState.PPG_CANDIDATE -> "ESTABILIZANDO SEÑAL..."
        PpgValidityState.PPG_VALID -> "PPG VÁLIDA - MEDICIÓN ACTIVA"
        PpgValidityState.SATURATED -> "SENSOR SATURADO - AJUSTE PRESIÓN"
        PpgValidityState.MOTION_ARTIFACT -> "MOVIMIENTO DETECTADO"
        else -> "ESPERANDO..."
    }

    fun toggleBeep(enabled: Boolean) {
        beatFeedback.isBeepEnabled = enabled
        _uiState.update { it.copy(beepEnabled = enabled) }
    }

    fun toggleVibration(enabled: Boolean) {
        beatFeedback.isVibrationEnabled = enabled
        _uiState.update { it.copy(vibrationEnabled = enabled) }
    }

    fun start() {
        cameraController.start()
        _uiState.update { it.copy(isRunning = true) }
    }

    fun stop() {
        cameraController.stop()
        _uiState.update { it.copy(isRunning = false, validityState = PpgValidityState.MEASURING_RAW_OPTICAL) }
        resetMetrics()
    }

    private fun resetMetrics() {
        physiologyClassifier.reset()
        peakDetector.reset()
        rhythmAnalyzer.reset()
        waveformPoints.clear()
        rawPoints.clear()
        lastPeakTimestampNs = 0L
    }

    override fun onCleared() {
        super.onCleared()
        cameraController.stop()
        beatFeedback.release()
    }
}

data class MonitorUiState(
    val isRunning: Boolean = false,
    val validityState: PpgValidityState = PpgValidityState.MEASURING_RAW_OPTICAL,
    val ppgWaveform: List<Float> = emptyList(),
    val rawWaveform: List<Float> = emptyList(),
    val currentBpm: Int = 0,
    val currentSpo2: Double? = null,
    val sqi: Double = 0.0,
    val perfusionIndex: Double = 0.0,
    val rhythmState: RhythmAnalyzer.RhythmState = RhythmAnalyzer.RhythmState.INSUFFICIENT_DATA,
    val fps: Double = 0.0,
    val statusMessage: String = "INICIAR MONITOR",
    val beepEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true
)
