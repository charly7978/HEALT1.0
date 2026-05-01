package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.camera.Camera2PpgController
import com.example.myapplication.camera.CameraFrame
import com.example.myapplication.haptics.BeatFeedbackController
import com.example.myapplication.signal.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*

class MonitorViewModel(
    private val cameraController: Camera2PpgController,
    private val feedbackController: BeatFeedbackController,
    private val context: android.content.Context
) : ViewModel() {

    data class MonitorUiState(
        val state: MeasurementState = MeasurementState.SEARCHING_FINGER,
        val bpm: Double? = null,
        val bpmConfidence: Double = 0.0,
        val spo2: Double? = null,
        val spo2Confidence: Double = 0.0,
        val spo2Message: String = "SpO₂ REQUIERE CALIBRACIÓN",
        val sqi: Double = 0.0,
        val perfusionIndex: Double = 0.0,
        val motionScore: Double = 0.0,
        val waveform: List<Double> = emptyList(),
        val beatEvents: List<PpgPeakDetector.BeatEvent> = emptyList(),
        val isIrregular: Boolean = false,
        val irregularityMessage: String = "",
        val statusMessage: String = "COLOQUE DEDO EN CÁMARA",
        val actualFps: Double = 0.0,
        val exposureTimeNs: Long? = null,
        val iso: Int? = null,
        val frameDurationNs: Long? = null,
        val showCalibrationScreen: Boolean = false
    )

    private val _uiState = MutableStateFlow(MonitorUiState())
    val uiState = _uiState.asStateFlow()

    // Motores de procesamiento de señal
    private val frameAnalyzer = PpgFrameAnalyzer()
    private val contactGate = PhysiologicalContactGate()
    private val signalProcessor = PpgSignalProcessor(30.0) // FPS base
    private val peakDetector = PpgPeakDetector()
    private val qualityEngine = PpgSignalQualityEngine()
    private val rhythmAnalyzer = RhythmAnalyzer()
    private val spo2Estimator = Spo2Estimator()

    private val waveformBuffer = LinkedList<Double>()
    private val maxWaveformSize = 300
    private val beatEventsBuffer = LinkedList<PpgPeakDetector.BeatEvent>()
    private val rrIntervals = LinkedList<Long>()
    private val maxRrBuffer = 30

    init {
        cameraController.onFrameAvailable = { frame ->
            processFrame(frame)
        }
    }

    fun start() {
        cameraController.start()
        _uiState.value = _uiState.value.copy(statusMessage = "INICIANDO CÁMARA...")
    }

    fun stop() {
        cameraController.stop()
        resetMetrics(MeasurementState.SEARCHING_FINGER, 0.0)
    }

    fun showCalibrationScreen() {
        _uiState.value = _uiState.value.copy(showCalibrationScreen = true)
    }

    fun hideCalibrationScreen() {
        _uiState.value = _uiState.value.copy(showCalibrationScreen = false)
    }

    private fun processFrame(frame: CameraFrame) {
        viewModelScope.launch {
            // 1. Extraer características del frame
            // Usamos los datos ya extraídos por el controlador para evitar doble procesamiento
            val features = PpgFrameAnalyzer.FrameFeatures(
                timestampNs = frame.timestampNs,
                redMean = frame.redMean,
                greenMean = frame.greenMean,
                blueMean = frame.blueMean,
                lumaMean = (frame.redMean + frame.greenMean + frame.blueMean) / 3.0,
                clippedPixelRatio = frame.clipHighRatio,
                darkPixelRatio = frame.clipLowRatio,
                actualFps = frame.actualFps
            )

            // 2. Validar contacto fisiológico
            val gateState = contactGate.evaluate(
                PhysiologicalContactGate.ContactFeatures(
                    redMean = features.redMean,
                    greenMean = features.greenMean,
                    blueMean = features.blueMean,
                    lumaMean = features.lumaMean,
                    saturationRatio = frame.clipHighRatio,
                    clippedPixelRatio = frame.clipHighRatio,
                    skinLikeScore = 0.0 // Placeholder para lógica futura
                )
            )

            if (gateState == MeasurementState.SEARCHING_FINGER || gateState == MeasurementState.SATURATED) {
                resetMetrics(gateState, frame.actualFps)
                return@launch
            }

            // 3. Procesar Señal (Filtrado y AC/DC)
            val processed = signalProcessor.process(features)
            
            // 4. Evaluar Calidad (SQI)
            val sqiResult = qualityEngine.compute(
                features = features,
                acRed = processed.acRed,
                acGreen = processed.acGreen,
                isPeriodical = true // Por ahora asumimos estabilidad si pasa filtros
            )

            // 5. Detectar Latidos
            val beat = peakDetector.detect(processed, sqiResult.totalSqi)
            if (beat != null) {
                feedbackController.trigger()
                beatEventsBuffer.addLast(beat)
                if (beatEventsBuffer.size > 50) beatEventsBuffer.removeFirst()
                
                rrIntervals.addLast(beat.rrMs)
                if (rrIntervals.size > maxRrBuffer) rrIntervals.removeFirst()
            }

            // 6. Análisis de Ritmo
            val rhythmMetrics = if (rrIntervals.size >= 5) {
                rhythmAnalyzer.analyze(rrIntervals.toList())
            } else null

            // 7. Estimación de SpO2
            val spo2Result = spo2Estimator.estimate(
                acRed = processed.acRed,
                dcRed = processed.dcRed,
                acGreen = processed.acGreen,
                dcGreen = processed.dcGreen,
                sqi = sqiResult.totalSqi,
                isCalibrated = false
            )

            // 8. Actualizar UI
            waveformBuffer.addLast(processed.filteredValue)
            if (waveformBuffer.size > maxWaveformSize) waveformBuffer.removeFirst()

            _uiState.value = _uiState.value.copy(
                state = sqiResult.state,
                bpm = if (beat != null) 60000.0 / beat.rrMs else _uiState.value.bpm,
                sqi = sqiResult.totalSqi,
                perfusionIndex = sqiResult.perfusionIndex,
                waveform = waveformBuffer.toList(),
                beatEvents = beatEventsBuffer.toList(),
                isIrregular = rhythmMetrics?.state == RhythmAnalyzer.RhythmState.IRREGULAR,
                irregularityMessage = rhythmMetrics?.state?.name ?: "",
                spo2 = spo2Result.value?.toDouble(),
                spo2Message = spo2Result.status.name,
                statusMessage = getStatusMessage(sqiResult.state),
                actualFps = frame.actualFps,
                exposureTimeNs = frame.exposureTimeNs,
                iso = frame.iso,
                frameDurationNs = frame.frameDurationNs
            )
        }
    }

    private fun resetMetrics(state: MeasurementState, fps: Double) {
        waveformBuffer.clear()
        beatEventsBuffer.clear()
        rrIntervals.clear()
        _uiState.value = _uiState.value.copy(
            state = state,
            bpm = null,
            sqi = 0.0,
            waveform = emptyList(),
            beatEvents = emptyList(),
            isIrregular = false,
            statusMessage = getStatusMessage(state),
            actualFps = fps
        )
    }

    private fun getStatusMessage(state: MeasurementState): String {
        return when (state) {
            MeasurementState.SEARCHING_FINGER -> "COLOQUE DEDO EN CÁMARA"
            MeasurementState.SATURATED -> "DEMASIADA LUZ / SATURACIÓN"
            MeasurementState.MOTION_ARTIFACT -> "MANTENGA EL DEDO QUIETO"
            MeasurementState.LOW_QUALITY -> "MEJORANDO SEÑAL..."
            MeasurementState.LOCKING_SIGNAL -> "SINCRONIZANDO PULSO..."
            MeasurementState.MEASURING -> "MEDICIÓN ACTIVA"
            else -> "BUSCANDO SEÑAL..."
        }
    }
}
