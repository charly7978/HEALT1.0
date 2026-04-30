package com.example.myapplication.viewmodel

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.camera.Camera2PpgController
import com.example.myapplication.domain.MeasurementState
import com.example.myapplication.domain.VitalReading
import com.example.myapplication.forensic.MeasurementEvent
import com.example.myapplication.forensic.MeasurementSession
import com.example.myapplication.forensic.SessionExporter
import com.example.myapplication.haptics.BeatFeedbackController
import com.example.myapplication.ppg.ArrhythmiaScreening
import com.example.myapplication.ppg.BeatClassifier
import com.example.myapplication.ppg.DeviceCalibrationManager
import com.example.myapplication.ppg.HeartRateFusion
import com.example.myapplication.ppg.PpgSample
import com.example.myapplication.ppg.Spo2Estimator
import com.example.myapplication.sensors.MotionArtifactEstimator
import com.example.myapplication.sensors.MotionSensorController
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
        val state: MeasurementState = MeasurementState.NO_CONTACT,
        val bpm: Double? = null,
        val bpmConfidence: Double = 0.0,
        val spo2: Double? = null,
        val spo2Confidence: Double = 0.0,
        val spo2Message: String = "SpO₂ REQUIERE CALIBRACIÓN",
        val sqi: Double = 0.0,
        val perfusionIndex: Double = 0.0,
        val motionScore: Double = 0.0,
        val waveform: List<Double> = emptyList(),
        val beatEvents: List<com.example.myapplication.ppg.BeatEvent> = emptyList(),
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

    // Nuevos componentes de arquitectura
    private val motionSensorController = MotionSensorController(context)
    private val motionArtifactEstimator = MotionArtifactEstimator()
    private val heartRateFusion = HeartRateFusion(30.0)
    private val beatClassifier = BeatClassifier()
    private val arrhythmiaScreening = ArrhythmiaScreening()
    private val sessionExporter = SessionExporter(context)
    private val calibrationManager = DeviceCalibrationManager(context)
    private val spo2Estimator = Spo2Estimator(calibrationManager)

    private var currentSession: MeasurementSession? = null
    private val waveformBuffer = LinkedList<Double>()
    private val maxWaveformSize = 300 // ~10 segundos a 30 FPS
    private val beatEventsBuffer = LinkedList<com.example.myapplication.ppg.BeatEvent>()
    private val maxBeatEvents = 50

    init {
        cameraController.onFrameAvailable = { frame ->
            processFrame(frame)
        }
    }

    fun start() {
        cameraController.start()
        motionSensorController.start()
        startNewSession()
    }

    fun stop() {
        cameraController.stop()
        motionSensorController.stop()
        currentSession?.end()
    }

    fun exportSession(): String? {
        return currentSession?.let { sessionExporter.exportSession(it) }
    }

    fun showCalibrationScreen() {
        _uiState.value = _uiState.value.copy(showCalibrationScreen = true)
    }

    fun hideCalibrationScreen() {
        _uiState.value = _uiState.value.copy(showCalibrationScreen = false)
    }

    fun hasCalibration(): Boolean {
        return calibrationManager.hasValidCalibration()
    }

    private fun startNewSession() {
        currentSession = MeasurementSession(
            sessionId = UUID.randomUUID().toString(),
            startTime = Date(),
            deviceModel = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            cameraId = "back", // Se actualizará cuando se obtenga de cámara
            exposureTimeNs = null,
            iso = null,
            frameDurationNs = null,
            torchEnabled = true,
            calibrationProfileId = null,
            algorithmVersion = "1.0"
        )
    }

    private fun processFrame(frame: com.example.myapplication.ppg.CameraFrame) {
        viewModelScope.launch {
            // 1. Evaluar movimiento
            val motionSample = motionSensorController.motionSample.value
            val motionResult = motionSample?.let { motionArtifactEstimator.evaluate(it) }
            val motionScore = motionResult?.motionScore ?: 0.0

            // 2. Evaluar contacto y calidad básica
            val hasContact = evaluateContact(frame)
            if (!hasContact) {
                resetMetrics(MeasurementState.NO_CONTACT, frame.actualFps)
                return@launch
            }

            // 3. Evaluar clipping y saturación
            if (frame.clipHighRatio > 0.3) {
                resetMetrics(MeasurementState.INVALID, frame.actualFps, "SATURACIÓN ÓPTICA")
                return@launch
            }

            if (frame.clipLowRatio > 0.8) {
                resetMetrics(MeasurementState.NO_CONTACT, frame.actualFps)
                return@launch
            }

            // 4. Evaluar movimiento excesivo
            if (motionScore > 0.3) {
                resetMetrics(MeasurementState.DEGRADED, frame.actualFps, "MOVIMIENTO EXCESIVO")
                return@launch
            }

            // 5. Calcular perfusion index
            val perfusionIndex = if (frame.redMean > 0) {
                (frame.redAcDc / frame.redMean) * 100.0
            } else 0.0

            // 6. Calcular SQI básico
            val sqi = calculateBasicSqi(frame, perfusionIndex, motionScore)

            // 7. Agregar a buffer de onda (usar canal verde)
            waveformBuffer.addLast(frame.greenMean)
            if (waveformBuffer.size > maxWaveformSize) {
                waveformBuffer.removeFirst()
            }

            // 8. Detección de latidos solo si SQI es suficiente
            var currentBpm: Double? = null
            var currentBeat: com.example.myapplication.ppg.BeatEvent? = null

            if (sqi > 0.5) {
                currentBeat = heartRateFusion.processSample(frame.greenMean, frame.timestampNs, sqi)
                if (currentBeat != null) {
                    feedbackController.trigger()
                    
                    // Clasificar latido
                    val classifiedBeat = beatClassifier.classify(currentBeat, sqi)
                    beatEventsBuffer.addLast(classifiedBeat)
                    if (beatEventsBuffer.size > maxBeatEvents) {
                        beatEventsBuffer.removeFirst()
                    }

                    // Evaluar arritmia
                    val arrhythmiaResult = arrhythmiaScreening.evaluate(classifiedBeat, sqi)
                    
                    currentBpm = heartRateFusion.getCurrentBpm()

                    // Actualizar sesión
                    currentSession?.addBeat(classifiedBeat)
                    if (arrhythmiaResult.isIrregular) {
                        currentSession?.addEvent(
                            MeasurementEvent(
                                timestamp = Date(),
                                type = com.example.myapplication.forensic.EventType.ARRHYTHMIA_DETECTED,
                                description = arrhythmiaResult.message
                            )
                        )
                    }
                }
            }

            // 9. Actualizar UI
            _uiState.value = _uiState.value.copy(
                state = if (sqi > 0.7) MeasurementState.MEASURING else if (sqi > 0.4) MeasurementState.WARMUP else MeasurementState.DEGRADED,
                bpm = currentBpm,
                bpmConfidence = currentBeat?.quality ?: 0.0,
                spo2 = null, // SpO2 requiere calibración, se manejará en pantalla de calibración
                spo2Message = "SpO₂ REQUIERE CALIBRACIÓN",
                sqi = sqi,
                perfusionIndex = perfusionIndex,
                motionScore = motionScore,
                waveform = waveformBuffer.toList(),
                beatEvents = beatEventsBuffer.toList(),
                isIrregular = beatEventsBuffer.any { it.type != com.example.myapplication.ppg.BeatType.NORMAL && it.type != com.example.myapplication.ppg.BeatType.INVALID_SIGNAL },
                irregularityMessage = if (beatEventsBuffer.isNotEmpty()) arrhythmiaScreening.evaluate(beatEventsBuffer.last(), sqi).message else "",
                statusMessage = getStatusMessage(sqi, motionScore),
                actualFps = frame.actualFps,
                exposureTimeNs = frame.exposureTimeNs,
                iso = frame.iso,
                frameDurationNs = frame.frameDurationNs
            )
        }
    }

    private fun evaluateContact(frame: com.example.myapplication.ppg.CameraFrame): Boolean {
        // Dominancia roja (tejido humano traslúcido)
        val isRedDominant = frame.redMean > (frame.greenMean * 1.5) && frame.redMean > (frame.blueMean * 2.0)
        val hasSufficientLight = frame.redMean > 50.0 && frame.greenMean > 30.0
        return isRedDominant && hasSufficientLight
    }

    private fun calculateBasicSqi(frame: com.example.myapplication.ppg.CameraFrame, perfusionIndex: Double, motionScore: Double): Double {
        var score = 0.0

        // Perfusion (30%)
        if (perfusionIndex in 0.05..5.0) score += 0.3
        else if (perfusionIndex in 0.01..10.0) score += 0.15

        // Sin clipping (30%)
        if (frame.clipHighRatio < 0.1 && frame.clipLowRatio < 0.1) score += 0.3
        else if (frame.clipHighRatio < 0.3) score += 0.15

        // Sin movimiento (40%)
        score += (1.0 - motionScore) * 0.4

        return score.coerceIn(0.0, 1.0)
    }

    private fun resetMetrics(state: MeasurementState, fps: Double, message: String? = null) {
        waveformBuffer.clear()
        beatEventsBuffer.clear()
        heartRateFusion.reset()
        beatClassifier.reset()
        arrhythmiaScreening.reset()
        
        _uiState.value = _uiState.value.copy(
            state = state,
            bpm = null,
            bpmConfidence = 0.0,
            spo2 = null,
            spo2Confidence = 0.0,
            sqi = 0.0,
            perfusionIndex = 0.0,
            motionScore = 0.0,
            waveform = emptyList(),
            beatEvents = emptyList(),
            isIrregular = false,
            irregularityMessage = "",
            statusMessage = message ?: getStatusMessage(state, 0.0),
            actualFps = fps
        )
    }

    private fun getStatusMessage(state: MeasurementState, motionScore: Double): String {
        return when (state) {
            MeasurementState.NO_CONTACT -> "COLOQUE DEDO EN CÁMARA"
            MeasurementState.CONTACT_PARTIAL -> "CONTACTO PARCIAL"
            MeasurementState.WARMUP -> "ESTABILIZANDO SEÑAL..."
            MeasurementState.MEASURING -> "MEDICIÓN ACTIVA"
            MeasurementState.DEGRADED -> if (motionScore > 0.3) "MOVIMIENTO EXCESIVO" else "SEÑAL DEGRADADA"
            MeasurementState.INVALID -> "SEÑAL INVÁLIDA"
            MeasurementState.CALIBRATION_REQUIRED -> "REQUIERE CALIBRACIÓN"
        }
    }
}
