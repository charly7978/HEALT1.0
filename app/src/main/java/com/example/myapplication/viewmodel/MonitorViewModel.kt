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

    data class MonitorUiState(
        val state: MeasurementState = MeasurementState.CAMERA_READY,
        val bpm: Int? = null,
        val spo2: Int? = null,
        val spo2Status: Spo2Estimator.Spo2Status = Spo2Estimator.Spo2Status.NOT_AVAILABLE,
        val sqi: Double = 0.0,
        val waveform: List<Double> = emptyList(),
        val isArhythmic: Boolean = false,
        val statusMessage: String = "SISTEMA LISTO",
        val actualFps: Double = 0.0,
        val rrIntervals: List<Long> = emptyList()
    )

    private val _uiState = MutableStateFlow(MonitorUiState())
    val uiState = _uiState.asStateFlow()

    private val frameAnalyzer = PpgFrameAnalyzer()
    private val contactGate = PhysiologicalContactGate()
    private val signalProcessor = PpgSignalProcessor(30.0)
    private val qualityEngine = PpgSignalQualityEngine()
    private val peakDetector = PpgPeakDetector()
    private val spo2Estimator = Spo2Estimator()
    private val rhythmAnalyzer = RhythmAnalyzer()

    private val rrHistory = LinkedList<Long>()

    init {
        cameraController.onFrameAvailable = { features ->
            processFrame(features)
        }
    }

    fun start() {
        cameraController.start()
    }

    fun stop() {
        cameraController.stop()
    }

    private fun processFrame(features: PpgFrameAnalyzer.FrameFeatures) {
        viewModelScope.launch {
            // 1. Gate Fisiológico (Anti-objetos)
            val contactFeatures = PhysiologicalContactGate.ContactFeatures(
                redMean = features.redMean,
                greenMean = features.greenMean,
                blueMean = features.blueMean,
                lumaMean = features.lumaMean,
                saturationRatio = features.clippedPixelRatio,
                clippedPixelRatio = features.clippedPixelRatio,
                skinLikeScore = 0.0 
            )
            
            val gateState = contactGate.evaluate(contactFeatures)
            if (gateState == MeasurementState.INVALID_SIGNAL || gateState == MeasurementState.SEARCHING_FINGER) {
                resetMetrics(gateState, features.actualFps)
                return@launch
            }

            // 2. Procesamiento de Señal
            val processed = signalProcessor.process(features)

            // 3. Calidad de Señal
            val sqiResult = qualityEngine.compute(
                features = features,
                acRed = processed.acRed,
                acGreen = processed.acGreen,
                isPeriodical = rrHistory.size > 2
            )

            // 4. Detección de Latidos y SpO2
            var currentBpm: Int? = null
            var currentSpo2: Spo2Estimator.Spo2Result? = null

            if (sqiResult.state == MeasurementState.MEASURING || sqiResult.state == MeasurementState.LOCKING_SIGNAL) {
                val beat = peakDetector.detect(processed, sqiResult.totalSqi)
                if (beat != null) {
                    feedbackController.trigger()
                    rrHistory.addLast(beat.rrMs)
                    if (rrHistory.size > 30) rrHistory.removeFirst()
                    rhythmAnalyzer.analyze(rrHistory)
                }

                if (rrHistory.isNotEmpty()) {
                    currentBpm = (60000.0 / rrHistory.takeLast(10).average()).toInt()
                }

                currentSpo2 = spo2Estimator.estimate(
                    acRed = processed.acRed, dcRed = processed.dcRed,
                    acGreen = processed.acGreen, dcGreen = processed.dcGreen,
                    sqi = sqiResult.totalSqi
                )
            }

            // 5. Actualizar UI
            _uiState.value = _uiState.value.copy(
                state = sqiResult.state,
                bpm = currentBpm,
                spo2 = currentSpo2?.value,
                spo2Status = currentSpo2?.status ?: Spo2Estimator.Spo2Status.NOT_AVAILABLE,
                sqi = sqiResult.totalSqi,
                waveform = if (sqiResult.state >= MeasurementState.LOCKING_SIGNAL) signalProcessor.getFilteredBuffer() else emptyList(),
                isArhythmic = rhythmAnalyzer.isArhythmic,
                statusMessage = getStatusMessage(sqiResult.state),
                actualFps = features.actualFps,
                rrIntervals = rrHistory.toList()
            )
        }
    }

    private fun resetMetrics(state: MeasurementState, fps: Double) {
        rrHistory.clear()
        _uiState.value = _uiState.value.copy(
            state = state,
            bpm = null,
            spo2 = null,
            waveform = emptyList(),
            statusMessage = getStatusMessage(state),
            actualFps = fps
        )
    }

    private fun getStatusMessage(state: MeasurementState) = when(state) {
        MeasurementState.SEARCHING_FINGER -> "COLOQUE DEDO EN CÁMARA"
        MeasurementState.SATURATED -> "LUZ DEMASIADO FUERTE"
        MeasurementState.MOTION_ARTIFACT -> "MANTENGA PRESIONADO SIN MOVER"
        MeasurementState.LOW_QUALITY -> "MEJORANDO SEÑAL..."
        MeasurementState.LOCKING_SIGNAL -> "SINCRONIZANDO PULSO..."
        MeasurementState.MEASURING -> "MEDICIÓN ACTIVA"
        MeasurementState.INVALID_SIGNAL -> "SEÑAL NO FISIOLÓGICA"
        else -> "SISTEMA INICIALIZANDO"
    }
}
