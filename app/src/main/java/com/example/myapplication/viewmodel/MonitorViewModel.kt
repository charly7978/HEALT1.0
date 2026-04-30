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
            val quality = signalProcessor.lastQuality ?: return@launch

            // 1. Detección de latidos SIEMPRE ACTIVA (Uso Forense)
            // No bloqueamos por fisiología, procesamos lo que venga
            val beat = peakDetector.detect(processed, quality.sqi)

            if (beat != null) {
                // FEEDBACK INMEDIATO: Sonido y Vibración
                feedbackController.trigger()
                
                if (beat.rrIntervalMs > 0) {
                    rrHistory.addLast(beat.rrIntervalMs)
                    if (rrHistory.size > 30) rrHistory.removeFirst()
                    rhythmAnalyzer.analyze(rrHistory)
                }
            }

            // 2. Cálculo de métricas continuo
            val currentBpm = if (rrHistory.size >= 2) {
                (60000.0 / rrHistory.takeLast(10).average()).toInt()
            } else 0

            // Cálculo de SpO2 usando la amplitud real detectada por el procesador
            val spo2Result = if (quality.state >= PpgSignalQuality.PpgValidityState.PPG_CANDIDATE) {
                // Cálculo dinámico de AC/DC para SpO2
                val currentAcRed = if (signalProcessor.getFilteredBuffer().size >= 20) {
                    val window = signalProcessor.getFilteredBuffer().takeLast(20)
                    (window.maxOrNull()!! - window.minOrNull()!!) / 2.0
                } else 0.1

                spo2Estimator.estimate(
                    redAc = currentAcRed,
                    redDc = sample.red,
                    greenAc = (sample.green * 0.015).coerceAtLeast(0.05),
                    greenDc = sample.green,
                    sqi = quality.sqi
                )
            } else null

            // 3. Actualizar UI
            _uiState.value = _uiState.value.copy(
                bpm = currentBpm,
                spo2 = if (currentBpm > 0 && spo2Result != null) spo2Result.spo2.toInt() else 0,
                isPhysiological = quality.isPhysiological,
                sqi = quality.sqi,
                validityState = quality.state,
                filteredWaveform = signalProcessor.getFilteredBuffer(),
                actualFps = sample.actualFps,
                statusMessage = getStatusDescription(quality.state),
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
