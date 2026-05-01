package com.example.myapplication.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.camera.Camera2PpgController
import com.example.myapplication.haptics.BeatFeedbackController
import com.example.myapplication.ppg.*
import com.example.myapplication.signal.BpmEstimator
import com.example.myapplication.signal.PpgFrame
import com.example.myapplication.signal.PpgSignalBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*

/**
 * ViewModel orquestador del pipeline PPG.
 * 
 * PIPELINE: Camera → PpgFrameAnalyzer → PpgSignalProcessor → Filter → 
 *           SQI → PhysiologyClassifier → PeakDetector → BpmEstimator → 
 *           SpO2Estimator → RhythmAnalyzer → BeatFeedbackController → UiState
 * 
 * REGLA CRÍTICA: La UI nunca dibuja ondas cardíacas sin PPG_VALID.
 * Los beeps/vibraciones solo ocurren con latidos validados.
 */
class MonitorViewModel(
    private val cameraController: Camera2PpgController,
    private val feedbackController: BeatFeedbackController,
    private val context: android.content.Context
) : ViewModel() {

    data class MonitorUiState(
        // Estado fisiológico
        val validityState: PpgPhysiologyClassifier.PpgValidityState = 
            PpgPhysiologyClassifier.PpgValidityState.MEASURING_RAW_OPTICAL,
        val isPhysiologicalSignal: Boolean = false,
        val statusMessage: String = "MIDIENDO DATOS ÓPTICOS CRUDOS",
        val classificationReason: String = "",
        
        // Ondas
        val rawWaveform: List<Double> = emptyList(),
        val filteredWaveform: List<Double> = emptyList(),
        val showFilteredWaveform: Boolean = false, // Solo true si PPG_VALID
        
        // Métricas BPM
        val bpm: Double? = null,
        val bpmInstant: Double? = null,
        val bpmConfidence: Double = 0.0,
        val beatCount: Int = 0,
        val lastRrMs: Double? = null,
        
        // Métricas SpO2
        val spo2: Int? = null,
        val spo2Status: Spo2Estimator.Spo2Status = Spo2Estimator.Spo2Status.NOT_AVAILABLE,
        val spo2Confidence: Double = 0.0,
        val spo2Warning: String? = null,
        val ratioR: Double = 0.0,
        
        // Ritmo
        val rhythmState: RhythmAnalyzer.RhythmState = RhythmAnalyzer.RhythmState.INSUFFICIENT_DATA,
        val isIrregular: Boolean = false,
        val rmssd: Double = 0.0,
        val sdnn: Double = 0.0,
        val pnn50: Double = 0.0,
        val cv: Double = 0.0,
        
        // Calidad
        val sqi: Double = 0.0,
        val perfusionIndex: Double = 0.0,
        val motionScore: Double = 0.0,
        val snr: Double = 0.0,
        
        // Telemetría de cámara
        val cameraRunning: Boolean = false,
        val actualFps: Double = 0.0,
        val exposureTimeNs: Long? = null,
        val iso: Int? = null,
        val latencyMs: Double = 0.0,
        val clippingHigh: Double = 0.0,
        val clippingLow: Double = 0.0,
        
        // Controles UI
        val beepEnabled: Boolean = true,
        val vibrationEnabled: Boolean = true,
        
        // Debug
        val debugText: String = ""
    )

    private val _uiState = MutableStateFlow(MonitorUiState())
    val uiState = _uiState.asStateFlow()

    // Pipeline de procesamiento
    private val signalProcessor = PpgSignalProcessor(30.0)
    private val peakDetector = PpgPeakDetector()
    private val qualityEngine = PpgSignalQuality()
    private val physiologyClassifier = PpgPhysiologyClassifier()
    private val rhythmAnalyzer = RhythmAnalyzer()
    private val spo2Estimator = Spo2Estimator()
    private val bpmEstimator = BpmEstimator()
    private val motionDetector = com.example.myapplication.signal.MotionArtifactDetector(context)

    // Buffers
    private val waveformBuffer = LinkedList<Double>()
    private val rawWaveformBuffer = LinkedList<Double>()
    private val maxWaveformSize = 400
    
    // Tracking de tiempo con PPG válido
    private var ppgValidStartTime: Long = 0
    private var lastPpgValidState: Boolean = false
    private var frameCount: Long = 0
    private var lastFrameTime: Long = 0

    init {
        motionDetector.start()
        cameraController.onFrameAvailable = { frame ->
            frameCount++
            val frameStart = System.nanoTime()

            // Integrar movimiento real del acelerómetro
            val enrichedFrame = frame.copy(motionScore = motionDetector.getMotionScore())

            try {
                processFrame(enrichedFrame, frameStart)
            } catch (e: Exception) {
                Log.e("MonitorViewModel", "Error processing frame", e)
            }
        }
    }

    fun start() {
        try {
            cameraController.start()
            _uiState.value = _uiState.value.copy(
                cameraRunning = true,
                statusMessage = "INICIANDO CÁMARA Y FLASH..."
            )
        } catch (e: Exception) {
            Log.e("MonitorViewModel", "Error starting camera", e)
            _uiState.value = _uiState.value.copy(
                cameraRunning = false,
                statusMessage = "ERROR AL INICIAR CÁMARA"
            )
        }
    }

    fun stop() {
        cameraController.stop()
        motionDetector.stop()
        resetPipeline()
        _uiState.value = _uiState.value.copy(cameraRunning = false)
    }

    fun setBeepEnabled(enabled: Boolean) {
        feedbackController.isBeepEnabled = enabled
        _uiState.value = _uiState.value.copy(beepEnabled = enabled)
    }

    fun setVibrationEnabled(enabled: Boolean) {
        feedbackController.isVibrationEnabled = enabled
        _uiState.value = _uiState.value.copy(vibrationEnabled = enabled)
    }

    fun setSpO2Calibration(a: Double, b: Double, c: Double = 0.0) {
        spo2Estimator.setCalibration(a, b, c)
    }

    fun clearSpO2Calibration() {
        spo2Estimator.clearCalibration()
    }

    private fun processFrame(frame: PpgFrame, frameStartNs: Long) {
        viewModelScope.launch {
            val processingStart = System.nanoTime()

            // 1. PROCESAR SEÑAL (filtrado, AC/DC)
            val processed = signalProcessor.process(frame)

            // 2. CALCULAR SQI
            val sqiResult = qualityEngine.compute(
                frame = frame,
                acRed = processed.acRed,
                acGreen = processed.acGreen,
                filteredSignal = processed.filteredValue,
                isPeriodical = false // Se actualizará después de clasificación
            )

            // 3. CLASIFICAR FISIOLOGÍA
            val classification = physiologyClassifier.classify(
                frame = frame,
                filteredSignal = processed.filteredValue,
                acRed = processed.acRed,
                dcRed = processed.dcRed,
                acGreen = processed.acGreen,
                dcGreen = processed.dcGreen,
                sqi = sqiResult.totalSqi
            )
            
            // Actualizar tracking de tiempo PPG válido
            val isPpgValidNow = classification.state == 
                PpgPhysiologyClassifier.PpgValidityState.PPG_VALID ||
                classification.state == 
                PpgPhysiologyClassifier.PpgValidityState.PPG_DEGRADED
            
            if (isPpgValidNow && !lastPpgValidState) {
                ppgValidStartTime = System.nanoTime()
            }
            lastPpgValidState = isPpgValidNow
            
            val secondsWithValidPPG = if (isPpgValidNow && ppgValidStartTime > 0) {
                (System.nanoTime() - ppgValidStartTime) / 1_000_000_000.0
            } else 0.0
            
            // 4. DETECTAR PICOS (SOLO si PPG válido)
            val beatEvent = peakDetector.detect(
                filteredValue = processed.filteredValue,
                timestampNs = frame.timestampNs,
                sqi = sqiResult.totalSqi,
                validityState = classification.state,
                acComponent = processed.acGreen
            )
            
            // 5. FEEDBACK HÁPTICO (solo con latido válido y PPG validado)
            if (beatEvent != null) {
                feedbackController.triggerOnBeat(beatEvent, classification.state)
            }
            
            // 6. ESTIMAR BPM (solo si hay latidos)
            if (beatEvent != null) {
                bpmEstimator.addBeat(beatEvent)
            }
            
            // Congelar BPM si se pierde PPG
            val currentBpm = if (isPpgValidNow) {
                bpmEstimator.getCurrentBpm()
            } else {
                bpmEstimator.freeze().bpm // Usar último valor congelado
            }
            
            // 7. ESTIMAR SpO2 (solo con PPG válido sostenido)
            val spo2Result = if (isPpgValidNow) {
                spo2Estimator.estimate(
                    acRed = processed.acRed,
                    dcRed = processed.dcRed,
                    acGreen = processed.acGreen,
                    dcGreen = processed.dcGreen,
                    sqi = sqiResult.totalSqi,
                    validityState = classification.state,
                    secondsWithValidPPG = secondsWithValidPPG
                )
            } else {
                Spo2Estimator.Spo2Result(
                    value = null,
                    status = Spo2Estimator.Spo2Status.NOT_AVAILABLE,
                    confidence = 0.0,
                    ratioR = 0.0,
                    perfusionIndex = 0.0,
                    calibrationType = Spo2Estimator.CalibrationType.NONE,
                    isCalibrated = false,
                    warningMessage = "SpO2 requiere PPG válido"
                )
            }
            
            // 8. ANALIZAR RITMO (requiere suficiente ventana RR)
            beatEvent?.let { beat ->
                val raBeat = RhythmAnalyzer.ConfirmedBeat(
                    timestampNs = beat.timestampNs,
                    amplitude = beat.amplitude,
                    rrMs = beat.rrMs?.toLong() ?: 0,
                    confidence = beat.confidence,
                    sourceChannel = beat.sourceChannel
                )
                rhythmAnalyzer.addConfirmedBeat(raBeat)
            }
            val rhythmMetrics = if (isPpgValidNow && secondsWithValidPPG >= 20) {
                rhythmAnalyzer.analyze()
            } else {
                RhythmAnalyzer.RhythmMetrics(
                    state = RhythmAnalyzer.RhythmState.INSUFFICIENT_DATA,
                    rmssd = 0.0, sdnn = 0.0, pnn50 = 0.0, cv = 0.0,
                    irregularityIndex = 0.0, meanRR = 0.0, beatCount = 0
                )
            }
            
            // 9. ACTUALIZAR BUFFERS DE ONDA
            rawWaveformBuffer.addLast(processed.rawValue)
            if (rawWaveformBuffer.size > maxWaveformSize) rawWaveformBuffer.removeFirst()
            
            // Solo agregar a buffer filtrado si hay señal fisiológica
            if (classification.state != PpgPhysiologyClassifier.PpgValidityState.NO_PPG_PHYSIOLOGICAL_SIGNAL &&
                classification.state != PpgPhysiologyClassifier.PpgValidityState.MEASURING_RAW_OPTICAL) {
                waveformBuffer.addLast(processed.filteredValue)
                if (waveformBuffer.size > maxWaveformSize) waveformBuffer.removeFirst()
            } else {
                // Limpiar buffer filtrado si no hay PPG
                if (waveformBuffer.isNotEmpty()) {
                    waveformBuffer.clear()
                }
            }
            
            // 10. CALCULAR LATENCIA
            val processingEnd = System.nanoTime()
            val latencyMs = (processingEnd - processingStart) / 1_000_000.0
            
            // 11. ACTUALIZAR UI
            val fps = frame.exposureDiagnostics.frameDurationNs?.let {
                1_000_000_000.0 / it
            } ?: 0.0
            
            _uiState.value = MonitorUiState(
                validityState = classification.state,
                isPhysiologicalSignal = classification.isPhysiologicalSignal,
                statusMessage = getStatusMessage(classification.state),
                classificationReason = classification.reason,
                
                rawWaveform = rawWaveformBuffer.toList(),
                filteredWaveform = if (isPpgValidNow) waveformBuffer.toList() else emptyList(),
                showFilteredWaveform = isPpgValidNow,
                
                bpm = currentBpm,
                bpmInstant = beatEvent?.instantaneousBpm,
                bpmConfidence = bpmEstimator.getConfidence(),
                beatCount = bpmEstimator.getBeatCount(),
                lastRrMs = beatEvent?.rrMs,
                
                spo2 = spo2Result.value,
                spo2Status = spo2Result.status,
                spo2Confidence = spo2Result.confidence,
                spo2Warning = spo2Result.warningMessage,
                ratioR = spo2Result.ratioR,
                
                rhythmState = rhythmMetrics.state,
                isIrregular = rhythmMetrics.state != RhythmAnalyzer.RhythmState.REGULAR &&
                             rhythmMetrics.state != RhythmAnalyzer.RhythmState.INSUFFICIENT_DATA,
                rmssd = rhythmMetrics.rmssd,
                sdnn = rhythmMetrics.sdnn,
                pnn50 = rhythmMetrics.pnn50,
                cv = rhythmMetrics.cv,
                
                sqi = sqiResult.totalSqi,
                perfusionIndex = sqiResult.perfusionIndex,
                motionScore = frame.motionScore,
                snr = sqiResult.snr,

                cameraRunning = true,
                actualFps = fps,
                exposureTimeNs = frame.exposureDiagnostics.exposureTimeNs,
                iso = frame.exposureDiagnostics.iso,
                latencyMs = latencyMs,
                clippingHigh = frame.clipping.highClipRatio,
                clippingLow = frame.clipping.lowClipRatio,
                
                beepEnabled = feedbackController.isBeepEnabled,
                vibrationEnabled = feedbackController.isVibrationEnabled,
                
                debugText = buildDebugText(classification, fps, latencyMs, secondsWithValidPPG)
            )
        }
    }
    
    private fun buildDebugText(
        classification: PpgPhysiologyClassifier.ClassificationResult,
        fps: Double,
        latencyMs: Double,
        secondsValid: Double
    ): String {
        return buildString {
            appendLine("FPS: ${"%.1f".format(fps)} | Latency: ${"%.1f".format(latencyMs)}ms")
            appendLine("State: ${classification.state.name}")
            appendLine("Freq: ${"%.1f".format(classification.dominantFrequencyHz * 60)} BPM")
            appendLine("Valid PPG: ${"%.1f".format(secondsValid)}s")
            appendLine("Conf: ${"%.2f".format(classification.confidence)} | PI: ${"%.2f".format(classification.perfusionIndex)}")
        }.trim()
    }

    private fun resetPipeline() {
        waveformBuffer.clear()
        rawWaveformBuffer.clear()
        signalProcessor.reset()
        peakDetector.reset()
        qualityEngine.reset()
        physiologyClassifier.reset()
        rhythmAnalyzer.reset()
        spo2Estimator.reset()
        bpmEstimator.reset()
        feedbackController.reset()
        
        ppgValidStartTime = 0
        lastPpgValidState = false
        frameCount = 0
    }

    private fun getStatusMessage(state: PpgPhysiologyClassifier.PpgValidityState): String {
        return when (state) {
            PpgPhysiologyClassifier.PpgValidityState.MEASURING_RAW_OPTICAL -> 
                "MIDIENDO DATOS ÓPTICOS CRUDOS"
            PpgPhysiologyClassifier.PpgValidityState.NO_PPG_PHYSIOLOGICAL_SIGNAL -> 
                "NO HAY SEÑAL FISIOLÓGICA"
            PpgPhysiologyClassifier.PpgValidityState.SEARCHING_PPG -> 
                "BUSCANDO PPG FISIOLÓGICA..."
            PpgPhysiologyClassifier.PpgValidityState.PPG_CANDIDATE -> 
                "PPG CANDIDATO - VALIDANDO..."
            PpgPhysiologyClassifier.PpgValidityState.PPG_VALID -> 
                "PPG VÁLIDO"
            PpgPhysiologyClassifier.PpgValidityState.PPG_DEGRADED -> 
                "PPG DEGRADADO"
            PpgPhysiologyClassifier.PpgValidityState.SATURATED -> 
                "SEÑAL SATURADA"
            PpgPhysiologyClassifier.PpgValidityState.MOTION_ARTIFACT -> 
                "ARTEFACTO DE MOVIMIENTO"
            PpgPhysiologyClassifier.PpgValidityState.LOW_PERFUSION -> 
                "PERFUSIÓN INSUFICIENTE"
            PpgPhysiologyClassifier.PpgValidityState.ERROR -> 
                "ERROR DE PROCESAMIENTO"
        }
    }

    override fun onCleared() {
        super.onCleared()
        stop()
        motionDetector.stop()
        feedbackController.release()
    }
}
