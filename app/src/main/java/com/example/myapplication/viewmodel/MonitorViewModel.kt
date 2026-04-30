package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.camera.Camera2PpgController
import com.example.myapplication.signal.*
import com.example.myapplication.haptics.BeatFeedbackController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*

class MonitorViewModel(
    private val cameraController: Camera2PpgController,
    private val feedbackController: BeatFeedbackController
) : ViewModel() {

    data class MonitorState(
        val bpm: Int = 0,
        val spo2: Int = 0,
        val isPhysiological: Boolean = false,
        val sqi: Double = 0.0,
        val validityState: PpgSignalQuality.PpgValidityState = PpgSignalQuality.PpgValidityState.RAW_OPTICAL_ONLY,
        val filteredWaveform: List<Double> = emptyList(),
        val actualFps: Double = 0.0,
        val statusMessage: String = "Esperando señal...",
        val isArhythmic: Boolean = false
    )

    private val _uiState = MutableStateFlow(MonitorState())
    val uiState = _uiState.asStateFlow()

    private val signalProcessor = PpgSignalProcessor(30.0)
    private val peakDetector = PpgPeakDetector()
    private val rhythmAnalyzer = RhythmAnalyzer()
    private val spo2Estimator = Spo2Estimator()
    private val physiologyClassifier = PpgPhysiologyClassifier()
    
    private val rrHistory = LinkedList<Long>()

    init {
        cameraController.onFrameAvailable = { sample ->
            processSample(sample)
        }
    }

    fun start() {
        cameraController.start()
    }

    fun stop() {
        cameraController.stop()
    }

    private fun processSample(sample: PpgSample) {
        viewModelScope.launch {
            val processed = signalProcessor.process(sample)
            val initialQuality = signalProcessor.lastQuality ?: return@launch

            // 1. Clasificación Fisiológica avanzada
            val vState = physiologyClassifier.classify(
                processed, 
                initialQuality.sqi, 
                (processed.redFiltered / (sample.red + 0.1)) // PI estimado
            )

            val isPhysiological = vState >= PpgSignalQuality.PpgValidityState.PPG_CANDIDATE

            // 2. Detección de latidos solo si es fisiológico
            val beat = if (isPhysiological) {
                peakDetector.detect(processed, initialQuality.sqi)
            } else null

            if (beat != null && beat.rrIntervalMs > 0) {
                feedbackController.trigger()
                rrHistory.addLast(beat.rrIntervalMs)
                if (rrHistory.size > 20) rrHistory.removeFirst()
                
                rhythmAnalyzer.analyze(rrHistory)
            }

            // 3. Cálculo de métricas reales
            val currentBpm = if (rrHistory.size >= 3) {
                (60000.0 / rrHistory.average()).toInt()
            } else 0

            val spo2Result = if (vState >= PpgSignalQuality.PpgValidityState.PPG_VALID) {
                spo2Estimator.estimate(
                    redAc = if (beat != null) beat.amplitude else 0.5,
                    redDc = sample.red,
                    greenAc = 0.2, // Placeholder de calibración
                    greenDc = sample.green,
                    sqi = initialQuality.sqi
                )
            } else null

            // 4. Actualizar UI
            _uiState.value = _uiState.value.copy(
                bpm = currentBpm,
                spo2 = spo2Result?.spo2?.toInt() ?: 0,
                isPhysiological = isPhysiological,
                sqi = initialQuality.sqi,
                validityState = vState,
                filteredWaveform = signalProcessor.getFilteredBuffer(),
                actualFps = sample.actualFps,
                statusMessage = getStatusDescription(vState),
                isArhythmic = rhythmAnalyzer.isArhythmic
            )
        }
    }

    private fun getStatusDescription(state: PpgSignalQuality.PpgValidityState): String {
        return when(state) {
            PpgSignalQuality.PpgValidityState.RAW_OPTICAL_ONLY -> "Buscando dedo..."
            PpgSignalQuality.PpgValidityState.NO_PHYSIOLOGICAL_SIGNAL -> "Señal no humana detectada"
            PpgSignalQuality.PpgValidityState.PPG_CANDIDATE -> "Analizando pulso..."
            PpgSignalQuality.PpgValidityState.PPG_VALID -> "Pulso estable"
            PpgSignalQuality.PpgValidityState.BIOMETRIC_VALID -> "Señal biométrica óptima"
        }
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }
}
