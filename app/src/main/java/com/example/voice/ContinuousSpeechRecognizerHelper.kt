package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "ContinuousSpeech"

enum class ListeningState {
    IDLE,
    LISTENING,
    PROCESSING,
    AWAITING_RESTART,
    MUTED_FOR_TTS,
    ERROR
}

/**
 * ContinuousSpeechRecognizerHelper
 *
 * Handles continuous voice listening for natural language user commands.
 * Features:
 * - Continuous listening mode with automatic seamless recovery after user pauses or speech completion
 * - Single-shot listening mode for push-to-talk
 * - Audio feedback loop prevention (automatically mutes/pauses listening while TTS is speaking)
 * - Error resilience (handles ERROR_NO_MATCH, ERROR_SPEECH_TIMEOUT, ERROR_RECOGNIZER_BUSY with clean recreation)
 * - Real-time volume RMS dB levels for animated voice waveforms
 * - Live partial transcription and final command delivery
 */
class ContinuousSpeechRecognizerHelper(
    private val context: Context,
    private val onCommandRecognized: (String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var speechRecognizer: SpeechRecognizer? = null
    private var restartJob: Job? = null
    private var consecutiveErrorCount = 0
    private var currentLanguageCode: String = "en-US"

    // State Flows
    private val _isContinuousMode = MutableStateFlow(false)
    val isContinuousMode: StateFlow<Boolean> = _isContinuousMode.asStateFlow()

    private val _listeningState = MutableStateFlow(ListeningState.IDLE)
    val listeningState: StateFlow<ListeningState> = _listeningState.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _rmsDb = MutableStateFlow(0f)
    val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _lastRecognizedText = MutableStateFlow("")
    val lastRecognizedText: StateFlow<String> = _lastRecognizedText.asStateFlow()
    val spokenText: StateFlow<String> = _lastRecognizedText.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _statusMessage = MutableStateFlow("Ready")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    val isRecognitionAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Convenience method matching standard SpeechRecognizerHelper interface.
     * Uses continuous mode if isContinuousMode is true, otherwise single-shot.
     */
    fun startListening(languageCode: String = "en-US") {
        if (_isContinuousMode.value) {
            startContinuousListening(languageCode)
        } else {
            startSingleShotListening(languageCode)
        }
    }

    fun clearError() {
        _error.value = null
    }

    /**
     * Starts continuous listening mode. The assistant will continuously listen for commands,
     * restarting seamlessly when pauses occur or after commands are processed.
     */
    fun startContinuousListening(languageCode: String = "en-US") {
        currentLanguageCode = languageCode
        _isContinuousMode.value = true
        _error.value = null
        consecutiveErrorCount = 0
        startListeningInternal()
    }

    /**
     * Starts single-shot listening (push-to-talk). Stops automatically after one utterance.
     */
    fun startSingleShotListening(languageCode: String = "en-US") {
        currentLanguageCode = languageCode
        _isContinuousMode.value = false
        _error.value = null
        consecutiveErrorCount = 0
        startListeningInternal()
    }

    /**
     * Completely stops both continuous and single-shot listening.
     */
    fun stopListening() {
        _isContinuousMode.value = false
        restartJob?.cancel()
        restartJob = null
        stopRecognizer()
        _isListening.value = false
        _listeningState.value = ListeningState.IDLE
        _statusMessage.value = "Stopped"
        _rmsDb.value = 0f
    }

    /**
     * Temporarily mutes/pauses listening while TTS is speaking to prevent acoustic feedback loop.
     */
    fun pauseForTts() {
        if (_isContinuousMode.value || _isListening.value) {
            _listeningState.value = ListeningState.MUTED_FOR_TTS
            _statusMessage.value = "Assistant speaking..."
            stopRecognizer()
        }
    }

    /**
     * Resumes listening after TTS finishes speaking.
     */
    fun resumeAfterTts() {
        if (_isContinuousMode.value) {
            _listeningState.value = ListeningState.AWAITING_RESTART
            _statusMessage.value = "Resuming continuous listening..."
            scheduleRestart(delayMillis = 400)
        } else {
            _listeningState.value = ListeningState.IDLE
            _statusMessage.value = "Ready"
        }
    }

    /**
     * Internal starter that creates or re-uses the SpeechRecognizer on the Main Thread.
     */
    private fun startListeningInternal() {
        if (!isRecognitionAvailable) {
            _error.value = "Speech recognition is not available on this device."
            _listeningState.value = ListeningState.ERROR
            return
        }

        mainHandler.post {
            try {
                // Ensure existing recognizer is cleanly torn down before starting new session
                cleanupRecognizer()

                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(createRecognitionListener())
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguageCode)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    // Request complete silence threshold
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
                }

                speechRecognizer?.startListening(intent)
                _listeningState.value = ListeningState.LISTENING
                _statusMessage.value = if (_isContinuousMode.value) "Listening continuously..." else "Listening..."
                _partialText.value = ""
            } catch (e: Exception) {
                Log.e(TAG, "Error starting speech recognition", e)
                _error.value = "Failed to start speech recognition: ${e.localizedMessage}"
                _listeningState.value = ListeningState.ERROR
                handleContinuousRecovery(isTransient = true)
            }
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _isListening.value = true
                _listeningState.value = ListeningState.LISTENING
                _error.value = null
                consecutiveErrorCount = 0
                _statusMessage.value = if (_isContinuousMode.value) "Listening continuously..." else "Listening..."
            }

            override fun onBeginningOfSpeech() {
                _isListening.value = true
                _statusMessage.value = "Detecting speech..."
            }

            override fun onRmsChanged(rmsdB: Float) {
                _rmsDb.value = rmsdB
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _isListening.value = false
                _listeningState.value = ListeningState.PROCESSING
                _statusMessage.value = "Processing command..."
            }

            override fun onError(errorCode: Int) {
                _isListening.value = false
                _rmsDb.value = 0f

                val isTransientSilence = (errorCode == SpeechRecognizer.ERROR_NO_MATCH ||
                        errorCode == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)

                val errorMsg = when (errorCode) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error during recognition"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy, resetting..."
                    SpeechRecognizer.ERROR_SERVER -> "Speech server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout (silence)"
                    else -> "Recognition error ($errorCode)"
                }

                if (!isTransientSilence) {
                    _error.value = errorMsg
                    Log.w(TAG, "SpeechRecognizer error: $errorMsg (code $errorCode)")
                }

                if (errorCode == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    // Fatal: Don't loop if user denied mic permission
                    _isContinuousMode.value = false
                    _listeningState.value = ListeningState.ERROR
                    return
                }

                if (_isContinuousMode.value) {
                    handleContinuousRecovery(isTransient = isTransientSilence)
                } else {
                    _listeningState.value = if (isTransientSilence) ListeningState.IDLE else ListeningState.ERROR
                }
            }

            override fun onResults(results: Bundle?) {
                _isListening.value = false
                _rmsDb.value = 0f
                _listeningState.value = ListeningState.PROCESSING

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val recognizedText = matches[0].trim()
                    if (recognizedText.isNotEmpty()) {
                        _lastRecognizedText.value = recognizedText
                        _partialText.value = recognizedText
                        _statusMessage.value = "Command: \"$recognizedText\""
                        onCommandRecognized(recognizedText)
                    }
                }

                if (_isContinuousMode.value) {
                    // Schedule seamless restart after command is handed over
                    scheduleRestart(delayMillis = 750)
                } else {
                    _listeningState.value = ListeningState.IDLE
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    _partialText.value = matches[0]
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun handleContinuousRecovery(isTransient: Boolean) {
        if (!_isContinuousMode.value) return

        consecutiveErrorCount++
        val backoffDelay = when {
            isTransient -> 250L // Normal pause between speaking turns
            consecutiveErrorCount < 3 -> 500L
            consecutiveErrorCount < 6 -> 1500L
            else -> 3000L // Avoid tight error spinning
        }

        _listeningState.value = ListeningState.AWAITING_RESTART
        _statusMessage.value = "Listening..."
        scheduleRestart(backoffDelay)
    }

    private fun scheduleRestart(delayMillis: Long) {
        restartJob?.cancel()
        restartJob = scope.launch {
            delay(delayMillis)
            if (_isContinuousMode.value && _listeningState.value != ListeningState.MUTED_FOR_TTS) {
                startListeningInternal()
            }
        }
    }

    private fun stopRecognizer() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (_: Exception) {}
        }
    }

    private fun cleanupRecognizer() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (_: Exception) {}
    }

    fun destroy() {
        stopListening()
        cleanupRecognizer()
        scope.cancel()
    }
}
