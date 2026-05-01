package com.example.myapplication.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.camera.Camera2PpgController
import com.example.myapplication.ppg.*
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
        val physiologyState: PpgPhysiologyClassifier.PpgValidityState = PpgPhysiologyClassifier.PpgValidityState.RAW_OPTICAL_ONLY,
        val bpm: Double? = null,
        val bpmConfidence: Double = 0.0,
        val spo2: Int? = null,
        val spo2Status: String = "SIN CALIBRACIÓN",
        val spo2RatioR: Double = 0.0,
        val sqi: Double = 0.0,
        val snr: Double = 0.0,
        val perfusionIndex: Double = 0.0,
        val motionScore: Double = 0.0,
        val waveform: List<Double> = emptyList(),
        val rawWaveform: List<Double> = emptyList(),
        val confirmedBeats: List<RhythmAnalyzer.ConfirmedBeat> = emptyList(),
        val rrIntervals: List<Long> = emptyList(),
        val isIrregular: Boolean = false,
        val rhythmState: RhythmAnalyzer.RhythmState = RhythmAnalyzer.RhythmState.INSUFFICIENT_DATA,
        val rmssd: Double = 0.0,
        val sdnn: Double = 0.0,
        val pnn50: Double = 0.0,
        val cv: Double = 0.0,
        val statusMessage: String = "SEÑAL ÓPTICA CRUDA",
        val classificationReason: String = "",
        val actualFps: Double = 0.0,
        val exposureTimeNs: Long? = null,
        val iso: Int? = null,
        val frameDurationNs: Long? = null,
        val clippingHigh: Double = 0.0,
        val clippingLow: Double = 0.0,
        val dominantFrequency: Double = 0.0
    )

    private val _uiState = MutableStateFlow(MonitorUiState())
    val uiState = _uiState.asStateFlow()

    // Motores de procesamiento de señal
    private val signalProcessor = PpgSignalProcessor(30.0)
    private val peakDetector = PpgPeakDetector()
    private val qualityEngine = PpgSignalQuality()
    private val physiologyClassifier = PpgPhysiologyClassifier()
    private val rhythmAnalyzer = RhythmAnalyzer()
    private val spo2Estimator = Spo2Estimator()

    private val waveformBuffer = LinkedList<Double>()
    private val rawWaveformBuffer = LinkedList<Double>()
    private val maxWaveformSize = 400 // ~13 segundos a 30 FPS

    init {
        cameraController.onFrameAvailable = { sample ->
            try {
                processFrame(sample)
            } catch (e: Exception) {
                Log.e("MonitorViewModel", "Error processing frame", e)
            }
        }
    }

    fun start() {
        try {
            cameraController.start()
            _uiState.value = _uiState.value.copy(statusMessage = "INICIANDO CÁMARA...")
        } catch (e: Exception) {
            Log.e("MonitorViewModel", "Error starting camera", e)
            _uiState.value = _uiState.value.copy(statusMessage = "ERROR AL INICIAR CÁMARA")
        }
    }

    fun stop() {
        cameraController.stop()
        resetMetrics()
    }

    fun setSpO2Calibration(a: Double, b: Double, c: Double = 0.0) {
        spo2Estimator.setCalibration(a, b, c)
    }

    fun clearSpO2Calibration() {
        spo2Estimator.clearCalibration()
    }

    private fun processFrame(sample: PpgSample) {
        viewModelScope.launch {
            // 1. Procesar Señal (Filtrado y AC/DC)
            val processed = signalProcessor.process(sample)
            
            // 2. Clasificación Fisiológica
            val classification = physiologyClassifier.classify(
                sample = sample,
                filteredSignal = processed.filteredValue,
                acRed = processed.acRed,
                acGreen = processed.acGreen,
                sqi = 0.0 // Se calculará después
            )

            // 3. Evaluar Calidad (SQI)
            val sqiResult = qualityEngine.compute(
                sample = sample,
                acRed = processed.acRed,
                acGreen = processed.acGreen,
                filteredSignal = processed.filteredValue,
                isPeriodical = classification.isPeriodical
            )

            // 4. Detectar Latidos (solo si hay señal fisiológica)
            val beat = if (classification.state != PpgPhysiologyClassifier.PpgValidityState.RAW_OPTICAL_ONLY &&
                           classification.state != PpgPhysiologyClassifier.PpgValidityState.NO_PHYSIOLOGICAL_SIGNAL) {
                peakDetector.detect(
                    filteredValue = processed.filteredValue,
                    timestampNs = sample.timestampNs,
                    sqi = sqiResult.totalSqi,
                    acGreen = processed.acGreen,
                    dcGreen = processed.dcGreen
                )
            } else null

            // 5. Feedback háptico solo por latido confirmado
            if (beat != null && beat.confidence > 0.7) {
                feedbackController.trigger()
            }

            // 6. Análisis de Ritmo
            if (beat != null) {
                val rhythmBeat = RhythmAnalyzer.ConfirmedBeat(
                    timestampNs = beat.timestampNs,
                    amplitude = beat.amplitude,
                    rrMs = beat.rrMs,
                    confidence = beat.confidence,
                    sourceChannel = beat.sourceChannel
                )
                rhythmAnalyzer.addConfirmedBeat(rhythmBeat)
            }

            val rhythmMetrics = rhythmAnalyzer.analyze()

            // 7. Estimación de SpO2 (estimar siempre que haya señal fisiológica)
            val spo2Result = if (classification.state != PpgPhysiologyClassifier.PpgValidityState.RAW_OPTICAL_ONLY &&
                               classification.state != PpgPhysiologyClassifier.PpgValidityState.NO_PHYSIOLOGICAL_SIGNAL) {
                spo2Estimator.estimate(
                    acRed = processed.acRed,
                    dcRed = processed.dcRed,
                    acGreen = processed.acGreen,
                    dcGreen = processed.dcGreen,
                    sqi = sqiResult.totalSqi,
                    windowStability = sqiResult.isStable.let { if (it) 1.0 else 0.0 }
                )
            } else {
                Spo2Estimator.Spo2Result(null, Spo2Estimator.Spo2Status.NOT_AVAILABLE, 0.0, 0.0, "NO APLICABLE")
            }

            // 8. Calcular BPM desde RR confirmados
            val bpm = if (rhythmMetrics.beatCount >= 2) {
                60000.0 / rhythmMetrics.meanRR
            } else null

            // 9. Actualizar buffers de onda
            waveformBuffer.addLast(processed.filteredValue)
            if (waveformBuffer.size > maxWaveformSize) waveformBuffer.removeFirst()

            rawWaveformBuffer.addLast(processed.rawValue)
            if (rawWaveformBuffer.size > maxWaveformSize) rawWaveformBuffer.removeFirst()

            // 10. Actualizar UI
            _uiState.value = _uiState.value.copy(
                physiologyState = classification.state,
                bpm = bpm,
                bpmConfidence = if (rhythmMetrics.beatCount >= 5) 0.8 else 0.0,
                spo2 = spo2Result.value,
                spo2Status = spo2Result.status.name,
                spo2RatioR = spo2Result.ratioR,
                sqi = sqiResult.totalSqi,
                snr = sqiResult.snr,
                perfusionIndex = sqiResult.perfusionIndex,
                motionScore = sample.motionScore,
                waveform = waveformBuffer.toList(),
                rawWaveform = rawWaveformBuffer.toList(),
                confirmedBeats = rhythmAnalyzer.getConfirmedBeats(),
                rrIntervals = rhythmAnalyzer.getRRIntervals(),
                isIrregular = rhythmAnalyzer.isArhythmic,
                rhythmState = rhythmMetrics.state,
                rmssd = rhythmMetrics.rmssd,
                sdnn = rhythmMetrics.sdnn,
                pnn50 = rhythmMetrics.pnn50,
                cv = rhythmMetrics.cv,
                statusMessage = getStatusMessage(classification.state, sqiResult.totalSqi),
                classificationReason = classification.reason,
                actualFps = sample.exposureDiagnostics.frameDurationNs?.let { 1_000_000_000.0 / it } ?: 0.0,
                exposureTimeNs = sample.exposureDiagnostics.exposureTimeNs,
                iso = sample.exposureDiagnostics.iso,
                frameDurationNs = sample.exposureDiagnostics.frameDurationNs,
                clippingHigh = sample.clipping.highClipRatio,
                clippingLow = sample.clipping.lowClipRatio,
                dominantFrequency = classification.dominantFrequency
            )
        }
    }

    private fun resetMetrics() {
        waveformBuffer.clear()
        rawWaveformBuffer.clear()
        signalProcessor.reset()
        peakDetector.reset()
        qualityEngine.reset()
        physiologyClassifier.reset()
        rhythmAnalyzer.reset()
        _uiState.value = _uiState.value.copy(
            physiologyState = PpgPhysiologyClassifier.PpgValidityState.RAW_OPTICAL_ONLY,
            bpm = null,
            spo2 = null,
            sqi = 0.0,
            waveform = emptyList(),
            rawWaveform = emptyList(),
            confirmedBeats = emptyList(),
            rrIntervals = emptyList(),
            isIrregular = false,
            rhythmState = RhythmAnalyzer.RhythmState.INSUFFICIENT_DATA,
            statusMessage = "SEÑAL ÓPTICA CRUDA",
            classificationReason = ""
        )
    }

    private fun getStatusMessage(state: PpgPhysiologyClassifier.PpgValidityState, sqi: Double): String {
        return when (state) {
            PpgPhysiologyClassifier.PpgValidityState.RAW_OPTICAL_ONLY -> "SEÑAL ÓPTICA CRUDA"
            PpgPhysiologyClassifier.PpgValidityState.NO_PHYSIOLOGICAL_SIGNAL -> "NO HAY SEÑAL FISIOLÓGICA"
            PpgPhysiologyClassifier.PpgValidityState.PPG_CANDIDATE -> "PPG CANDIDATO"
            PpgPhysiologyClassifier.PpgValidityState.PPG_VALID -> "PPG VÁLIDO"
            PpgPhysiologyClassifier.PpgValidityState.BIOMETRIC_VALID -> "MÉTRICAS BIOMÉDICAS VÁLIDAS"
        }
    }
}
